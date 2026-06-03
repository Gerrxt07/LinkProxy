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

package com.linkpowered.proxy.connection.util;

import com.google.common.collect.ImmutableList;
import com.linkpowered.api.network.ProtocolVersion;
import com.linkpowered.api.proxy.server.ServerPing;
import com.linkpowered.proxy.LinkServer;
import com.linkpowered.proxy.config.LinkConfiguration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Common utilities for handling server list ping results.
 */
public class ServerListPingHandler {

  private final LinkServer server;

  public ServerListPingHandler(LinkServer server) {
    this.server = server;
  }

  private ServerPing constructLocalPing(ProtocolVersion version) {
    if (version == ProtocolVersion.UNKNOWN) {
      version = ProtocolVersion.MAXIMUM_VERSION;
    }
    LinkConfiguration configuration = server.getConfiguration();
    final List<ServerPing.SamplePlayer> samplePlayers = ImmutableList.of();
    return new ServerPing(
        new ServerPing.Version(version.getProtocol(),
            "Link " + ProtocolVersion.SUPPORTED_VERSION_STRING),
        new ServerPing.Players(server.getPlayerCount(), configuration.getShowMaxPlayers(),
            samplePlayers),
        configuration.getMotd(),
        configuration.getFavicon().orElse(null),
        null
    );
  }

  /**
   * Fetches the "default" server ping for a player.
   *
   * @param connection the connection
   * @return a future with the initial ping result
   */
  public CompletableFuture<ServerPing> getInitialPing(LinkInboundConnection connection) {
    ProtocolVersion shownVersion = connection.getProtocolVersion().isSupported()
        ? connection.getProtocolVersion() : ProtocolVersion.MAXIMUM_VERSION;
    return CompletableFuture.completedFuture(constructLocalPing(shownVersion));
  }
}
