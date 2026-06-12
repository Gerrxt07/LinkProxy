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

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linkpowered.api.network.ProtocolVersion;
import com.linkpowered.proxy.protocol.ProtocolUtils;
import com.linkpowered.proxy.util.except.QuietDecoderException;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class PlayPacketQueueInboundHandlerTest {

  @Test
  void rejectsZeroByteObjectsPastPacketLimit() {
    EmbeddedChannel channel = new EmbeddedChannel(new PlayPacketQueueInboundHandler(
        ProtocolVersion.MINECRAFT_1_21_11, ProtocolUtils.Direction.CLIENTBOUND));

    for (int i = 0; i < 10_000; i++) {
      channel.writeInbound(new Object());
    }

    assertThrows(QuietDecoderException.class, () -> channel.writeInbound(new Object()));
    channel.finishAndReleaseAll();
  }
}
