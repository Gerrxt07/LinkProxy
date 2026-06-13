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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Strict, single-use HAProxy PROXY protocol v2 decoder.
 */
public class ProxyProtocolV2Decoder extends ByteToMessageDecoder {

  private static final Consumer<ChannelHandlerContext> NOOP_ACCEPTED_CALLBACK = ctx -> {
  };

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

  private final Consumer<ChannelHandlerContext> acceptedCallback;

  public ProxyProtocolV2Decoder() {
    this(NOOP_ACCEPTED_CALLBACK);
  }

  public ProxyProtocolV2Decoder(Consumer<ChannelHandlerContext> acceptedCallback) {
    this.acceptedCallback = acceptedCallback;
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
      throws UnknownHostException {
    if (in.readableBytes() < HEADER_LENGTH) {
      return;
    }

    SegmentView header = segmentView(in, HEADER_LENGTH);
    for (int i = 0; i < SIGNATURE.length; i++) {
      if (header.getByte(i) != SIGNATURE[i]) {
        throw new CorruptedFrameException("Expected HAProxy PROXY protocol v2 signature");
      }
    }

    int versionCommand = header.getUnsignedByte(12);
    if ((versionCommand & 0xF0) != VERSION) {
      throw new CorruptedFrameException("Expected HAProxy PROXY protocol v2 header");
    }

    int command = versionCommand & 0x0F;
    int addressLength = header.getUnsignedShort(14);
    if (in.readableBytes() < HEADER_LENGTH + addressLength) {
      return;
    }

    SegmentView packet = segmentView(in, HEADER_LENGTH + addressLength);
    final int familyTransport = packet.getUnsignedByte(13);
    in.skipBytes(HEADER_LENGTH);
    if (command == COMMAND_LOCAL) {
      in.skipBytes(addressLength);
      proxyHeaderAccepted(ctx);
      removeSelfAndForwardRemainder(ctx, in, out);
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
      decodeTcpAddress(ctx, in, out, packet, addressLength, 4, IPV4_ADDRESS_LENGTH);
    } else if (family == FAMILY_IPV6) {
      decodeTcpAddress(ctx, in, out, packet, addressLength, 16, IPV6_ADDRESS_LENGTH);
    } else {
      throw new CorruptedFrameException("Unsupported HAProxy PROXY protocol v2 address family");
    }
  }

  private void decodeTcpAddress(
      ChannelHandlerContext ctx,
      ByteBuf in,
      List<Object> out,
      SegmentView packet,
      int addressLength,
      int bytesPerAddress,
      int minimumAddressLength) throws UnknownHostException {
    if (addressLength < minimumAddressLength) {
      throw new CorruptedFrameException("Truncated HAProxy PROXY protocol v2 address block");
    }

    byte[] sourceAddressBytes = new byte[bytesPerAddress];
    packet.copyTo(sourceAddressBytes, HEADER_LENGTH);
    in.skipBytes(bytesPerAddress);
    in.skipBytes(bytesPerAddress);
    int sourcePort = packet.getUnsignedShort(HEADER_LENGTH + (bytesPerAddress * 2L));
    in.skipBytes(2);
    in.skipBytes(2); // destination port
    in.skipBytes(addressLength - minimumAddressLength);

    InetAddress sourceAddress = InetAddress.getByAddress(sourceAddressBytes);
    InetSocketAddress proxiedAddress = new InetSocketAddress(sourceAddress, sourcePort);
    if (shouldDropSource(proxiedAddress)) {
      ctx.close();
      return;
    }
    proxyHeaderAccepted(ctx);
    out.add(new ProxiedAddress(proxiedAddress));
    removeSelfAndForwardRemainder(ctx, in, out);
  }

  private void removeSelfAndForwardRemainder(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    ctx.pipeline().remove(this);
    if (in.isReadable()) {
      out.add(in.readRetainedSlice(in.readableBytes()));
    }
  }

  protected boolean shouldDropSource(InetSocketAddress sourceAddress) {
    return false;
  }

  protected void proxyHeaderAccepted(ChannelHandlerContext ctx) {
    acceptedCallback.accept(ctx);
  }

  private static SegmentView segmentView(ByteBuf in, int length) {
    if (in.hasMemoryAddress()) {
      MemorySegment segment = MemorySegment.ofAddress(in.memoryAddress() + in.readerIndex())
          .reinterpret(length);
      return new SegmentView(segment, 0);
    }
    if (in.hasArray()) {
      MemorySegment segment = MemorySegment.ofArray(in.array());
      return new SegmentView(segment, in.arrayOffset() + in.readerIndex());
    }

    byte[] bytes = new byte[length];
    in.getBytes(in.readerIndex(), bytes);
    return new SegmentView(MemorySegment.ofArray(bytes), 0);
  }

  private record SegmentView(MemorySegment segment, long baseOffset) {

    private byte getByte(long offset) {
      return segment.get(ValueLayout.JAVA_BYTE, baseOffset + offset);
    }

    private int getUnsignedByte(long offset) {
      return getByte(offset) & 0xFF;
    }

    private int getUnsignedShort(long offset) {
      return (getUnsignedByte(offset) << 8) | getUnsignedByte(offset + 1);
    }

    private void copyTo(byte[] destination, long offset) {
      MemorySegment.copy(segment, baseOffset + offset, MemorySegment.ofArray(destination), 0,
          destination.length);
    }
  }
}
