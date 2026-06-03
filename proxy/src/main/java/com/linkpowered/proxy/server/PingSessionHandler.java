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

import com.linkpowered.api.network.HandshakeIntent;
import com.linkpowered.api.network.ProtocolVersion;
import com.linkpowered.api.proxy.server.RegisteredServer;
import com.linkpowered.api.proxy.server.ServerPing;
import com.linkpowered.proxy.LinkServer;
import com.linkpowered.proxy.connection.MinecraftConnection;
import com.linkpowered.proxy.connection.MinecraftSessionHandler;
import com.linkpowered.proxy.protocol.StateRegistry;
import com.linkpowered.proxy.protocol.packet.HandshakePacket;
import com.linkpowered.proxy.protocol.packet.StatusRequestPacket;
import com.linkpowered.proxy.protocol.packet.StatusResponsePacket;
import io.netty.channel.EventLoop;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Session handler used to implement {@link LinkRegisteredServer#ping(EventLoop,
 * com.linkpowered.api.proxy.server.PingOptions)}.
 */
public class PingSessionHandler implements MinecraftSessionHandler {

  private final CompletableFuture<ServerPing> result;
  private final RegisteredServer server;
  private final MinecraftConnection connection;
  private final ProtocolVersion version;
  private boolean completed = false;
  private final String virtualHostString;

  PingSessionHandler(CompletableFuture<ServerPing> result, RegisteredServer server,
      MinecraftConnection connection, ProtocolVersion version, String virtualHostString) {
    this.result = result;
    this.server = server;
    this.connection = connection;
    this.version = version;
    this.virtualHostString = virtualHostString;
  }

  @Override
  public void activated() {
    HandshakePacket handshake = new HandshakePacket();
    handshake.setIntent(HandshakeIntent.STATUS);
    handshake.setServerAddress(this.virtualHostString == null || this.virtualHostString.isEmpty()
            ? server.getServerInfo().getAddress().getHostString() : this.virtualHostString);
    handshake.setPort(server.getServerInfo().getAddress().getPort());
    handshake.setProtocolVersion(version);
    connection.delayedWrite(handshake);

    connection.setActiveSessionHandler(StateRegistry.STATUS);
    connection.setState(StateRegistry.STATUS);
    connection.delayedWrite(StatusRequestPacket.INSTANCE);

    connection.flush();
  }

  @Override
  public boolean handle(StatusResponsePacket packet) {
    // All good!
    completed = true;
    connection.close(true);

    ServerPing ping = LinkServer.getPingGsonInstance(version).fromJson(packet.getStatus(),
        ServerPing.class);
    result.complete(ping);
    return true;
  }

  @Override
  public void disconnected() {
    if (!completed) {
      result.completeExceptionally(new IOException("Unexpectedly disconnected from remote server"));
    }
  }

  @Override
  public void exception(Throwable throwable) {
    completed = true;
    result.completeExceptionally(throwable);
  }
}
