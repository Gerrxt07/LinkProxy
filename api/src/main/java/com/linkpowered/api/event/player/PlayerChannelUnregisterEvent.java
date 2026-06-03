/*
 * Copyright (C) 2025 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.event.player;

import com.google.common.base.Preconditions;
import com.linkpowered.api.proxy.Player;
import com.linkpowered.api.proxy.messages.ChannelIdentifier;
import java.util.List;

/**
 * This event is fired when a client ({@link Player}) sends a plugin message through the
 * unregister channel. Link will not wait on this event to finish firing.
 */
public final class PlayerChannelUnregisterEvent {

  private final Player player;
  private final List<ChannelIdentifier> channels;

  public PlayerChannelUnregisterEvent(Player player, List<ChannelIdentifier> channels) {
    this.player = Preconditions.checkNotNull(player, "player");
    this.channels = Preconditions.checkNotNull(channels, "channels");
  }

  public Player getPlayer() {
    return player;
  }

  public List<ChannelIdentifier> getChannels() {
    return channels;
  }

  @Override
  public String toString() {
    return "PlayerChannelUnregisterEvent{"
            + "player=" + player
            + ", channels=" + channels
            + '}';
  }
}
