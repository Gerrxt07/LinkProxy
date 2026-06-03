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

package com.linkpowered.proxy.server;

import static com.linkpowered.proxy.network.Connections.FRAME_DECODER;
import static com.linkpowered.proxy.network.Connections.FRAME_ENCODER;
import static com.linkpowered.proxy.network.Connections.HANDLER;
import static com.linkpowered.proxy.network.Connections.MINECRAFT_DECODER;
import static com.linkpowered.proxy.network.Connections.MINECRAFT_ENCODER;
import static com.linkpowered.proxy.network.Connections.READ_TIMEOUT;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.linkpowered.api.proxy.Player;
import com.linkpowered.api.proxy.messages.ChannelIdentifier;
import com.linkpowered.api.proxy.messages.PluginMessageEncoder;
import com.linkpowered.api.proxy.server.PingOptions;
import com.linkpowered.api.proxy.server.RegisteredServer;
import com.linkpowered.api.proxy.server.ServerInfo;
import com.linkpowered.api.proxy.server.ServerPing;
import com.linkpowered.proxy.LinkServer;
import com.linkpowered.proxy.connection.MinecraftConnection;
import com.linkpowered.proxy.connection.backend.LinkServerConnection;
import com.linkpowered.proxy.connection.client.ConnectedPlayer;
import com.linkpowered.proxy.protocol.ProtocolUtils;
import com.linkpowered.proxy.protocol.StateRegistry;
import com.linkpowered.proxy.protocol.netty.MinecraftDecoder;
import com.linkpowered.proxy.protocol.netty.MinecraftEncoder;
import com.linkpowered.proxy.protocol.netty.MinecraftVarintFrameDecoder;
import com.linkpowered.proxy.protocol.netty.MinecraftVarintLengthEncoder;
import com.linkpowered.proxy.protocol.util.ByteBufDataOutput;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a server registered on the proxy.
 */
public class LinkRegisteredServer implements RegisteredServer, ForwardingAudience {

  private final @Nullable LinkServer server;
  private final ServerInfo serverInfo;
  private final Map<UUID, ConnectedPlayer> players = new ConcurrentHashMap<>();

  public LinkRegisteredServer(@Nullable LinkServer server, ServerInfo serverInfo) {
    this.server = server;
    this.serverInfo = Preconditions.checkNotNull(serverInfo, "serverInfo");
  }

  @Override
  public ServerInfo getServerInfo() {
    return serverInfo;
  }

  @Override
  public Collection<Player> getPlayersConnected() {
    return ImmutableList.copyOf(players.values());
  }

  @Override
  public CompletableFuture<ServerPing> ping(PingOptions pingOptions) {
    return ping(null, pingOptions);
  }

  @Override
  public CompletableFuture<ServerPing> ping() {
    return ping(null, PingOptions.DEFAULT);
  }

  /**
   * Pings the specified server using the specified event {@code loop}, claiming to be {@code
   * version}.
   *
   * @param loop    the event loop to use
   * @param pingOptions the options to apply to this ping
   * @return the server list ping response
   */
  public CompletableFuture<ServerPing> ping(@Nullable EventLoop loop, PingOptions pingOptions) {
    if (server == null) {
      throw new IllegalStateException("No Link proxy instance available");
    }
    CompletableFuture<ServerPing> pingFuture = new CompletableFuture<>();
    server.createBootstrap(loop).handler(new ChannelInitializer<>() {
      @Override
      protected void initChannel(Channel ch) {
        ch.pipeline().addLast(FRAME_DECODER, new MinecraftVarintFrameDecoder(ProtocolUtils.Direction.CLIENTBOUND))
            .addLast(READ_TIMEOUT, new ReadTimeoutHandler(
                pingOptions.getTimeout() == 0
                    ? server.getConfiguration().getReadTimeout()
                    : pingOptions.getTimeout(), TimeUnit.MILLISECONDS))
            .addLast(FRAME_ENCODER, MinecraftVarintLengthEncoder.INSTANCE)
            .addLast(MINECRAFT_DECODER, new MinecraftDecoder(ProtocolUtils.Direction.CLIENTBOUND))
            .addLast(MINECRAFT_ENCODER, new MinecraftEncoder(ProtocolUtils.Direction.SERVERBOUND));

        ch.pipeline().addLast(HANDLER, new MinecraftConnection(ch, server));
      }
    }).connect(serverInfo.getAddress()).addListener((ChannelFutureListener) future -> {
      if (future.isSuccess()) {
        MinecraftConnection conn = future.channel().pipeline().get(MinecraftConnection.class);
        PingSessionHandler handler = new PingSessionHandler(pingFuture,
            LinkRegisteredServer.this, conn, pingOptions.getProtocolVersion(), pingOptions.getVirtualHost());
        conn.setActiveSessionHandler(StateRegistry.HANDSHAKE, handler);
      } else {
        pingFuture.completeExceptionally(future.cause());
      }
    });
    return pingFuture;
  }

  public void addPlayer(ConnectedPlayer player) {
    players.put(player.getUniqueId(), player);
  }

  public void removePlayer(ConnectedPlayer player) {
    players.remove(player.getUniqueId(), player);
  }

  @Override
  public boolean sendPluginMessage(final @NotNull ChannelIdentifier identifier, final byte @NotNull [] data) {
    requireNonNull(identifier);
    requireNonNull(data);
    return sendPluginMessage(identifier, Unpooled.wrappedBuffer(data));
  }

  @Override
  public boolean sendPluginMessage(
          final @NotNull ChannelIdentifier identifier,
          final @NotNull PluginMessageEncoder dataEncoder
  ) {
    requireNonNull(identifier);
    requireNonNull(dataEncoder);
    final ByteBuf buf = Unpooled.buffer();
    final ByteBufDataOutput dataInput = new ByteBufDataOutput(buf);
    dataEncoder.encode(dataInput);
    if (buf.isReadable()) {
      return sendPluginMessage(identifier, buf);
    } else {
      buf.release();
      return false;
    }
  }

  /**
   * Sends a plugin message to the server through this connection. The message will be released
   * afterwards.
   *
   * @param identifier the channel ID to use
   * @param data       the data
   * @return whether or not the message was sent
   */
  public boolean sendPluginMessage(ChannelIdentifier identifier, ByteBuf data) {
    for (final ConnectedPlayer player : players.values()) {
      final LinkServerConnection serverConnection = player.getConnectedServer();
      if (serverConnection != null && serverConnection.getConnection() != null
              && serverConnection.getServer() == this) {
        return serverConnection.sendPluginMessage(identifier, data);
      }
    }

    data.release();
    return false;
  }

  @Override
  public String toString() {
    return "registered server: " + serverInfo;
  }

  @Override
  public @NonNull Iterable<? extends Audience> audiences() {
    return this.getPlayersConnected();
  }
}
