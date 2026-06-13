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

package com.linkpowered.proxy.protocol.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.linkpowered.api.network.ProtocolVersion;
import com.linkpowered.proxy.protocol.ProtocolUtils;
import com.linkpowered.proxy.protocol.packet.PluginMessagePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginMessageUtilTest {

  @Test
  void transformLegacyToModernChannelWorksWithModern() {
    assertEquals("minecraft:brand", PluginMessageUtil
        .transformLegacyToModernChannel("minecraft:brand"));
    assertEquals("link:test", PluginMessageUtil
        .transformLegacyToModernChannel("link:test"));
  }

  @Test
  void transformLegacyToModernChannelRewritesSpecialCases() {
    assertEquals("minecraft:brand", PluginMessageUtil
        .transformLegacyToModernChannel("MC|Brand"));
    assertEquals("minecraft:register", PluginMessageUtil
        .transformLegacyToModernChannel("REGISTER"));
    assertEquals("minecraft:unregister", PluginMessageUtil
        .transformLegacyToModernChannel("UNREGISTER"));
    assertEquals("bungeecord:main", PluginMessageUtil
        .transformLegacyToModernChannel("BungeeCord"));
  }

  @Test
  void transformLegacyToModernChannelRewritesGeneral() {
    assertEquals("legacy:example", PluginMessageUtil
        .transformLegacyToModernChannel("Example"));
    assertEquals("legacy:pskeepalive", PluginMessageUtil
        .transformLegacyToModernChannel("PS|KeepAlive"));
  }

  @Test
  void rewriteMinecraftBrandAddsProxyNameForModernPayloads() {
    ByteBuf content = Unpooled.buffer();
    ProtocolUtils.writeString(content, "Paper");
    PluginMessagePacket rewritten = PluginMessageUtil.rewriteMinecraftBrand(
        new PluginMessagePacket("minecraft:brand", content),
        "Proxy-2",
        ProtocolVersion.MINECRAFT_1_20_5);

    assertEquals("minecraft:brand", rewritten.getChannel());
    assertEquals("Paper via Proxy-2", PluginMessageUtil.readBrandMessage(rewritten.content()));
  }

  @Test
  void rewriteMinecraftBrandAddsProxyNameForLegacyPayloads() {
    PluginMessagePacket rewritten = PluginMessageUtil.rewriteMinecraftBrand(
        new PluginMessagePacket("MC|Brand",
            Unpooled.copiedBuffer("CraftBukkit", StandardCharsets.UTF_8)),
        "Proxy-1",
        ProtocolVersion.MINECRAFT_1_7_6);

    assertEquals("MC|Brand", rewritten.getChannel());
    assertEquals("CraftBukkit via Proxy-1",
        PluginMessageUtil.readBrandMessage(rewritten.content()));
  }

}
