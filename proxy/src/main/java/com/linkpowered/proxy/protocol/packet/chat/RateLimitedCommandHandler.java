/*
 * Copyright (C) 2025 Link Contributors
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

package com.linkpowered.proxy.protocol.packet.chat;

import com.linkpowered.api.proxy.Player;
import com.linkpowered.proxy.LinkServer;
import com.linkpowered.proxy.protocol.MinecraftPacket;
import net.kyori.adventure.text.Component;

public abstract class RateLimitedCommandHandler<T extends MinecraftPacket> implements CommandHandler<T> {

    private final Player player;
    private final LinkServer linkServer;

    private int failedAttempts;

    protected RateLimitedCommandHandler(Player player, LinkServer linkServer) {
        this.player = player;
        this.linkServer = linkServer;
    }

    @Override
    public boolean handlePlayerCommand(MinecraftPacket packet) {
        if (packetClass().isInstance(packet)) {
            if (!linkServer.getCommandRateLimiter().attempt(player.getUniqueId())) {
                if (linkServer.getConfiguration().isKickOnCommandRateLimit() && failedAttempts++ >= linkServer.getConfiguration().getKickAfterRateLimitedCommands()) {
                    player.disconnect(Component.translatable("link.kick.command-rate-limit"));
                }

                if (linkServer.getConfiguration().isForwardCommandsIfRateLimited()) {
                    return false; // Send the packet to the server
                }
            } else {
                failedAttempts = 0;
            }

            handlePlayerCommandInternal(packetClass().cast(packet));
            return true;
        }

        return false;
    }
}
