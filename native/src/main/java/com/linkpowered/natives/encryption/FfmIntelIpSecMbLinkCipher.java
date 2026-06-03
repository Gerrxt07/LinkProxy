/*
 * Copyright (C) 2026 Link Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.linkpowered.natives.encryption;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import com.google.common.base.Preconditions;
import com.linkpowered.natives.util.BufferPreference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Locale;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implements AES-CFB8 encryption/decryption through Intel IPsec Multi-Buffer using FFM downcalls.
 */
public class FfmIntelIpSecMbLinkCipher implements LinkCipher {

  private static final int AES_BLOCK_BYTES = 16;
  private static final int AES_128_EXPANDED_KEY_BYTES = 240;

  public static final LinkCipherFactory FACTORY = new LinkCipherFactory() {
    @Override
    public LinkCipher forEncryption(SecretKey key) throws GeneralSecurityException {
      return new FfmIntelIpSecMbLinkCipher(true, key);
    }

    @Override
    public LinkCipher forDecryption(SecretKey key) throws GeneralSecurityException {
      return new FfmIntelIpSecMbLinkCipher(false, key);
    }
  };

  private final Arena arena = Arena.ofShared();
  private final MemorySegment expandedKey = arena.allocate(AES_128_EXPANDED_KEY_BYTES, 16);
  private final MemorySegment iv = arena.allocate(AES_BLOCK_BYTES, 16);
  private final boolean encrypt;
  private boolean disposed = false;

  private FfmIntelIpSecMbLinkCipher(boolean encrypt, SecretKey key) throws GeneralSecurityException {
    this.encrypt = encrypt;
    byte[] encoded = key.getEncoded();
    if (encoded.length != AES_BLOCK_BYTES) {
      throw new GeneralSecurityException("Intel IPsec-MB AES-CFB8 requires an AES-128 key");
    }
    MemorySegment keySegment = arena.allocate(encoded.length, 16);
    MemorySegment.copy(encoded, 0, keySegment, JAVA_BYTE, 0, encoded.length);
    MemorySegment.copy(encoded, 0, iv, JAVA_BYTE, 0, encoded.length);
    expandKey(keySegment, expandedKey);
  }

  /**
   * Verifies that IPsec-MB symbols are present and produce Minecraft-compatible AES-CFB8.
   */
  public static void ensureAvailable() {
    Bindings.ensureLoaded();
    selfTest();
  }

  @Override
  public void process(ByteBuf source) {
    ensureNotDisposed();
    Preconditions.checkArgument(source.hasMemoryAddress(), "No source memory address");

    int index = source.readerIndex();
    int len = source.readableBytes();
    for (int i = 0; i < len; i++) {
      byte input = source.getByte(index + i);
      MemorySegment position = MemorySegment.ofAddress(source.memoryAddress() + index + i);
      cfbOne(position, position, iv, expandedKey, 1);
      byte output = source.getByte(index + i);
      shiftIv(encrypt ? output : input);
    }
  }

  @Override
  public void close() {
    if (!disposed) {
      arena.close();
    }
    disposed = true;
  }

  @Override
  public BufferPreference preferredBufferType() {
    return BufferPreference.DIRECT_REQUIRED;
  }

  private void ensureNotDisposed() {
    Preconditions.checkState(!disposed, "Object already disposed");
  }

  private void shiftIv(byte next) {
    for (int i = 1; i < AES_BLOCK_BYTES; i++) {
      iv.set(JAVA_BYTE, i - 1L, iv.get(JAVA_BYTE, i));
    }
    iv.set(JAVA_BYTE, AES_BLOCK_BYTES - 1, next);
  }

  private static void expandKey(MemorySegment key, MemorySegment expandedKey) {
    try {
      Bindings.KEY_EXPANSION.invokeExact(key, expandedKey);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Intel IPsec-MB AES-128 key expansion failed", throwable);
    }
  }

  private static void cfbOne(
      MemorySegment output,
      MemorySegment input,
      MemorySegment iv,
      MemorySegment expandedKey,
      long length) {
    try {
      Bindings.AES_CFB_ONE.invokeExact(output, input, iv, expandedKey, length);
    } catch (Throwable throwable) {
      throw new IllegalStateException("Intel IPsec-MB AES-CFB failed", throwable);
    }
  }

  private static void selfTest() {
    byte[] keyBytes = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    byte[] plainBytes = "link-native-ipsecmb-cfb8-self-test".getBytes(StandardCharsets.US_ASCII);
    SecretKey key = new SecretKeySpec(keyBytes, "AES");

    ByteBuf javaEncrypted = Unpooled.wrappedBuffer(plainBytes.clone());
    ByteBuf intelEncrypted = Unpooled.directBuffer(plainBytes.length);
    ByteBuf decrypted = Unpooled.directBuffer(plainBytes.length);
    intelEncrypted.writeBytes(plainBytes);
    decrypted.writeBytes(plainBytes);
    try (LinkCipher javaCipher = JavaLinkCipher.FACTORY.forEncryption(key);
        LinkCipher intelEncrypt = FACTORY.forEncryption(key);
        LinkCipher intelDecrypt = FACTORY.forDecryption(key)) {
      javaCipher.process(javaEncrypted);
      intelEncrypt.process(intelEncrypted);
      if (!javaEncrypted.equals(intelEncrypted)) {
        throw new IllegalStateException("Intel IPsec-MB AES-CFB output does not match AES-CFB8");
      }
      decrypted.clear().writeBytes(intelEncrypted, intelEncrypted.readerIndex(),
          intelEncrypted.readableBytes());
      intelDecrypt.process(decrypted);
      if (!decrypted.equals(Unpooled.wrappedBuffer(plainBytes))) {
        throw new IllegalStateException("Intel IPsec-MB AES-CFB decrypt self-test failed");
      }
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Intel IPsec-MB AES-CFB self-test failed", exception);
    } finally {
      javaEncrypted.release();
      intelEncrypted.release();
      decrypted.release();
    }
  }

  private static final class Bindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOLS = SymbolLookup.loaderLookup()
        .or(LINKER.defaultLookup());
    private static final MethodHandle KEY_EXPANSION = downcall(
        "aes_keyexp_128_enc_" + selectedArchitecture(),
        FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
    private static final MethodHandle AES_CFB_ONE = downcall(
        "aes_cfb_128_one_" + selectedArchitecture(),
        FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));

    private static void ensureLoaded() {
      // Forces static binding resolution.
    }

    private static String selectedArchitecture() {
      String override = System.getProperty("link.ipsecmb.arch");
      if (override != null && !override.isBlank()) {
        return override.toLowerCase(Locale.ROOT);
      }

      String flags = readCpuFlags();
      if (flags.contains("avx512f") && flags.contains("vaes")) {
        return "avx512";
      }
      if (flags.contains("avx2")) {
        return "avx2";
      }
      if (flags.contains("avx")) {
        return "avx";
      }
      return "sse";
    }

    private static String readCpuFlags() {
      try {
        return Files.readString(Path.of("/proc/cpuinfo"), StandardCharsets.UTF_8)
            .toLowerCase(Locale.ROOT);
      } catch (Exception ignored) {
        return "";
      }
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
      MemorySegment symbol = SYMBOLS.find(name)
          .orElseThrow(() -> new IllegalStateException("Missing native symbol " + name));
      return LINKER.downcallHandle(symbol, descriptor);
    }
  }
}
