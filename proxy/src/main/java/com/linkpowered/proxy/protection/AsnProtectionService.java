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

package com.linkpowered.proxy.protection;

import com.linkpowered.proxy.config.LinkConfiguration;
import com.linkpowered.proxy.connection.client.ConnectedPlayer;
import com.linkpowered.proxy.dragonfly.DragonflyProtectionService;
import com.maxmind.db.CHMCache;
import com.maxmind.db.Reader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Local MMDB-backed ASN guard for post-login VPN/proxy detection.
 */
public final class AsnProtectionService implements AutoCloseable {

  private static final Logger logger = LogManager.getLogger(AsnProtectionService.class);
  private static final Set<String> ASN_FIELD_NAMES = Set.of(
      "autonomous_system_number", "autonomoussystemnumber", "asn");

  private final LinkConfiguration.AsnGuard configuration;
  private final DragonflyProtectionService dragonflyProtection;
  private final Set<Integer> blockedAsns;
  private ExecutorService executor;
  private Reader reader;
  private volatile boolean available;

  /**
   * Creates the ASN protection service.
   *
   * @param configuration ASN guard configuration
   * @param dragonflyProtection Dragonfly protection service used to share blocked IPs
   */
  public AsnProtectionService(LinkConfiguration.AsnGuard configuration,
      DragonflyProtectionService dragonflyProtection) {
    this.configuration = configuration;
    this.dragonflyProtection = dragonflyProtection;
    this.blockedAsns = Set.copyOf(configuration.getBlockedAsns());
  }

  /**
   * Opens the local MMDB reader and worker pool. Failures leave the guard disabled.
   */
  public void start() {
    if (!configuration.isEnabled()) {
      logger.info("ASN guard is disabled.");
      return;
    }

    if (blockedAsns.isEmpty()) {
      logger.warn("ASN guard is enabled without blocked ASNs; lookups will not block players.");
    }

    Path databasePath = Path.of(configuration.getDatabaseFile());
    if (!Files.isRegularFile(databasePath)) {
      logger.error("ASN guard MMDB file {} does not exist or is not a file; allowing all players.",
          databasePath.toAbsolutePath());
      return;
    }

    try {
      this.reader = new Reader(databasePath.toFile(), new CHMCache());
      this.executor = Executors.newFixedThreadPool(configuration.getThreads(), new AsnGuardThreadFactory());
      this.available = true;
      logger.info("ASN guard loaded {} (database type '{}', build {}) with {} blocked ASN(s).",
          databasePath.toAbsolutePath(), reader.getMetadata().databaseType(),
          reader.getMetadata().buildTime(), blockedAsns.size());
    } catch (Exception ex) {
      this.available = false;
      closeReaderOnly();
      logger.error("ASN guard could not open MMDB file {}; allowing all players.",
          databasePath.toAbsolutePath(), ex);
    }
  }

  /**
   * Queues an asynchronous ASN check for a successfully logged-in player.
   *
   * @param player the player to check
   */
  public void checkPlayer(ConnectedPlayer player) {
    ExecutorService activeExecutor = executor;
    Reader activeReader = reader;
    if (!available || activeExecutor == null || activeReader == null || blockedAsns.isEmpty()) {
      return;
    }

    InetAddress address = player.getRemoteAddress().getAddress();
    String hostAddress = address.getHostAddress();
    activeExecutor.execute(() -> checkPlayer(address, hostAddress, player, activeReader));
  }

  private void checkPlayer(InetAddress address, String hostAddress, ConnectedPlayer player, Reader activeReader) {
    try {
      Map<String, Object> response = activeReader.get(address, Map.class);
      OptionalInt asn = extractAsn(response);
      if (asn.isEmpty()) {
        logger.debug("ASN guard found no ASN for {} ({}).", player.getUsername(), hostAddress);
        return;
      }

      int asNumber = asn.getAsInt();
      if (!blockedAsns.contains(asNumber)) {
        logger.debug("ASN guard allowed {} from {} (AS{}).", player.getUsername(), hostAddress, asNumber);
        return;
      }

      dragonflyProtection.addVpnAddressAsync(hostAddress);
      logger.info("ASN guard blocked {} from {} (AS{}).", player.getUsername(), hostAddress, asNumber);
      player.disconnect(Component.text(configuration.getDisconnectMessage(), NamedTextColor.RED));
    } catch (IOException ex) {
      logger.warn("ASN guard lookup failed for {}; allowing player.", hostAddress, ex);
    } catch (Exception ex) {
      logger.warn("ASN guard check failed for {}; allowing player.", hostAddress, ex);
    }
  }

  @Override
  public void close() {
    available = false;
    ExecutorService activeExecutor = executor;
    executor = null;
    if (activeExecutor != null) {
      activeExecutor.shutdownNow();
    }
    closeReaderOnly();
  }

  private void closeReaderOnly() {
    Reader activeReader = reader;
    reader = null;
    if (activeReader != null) {
      try {
        activeReader.close();
      } catch (IOException ex) {
        logger.debug("Failed to close ASN guard MMDB reader.", ex);
      }
    }
  }

  static OptionalInt extractAsn(Object value) {
    if (value == null) {
      return OptionalInt.empty();
    }

    OptionalInt direct = extractAsnValue(value);
    if (direct.isPresent()) {
      return direct;
    }

    if (value instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() instanceof String key
            && ASN_FIELD_NAMES.contains(normalizeFieldName(key))) {
          OptionalInt asn = extractAsnValue(entry.getValue());
          if (asn.isPresent()) {
            return asn;
          }
        }
      }
      for (Object child : map.values()) {
        OptionalInt nested = extractAsn(child);
        if (nested.isPresent()) {
          return nested;
        }
      }
    } else if (value instanceof Iterable<?> iterable) {
      for (Object child : iterable) {
        OptionalInt nested = extractAsn(child);
        if (nested.isPresent()) {
          return nested;
        }
      }
    } else if (value.getClass().isArray()) {
      Object[] array = (Object[]) value;
      for (Object child : array) {
        OptionalInt nested = extractAsn(child);
        if (nested.isPresent()) {
          return nested;
        }
      }
    }

    return OptionalInt.empty();
  }

  private static OptionalInt extractAsnValue(Object value) {
    if (value == null) {
      return OptionalInt.empty();
    }
    if (value instanceof Number number) {
      return OptionalInt.of(number.intValue());
    }
    if (value instanceof String string) {
      String text = string.toUpperCase(Locale.ROOT);
      if (text.startsWith("AS")) {
        text = text.substring(2);
      }
      try {
        return OptionalInt.of(Integer.parseInt(text));
      } catch (NumberFormatException ignored) {
        return OptionalInt.empty();
      }
    }
    return OptionalInt.empty();
  }

  private static String normalizeFieldName(String fieldName) {
    return fieldName.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
  }

  private static final class AsnGuardThreadFactory implements ThreadFactory {
    private int threadId;

    @Override
    public Thread newThread(Runnable task) {
      Thread thread = new Thread(task, "Link ASN Guard " + ++threadId);
      thread.setDaemon(true);
      return thread;
    }
  }
}
