/*
 * Copyright (C) 2020-2023 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.event.command;

import static com.google.common.base.Preconditions.checkNotNull;

import com.linkpowered.api.event.annotation.AwaitingEvent;
import com.linkpowered.api.proxy.Player;
import com.mojang.brigadier.tree.RootCommandNode;

/**
 * Allows plugins to modify the packet indicating commands available on the server to a
 * Minecraft 1.13+ client. The given {@link RootCommandNode} is mutable. Link will wait
 * for this event to finish firing before sending the list of available commands to the
 * client.
 */
@AwaitingEvent
public class PlayerAvailableCommandsEvent {

  private final Player player;
  private final RootCommandNode<?> rootNode;

  /**
   * Constructs an available commands event.
   *
   * @param player the targeted player
   * @param rootNode the Brigadier root node
   */
  public PlayerAvailableCommandsEvent(Player player,
      RootCommandNode<?> rootNode) {
    this.player = checkNotNull(player, "player");
    this.rootNode = checkNotNull(rootNode, "rootNode");
  }

  public Player getPlayer() {
    return player;
  }

  public RootCommandNode<?> getRootNode() {
    return rootNode;
  }
}
