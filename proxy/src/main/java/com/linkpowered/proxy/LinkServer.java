/*
 * Copyright (C) 2018-2023 Link Contributors
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

package com.linkpowered.proxy;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.linkpowered.api.command.BrigadierCommand;
import com.linkpowered.api.event.proxy.ProxyInitializeEvent;
import com.linkpowered.api.event.proxy.ProxyPreShutdownEvent;
import com.linkpowered.api.event.proxy.ProxyReloadEvent;
import com.linkpowered.api.event.proxy.ProxyShutdownEvent;
import com.linkpowered.api.network.ProtocolVersion;
import com.linkpowered.api.plugin.PluginContainer;
import com.linkpowered.api.plugin.PluginDescription;
import com.linkpowered.api.plugin.PluginManager;
import com.linkpowered.api.proxy.Player;
import com.linkpowered.api.proxy.ProxyServer;
import com.linkpowered.api.proxy.player.ResourcePackInfo;
import com.linkpowered.api.proxy.server.RegisteredServer;
import com.linkpowered.api.proxy.server.ServerInfo;
import com.linkpowered.api.util.Favicon;
import com.linkpowered.api.util.GameProfile;
import com.linkpowered.api.util.ProxyVersion;
import com.linkpowered.proxy.command.LinkCommandManager;
import com.linkpowered.proxy.command.builtin.CallbackCommand;
import com.linkpowered.proxy.command.builtin.GlistCommand;
import com.linkpowered.proxy.command.builtin.LinkCommand;
import com.linkpowered.proxy.command.builtin.SendCommand;
import com.linkpowered.proxy.command.builtin.ServerCommand;
import com.linkpowered.proxy.command.builtin.ShutdownCommand;
import com.linkpowered.proxy.config.LinkConfiguration;
import com.linkpowered.proxy.connection.client.ConnectedPlayer;
import com.linkpowered.proxy.connection.player.resourcepack.LinkResourcePackInfo;
import com.linkpowered.proxy.connection.util.ServerListPingHandler;
import com.linkpowered.proxy.console.LinkConsole;
import com.linkpowered.proxy.crypto.EncryptionUtils;
import com.linkpowered.proxy.dragonfly.DragonflyProtectionService;
import com.linkpowered.proxy.event.LinkEventManager;
import com.linkpowered.proxy.network.ConnectionManager;
import com.linkpowered.proxy.plugin.LinkPluginManager;
import com.linkpowered.proxy.plugin.loader.LinkPluginContainer;
import com.linkpowered.proxy.plugin.loader.LinkPluginDescription;
import com.linkpowered.proxy.plugin.virtual.LinkVirtualPlugin;
import com.linkpowered.proxy.protection.AsnProtectionService;
import com.linkpowered.proxy.protocol.ProtocolUtils;
import com.linkpowered.proxy.protocol.util.FaviconSerializer;
import com.linkpowered.proxy.protocol.util.GameProfileSerializer;
import com.linkpowered.proxy.scheduler.LinkScheduler;
import com.linkpowered.proxy.server.ServerMap;
import com.linkpowered.proxy.util.AddressUtil;
import com.linkpowered.proxy.util.ClosestLocaleMatcher;
import com.linkpowered.proxy.util.LinkChannelRegistrar;
import com.linkpowered.proxy.util.ResourceUtils;
import com.linkpowered.proxy.util.ratelimit.Ratelimiter;
import com.linkpowered.proxy.util.ratelimit.Ratelimiters;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslationStore;
import net.kyori.adventure.translation.GlobalTranslator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implementation of {@link ProxyServer}.
 */
public class LinkServer implements ProxyServer, ForwardingAudience {

  public static final String LINK_URL = "https://papermc.io/software/link";
  private static final String COLOR_RESET = "\u001B[0m";
  private static final String COLOR_BLUE = "\u001B[94m";
  private static final String COLOR_CYAN = "\u001B[96m";
  private static final String COLOR_GRAY = "\u001B[90m";

