/*
 * Copyright (C) 2018-2021 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.event.connection;

import com.google.common.base.Preconditions;
import com.linkpowered.api.event.annotation.AwaitingEvent;
import com.linkpowered.api.proxy.Player;

/**
 * This event is fired once the player has been fully initialized and is about to connect to their
 * first server. Link will wait for this event to finish firing before it fires
 * {@link com.linkpowered.api.event.player.PlayerChooseInitialServerEvent} with any default
 * servers specified in the configuration, but you should try to limit the work done in any event
 * that fires during the login process.
 */
@AwaitingEvent
public final class PostLoginEvent {

  private final Player player;

  public PostLoginEvent(Player player) {
    this.player = Preconditions.checkNotNull(player, "player");
  }

  public Player getPlayer() {
    return player;
  }

  @Override
  public String toString() {
    return "PostLoginEvent{"
        + "player=" + player
        + '}';
  }
}
