/*
 * Copyright (C) 2024 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.event.player.configuration;

import com.linkpowered.api.event.annotation.AwaitingEvent;
import com.linkpowered.api.proxy.Player;
import com.linkpowered.api.proxy.ServerConnection;
import org.jetbrains.annotations.NotNull;

/**
 * This event is executed when a player is about to enter the configuration state.
 * It is <b>not</b> called for the initial configuration of a player after login.
 * <p>Link will wait for this event before asking the client to enter configuration state.
 * However due to backend server being unable to keep the connection alive during state changes,
 * Link will only wait for a maximum of 5 seconds.</p>
 *
 * @param player The player who is about to enter configuration state.
 * @param server The server that wants to reconfigure the player.
 * @since 3.3.0
 * @sinceMinecraft 1.20.2
 */
@AwaitingEvent
public record PlayerEnterConfigurationEvent(@NotNull Player player, ServerConnection server) {
}
