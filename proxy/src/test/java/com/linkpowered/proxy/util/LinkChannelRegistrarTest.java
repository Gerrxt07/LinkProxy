/*
 * Copyright (C) 2019-2021 Link Contributors
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

package com.linkpowered.proxy.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableSet;
import com.linkpowered.api.proxy.messages.ChannelIdentifier;
import com.linkpowered.api.proxy.messages.LegacyChannelIdentifier;
import com.linkpowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LinkChannelRegistrarTest {

  private static final MinecraftChannelIdentifier MODERN = MinecraftChannelIdentifier
      .create("link", "test");
  private static final LegacyChannelIdentifier SIMPLE_LEGACY =
      new LegacyChannelIdentifier("LinkTest");

  private static final MinecraftChannelIdentifier MODERN_SPECIAL_REMAP = MinecraftChannelIdentifier
      .create("bungeecord", "main");
  private static final LegacyChannelIdentifier SPECIAL_REMAP_LEGACY =
      new LegacyChannelIdentifier("BungeeCord");

  private static final String SIMPLE_LEGACY_REMAPPED = "legacy:linktest";

  @Test
  void register() {
    LinkChannelRegistrar registrar = new LinkChannelRegistrar();
    registrar.register(MODERN, SIMPLE_LEGACY);

    // Two channels cover the modern channel (link:test) and the legacy-mapped channel
    // (legacy:linktest). Make sure they're what we expect.
    assertEquals(ImmutableSet.of(MODERN.getId(), SIMPLE_LEGACY_REMAPPED), registrar
        .getModernChannelIds().stream().map(ChannelIdentifier::getId).collect(Collectors.toSet()));
    assertEquals(ImmutableSet.of(SIMPLE_LEGACY.getId(), MODERN.getId()), registrar
        .getLegacyChannelIds().stream().map(ChannelIdentifier::getId).collect(Collectors.toSet()));
  }

  @Test
  void registerSpecialRewrite() {
    LinkChannelRegistrar registrar = new LinkChannelRegistrar();
    registrar.register(SPECIAL_REMAP_LEGACY, MODERN_SPECIAL_REMAP);

    // This one, just one channel for the modern case.
    assertEquals(ImmutableSet.of(MODERN_SPECIAL_REMAP.getId()),
        registrar.getModernChannelIds().stream().map(ChannelIdentifier::getId).collect(Collectors.toSet()));
    assertEquals(ImmutableSet.of(MODERN_SPECIAL_REMAP.getId(), SPECIAL_REMAP_LEGACY.getId()),
        registrar.getLegacyChannelIds().stream().map(ChannelIdentifier::getId).collect(Collectors.toSet()));
  }

  @Test
  void unregister() {
    LinkChannelRegistrar registrar = new LinkChannelRegistrar();
    registrar.register(MODERN, SIMPLE_LEGACY);
    registrar.unregister(SIMPLE_LEGACY);

    assertEquals(ImmutableSet.of(MODERN.getId()),
        registrar.getModernChannelIds().stream().map(ChannelIdentifier::getId).collect(Collectors.toSet()));;
    assertEquals(ImmutableSet.of(MODERN.getId()),
        registrar.getLegacyChannelIds().stream().map(ChannelIdentifier::getId).collect(Collectors.toSet()));
  }
}