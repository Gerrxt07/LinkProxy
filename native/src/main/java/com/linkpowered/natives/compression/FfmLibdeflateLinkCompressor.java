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

package com.linkpowered.natives.compression;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import com.google.common.base.Preconditions;
import com.linkpowered.natives.util.BufferPreference;
import io.netty.buffer.ByteBuf;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.zip.DataFormatException;

/**
 * Implements zlib compression through libdeflate using the Foreign Function & Memory API.
 */
public class FfmLibdeflateLinkCompressor implements LinkCompressor {

  private static final int LIBDEFLATE_SUCCESS = 0;
  private static final int LIBDEFLATE_BAD_DATA = 1;
  private static final int LIBDEFLATE_SHORT_OUTPUT = 2;

  public static final LinkCompressorFactory FACTORY = FfmLibdeflateLinkCompressor::new;

  private final MemorySegment inflateCtx;
  private final MemorySegment deflateCtx;
  private boolean disposed = false;

  private FfmLibdeflateLinkCompressor(int level) {
    int correctedLevel = level == -1 ? 6 : level;
    if (correctedLevel > 12 || correctedLevel < 1) {
      throw new IllegalArgumentException("Invalid compression level " + level);
    }

    this.inflateCtx = allocDecompressor();
    this.deflateCtx = allocCompressor(correctedLevel);
    if (this.inflateCtx.equals(MemorySegment.NULL) || this.deflateCtx.equals(MemorySegment.NULL)) {
      close();
      throw new OutOfMemoryError("libdeflate allocate context");
    }
  }

  @Override
  public void inflate(ByteBuf source, ByteBuf destination, int uncompressedSize)
      throws DataFormatException {
    ensureNotDisposed();
    destination.ensureWritable(uncompressedSize);

    MemorySegment sourceAddress = MemorySegment.ofAddress(source.memoryAddress() + source.readerIndex());
    MemorySegment destinationAddress = MemorySegment.ofAddress(
        destination.memoryAddress() + destination.writerIndex());
    int result = zlibDecompress(
        inflateCtx,
        sourceAddress,
        source.readableBytes(),
        destinationAddress,
        uncompressedSize);
    if (result == LIBDEFLATE_BAD_DATA) {
      throw new DataFormatException("inflate data is bad");
    }
    if (result == LIBDEFLATE_SHORT_OUTPUT) {
      throw new DataFormatException("inflate output is short");
    }
    if (result != LIBDEFLATE_SUCCESS) {
      throw new DataFormatException("unknown libdeflate return code " + result);
    }
    destination.writerIndex(destination.writerIndex() + uncompressedSize);
  }

  @Override
  public void deflate(ByteBuf source, ByteBuf destination) throws DataFormatException {
    ensureNotDisposed();

    while (true) {
      MemorySegment sourceAddress = MemorySegment.ofAddress(source.memoryAddress() + source.readerIndex());
      MemorySegment destinationAddress = MemorySegment.ofAddress(
          destination.memoryAddress() + destination.writerIndex());
      long produced = zlibCompress(
          deflateCtx,
          sourceAddress,
          source.readableBytes(),
          destinationAddress,
          destination.writableBytes());
      if (produced > 0 && produced <= Integer.MAX_VALUE) {
        destination.writerIndex(destination.writerIndex() + (int) produced);
        break;
      } else if (produced == 0) {
        destination.capacity(destination.capacity() * 2);
      } else {
        throw new DataFormatException("libdeflate returned unknown code " + produced);
      }
    }
  }

  @Override
  public void close() {
    if (!disposed) {
      if (!inflateCtx.equals(MemorySegment.NULL)) {
        freeDecompressor(inflateCtx);
      }
      if (!deflateCtx.equals(MemorySegment.NULL)) {
        freeCompressor(deflateCtx);
      }
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

  private static MemorySegment allocCompressor(int level) {
    try {
      return (MemorySegment) Bindings.ALLOC_COMPRESSOR.invokeExact(level);
    } catch (Throwable throwable) {
      throw new IllegalStateException("libdeflate_alloc_compressor failed", throwable);
    }
  }

  private static void freeCompressor(MemorySegment ctx) {
    try {
      Bindings.FREE_COMPRESSOR.invokeExact(ctx);
    } catch (Throwable throwable) {
      throw new IllegalStateException("libdeflate_free_compressor failed", throwable);
    }
  }

  private static long zlibCompress(
      MemorySegment ctx,
      MemorySegment source,
      int sourceLength,
      MemorySegment destination,
      int destinationLength) {
    try {
      return (long) Bindings.ZLIB_COMPRESS.invokeExact(
          ctx, source, (long) sourceLength, destination, (long) destinationLength);
    } catch (Throwable throwable) {
      throw new IllegalStateException("libdeflate_zlib_compress failed", throwable);
    }
  }

  private static MemorySegment allocDecompressor() {
    try {
      return (MemorySegment) Bindings.ALLOC_DECOMPRESSOR.invokeExact();
    } catch (Throwable throwable) {
      throw new IllegalStateException("libdeflate_alloc_decompressor failed", throwable);
    }
  }

  private static void freeDecompressor(MemorySegment ctx) {
    try {
      Bindings.FREE_DECOMPRESSOR.invokeExact(ctx);
    } catch (Throwable throwable) {
      throw new IllegalStateException("libdeflate_free_decompressor failed", throwable);
    }
  }

  private static int zlibDecompress(
      MemorySegment ctx,
      MemorySegment source,
      int sourceLength,
      MemorySegment destination,
      int destinationLength) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment actualOut = arena.allocate(JAVA_LONG);
      return (int) Bindings.ZLIB_DECOMPRESS.invokeExact(
          ctx, source, (long) sourceLength, destination, (long) destinationLength, actualOut);
    } catch (Throwable throwable) {
      throw new IllegalStateException("libdeflate_zlib_decompress failed", throwable);
    }
  }

  private static final class Bindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOLS = SymbolLookup.loaderLookup()
        .or(LINKER.defaultLookup());
    private static final MethodHandle ALLOC_COMPRESSOR = downcall(
        "libdeflate_alloc_compressor", FunctionDescriptor.of(ADDRESS, JAVA_INT));
    private static final MethodHandle FREE_COMPRESSOR = downcall(
        "libdeflate_free_compressor", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle ZLIB_COMPRESS = downcall(
        "libdeflate_zlib_compress",
        FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG));
    private static final MethodHandle ALLOC_DECOMPRESSOR = downcall(
        "libdeflate_alloc_decompressor", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle FREE_DECOMPRESSOR = downcall(
        "libdeflate_free_decompressor", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle ZLIB_DECOMPRESS = downcall(
        "libdeflate_zlib_decompress",
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS));

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
      MemorySegment symbol = SYMBOLS.find(name)
          .orElseThrow(() -> new IllegalStateException("Missing native symbol " + name));
      return LINKER.downcallHandle(symbol, descriptor);
    }
  }
}
