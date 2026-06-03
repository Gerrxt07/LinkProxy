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
import static java.lang.foreign.ValueLayout.JAVA_INT;

import com.google.common.base.Preconditions;
import com.linkpowered.natives.util.BufferPreference;
import io.netty.buffer.ByteBuf;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.security.GeneralSecurityException;
import javax.crypto.SecretKey;

/**
 * Implements AES-CFB8 encryption/decryption through OpenSSL using FFM downcalls.
 */
public class FfmOpenSslLinkCipher implements LinkCipher {

  public static final LinkCipherFactory FACTORY = new LinkCipherFactory() {
    @Override
    public LinkCipher forEncryption(SecretKey key) throws GeneralSecurityException {
      return new FfmOpenSslLinkCipher(true, key);
    }

    @Override
    public LinkCipher forDecryption(SecretKey key) throws GeneralSecurityException {
      return new FfmOpenSslLinkCipher(false, key);
    }
  };

  private final Arena arena = Arena.ofShared();
  private final MemorySegment ctx;
  private final MemorySegment updateLength = arena.allocate(JAVA_INT);
  private boolean disposed = false;

  private FfmOpenSslLinkCipher(boolean encrypt, SecretKey key) throws GeneralSecurityException {
    this.ctx = ctxNew();
    if (ctx.equals(MemorySegment.NULL)) {
      throw new GeneralSecurityException("OpenSSL failed to allocate cipher context");
    }
    byte[] encoded = key.getEncoded();
    MemorySegment keySegment = arena.allocate(encoded.length, 1);
    MemorySegment.copy(encoded, 0, keySegment, JAVA_BYTE, 0, encoded.length);
    int result = cipherInit(ctx, aes128Cfb8(), MemorySegment.NULL, keySegment, keySegment, encrypt);
    if (result != 1) {
      close();
      throw new GeneralSecurityException("OpenSSL failed to initialize AES-CFB8");
    }
  }

  @Override
  public void process(ByteBuf source) {
    ensureNotDisposed();

    MemorySegment base = MemorySegment.ofAddress(source.memoryAddress() + source.readerIndex());
    int len = source.readableBytes();
    int result = cipherUpdate(ctx, base, updateLength, base, len);
    if (result != 1) {
      throw new IllegalStateException("OpenSSL failed to process AES-CFB8 data");
    }
  }

  @Override
  public void close() {
    if (!disposed) {
      ctxFree(ctx);
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

  private static MemorySegment ctxNew() {
    try {
      return (MemorySegment) Bindings.CTX_NEW.invokeExact();
    } catch (Throwable throwable) {
      throw new IllegalStateException("EVP_CIPHER_CTX_new failed", throwable);
    }
  }

  private static void ctxFree(MemorySegment ctx) {
    try {
      Bindings.CTX_FREE.invokeExact(ctx);
    } catch (Throwable throwable) {
      throw new IllegalStateException("EVP_CIPHER_CTX_free failed", throwable);
    }
  }

  private static MemorySegment aes128Cfb8() {
    try {
      return (MemorySegment) Bindings.AES_128_CFB8.invokeExact();
    } catch (Throwable throwable) {
      throw new IllegalStateException("EVP_aes_128_cfb8 failed", throwable);
    }
  }

  private static int cipherInit(
      MemorySegment ctx,
      MemorySegment type,
      MemorySegment impl,
      MemorySegment key,
      MemorySegment iv,
      boolean encrypt) {
    try {
      return (int) Bindings.CIPHER_INIT.invokeExact(ctx, type, impl, key, iv, encrypt ? 1 : 0);
    } catch (Throwable throwable) {
      throw new IllegalStateException("EVP_CipherInit_ex failed", throwable);
    }
  }

  private static int cipherUpdate(
      MemorySegment ctx,
      MemorySegment output,
      MemorySegment outputLength,
      MemorySegment input,
      int inputLength) {
    try {
      return (int) Bindings.CIPHER_UPDATE.invokeExact(ctx, output, outputLength, input, inputLength);
    } catch (Throwable throwable) {
      throw new IllegalStateException("EVP_CipherUpdate failed", throwable);
    }
  }

  private static final class Bindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOLS = SymbolLookup.loaderLookup()
        .or(LINKER.defaultLookup());
    private static final MethodHandle CTX_NEW = downcall(
        "EVP_CIPHER_CTX_new", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle CTX_FREE = downcall(
        "EVP_CIPHER_CTX_free", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle AES_128_CFB8 = downcall(
        "EVP_aes_128_cfb8", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle CIPHER_INIT = downcall(
        "EVP_CipherInit_ex",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle CIPHER_UPDATE = downcall(
        "EVP_CipherUpdate",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
      MemorySegment symbol = SYMBOLS.find(name)
          .orElseThrow(() -> new IllegalStateException("Missing native symbol " + name));
      return LINKER.downcallHandle(symbol, descriptor);
    }
  }
}