  private static final Logger logger = LogManager.getLogger(LinkServer.class);
  public static final Gson GENERAL_GSON = new GsonBuilder()
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .registerTypeHierarchyAdapter(GameProfile.class, GameProfileSerializer.INSTANCE)
      .create();
  private static final Gson PRE_1_16_PING_SERIALIZER = new GsonBuilder()
      .registerTypeHierarchyAdapter(
          Component.class,
          ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_15_2)
                  .serializer().getAdapter(Component.class)
      )
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .create();
  private static final Gson PRE_1_20_3_PING_SERIALIZER = new GsonBuilder()
      .registerTypeHierarchyAdapter(
          Component.class,
          ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_20_2)
                  .serializer().getAdapter(Component.class)
      )
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .create();
  private static final Gson MODERN_PING_SERIALIZER = new GsonBuilder()
      .registerTypeHierarchyAdapter(
          Component.class,
          ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_20_3)
                  .serializer().getAdapter(Component.class)
      )
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .create();
  private static final int PRE_SHUTDOWN_TIMEOUT =
            Integer.getInteger("link.pre-shutdown-timeout", 10);

  private @MonotonicNonNull ConnectionManager cm;
  private final ProxyOptions options;
  private @MonotonicNonNull LinkConfiguration configuration;
  private @MonotonicNonNull DragonflyProtectionService dragonflyProtection;
  private @MonotonicNonNull AsnProtectionService asnProtection;
  private @MonotonicNonNull KeyPair serverKeyPair;
  private final ServerMap servers;
  private final LinkCommandManager commandManager;
  private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
  private boolean shutdown = false;
  private final LinkPluginManager pluginManager;

  private final Map<UUID, ConnectedPlayer> connectionsByUuid = new ConcurrentHashMap<>();
  private final Map<String, ConnectedPlayer> connectionsByName = new ConcurrentHashMap<>();
  private final LinkConsole console;
  private @MonotonicNonNull Ratelimiter<InetAddress> ipAttemptLimiter;
  private @MonotonicNonNull Ratelimiter<UUID> commandRateLimiter;
  private @MonotonicNonNull Ratelimiter<UUID> tabCompleteRateLimiter;
  private final LinkEventManager eventManager;
  private final LinkScheduler scheduler;
  private final LinkChannelRegistrar channelRegistrar = new LinkChannelRegistrar();
  private final ServerListPingHandler serverListPingHandler;

  LinkServer(final ProxyOptions options) {
    pluginManager = new LinkPluginManager(this);
    eventManager = new LinkEventManager(pluginManager);
    commandManager = new LinkCommandManager(eventManager, pluginManager);
    scheduler = new LinkScheduler(pluginManager);
    console = new LinkConsole(this);
    servers = new ServerMap(this);
    serverListPingHandler = new ServerListPingHandler(this);
    this.options = options;
  }

  public KeyPair getServerKeyPair() {
    return serverKeyPair;
  }

  @Override
  public LinkConfiguration getConfiguration() {
    return this.configuration;
  }

  @Override
  public ProxyVersion getVersion() {
    Package pkg = LinkServer.class.getPackage();
    String implName;
    String implVersion;
    String implVendor;
    if (pkg != null) {
      implName = MoreObjects.firstNonNull(pkg.getImplementationTitle(), "Link");
      implVersion = MoreObjects.firstNonNull(pkg.getImplementationVersion(), "<unknown>");
      implVendor = MoreObjects.firstNonNull(pkg.getImplementationVendor(), "Link Contributors");
    } else {
      implName = "Link";
      implVersion = "<unknown>";
      implVendor = "Link Contributors";
    }

    return new ProxyVersion(implName, implVendor, implVersion);
  }

  private LinkPluginContainer createVirtualPlugin() {
    ProxyVersion version = getVersion();
    PluginDescription description = new LinkPluginDescription(
        "link", version.getName(), version.getVersion(), "The Link proxy",
            version.getName().equals("Link") ? LINK_URL : null,
            ImmutableList.of(version.getVendor()), Collections.emptyList(), null);
    LinkPluginContainer container = new LinkPluginContainer(description);
    container.setInstance(LinkVirtualPlugin.INSTANCE);
    return container;
  }

  @Override
  public LinkCommandManager getCommandManager() {
    return commandManager;
  }

  void awaitProxyShutdown() {
    cm.getBossGroup().terminationFuture().syncUninterruptibly();
  }

  @EnsuresNonNull({"serverKeyPair", "servers", "pluginManager", "eventManager", "scheduler",
      "console", "cm", "configuration"})
  void start() {
    console.setupStreams();
    logStartupBanner();
    pluginManager.registerPlugin(this.createVirtualPlugin());

    // Yes, you're reading that correctly. We're generating a 1024-bit RSA keypair. Sounds
    // dangerous, right? We're well within the realm of factoring such a key...
    //
    // You can blame Mojang. For the record, we also don't consider the Minecraft protocol
    // encryption scheme to be secure, and it has reached the point where any serious cryptographic
    // protocol needs a refresh. There are multiple obvious weaknesses, and this is far from the
    // most serious.
    //
    // If you are using Minecraft in a security-sensitive application, *I don't know what to say.*
    serverKeyPair = EncryptionUtils.createRsaKeyPair(1024);

    // Initialize commands first
    final BrigadierCommand linkParentCommand = LinkCommand.create(this);
    commandManager.register(
        commandManager.metaBuilder(linkParentCommand)
            .plugin(LinkVirtualPlugin.INSTANCE)
            .build(),
        linkParentCommand
    );
    final BrigadierCommand callbackCommand = CallbackCommand.create();
    commandManager.register(
        commandManager.metaBuilder(callbackCommand)
            .plugin(LinkVirtualPlugin.INSTANCE)
            .build(),
        callbackCommand
    );
    final BrigadierCommand serverCommand = ServerCommand.create(this);
    commandManager.register(
        commandManager.metaBuilder(serverCommand)
            .plugin(LinkVirtualPlugin.INSTANCE)
            .build(),
        serverCommand
    );
    final BrigadierCommand shutdownCommand = ShutdownCommand.command(this);
    commandManager.register(
        commandManager.metaBuilder(shutdownCommand)
            .plugin(LinkVirtualPlugin.INSTANCE)
            .aliases("end", "stop")
            .build(),
        shutdownCommand
    );
    new GlistCommand(this).register();
    new SendCommand(this).register();

    this.doStartupConfigLoad();
    dragonflyProtection = new DragonflyProtectionService(configuration.getDragonfly(),
        configuration.getProxyName());
    dragonflyProtection.start(uuid -> getPlayer(uuid).ifPresent(player ->
        player.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"))));
    asnProtection = new AsnProtectionService(configuration.getAsnGuard(), dragonflyProtection);
    asnProtection.start();
    cm = new ConnectionManager(this, configuration.getNetworkTransport());
    cm.logChannelInformation();

    final CompletableFuture<Void> translationsFuture = registerTranslationsAsync();

    for (ServerInfo cliServer : options.getServers()) {
      servers.register(cliServer);
    }

    if (!options.isIgnoreConfigServers()) {
      for (Map.Entry<String, String> entry : configuration.getServers().entrySet()) {
        servers.register(new ServerInfo(entry.getKey(), AddressUtil.parseAddress(entry.getValue())));
      }
    }

    ipAttemptLimiter = Ratelimiters.createWithMilliseconds(configuration.getLoginRatelimit());
    commandRateLimiter = Ratelimiters.createWithMilliseconds(configuration.getCommandRatelimit());
    tabCompleteRateLimiter = Ratelimiters.createWithMilliseconds(configuration.getTabCompleteRatelimit());
    loadPlugins();

    // Go ahead and fire the proxy initialization event. We block since plugins should have a chance
    // to fully initialize before we accept any connections to the server.
    eventManager.fire(new ProxyInitializeEvent()).join();

    // init console permissions after plugins are loaded
    console.setupPermissions();

    final Boolean haproxy = this.options.isHaproxy();
    if (haproxy != null) {
      logger.debug("Overriding HAProxy protocol to {} from command line option", haproxy);
      configuration.setProxyProtocol(haproxy);
    }

    final Integer port = this.options.getPort();
    final InetSocketAddress bindAddress;
    if (port != null) {
      logger.debug("Overriding bind port to {} from command line option", port);
      bindAddress = new InetSocketAddress(configuration.getBind().getHostString(), port);
    } else {
      bindAddress = configuration.getBind();
    }
    logStartupConfigurationWarnings(bindAddress);
    this.cm.bind(bindAddress);

    if (configuration.isQueryEnabled()) {
      this.cm.queryBind(configuration.getBind().getHostString(), configuration.getQueryPort());
    }

    translationsFuture.whenComplete((ignored, throwable) -> {
      if (throwable != null) {
        logger.error("Unable to finish loading localizations", throwable);
      }
    });
  }

  private void logStartupBanner() {
    final ProxyVersion version = getVersion();
    final String lineSeparator = System.lineSeparator();
    logger.info(lineSeparator
        + COLOR_CYAN + " _      _       _" + COLOR_RESET + lineSeparator
        + COLOR_CYAN + "| |    (_)     | |" + COLOR_RESET + lineSeparator
        + COLOR_BLUE + "| |     _ _ __ | | __" + COLOR_RESET + lineSeparator
        + COLOR_BLUE + "| |    | | '_ \\| |/ /" + COLOR_RESET + lineSeparator
        + COLOR_CYAN + "| |____| | | | |   <" + COLOR_RESET + lineSeparator
        + COLOR_CYAN + "|______|_|_| |_|_|\\_\\" + COLOR_RESET + lineSeparator
        + COLOR_GRAY + version.getName() + " " + version.getVersion() + COLOR_RESET);
  }

  private void logStartupConfigurationWarnings(InetSocketAddress bindAddress) {
    final InetAddress bindHost = bindAddress.getAddress();
    if (configuration.isProxyProtocol() && bindHost != null && bindHost.isAnyLocalAddress()) {
      logger.warn("HAProxy PROXY protocol is enabled while the proxy listens on all interfaces. "
          + "Firewall this port so only trusted HAProxy nodes can connect.");
    }

    if (configuration.shouldPreventClientProxyConnections() && !configuration.isProxyProtocol()) {
      logger.warn("prevent-client-proxy-connections is enabled without HAProxy PROXY protocol. "
          + "If Link is behind HAProxy, valid players may be checked against the HAProxy IP.");
    }

    if (Boolean.getBoolean("link.packet-decode-logging")) {
      logger.warn("Packet decode logging is enabled. This can produce large logs under malformed "
          + "traffic and should only be used while debugging.");
    }
  }

  private CompletableFuture<Void> registerTranslationsAsync() {
    logger.info("Loading localizations...");
    return CompletableFuture.runAsync(
        this::registerTranslations,
        command -> Thread.ofVirtual().name("Link Translation Loader", 0).start(command));
  }

  private void registerTranslations() {
    final MiniMessageTranslationStore translationRegistry =
            MiniMessageTranslationStore.create(Key.key("link", "translations"));
    translationRegistry.defaultLocale(Locale.US);
    try {
      ResourceUtils.visitResources(LinkServer.class, path -> {
        final Path langPath = Path.of("lang");

        try {
          if (!Files.exists(langPath)) {
            Files.createDirectory(langPath);
            try (final Stream<Path> files = Files.walk(path)) {
              files.filter(Files::isRegularFile).forEach(file -> {
                try {
                  final Path langFile = langPath.resolve(file.getFileName().toString());
                  if (!Files.exists(langFile)) {
                    try (final InputStream is = Files.newInputStream(file)) {
                      Files.copy(is, langFile);
                    }
                  }
                } catch (IOException e) {
                  logger.error("Encountered an I/O error whilst loading translations", e);
                }
              });
            }

          }

          try (final Stream<Path> files = Files.walk(langPath)) {
            files.filter(Files::isRegularFile).forEach(file -> {
              final String filename = com.google.common.io.Files
                      .getNameWithoutExtension(file.getFileName().toString());
              final String localeName = filename.replace("messages_", "")
                      .replace("messages", "")
                      .replace('_', '-');
              final Locale locale = localeName.isBlank()
                      ? Locale.US
                      : Locale.forLanguageTag(localeName);

              translationRegistry.registerAll(locale, file, false);
              ClosestLocaleMatcher.INSTANCE.registerKnown(locale);
            });
          }

        } catch (IOException e) {
          logger.error("Encountered an I/O error whilst loading translations", e);
        }
      }, "com", "linkpowered", "proxy", "l10n");
    } catch (IOException e) {
      logger.error("Encountered an I/O error whilst loading translations", e);
      return;
    }
    GlobalTranslator.translator().addSource(translationRegistry);
  }

  @SuppressFBWarnings("DM_EXIT")
  private void doStartupConfigLoad() {
    try {
      Path configPath = Path.of("link.toml");
      configuration = LinkConfiguration.read(configPath);

      if (!configuration.validate()) {
        logger.error("Your configuration is invalid. Link will not start up until the errors "
            + "are resolved.");
        LogManager.shutdown();
        System.exit(1);
      }

      commandManager.setAnnounceProxyCommands(configuration.isAnnounceProxyCommands());
    } catch (Exception e) {
      logger.error("Unable to read/load/save your link.toml. The server will shut down.", e);
      LogManager.shutdown();
      System.exit(1);
    }
  }

  private void loadPlugins() {
    logger.info("Loading plugins...");

    try {
      Path pluginPath = Path.of("plugins");

      if (!pluginPath.toFile().exists()) {
        Files.createDirectory(pluginPath);
      } else {
        if (!pluginPath.toFile().isDirectory()) {
          logger.warn("Plugin location {} is not a directory, continuing without loading plugins",
              pluginPath);
          return;
        }

        pluginManager.loadPlugins(pluginPath);
      }
    } catch (Exception e) {
      logger.error("Couldn't load plugins", e);
    }

    // Register the plugin main classes so that we can fire the proxy initialize event
    for (PluginContainer plugin : pluginManager.getPlugins()) {
      Optional<?> instance = plugin.getInstance();
      if (instance.isPresent()) {
        try {
          eventManager.registerInternally(plugin, instance.get());
        } catch (Exception e) {
          logger.error("Unable to register plugin listener for {}",
              plugin.getDescription().getName().orElse(plugin.getDescription().getId()), e);
        }
      }
    }

    logger.info("Loaded {} plugins", pluginManager.getPlugins().size());
  }

  public Bootstrap createBootstrap(@Nullable EventLoopGroup group) {
    return this.cm.createWorker(group);
  }

  public ChannelInitializer<Channel> getBackendChannelInitializer() {
    return this.cm.backendChannelInitializer.get();
  }

  public ServerListPingHandler getServerListPingHandler() {
    return serverListPingHandler;
  }

  public boolean isShutdown() {
    return shutdown;
  }

  /**
   * Reloads the proxy's configuration.
   *
   * @return {@code true} if successful, {@code false} if we can't read the configuration
   * @throws IOException if we can't read {@code link.toml}
   */
  public boolean reloadConfiguration() throws IOException {
    Path configPath = Path.of("link.toml");
    LinkConfiguration newConfiguration = LinkConfiguration.read(configPath);

    if (!newConfiguration.validate()) {
      return false;
    }

    // Re-register servers. If a server is being replaced, make sure to note what players need to
    // move back to a fallback server.
    Collection<ConnectedPlayer> evacuate = new ArrayList<>();
    for (Map.Entry<String, String> entry : newConfiguration.getServers().entrySet()) {
      ServerInfo newInfo = new ServerInfo(entry.getKey(), AddressUtil.parseAddress(entry.getValue()));
      Optional<RegisteredServer> rs = servers.getServer(entry.getKey());
      if (rs.isEmpty()) {
        servers.register(newInfo);
      } else if (!rs.get().getServerInfo().equals(newInfo)) {
        for (Player player : rs.get().getPlayersConnected()) {
          if (!(player instanceof ConnectedPlayer)) {
            throw new IllegalStateException("ConnectedPlayer not found for player " + player
                + " in server " + rs.get().getServerInfo().getName());
          }
          evacuate.add((ConnectedPlayer) player);
        }
        servers.unregister(rs.get().getServerInfo());
        servers.register(newInfo);
      }
    }

    // If we had any players to evacuate, let's move them now. Wait until they are all moved off.
    if (!evacuate.isEmpty()) {
      CountDownLatch latch = new CountDownLatch(evacuate.size());
      for (ConnectedPlayer player : evacuate) {
        Optional<RegisteredServer> next = player.getNextServerToTry();
        if (next.isPresent()) {
          player.createConnectionRequest(next.get()).connectWithIndication()
              .whenComplete((success, ex) -> {
                if (ex != null || success == null || !success) {
                  player.disconnect(Component.text("Your server has been changed, but we could "
                      + "not move you to any fallback servers."));
                }
                latch.countDown();
              });
        } else {
          latch.countDown();
          player.disconnect(Component.text("Your server has been changed, but we could "
              + "not move you to any fallback servers."));
        }
      }
      try {
        latch.await();
      } catch (InterruptedException e) {
        logger.error("Interrupted whilst moving players", e);
        Thread.currentThread().interrupt();
      }
    }

    // If we have a new bind address, bind to it
    if (!configuration.getBind().equals(newConfiguration.getBind())) {
      this.cm.bind(newConfiguration.getBind());
      this.cm.close(configuration.getBind());
    }

    boolean queryPortChanged = newConfiguration.getQueryPort() != configuration.getQueryPort();
    boolean queryAlreadyEnabled = configuration.isQueryEnabled();
    boolean queryEnabled = newConfiguration.isQueryEnabled();
    if (queryAlreadyEnabled && (!queryEnabled || queryPortChanged)) {
      this.cm.close(new InetSocketAddress(
          configuration.getBind().getHostString(), configuration.getQueryPort()));
    }
    if (queryEnabled && (!queryAlreadyEnabled || queryPortChanged)) {
      this.cm.queryBind(newConfiguration.getBind().getHostString(),
          newConfiguration.getQueryPort());
    }

    if (!configuration.getNetworkTransport().equalsIgnoreCase(newConfiguration.getNetworkTransport())) {
      logger.warn("network-transport changes require a full proxy restart to take effect.");
    }

    commandManager.setAnnounceProxyCommands(newConfiguration.isAnnounceProxyCommands());
    ipAttemptLimiter = Ratelimiters.createWithMilliseconds(newConfiguration.getLoginRatelimit());
    DragonflyProtectionService newDragonflyProtection = new DragonflyProtectionService(
        newConfiguration.getDragonfly(), newConfiguration.getProxyName());
    newDragonflyProtection.start(uuid -> getPlayer(uuid).ifPresent(player ->
        player.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"))));
    AsnProtectionService newAsnProtection = new AsnProtectionService(
        newConfiguration.getAsnGuard(), newDragonflyProtection);
    newAsnProtection.start();
    asnProtection.close();
    dragonflyProtection.close();
    dragonflyProtection = newDragonflyProtection;
    asnProtection = newAsnProtection;
    this.configuration = newConfiguration;
    eventManager.fireAndForget(new ProxyReloadEvent());
    return true;
  }

  private void transferPlayersBeforeShutdown(ImmutableList<ConnectedPlayer> players) {
    if (!configuration.isShutdownTransferEnabled() || players.isEmpty()) {
      return;
    }

    InetSocketAddress transferTarget = InetSocketAddress.createUnresolved(
        configuration.getShutdownTransferHost(), configuration.getShutdownTransferPort());
    int transferred = 0;
    int skipped = 0;
    for (ConnectedPlayer player : players) {
      if (!player.isActive() || player.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_20_5)) {
        skipped++;
        continue;
      }
      try {
        player.transferToHost(transferTarget);
        transferred++;
        logger.info("Transferring {} to {}:{} before proxy shutdown.",
            player.getUsername(), transferTarget.getHostString(), transferTarget.getPort());
      } catch (Exception ex) {
        skipped++;
        logger.warn("Unable to transfer {} to {}:{} before proxy shutdown; player will be kicked normally.",
            player.getUsername(), transferTarget.getHostString(), transferTarget.getPort(), ex);
      }
    }

    if (transferred == 0) {
      logger.info("No shutdown transfer packets sent; {} player(s) were not eligible.", skipped);
      return;
    }

    int waitMillis = configuration.getShutdownTransferWaitMillis();
    logger.info("Sent shutdown transfer packet to {} player(s); waiting {}ms before kicking remaining connections.",
        transferred, waitMillis);
    if (waitMillis <= 0) {
      return;
    }
    try {
      Thread.sleep(waitMillis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      logger.warn("Interrupted while waiting for shutdown transfers to complete; continuing shutdown.");
    }
  }

  /**
   * Shuts down the proxy, kicking players with the specified reason.
   *
   * @param explicitExit whether the user explicitly shut down the proxy
   * @param reason       message to kick online players with
   */
  public void shutdown(boolean explicitExit, Component reason) {
    if (eventManager == null || pluginManager == null || cm == null || scheduler == null) {
      throw new AssertionError();
    }

    if (!shutdownInProgress.compareAndSet(false, true)) {
      return;
    }

    Runnable shutdownProcess = () -> {
      logger.info("Shutting down the proxy...");

      // Shutdown the connection manager, this should be
      // done first to refuse new connections
      cm.shutdown();

      ImmutableList<ConnectedPlayer> players = ImmutableList.copyOf(connectionsByUuid.values());
      transferPlayersBeforeShutdown(players);

      asnProtection.close();
      dragonflyProtection.close();

      try {
        eventManager.fire(new ProxyPreShutdownEvent())
                .toCompletableFuture()
                .get(PRE_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
      } catch (TimeoutException ignored) {
        logger.warn("Your plugins took over {} seconds during pre shutdown.",
                PRE_SHUTDOWN_TIMEOUT);
      } catch (ExecutionException ee) {
        logger.error("Exception in ProxyPreShutdownEvent handler; continuing shutdown.", ee);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        logger.warn("Interrupted while waiting for ProxyPreShutdownEvent; continuing shutdown.");
      }

      for (ConnectedPlayer player : players) {
        if (player.isActive()) {
          player.disconnect(reason);
        }
      }

      try {
        boolean timedOut = false;

        try {
          // Wait for the connections finish tearing down, this
          // makes sure that all the disconnect events are being fired

          CompletableFuture<Void> playersTeardownFuture = CompletableFuture.allOf(players.stream()
                  .map(ConnectedPlayer::getTeardownFuture)
                  .toArray((IntFunction<CompletableFuture<Void>[]>) CompletableFuture[]::new));

          playersTeardownFuture.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
          timedOut = true;
        } catch (ExecutionException e) {
          timedOut = true;
          logger.error("Exception while tearing down player connections", e);
        }

        eventManager.fire(new ProxyShutdownEvent()).join();

        timedOut = !scheduler.shutdown() || timedOut;

        if (timedOut) {
          logger.error("Your plugins took over 10 seconds to shut down.");
        }
      } catch (InterruptedException e) {
        // Not much we can do about this...
        Thread.currentThread().interrupt();
      }

      // Since we manually removed the shutdown hook, we need to handle the shutdown ourselves.
      LogManager.shutdown();

      shutdown = true;

      if (explicitExit) {
        System.exit(0);
      }
    };

    if (explicitExit) {
      Thread thread = new Thread(shutdownProcess);
      thread.start();
    } else {
      shutdownProcess.run();
    }
  }

  /**
   * Calls {@link #shutdown(boolean, Component)} with the default reason "Proxy shutting down."
   *
   * @param explicitExit whether the user explicitly shut down the proxy
   */
  public void shutdown(boolean explicitExit) {
    shutdown(explicitExit, Component.translatable("link.kick.shutdown"));
  }

  @Override
  public void shutdown(Component reason) {
    shutdown(true, reason);
  }

  @Override
  public void shutdown() {
    shutdown(true);
  }

  @Override
  public void closeListeners() {
    this.cm.closeEndpoints(false);
  }

  public HttpClient createHttpClient() {
    return cm.createHttpClient();
  }

  public @MonotonicNonNull Ratelimiter<InetAddress> getIpAttemptLimiter() {
    return ipAttemptLimiter;
  }

  public @MonotonicNonNull DragonflyProtectionService getDragonflyProtection() {
    return dragonflyProtection;
  }

  public @MonotonicNonNull AsnProtectionService getAsnProtection() {
    return asnProtection;
  }

  public @MonotonicNonNull Ratelimiter<UUID> getCommandRateLimiter() {
    return commandRateLimiter;
  }

  public @MonotonicNonNull Ratelimiter<UUID> getTabCompleteRateLimiter() {
    return tabCompleteRateLimiter;
  }

  /**
   * Checks if the {@code connection} can be registered with the proxy.
   *
   * @param connection the connection to check
   * @return {@code true} if we can register the connection, {@code false} if not
   */
  public boolean canRegisterConnection(ConnectedPlayer connection) {
    if (configuration.isOnlineMode() && configuration.isOnlineModeKickExistingPlayers()) {
      return true;
    }
    String lowerName = connection.getUsername().toLowerCase(Locale.US);
    return !(connectionsByName.containsKey(lowerName)
        || connectionsByUuid.containsKey(connection.getUniqueId()));
  }

  /**
   * Attempts to register the {@code connection} with the proxy.
   *
   * @param connection the connection to register
   * @return {@code true} if we registered the connection, {@code false} if not
   */
  public boolean registerConnection(ConnectedPlayer connection) {
    String lowerName = connection.getUsername().toLowerCase(Locale.US);

    if (!this.configuration.isOnlineModeKickExistingPlayers()) {
      if (!dragonflyProtection.registerPlayer(connection, false)) {
        return false;
      }
      if (connectionsByName.putIfAbsent(lowerName, connection) != null) {
        dragonflyProtection.unregisterPlayer(connection);
        return false;
      }
      if (connectionsByUuid.putIfAbsent(connection.getUniqueId(), connection) != null) {
        connectionsByName.remove(lowerName, connection);
        dragonflyProtection.unregisterPlayer(connection);
        return false;
      }
    } else {
      dragonflyProtection.registerPlayer(connection, true);
      ConnectedPlayer existing = connectionsByUuid.get(connection.getUniqueId());
      if (existing != null) {
        existing.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));
      }

      // We can now replace the entries as needed.
      connectionsByName.put(lowerName, connection);
      connectionsByUuid.put(connection.getUniqueId(), connection);
    }
    return true;
  }

  /**
   * Unregisters the given player from the proxy.
   *
   * @param connection the connection to unregister
   */
  public void unregisterConnection(ConnectedPlayer connection) {
    connectionsByName.remove(connection.getUsername().toLowerCase(Locale.US), connection);
    connectionsByUuid.remove(connection.getUniqueId(), connection);
    dragonflyProtection.unregisterPlayer(connection);
    connection.disconnected();
  }

  @Override
  public Optional<Player> getPlayer(String username) {
    Preconditions.checkNotNull(username, "username");
    return Optional.ofNullable(connectionsByName.get(username.toLowerCase(Locale.US)));
  }

  @Override
  public Optional<Player> getPlayer(UUID uuid) {
    Preconditions.checkNotNull(uuid, "uuid");
    return Optional.ofNullable(connectionsByUuid.get(uuid));
  }

  @Override
  public Collection<Player> matchPlayer(String partialName) {
    Objects.requireNonNull(partialName);

    return getAllPlayers().stream().filter(p -> p.getUsername()
            .regionMatches(true, 0, partialName, 0, partialName.length()))
        .collect(Collectors.toList());
  }

  @Override
  public Collection<RegisteredServer> matchServer(String partialName) {
    Objects.requireNonNull(partialName);

    return getAllServers().stream().filter(s -> s.getServerInfo().getName()
            .regionMatches(true, 0, partialName, 0, partialName.length()))
        .collect(Collectors.toList());
  }

  @Override
  public Collection<Player> getAllPlayers() {
    return ImmutableList.copyOf(connectionsByUuid.values());
  }

  @Override
  public int getPlayerCount() {
    return connectionsByUuid.size();
  }

  @Override
  public Optional<RegisteredServer> getServer(String name) {
    return servers.getServer(name);
  }

  @Override
  public Collection<RegisteredServer> getAllServers() {
    return servers.getAllServers();
  }

  @Override
  public RegisteredServer createRawRegisteredServer(ServerInfo server) {
    return servers.createRawRegisteredServer(server);
  }

  @Override
  public RegisteredServer registerServer(ServerInfo server) {
    return servers.register(server);
  }

  @Override
  public void unregisterServer(ServerInfo server) {
    servers.unregister(server);
  }

  @Override
  public LinkConsole getConsoleCommandSource() {
    return console;
  }

  @Override
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  @Override
  public LinkEventManager getEventManager() {
    return eventManager;
  }

  @Override
  public LinkScheduler getScheduler() {
    return scheduler;
  }

  @Override
  public LinkChannelRegistrar getChannelRegistrar() {
    return channelRegistrar;
  }

  @Override
  public boolean isShuttingDown() {
    return shutdownInProgress.get();
  }

  @Override
  public InetSocketAddress getBoundAddress() {
    if (configuration == null) {
      throw new IllegalStateException(
          "No configuration"); // even though you'll never get the chance... heh, heh
    }
    return configuration.getBind();
  }

  @Override
  public @NonNull Iterable<? extends Audience> audiences() {
    Collection<Audience> audiences = new ArrayList<>(this.getPlayerCount() + 1);
    audiences.add(this.console);
    audiences.addAll(this.getAllPlayers());
    return audiences;
  }

  /**
   * Returns a Gson instance for use in serializing server ping instances.
   *
   * @param version the protocol version in use
   * @return the Gson instance
   */
  public static Gson getPingGsonInstance(ProtocolVersion version) {
    if (version == ProtocolVersion.UNKNOWN
        || version.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      return MODERN_PING_SERIALIZER;
    }
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      return PRE_1_20_3_PING_SERIALIZER;
    }
    return PRE_1_16_PING_SERIALIZER;
  }

  @Override
  public ResourcePackInfo.Builder createResourcePackBuilder(String url) {
    return new LinkResourcePackInfo.BuilderImpl(url);
  }
}
