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

package com.linkpowered.proxy.dragonfly;

import com.linkpowered.proxy.config.LinkConfiguration;
import com.linkpowered.proxy.connection.client.ConnectedPlayer;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RSet;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * Dragonfly/Redis-backed multi-proxy state and admission protection.
 */
public final class DragonflyProtectionService implements AutoCloseable {

  private static final Logger logger = LogManager.getLogger(DragonflyProtectionService.class);
  private static final Duration PLAYER_TTL = Duration.ofSeconds(90);
  private static final Duration TRANSFER_NOTICE_TTL = Duration.ofSeconds(45);
  private static final Duration RATE_LIMIT_TTL_FLOOR = Duration.ofMillis(1);

  private final LinkConfiguration.Dragonfly configuration;
  private final String proxyName;
  private final Map<UUID, PlayerRecord> localPlayers = new ConcurrentHashMap<>();
  private final ScheduledExecutorService heartbeatExecutor;
  private volatile RedissonClient client;
  private volatile boolean connected;
  private volatile int disconnectListenerId;

  /**
   * Creates a Dragonfly protection service for a proxy instance.
   *
   * @param configuration the Dragonfly configuration
   * @param proxyName the configured proxy identity
   */
  public DragonflyProtectionService(LinkConfiguration.Dragonfly configuration, String proxyName) {
    this.configuration = configuration;
    this.proxyName = proxyName;
    this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
      Thread thread = new Thread(task, "Link Dragonfly Heartbeat");
      thread.setDaemon(true);
      return thread;
    });
  }

  /**
   * Starts the Dragonfly client and duplicate-player listener.
   *
   * @param duplicateDisconnectConsumer consumer invoked for cross-proxy duplicate disconnects
   */
  public void start(Consumer<UUID> duplicateDisconnectConsumer) {
    if (!configuration.isEnabled()) {
      logger.info("Dragonfly multi-proxy sync is disabled.");
      return;
    }

    try {
      Config redissonConfig = new Config();
      var singleServer = redissonConfig.useSingleServer()
          .setAddress(configuration.getAddress())
          .setDatabase(configuration.getDatabase())
          .setConnectionMinimumIdleSize(1)
          .setConnectionPoolSize(8);
      if (!configuration.getPassword().isBlank()) {
        singleServer.setPassword(configuration.getPassword());
      }
      this.client = Redisson.create(redissonConfig);
      this.client.getKeys().count();
      this.connected = true;
      subscribeDuplicateDisconnects(duplicateDisconnectConsumer);
      heartbeatExecutor.scheduleAtFixedRate(this::refreshPlayerHeartbeats, 30, 30, TimeUnit.SECONDS);
      logger.info("Connected to Dragonfly at {} with key prefix '{}' as proxy '{}'.",
          configuration.getAddress(), configuration.getKeyPrefix(), proxyName);
    } catch (Exception ex) {
      this.connected = false;
      logger.error("Unable to connect to Dragonfly at {}; multi-proxy sync and early IP protection are disabled.",
          configuration.getAddress(), ex);
      closeClientOnly();
    }
  }

  /**
   * Returns whether Dragonfly is enabled and connected.
   *
   * @return whether Dragonfly operations are available
   */
  public boolean isConnected() {
    return connected && client != null;
  }

  /**
   * Returns whether early HAProxy IP protection can be used.
   *
   * @return whether early protection is connected and enabled
   */
  public boolean isEarlyProtectionEnabled() {
    return isConnected() && configuration.isEarlyProtection();
  }

  /**
   * Checks whether an address is blocked by Dragonfly-backed IP sets.
   *
   * @param address the source address from the HAProxy header
   * @return whether the connection should be dropped
   */
  public boolean shouldDropAddress(InetAddress address) {
    if (!isEarlyProtectionEnabled()) {
      return false;
    }
    String hostAddress = address.getHostAddress();
    try {
      boolean blocked = set(configuration.getBlockedIpSet()).contains(hostAddress)
          || set(configuration.getVpnIpSet()).contains(hostAddress);
      if (blocked) {
        logger.info("Dragonfly early protection dropped connection from {}.", hostAddress);
      }
      return blocked;
    } catch (Exception ex) {
      logger.warn("Dragonfly IP protection lookup failed for {}; allowing connection.", hostAddress, ex);
      return false;
    }
  }

  /**
   * Attempts to reserve a shared login rate-limit slot for the address.
   *
   * @param address the source address attempting login
   * @return whether the login attempt may continue
   */
  public boolean attemptLogin(InetAddress address) {
    if (!isConnected() || configuration.getSharedLoginRatelimit() <= 0) {
      return true;
    }
    String key = key("rate:login:" + address.getHostAddress());
    Duration ttl = Duration.ofMillis(configuration.getSharedLoginRatelimit());
    if (ttl.compareTo(RATE_LIMIT_TTL_FLOOR) < 0) {
      ttl = RATE_LIMIT_TTL_FLOOR;
    }
    try {
      return bucket(key).trySet(proxyName, ttl.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception ex) {
      logger.warn("Dragonfly login rate-limit lookup failed for {}; allowing connection.",
          address.getHostAddress(), ex);
      return true;
    }
  }

  /**
   * Registers a player in shared multi-proxy state.
   *
   * @param player the player connection
   * @param replaceExisting whether existing ownership should be replaced
   * @return whether the player can be registered locally
   */
  public boolean registerPlayer(ConnectedPlayer player, boolean replaceExisting) {
    if (!isConnected() || !configuration.shouldSyncPlayers()) {
      return true;
    }

    PlayerRecord record = new PlayerRecord(
        player.getUniqueId(), player.getUsername().toLowerCase(Locale.US), proxyName);
    try {
      if (!replaceExisting) {
        if (!bucket(nameKey(record.username())).trySet(record.value(), PLAYER_TTL.toMillis(), TimeUnit.MILLISECONDS)) {
          return false;
        }
        if (!bucket(uuidKey(record.uuid())).trySet(record.value(), PLAYER_TTL.toMillis(), TimeUnit.MILLISECONDS)) {
          deleteIfOwned(nameKey(record.username()), record.value());
          return false;
        }
      } else {
        publishDuplicateDisconnect(record.uuid());
        bucket(nameKey(record.username())).set(record.value(), PLAYER_TTL.toMillis(), TimeUnit.MILLISECONDS);
        bucket(uuidKey(record.uuid())).set(record.value(), PLAYER_TTL.toMillis(), TimeUnit.MILLISECONDS);
      }
      localPlayers.put(record.uuid(), record);
      return true;
    } catch (Exception ex) {
      logger.warn("Dragonfly player registration failed for {}; falling back to local state.",
          player.getUsername(), ex);
      return true;
    }
  }

  /**
   * Adds an address to the shared VPN/proxy IP set for future early drops.
   *
   * @param hostAddress the textual IP address to add
   */
  public void addVpnAddressAsync(String hostAddress) {
    if (!isConnected() || hostAddress.isBlank()) {
      return;
    }
    try {
      set(configuration.getVpnIpSet()).addAsync(hostAddress)
          .whenComplete((added, throwable) -> {
            if (throwable != null) {
              logger.warn("Dragonfly failed to add {} to VPN IP set {}; future joins will not be dropped early.",
                  hostAddress, configuration.getVpnIpSet(), throwable);
            } else if (Boolean.TRUE.equals(added)) {
              logger.info("Dragonfly added {} to VPN IP set '{}' for future early drops.",
                  hostAddress, configuration.getVpnIpSet());
            }
          });
    } catch (Exception ex) {
      logger.warn("Dragonfly failed to queue VPN IP set add for {}; future joins will not be dropped early.",
          hostAddress, ex);
    }
  }

  /**
   * Records that a player should see a shutdown transfer notice on the next proxy.
   *
   * @param uuid the player UUID
   */
  public void recordShutdownTransferNotice(UUID uuid) {
    if (!isConnected()) {
      return;
    }
    try {
      bucket(transferNoticeKey(uuid)).set(proxyName, TRANSFER_NOTICE_TTL.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception ex) {
      logger.warn("Dragonfly failed to record shutdown transfer notice for {}; notice may not be shown.",
          uuid, ex);
    }
  }

  /**
   * Consumes a pending shutdown transfer notice for a player.
   *
   * @param uuid the player UUID
   * @return the source proxy name, or {@code null} if no notice was pending
   */
  public String consumeShutdownTransferNotice(UUID uuid) {
    if (!isConnected()) {
      return null;
    }
    try {
      RBucket<String> noticeBucket = bucket(transferNoticeKey(uuid));
      String sourceProxy = noticeBucket.get();
      if (sourceProxy != null) {
        noticeBucket.delete();
      }
      return sourceProxy;
    } catch (Exception ex) {
      logger.warn("Dragonfly failed to consume shutdown transfer notice for {}.", uuid, ex);
      return null;
    }
  }

  /**
   * Removes a player from shared multi-proxy state when this proxy owns the record.
   *
   * @param player the player connection
   */
  public void unregisterPlayer(ConnectedPlayer player) {
    PlayerRecord record = localPlayers.remove(player.getUniqueId());
    if (!isConnected() || !configuration.shouldSyncPlayers() || record == null) {
      return;
    }
    try {
      deleteIfOwned(nameKey(record.username()), record.value());
      deleteIfOwned(uuidKey(record.uuid()), record.value());
    } catch (Exception ex) {
      logger.debug("Dragonfly player unregister failed for {}.", player.getUsername(), ex);
    }
  }

  @Override
  public void close() {
    heartbeatExecutor.shutdownNow();
    RedissonClient activeClient = client;
    if (activeClient != null && disconnectListenerId != 0) {
      try {
        topic("players:disconnect").removeListener(disconnectListenerId);
      } catch (Exception ignored) {
        // Listener cleanup should not block shutdown.
      }
    }
    closeClientOnly();
    connected = false;
    localPlayers.clear();
  }

  private void subscribeDuplicateDisconnects(Consumer<UUID> duplicateDisconnectConsumer) {
    RTopic topic = topic("players:disconnect");
    this.disconnectListenerId = topic.addListener(String.class, (channel, message) -> {
      String[] parts = message.split(":", 2);
      if (parts.length != 2 || proxyName.equals(parts[0])) {
        return;
      }
      try {
        duplicateDisconnectConsumer.accept(UUID.fromString(parts[1]));
      } catch (IllegalArgumentException ignored) {
        // Ignore malformed messages from other Redis clients.
      }
    });
  }

  private void publishDuplicateDisconnect(UUID uuid) {
    topic("players:disconnect").publish(proxyName + ':' + uuid);
  }

  private void refreshPlayerHeartbeats() {
    if (!isConnected()) {
      return;
    }
    for (PlayerRecord record : localPlayers.values()) {
      try {
        bucket(nameKey(record.username())).set(record.value(), PLAYER_TTL.toMillis(), TimeUnit.MILLISECONDS);
        bucket(uuidKey(record.uuid())).set(record.value(), PLAYER_TTL.toMillis(), TimeUnit.MILLISECONDS);
      } catch (Exception ex) {
        logger.debug("Dragonfly heartbeat refresh failed for {}.", record.username(), ex);
      }
    }
  }

  private void deleteIfOwned(String key, String expectedValue) {
    RBucket<String> activeBucket = bucket(key);
    if (expectedValue.equals(activeBucket.get())) {
      activeBucket.delete();
    }
  }

  private RSet<String> set(String name) {
    return client.getSet(key(name));
  }

  private RTopic topic(String name) {
    return client.getTopic(key(name));
  }

  private RBucket<String> bucket(String key) {
    return client.getBucket(key);
  }

  private String nameKey(String username) {
    return key("players:name:" + username);
  }

  private String uuidKey(UUID uuid) {
    return key("players:uuid:" + uuid);
  }

  private String transferNoticeKey(UUID uuid) {
    return key("transfer:shutdown:" + uuid);
  }

  private String key(String suffix) {
    return configuration.getKeyPrefix() + ':' + suffix;
  }

  private void closeClientOnly() {
    RedissonClient activeClient = client;
    client = null;
    if (activeClient != null) {
      activeClient.shutdown();
    }
  }

  private record PlayerRecord(UUID uuid, String username, String proxyName) {
    private String value() {
      return proxyName + ':' + uuid + ':' + username;
    }
  }
}
