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

package com.linkpowered.proxy.network;

import static com.linkpowered.proxy.network.Connections.FLUSH_CONSOLIDATION;
import static com.linkpowered.proxy.network.Connections.FRAME_DECODER;
import static com.linkpowered.proxy.network.Connections.FRAME_ENCODER;
import static com.linkpowered.proxy.network.Connections.HAPROXY_DECODER;
import static com.linkpowered.proxy.network.Connections.LEGACY_PING_DECODER;
import static com.linkpowered.proxy.network.Connections.LEGACY_PING_ENCODER;
import static com.linkpowered.proxy.network.Connections.MINECRAFT_DECODER;
import static com.linkpowered.proxy.network.Connections.MINECRAFT_ENCODER;
import static com.linkpowered.proxy.network.Connections.READ_TIMEOUT;

import com.linkpowered.proxy.LinkServer;
import com.linkpowered.proxy.config.LinkConfiguration;
import com.linkpowered.proxy.connection.MinecraftConnection;
import com.linkpowered.proxy.connection.client.HandshakeSessionHandler;
import com.linkpowered.proxy.dragonfly.DragonflyProxyProtocolV2Decoder;
import com.linkpowered.proxy.network.limiter.SimpleBytesPerSecondLimiter;
import com.linkpowered.proxy.protocol.ProtocolUtils;
import com.linkpowered.proxy.protocol.StateRegistry;
import com.linkpowered.proxy.protocol.netty.AdaptiveFlushConsolidationHandler;
import com.linkpowered.proxy.protocol.netty.LegacyPingDecoder;
import com.linkpowered.proxy.protocol.netty.LegacyPingEncoder;
import com.linkpowered.proxy.protocol.netty.MinecraftDecoder;
import com.linkpowered.proxy.protocol.netty.MinecraftEncoder;
import com.linkpowered.proxy.protocol.netty.MinecraftVarintFrameDecoder;
import com.linkpowered.proxy.protocol.netty.MinecraftVarintLengthEncoder;
import com.linkpowered.proxy.protocol.netty.ProxyProtocolV2Decoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.TimeUnit;

/**
 * Server channel initializer.
 */
@SuppressWarnings("WeakerAccess")
public class ServerChannelInitializer extends ChannelInitializer<Channel> {

  private final LinkServer server;

  public ServerChannelInitializer(final LinkServer server) {
    this.server = server;
  }

  @Override
  protected void initChannel(final Channel ch) {
    if (this.server.getConfiguration().isProxyProtocol()) {
      if (this.server.getDragonflyProtection().isEarlyProtectionEnabled()) {
        ch.pipeline().addLast(HAPROXY_DECODER,
            new DragonflyProxyProtocolV2Decoder(this.server.getDragonflyProtection(),
                ctx -> installMinecraftPipeline(ch)));
      } else {
        ch.pipeline().addLast(HAPROXY_DECODER,
            new ProxyProtocolV2Decoder(ctx -> installMinecraftPipeline(ch)));
      }
    } else {
      installMinecraftPipeline(ch);
    }
  }

  private void installMinecraftPipeline(final Channel ch) {
    ch.pipeline()
        .addLast(LEGACY_PING_DECODER, new LegacyPingDecoder())
        .addLast(FRAME_DECODER, new MinecraftVarintFrameDecoder(ProtocolUtils.Direction.SERVERBOUND))
        .addLast(READ_TIMEOUT,
            new ReadTimeoutHandler(this.server.getConfiguration().getReadTimeout(),
                TimeUnit.MILLISECONDS))
        .addLast(LEGACY_PING_ENCODER, LegacyPingEncoder.INSTANCE)
        .addLast(FRAME_ENCODER, MinecraftVarintLengthEncoder.INSTANCE)
        .addLast(MINECRAFT_DECODER, new MinecraftDecoder(ProtocolUtils.Direction.SERVERBOUND))
        .addLast(MINECRAFT_ENCODER, new MinecraftEncoder(ProtocolUtils.Direction.CLIENTBOUND))
        .addLast(FLUSH_CONSOLIDATION, new AdaptiveFlushConsolidationHandler());

    final MinecraftConnection connection = new MinecraftConnection(ch, this.server);
    connection.setActiveSessionHandler(StateRegistry.HANDSHAKE,
        new HandshakeSessionHandler(connection, this.server));
    ch.pipeline().addLast(Connections.HANDLER, connection);

    LinkConfiguration.PacketLimiterConfig packetLimiterConfig =
        server.getConfiguration().getPacketLimiterConfig();
    int configuredInterval = packetLimiterConfig.interval();
    int configuredPacketsPerSecond = packetLimiterConfig.pps();
    int configuredBytes = packetLimiterConfig.bytes();

    if (configuredInterval > 0 && (configuredBytes > 0 || configuredPacketsPerSecond > 0)) {
      ch.pipeline().get(MinecraftVarintFrameDecoder.class).setPacketLimiter(
          new SimpleBytesPerSecondLimiter(configuredPacketsPerSecond, configuredBytes, configuredInterval)
      );
    }
  }
}
