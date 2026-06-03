/*
 * Copyright (C) 2018-2022 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.command;

import com.linkpowered.api.proxy.Player;

/**
 * Represents a command that can be executed by a {@link CommandSource}
 * such as a {@link Player} or the console.
 *
 * <p><strong>You must not subclass <code>Command</code></strong>. Use one of the following
 * <i>registrable</i> subinterfaces:</p>
 *
 * <ul>
 * <li>{@link BrigadierCommand}, which supports parameterized arguments and
 * specialized execution, tab complete suggestions and permission-checking logic.
 *
 * <li>{@link SimpleCommand}, modelled after the convention popularized by
 * Bukkit and BungeeCord. Older classes directly implementing {@link Command}
 * are suggested to migrate to this interface.
 *
 * <li>{@link RawCommand}, useful for bolting on external command frameworks
 * to Link.
 *
 * </ul>
 */
public sealed interface Command permits BrigadierCommand, InvocableCommand {
}
