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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProxyProtocolV2DecoderTest {

  @Test
  void decodesIpv4SourceAddress() {
    EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolV2Decoder());

    channel.writeInbound(ipv4Header());
    ProxiedAddress address = channel.readInbound();

    assertEquals(new InetSocketAddress("192.0.2.10", 25565), address.sourceAddress());
  }

  @Test
  void rejectsProxyProtocolV1() {
    EmbeddedChannel channel = new EmbeddedChannel(new ProxyProtocolV2Decoder());
    ByteBuf input = Unpooled.wrappedBuffer(
        "PROXY TCP4 192.0.2.10 198.51.100.2 1 2\r\n".getBytes(StandardCharsets.US_ASCII));

    assertThrows(CorruptedFrameException.class, () -> channel.writeInbound(input));
  }

  private static ByteBuf ipv4Header() {
    ByteBuf buf = Unpooled.buffer(28);
    buf.writeBytes(new byte[] {
        0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    });
    buf.writeByte(0x21);
    buf.writeByte(0x11);
    buf.writeShort(12);
    buf.writeBytes(new byte[] {(byte) 192, 0, 2, 10});
    buf.writeBytes(new byte[] {(byte) 198, 51, 100, 2});
    buf.writeShort(25565);
    buf.writeShort(25566);
    return buf;
  }
}
