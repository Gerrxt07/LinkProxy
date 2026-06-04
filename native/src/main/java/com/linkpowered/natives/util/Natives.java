/*
 * Copyright (C) 2018-2023 Link Contributors
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

package com.linkpowered.natives.util;

import com.google.common.collect.ImmutableList;
import com.linkpowered.natives.NativeSetupException;
import com.linkpowered.natives.compression.FfmLibdeflateLinkCompressor;
import com.linkpowered.natives.compression.JavaLinkCompressor;
import com.linkpowered.natives.compression.LibdeflateLinkCompressor;
import com.linkpowered.natives.compression.LinkCompressorFactory;
import com.linkpowered.natives.encryption.FfmIntelIpSecMbLinkCipher;
import com.linkpowered.natives.encryption.FfmOpenSslLinkCipher;
import com.linkpowered.natives.encryption.JavaLinkCipher;
import com.linkpowered.natives.encryption.LinkCipher;
import com.linkpowered.natives.encryption.LinkCipherFactory;
import com.linkpowered.natives.encryption.NativeLinkCipher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import javax.crypto.spec.SecretKeySpec;

/**
 * Enumerates all supported natives for Link.
 */
public class Natives {

  private Natives() {
    throw new AssertionError();
  }

  private static Runnable copyAndLoadNative(String path) {
    return () -> {
      try {
        InputStream nativeLib = Natives.class.getResourceAsStream(path);
        if (nativeLib == null) {
          throw new IllegalStateException("Native library " + path + " not found.");
        }

        Path tempFile = createTemporaryNativeFilename(path.substring(path.lastIndexOf('.')));
        Files.copy(nativeLib, tempFile, StandardCopyOption.REPLACE_EXISTING);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
          try {
            Files.deleteIfExists(tempFile);
          } catch (IOException ignored) {
            // Well, it doesn't matter...
          }
        }));

