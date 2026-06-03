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

package com.linkpowered.proxy.protocol.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Strict, single-use HAProxy PROXY protocol v2 decoder.
 */
public class ProxyProtocolV2Decoder extends ByteToMessageDecoder {

  private static final byte[] SIGNATURE = {
      0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
  };
  private static final int HEADER_LENGTH = 16;
  private static final int VERSION = 0x20;
  private static final int COMMAND_LOCAL = 0x00;
  private static final int COMMAND_PROXY = 0x01;
  private static final int FAMILY_IPV4 = 0x10;
  private static final int FAMILY_IPV6 = 0x20;
  private static final int TRANSPORT_STREAM = 0x01;
  private static final int IPV4_ADDRESS_LENGTH = 12;
  private static final int IPV6_ADDRESS_LENGTH = 36;

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
      throws UnknownHostException {
    if (in.readableBytes() < HEADER_LENGTH) {
      return;
    }

    int readerIndex = in.readerIndex();
    for (int i = 0; i < SIGNATURE.length; i++) {
      if (in.getByte(readerIndex + i) != SIGNATURE[i]) {
        throw new CorruptedFrameException("Expected HAProxy PROXY protocol v2 signature");
      }
    }

    int versionCommand = in.getUnsignedByte(readerIndex + 12);
    if ((versionCommand & 0xF0) != VERSION) {
      throw new CorruptedFrameException("Expected HAProxy PROXY protocol v2 header");
    }

    int command = versionCommand & 0x0F;
    int addressLength = in.getUnsignedShort(readerIndex + 14);
    if (in.readableBytes() < HEADER_LENGTH + addressLength) {
      return;
    }

    final int familyTransport = in.getUnsignedByte(readerIndex + 13);
    in.skipBytes(HEADER_LENGTH);
    if (command == COMMAND_LOCAL) {
      in.skipBytes(addressLength);
      ctx.pipeline().remove(this);
      return;
    }
    if (command != COMMAND_PROXY) {
      throw new CorruptedFrameException("Unsupported HAProxy PROXY protocol v2 command");
    }

    int transport = familyTransport & 0x0F;
    if (transport != TRANSPORT_STREAM) {
      throw new CorruptedFrameException("Unsupported HAProxy PROXY protocol v2 transport");
    }

    int family = familyTransport & 0xF0;
    if (family == FAMILY_IPV4) {
      decodeTcpAddress(ctx, in, out, addressLength, 4, IPV4_ADDRESS_LENGTH);
    } else if (family == FAMILY_IPV6) {
      decodeTcpAddress(ctx, in, out, addressLength, 16, IPV6_ADDRESS_LENGTH);
    } else {
      throw new CorruptedFrameException("Unsupported HAProxy PROXY protocol v2 address family");
    }
  }

  private void decodeTcpAddress(
      ChannelHandlerContext ctx,
      ByteBuf in,
      List<Object> out,
      int addressLength,
      int bytesPerAddress,
      int minimumAddressLength) throws UnknownHostException {
    if (addressLength < minimumAddressLength) {
      throw new CorruptedFrameException("Truncated HAProxy PROXY protocol v2 address block");
    }

    byte[] sourceAddressBytes = new byte[bytesPerAddress];
    in.readBytes(sourceAddressBytes);
    in.skipBytes(bytesPerAddress);
    int sourcePort = in.readUnsignedShort();
    in.skipBytes(2);
    in.skipBytes(addressLength - minimumAddressLength);

    InetAddress sourceAddress = InetAddress.getByAddress(sourceAddressBytes);
    out.add(new ProxiedAddress(new InetSocketAddress(sourceAddress, sourcePort)));
    ctx.pipeline().remove(this);
  }
}
