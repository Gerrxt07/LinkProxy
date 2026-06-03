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
import com.linkpowered.natives.encryption.FfmOpenSslLinkCipher;
import com.linkpowered.natives.encryption.JavaLinkCipher;
import com.linkpowered.natives.encryption.LinkCipherFactory;
import com.linkpowered.natives.encryption.NativeLinkCipher;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
              copyAndLoadNative("/linux_x86_64/link-cipher.so"),
              "OpenSSL FFM local (Linux x86_64)", FfmOpenSslLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher.so"), // Any local version
              "OpenSSL local (Linux x86_64)", NativeLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl30x.so"),
              "OpenSSL FFM 3.x.x (Linux x86_64)", FfmOpenSslLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl30x.so"), // Ubuntu 22.04
              "OpenSSL 3.x.x (Linux x86_64)", NativeLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl11x.so"),
              "OpenSSL FFM 1.1.x (Linux x86_64)", FfmOpenSslLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl11x.so"), // Ubuntu 20.04
              "OpenSSL 1.1.x (Linux x86_64)", NativeLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64_MUSL,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl30x-musl.so"),
              "OpenSSL FFM 3.x.x (Linux x86_64, musl)", FfmOpenSslLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_X86_64_MUSL,
              copyAndLoadNative("/linux_x86_64/link-cipher-ossl30x-musl.so"), // Alpine 3.18
              "OpenSSL 3.x.x (Linux x86_64, musl)", NativeLinkCipher.FACTORY),

          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher.so"),
              "OpenSSL FFM local (Linux aarch64)", FfmOpenSslLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher.so"),
              "OpenSSL local (Linux aarch64)", NativeLinkCipher.FACTORY), // Any local version
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl30x.so"),
              "OpenSSL FFM 3.x.x (Linux aarch64)", FfmOpenSslLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl30x.so"),
              "OpenSSL 3.x.x (Linux aarch64)", NativeLinkCipher.FACTORY), // Ubuntu 22.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl11x.so"),
              "OpenSSL FFM 1.1.x (Linux aarch64)", FfmOpenSslLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl11x.so"),
              "OpenSSL 1.1.x (Linux aarch64)", NativeLinkCipher.FACTORY), // Ubuntu 20.04
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64_MUSL,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl30x-musl.so"),
              "OpenSSL FFM 3.x.x (Linux aarch64, musl)", FfmOpenSslLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.LINUX_AARCH64_MUSL,
              copyAndLoadNative("/linux_aarch64/link-cipher-ossl30x-musl.so"),
              "OpenSSL 3.x.x (Linux aarch64, musl)", NativeLinkCipher.FACTORY), // Alpine 3.18

          new NativeCodeLoader.Variant<>(NativeConstraints.MACOS_AARCH64,
              copyAndLoadNative("/macos_arm64/link-cipher.dylib"),
              "OpenSSL FFM (macOS ARM64 / Apple Silicon)",
              FfmOpenSslLinkCipher.FACTORY),
          new NativeCodeLoader.Variant<>(NativeConstraints.MACOS_AARCH64,
              copyAndLoadNative("/macos_arm64/link-cipher.dylib"),
              "native (macOS ARM64 / Apple Silicon)",
               NativeLinkCipher.FACTORY),

          new NativeCodeLoader.Variant<>(NativeCodeLoader.ALWAYS, () -> {
          }, "Java", JavaLinkCipher.FACTORY)
      )
  );
}