        try {
          System.load(tempFile.toAbsolutePath().toString());
        } catch (UnsatisfiedLinkError e) {
          throw new NativeSetupException("Unable to load native " + tempFile.toAbsolutePath(), e);
        }
      } catch (IOException e) {
        throw new NativeSetupException("Unable to copy natives", e);
      }
    };
  }

  private static Path createTemporaryNativeFilename(String ext) throws IOException {
    String temporaryFolderPath = System.getProperty("link.natives-tmpdir");
    if (temporaryFolderPath != null) {
      return Files.createTempFile(Path.of(temporaryFolderPath), "native-", ext);
    } else {
      return Files.createTempFile("native-", ext);
    }
  }

  private static Runnable loadSystemLibrary(String name, Runnable afterLoad) {
    return () -> {
      try {
        System.loadLibrary(name);
        afterLoad.run();
      } catch (UnsatisfiedLinkError e) {
        throw new NativeSetupException("Unable to load system library " + name, e);
      }
    };
  }

  private static LinkCipherFactory requireJavaCompatibleCipher(LinkCipherFactory factory) {
    if (factory == JavaLinkCipher.FACTORY) {
      return factory;
    }

    final byte[] keyBytes = {
        0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
        (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb,
        (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff
    };
    final byte[] data = {
        0x0f, 0x22, 0x35, 0x48, 0x5b, 0x6e, 0x71, (byte) 0x84,
        (byte) 0x97, (byte) 0xaa, (byte) 0xbd, (byte) 0xd0,
        (byte) 0xe3, (byte) 0xf6, 0x09, 0x1c, 0x2f, 0x42, 0x55
    };
    SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

    try (LinkCipher nativeEncrypt = factory.forEncryption(key);
        LinkCipher nativeDecrypt = factory.forDecryption(key);
        LinkCipher javaEncrypt = JavaLinkCipher.FACTORY.forEncryption(key)) {
      ByteBuf nativeEncrypted = compatibleBuffer(nativeEncrypt).writeBytes(data);
      ByteBuf javaEncrypted = Unpooled.buffer(data.length).writeBytes(data);
      ByteBuf nativeDecrypted = compatibleBuffer(nativeDecrypt).writeBytes(data);
      try {
        nativeEncrypt.process(nativeEncrypted);
        javaEncrypt.process(javaEncrypted);
        if (!ByteBufUtil.equals(nativeEncrypted, javaEncrypted)) {
          throw new NativeSetupException("Native AES-CFB8 output does not match Java cipher");
        }

        nativeDecrypted.clear().writeBytes(javaEncrypted, javaEncrypted.readerIndex(),
            javaEncrypted.readableBytes());
        nativeDecrypt.process(nativeDecrypted);
        if (!ByteBufUtil.equals(nativeDecrypted, Unpooled.wrappedBuffer(data))) {
          throw new NativeSetupException("Native AES-CFB8 cannot decrypt Java cipher output");
        }
      } finally {
        nativeEncrypted.release();
        javaEncrypted.release();
        nativeDecrypted.release();
      }
    } catch (GeneralSecurityException e) {
      throw new NativeSetupException("Unable to validate native AES-CFB8 cipher", e);
    }

    return factory;
  }

  private static ByteBuf compatibleBuffer(LinkCipher cipher) {
    return switch (cipher.preferredBufferType()) {
      case DIRECT_REQUIRED, DIRECT_PREFERRED -> Unpooled.directBuffer();
      case HEAP_REQUIRED, HEAP_PREFERRED -> Unpooled.buffer();
    };
  }

  public static final NativeCodeLoader<LinkCompressorFactory> compress = new NativeCodeLoader<>(
      ImmutableList.of(
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-compress.so"),
              "libdeflate FFM (Linux x86_64)",
              FfmLibdeflateLinkCompressor.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-compress.so"),
              "libdeflate (Linux x86_64)",
              LibdeflateLinkCompressor.FACTORY), // compiled with Ubuntu 20.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64_MUSL,
              copyAndLoadNative("/linux_x86_64/link-compress-musl.so"),
              "libdeflate FFM (Linux x86_64, musl)",
              FfmLibdeflateLinkCompressor.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64_MUSL,
              copyAndLoadNative("/linux_x86_64/link-compress-musl.so"),
              "libdeflate (Linux x86_64, musl)",
              LibdeflateLinkCompressor.FACTORY), // compiled with Alpine 3.18

          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-compress.so"),
              "libdeflate FFM (Linux aarch64)",
              FfmLibdeflateLinkCompressor.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-compress.so"),
              "libdeflate (Linux aarch64)",
              LibdeflateLinkCompressor.FACTORY), // compiled with Ubuntu 20.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64_MUSL,
              copyAndLoadNative("/linux_aarch64/link-compress-musl.so"),
              "libdeflate FFM (Linux aarch64, musl)",
              FfmLibdeflateLinkCompressor.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64_MUSL,
              copyAndLoadNative("/linux_aarch64/link-compress-musl.so"),
              "libdeflate (Linux aarch64, musl)",
              LibdeflateLinkCompressor.FACTORY), // compiled with Alpine 3.18

          new NativeCodeLoader.Variant<>(NativeConstraints.MACOS_AARCH64,
              copyAndLoadNative("/macos_arm64/link-compress.dylib"),
              "libdeflate FFM (macOS ARM64 / Apple Silicon)",
              FfmLibdeflateLinkCompressor.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.MACOS_AARCH64,
              copyAndLoadNative("/macos_arm64/link-compress.dylib"),
              "libdeflate (macOS ARM64 / Apple Silicon)",
              LibdeflateLinkCompressor.FACTORY),
          new NativeCodeLoader.Variant<>(NativeCodeLoader.ALWAYS, () -> {
          }, "Java", JavaLinkCompressor.FACTORY)
      )
  );

  public static final NativeCodeLoader<LinkCipherFactory> cipher = new NativeCodeLoader<>(
      ImmutableList.of(
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              loadSystemLibrary("IPSec_MB", FfmIntelIpSecMbLinkCipher::ensureAvailable),
              "Intel IPsec-MB FFM AES-CFB8 (Linux x86_64)",
              () -> requireJavaCompatibleCipher(FfmIntelIpSecMbLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher.so"),
              "OpenSSL FFM local (Linux x86_64)",
              () -> requireJavaCompatibleCipher(FfmOpenSslLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher.so"), // Any local version
              "OpenSSL local (Linux x86_64)",
              () -> requireJavaCompatibleCipher(NativeLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl30x.so"),
              "OpenSSL FFM 3.x.x (Linux x86_64)",
              () -> requireJavaCompatibleCipher(FfmOpenSslLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl30x.so"), // Ubuntu 22.04
              "OpenSSL 3.x.x (Linux x86_64)",
              () -> requireJavaCompatibleCipher(NativeLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl11x.so"),
              "OpenSSL FFM 1.1.x (Linux x86_64)",
              () -> requireJavaCompatibleCipher(FfmOpenSslLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl11x.so"), // Ubuntu 20.04
              "OpenSSL 1.1.x (Linux x86_64)",
              () -> requireJavaCompatibleCipher(NativeLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64_MUSL,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl30x-musl.so"),
              "OpenSSL FFM 3.x.x (Linux x86_64, musl)",
              () -> requireJavaCompatibleCipher(FfmOpenSslLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64_MUSL,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl30x-musl.so"), // Alpine 3.18
              "OpenSSL 3.x.x (Linux x86_64, musl)",
              () -> requireJavaCompatibleCipher(NativeLinkCipher.FACTORY)),

          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher.so"),
              "OpenSSL FFM local (Linux aarch64)",
              () -> requireJavaCompatibleCipher(FfmOpenSslLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher.so"),
              "OpenSSL local (Linux aarch64)",
              () -> requireJavaCompatibleCipher(NativeLinkCipher.FACTORY)), // Any local version
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl30x.so"),
              "OpenSSL FFM 3.x.x (Linux aarch64)",
              () -> requireJavaCompatibleCipher(FfmOpenSslLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl30x.so"),
              "OpenSSL 3.x.x (Linux aarch64)",
              () -> requireJavaCompatibleCipher(NativeLinkCipher.FACTORY)), // Ubuntu 22.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl11x.so"),
              "OpenSSL FFM 1.1.x (Linux aarch64)",
              () -> requireJavaCompatibleCipher(FfmOpenSslLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl11x.so"),
              "OpenSSL 1.1.x (Linux aarch64)",
              () -> requireJavaCompatibleCipher(NativeLinkCipher.FACTORY)), // Ubuntu 20.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64_MUSL,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl30x-musl.so"),
              "OpenSSL FFM 3.x.x (Linux aarch64, musl)",
              () -> requireJavaCompatibleCipher(FfmOpenSslLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64_MUSL,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl30x-musl.so"),
              "OpenSSL 3.x.x (Linux aarch64, musl)",
              () -> requireJavaCompatibleCipher(NativeLinkCipher.FACTORY)), // Alpine 3.18

          new NativeCodeLoader.Variant<>(NativeConstraints.MACOS_AARCH64,
              copyAndLoadNative("/macos_arm64/link-cipher.dylib"),
              "OpenSSL FFM (macOS ARM64 / Apple Silicon)",
              () -> requireJavaCompatibleCipher(FfmOpenSslLinkCipher.FACTORY)),
          new NativeCodeLoader.Variant<>(NativeConstraints.MACOS_AARCH64,
              copyAndLoadNative("/macos_arm64/link-cipher.dylib"),
              "native (macOS ARM64 / Apple Silicon)",
              () -> requireJavaCompatibleCipher(NativeLinkCipher.FACTORY)),

          new NativeCodeLoader.Variant<>(NativeCodeLoader.ALWAYS, () -> {
          }, "Java", JavaLinkCipher.FACTORY)
      )
  );
}
