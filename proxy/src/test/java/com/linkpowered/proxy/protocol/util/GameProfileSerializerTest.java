/*
 * Copyright (C) 2026 Link Contributors
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

package com.linkpowered.proxy.protocol.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.linkpowered.api.util.GameProfile;
import com.linkpowered.proxy.LinkServer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameProfileSerializerTest {

  @Test
  void roundTripsSignedPropertiesWithoutReflectiveFieldMutation() {
    GameProfile profile = new GameProfile(
        UUID.fromString("12345678-1234-5678-1234-567812345678"),
        "player",
        List.of(new GameProfile.Property("textures", "value", "signature")));

    GameProfile decoded = LinkServer.GENERAL_GSON.fromJson(
        LinkServer.GENERAL_GSON.toJson(profile), GameProfile.class);

    assertEquals(profile.getId(), decoded.getId());
    assertEquals(profile.getName(), decoded.getName());
    assertEquals(1, decoded.getProperties().size());
    assertEquals("textures", decoded.getProperties().get(0).getName());
    assertEquals("value", decoded.getProperties().get(0).getValue());
    assertEquals("signature", decoded.getProperties().get(0).getSignature());
  }
}
