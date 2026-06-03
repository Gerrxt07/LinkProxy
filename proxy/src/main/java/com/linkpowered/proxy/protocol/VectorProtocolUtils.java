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

package com.linkpowered.proxy.protocol;

import io.netty.buffer.ByteBuf;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Vector API helpers for hot protocol byte scans.
 */
public final class VectorProtocolUtils {

  private static final VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
  private static final VectorSpecies<Byte> VARINT_SPECIES = ByteVector.SPECIES_64;

  private VectorProtocolUtils() {
  }

  /**
   * Finds the first non-zero byte in a heap-backed {@link ByteBuf}.
   *
   * @param buf the buffer to scan
   * @param index absolute buffer index to start at
   * @param length maximum number of bytes to scan
   * @return absolute index, {@code -1} if all scanned bytes are zero, or {@code -2} for fallback
   */
  public static int firstNonZero(ByteBuf buf, int index, int length) {
    if (!buf.hasArray()) {
      return -2;
    }

    byte[] array = buf.array();
    int offset = buf.arrayOffset() + index;
    int upper = offset + length;
    int vectorUpper = offset + BYTE_SPECIES.loopBound(length);
    int cursor = offset;
    while (cursor < vectorUpper) {
      ByteVector bytes = ByteVector.fromArray(BYTE_SPECIES, array, cursor);
      VectorMask<Byte> nonZero = bytes.compare(VectorOperators.NE, (byte) 0);
      if (nonZero.anyTrue()) {
        return index + (cursor - offset) + nonZero.firstTrue();
      }
      cursor += BYTE_SPECIES.length();
    }

    while (cursor < upper) {
      if (array[cursor] != 0) {
        return index + (cursor - offset);
      }
      cursor++;
    }
    return -1;
  }

  /**
   * Decodes a full 32-bit VarInt from heap-backed buffers using an 8-byte vector mask.
   *
   * @param buf buffer positioned at the VarInt
   * @return decoded value, or {@code Integer.MIN_VALUE} for fallback
   */
  public static int readVarInt(ByteBuf buf) {
    int readable = buf.readableBytes();
    if (!buf.hasArray() || readable < 5) {
      return Integer.MIN_VALUE;
    }

    byte[] array = buf.array();
    int offset = buf.arrayOffset() + buf.readerIndex();
    if (array.length - offset < VARINT_SPECIES.length()) {
      return Integer.MIN_VALUE;
    }

    ByteVector bytes = ByteVector.fromArray(VARINT_SPECIES, array, offset);
    VectorMask<Byte> stopBytes = bytes.and((byte) 0x80).eq((byte) 0);
    int stop = stopBytes.firstTrue();
    if (stop >= 5) {
      return Integer.MIN_VALUE;
    }

    int result = 0;
    for (int i = 0; i <= stop; i++) {
      result |= (array[offset + i] & 0x7F) << (i * 7);
    }
    buf.skipBytes(stop + 1);
    return result;
  }

}
