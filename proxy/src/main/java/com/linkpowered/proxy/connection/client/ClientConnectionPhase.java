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

package com.linkpowered.proxy.connection.client;

import com.linkpowered.proxy.connection.backend.LinkServerConnection;
import com.linkpowered.proxy.connection.forge.legacy.LegacyForgeHandshakeClientPhase;
import com.linkpowered.proxy.protocol.packet.PluginMessagePacket;

/**
 * Provides connection phase specific actions.
 *
 * <p>Note that Forge phases are found in the enum
 * {@link LegacyForgeHandshakeClientPhase}.</p>
 */
public interface ClientConnectionPhase {

  /**
   * Handle a plugin message in the context of this phase.
   *
   * @param player  The player
   * @param message The message to handle
   * @param server  The backend connection to use
   * @return true if handled, false otherwise.
   */
  default boolean handle(ConnectedPlayer player,
      PluginMessagePacket message,
      LinkServerConnection server) {
    return false;
  }

  /**
   * Instruct Link to reset the connection phase back to its default for the connection type.
   *
   * @param player The player
   */
  default void resetConnectionPhase(ConnectedPlayer player) {
  }

  /**
   * Perform actions just as the player joins the server.
   *
   * @param player The player
   */
  default void onFirstJoin(ConnectedPlayer player) {
  }

  /**
   * Indicates whether the connection is considered complete.
   *
   * @return true if so
   */
  default boolean consideredComplete() {
    return true;
  }
}
