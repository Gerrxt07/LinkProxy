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

package com.linkpowered.proxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkConfigurationTest {

  @Test
  void dragonflyDefaultsToLocalPasswordlessServer() {
    LinkConfiguration.Dragonfly dragonfly = dragonfly(null);

    assertFalse(dragonfly.isEnabled());
    assertEquals("redis://127.0.0.1:6379", dragonfly.getAddress());
    assertEquals("", dragonfly.getPassword());
    assertEquals(0, dragonfly.getDatabase());
    assertEquals("link", dragonfly.getKeyPrefix());
    assertTrue(dragonfly.shouldSyncPlayers());
    assertTrue(dragonfly.isEarlyProtection());
    assertEquals("blocked-ips", dragonfly.getBlockedIpSet());
    assertEquals("vpn-ips", dragonfly.getVpnIpSet());
    assertEquals(3000, dragonfly.getSharedLoginRatelimit());
  }

  @Test
  void readsCustomDragonflyConfiguration() {
    CommentedConfig config = CommentedConfig.inMemory();
    config.set("enabled", true);
    config.set("address", "redis://10.0.0.4:6379");
    config.set("password", "secret");
    config.set("database", 3);
    config.set("key-prefix", "network");
    config.set("sync-players", false);
    config.set("early-protection", false);
    config.set("blocked-ip-set", "blocked");
    config.set("vpn-ip-set", "vpn");
    config.set("shared-login-ratelimit", 1250);

    LinkConfiguration.Dragonfly dragonfly = dragonfly(config);

    assertTrue(dragonfly.isEnabled());
    assertEquals("redis://10.0.0.4:6379", dragonfly.getAddress());
    assertEquals("secret", dragonfly.getPassword());
    assertEquals(3, dragonfly.getDatabase());
    assertEquals("network", dragonfly.getKeyPrefix());
    assertFalse(dragonfly.shouldSyncPlayers());
    assertFalse(dragonfly.isEarlyProtection());
    assertEquals("blocked", dragonfly.getBlockedIpSet());
    assertEquals("vpn", dragonfly.getVpnIpSet());
    assertEquals(1250, dragonfly.getSharedLoginRatelimit());
  }


  @Test
  void asnGuardDefaultsToDisabledLocalDatabase() {
    LinkConfiguration.AsnGuard asnGuard = asnGuard(null);

    assertFalse(asnGuard.isEnabled());
    assertEquals("dbip-asn-lite-2026-06.mmdb", asnGuard.getDatabaseFile());
    assertEquals(List.of(), asnGuard.getBlockedAsns());
    assertEquals(2, asnGuard.getThreads());
    assertEquals("VPN/Proxy connections are prohibited on this network.",
        asnGuard.getDisconnectMessage());
  }

  @Test
  void readsCustomAsnGuardConfiguration() {
    CommentedConfig config = CommentedConfig.inMemory();
    config.set("enabled", true);
    config.set("database-file", "/srv/shared/Server/LinkProxy/dbip-asn-lite-2026-06.mmdb");
    config.set("blocked-asns", List.of(24940, 16276));
    config.set("threads", 4);
    config.set("disconnect-message", "No VPNs.");

    LinkConfiguration.AsnGuard asnGuard = asnGuard(config);

    assertTrue(asnGuard.isEnabled());
    assertEquals("/srv/shared/Server/LinkProxy/dbip-asn-lite-2026-06.mmdb", asnGuard.getDatabaseFile());
    assertEquals(List.of(24940, 16276), asnGuard.getBlockedAsns());
    assertEquals(4, asnGuard.getThreads());
    assertEquals("No VPNs.", asnGuard.getDisconnectMessage());
  }

  @Test
  void readsAndNormalizesAllowedHosts(@TempDir Path tempDir) throws IOException {
    Path config = tempDir.resolve("link.toml");
    Files.writeString(config, """
        config-version = "2.8"
        forwarding-secret-file = "%s"
        allowed-hosts = ["MC.PhantomCommunity.DE.", "play.example.com:25565"]

        [servers]
        forge-community = "127.0.0.1:25590"
        try = ["forge-community"]

        [forced-hosts]

        [advanced]

        [query]
        """.formatted(tempDir.resolve("forwarding.secret").toAbsolutePath()));

    LinkConfiguration configuration = LinkConfiguration.read(config);

    assertEquals(List.of("mc.phantomcommunity.de", "play.example.com"),
        configuration.getAllowedHosts());
    assertTrue(configuration.isHostAllowed("mc.phantomcommunity.de"));
    assertTrue(configuration.isHostAllowed("MC.PHANTOMCOMMUNITY.DE."));
    assertTrue(configuration.isHostAllowed("play.example.com:25565"));
    assertFalse(configuration.isHostAllowed("2.59.133.137"));
    assertFalse(configuration.isHostAllowed("unknown.example.com"));
  }

  private static LinkConfiguration.AsnGuard asnGuard(CommentedConfig config) {
    try {
      Constructor<LinkConfiguration.AsnGuard> constructor =
          LinkConfiguration.AsnGuard.class.getDeclaredConstructor(CommentedConfig.class);
      constructor.setAccessible(true);
      return constructor.newInstance(config);
    } catch (IllegalAccessException | InstantiationException | InvocationTargetException
        | NoSuchMethodException ex) {
      throw new AssertionError(ex);
    }
  }

  private static LinkConfiguration.Dragonfly dragonfly(CommentedConfig config) {
    try {
      Constructor<LinkConfiguration.Dragonfly> constructor =
          LinkConfiguration.Dragonfly.class.getDeclaredConstructor(CommentedConfig.class);
      constructor.setAccessible(true);
      return constructor.newInstance(config);
    } catch (IllegalAccessException | InstantiationException | InvocationTargetException
        | NoSuchMethodException ex) {
      throw new AssertionError(ex);
    }
  }
}
