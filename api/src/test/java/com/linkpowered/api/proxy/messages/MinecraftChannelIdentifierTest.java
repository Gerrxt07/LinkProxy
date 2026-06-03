/*
 * Copyright (C) 2019-2021 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.proxy.messages;

import static com.linkpowered.api.proxy.messages.MinecraftChannelIdentifier.create;
import static com.linkpowered.api.proxy.messages.MinecraftChannelIdentifier.from;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MinecraftChannelIdentifierTest {

  @Test
  void createAllowsValidNamespaces() {
    create("minecraft", "brand");
  }

  @Test
  void createAllowsEmptyName() {
    create("minecraft", "");
  }

  @Test
  void createDisallowsNull() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> create(null, "")),
        () -> assertThrows(IllegalArgumentException.class, () -> create("", "")),
        () -> assertThrows(IllegalArgumentException.class, () -> create("minecraft", null))
    );
  }

  @Test
  void fromIdentifierIsCorrect() {
    MinecraftChannelIdentifier expected = MinecraftChannelIdentifier.create("link", "test");
    assertEquals(expected, MinecraftChannelIdentifier.from("link:test"));
  }

  @Test
  void createAllowsSlashes() {
    create("link", "test/test2");
  }

  @Test
  void fromIdentifierDefaultNamespace() {
    assertEquals("minecraft", from("test").getNamespace());
    assertEquals("minecraft", from(":test").getNamespace());
  }

  @Test
  void fromIdentifierAllowsEmptyName() {
    from("minecraft:");
    from(":");
    from("");
  }

  @Test
  void fromIdentifierThrowsOnBadValues() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> from("hello:$$$$$$")),
        () -> assertThrows(IllegalArgumentException.class, () -> from("he/llo:wor/ld")),
        () -> assertThrows(IllegalArgumentException.class, () -> from("hello::"))
    );
  }
}
