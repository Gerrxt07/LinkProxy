/*
 * Copyright (C) 2021 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.command;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.Message;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an implementation of brigadier's {@link Message}, providing support for {@link
 * Component} messages.
 */
public final class LinkBrigadierMessage implements Message, ComponentLike {

  public static LinkBrigadierMessage tooltip(Component message) {
    return new LinkBrigadierMessage(message);
  }

  private final Component message;

  private LinkBrigadierMessage(Component message) {
    this.message = Preconditions.checkNotNull(message, "message");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public Component asComponent() {
    return message;
  }

  /**
   * Returns the message as a plain text.
   *
   * @return message as plain text
   */
  @Override
  public String getString() {
    return PlainTextComponentSerializer.plainText().serialize(message);
  }
}
