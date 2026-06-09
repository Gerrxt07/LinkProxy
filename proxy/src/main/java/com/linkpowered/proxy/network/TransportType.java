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

package com.linkpowered.proxy.network;

import com.linkpowered.proxy.util.concurrent.LinkNettyThreadFactory;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * Enumerates the supported transports for Link.
 */
public enum TransportType {
  NIO("NIO", NioServerSocketChannel::new,
      NioSocketChannel::new,
      NioDatagramChannel::new,
      NioIoHandler::newFactory),
  EPOLL("epoll", EpollServerSocketChannel::new,
      EpollSocketChannel::new,
      EpollDatagramChannel::new,
      EpollIoHandler::newFactory),
  KQUEUE("kqueue", KQueueServerSocketChannel::new,
      KQueueSocketChannel::new,
      KQueueDatagramChannel::new,
      KQueueIoHandler::newFactory),
  IO_URING("io_uring", IoUringServerSocketChannel::new,
      IoUringSocketChannel::new,
      IoUringDatagramChannel::new,
      IoUringIoHandler::newFactory);

  final String name;
  final ChannelFactory<? extends ServerSocketChannel> serverSocketChannelFactory;
  final ChannelFactory<? extends SocketChannel> socketChannelFactory;
  final ChannelFactory<? extends DatagramChannel> datagramChannelFactory;
  final Supplier<IoHandlerFactory> ioHandlerFactorySupplier;

  TransportType(final String name,
      final ChannelFactory<? extends ServerSocketChannel> serverSocketChannelFactory,
      final ChannelFactory<? extends SocketChannel> socketChannelFactory,
      final ChannelFactory<? extends DatagramChannel> datagramChannelFactory,
      final Supplier<IoHandlerFactory> ioHandlerFactorySupplier) {
    this.name = name;
    this.serverSocketChannelFactory = serverSocketChannelFactory;
    this.socketChannelFactory = socketChannelFactory;
    this.datagramChannelFactory = datagramChannelFactory;
    this.ioHandlerFactorySupplier = ioHandlerFactorySupplier;
  }

  @Override
  public String toString() {
    return this.name;
  }

  /**
   * Creates a new event loop group for the given type.
   *
   * @param type the type of event loop group to create
   * @return the event loop group
   */
  public EventLoopGroup createEventLoopGroup(final Type type) {
    return new MultiThreadIoEventLoopGroup(
        0, createThreadFactory(this.name, type), this.ioHandlerFactorySupplier.get());
  }

  private static ThreadFactory createThreadFactory(final String name, final Type type) {
    return new LinkNettyThreadFactory("Netty " + name + ' ' + type.toString() + " #%d");
  }

  /**
   * Determines the "best" transport to initialize.
   *
   * @return the transport to use
   */
  public static TransportType bestType(String configuredTransport) {
    if (Boolean.getBoolean("link.disable-native-transport")) {
      return NIO;
    }

    String requestedTransport = System.getProperty("link.transport");
    if (requestedTransport == null || requestedTransport.isBlank()) {
      requestedTransport = configuredTransport;
    }
    if (requestedTransport != null && !requestedTransport.isBlank()) {
      if (!"auto".equalsIgnoreCase(requestedTransport)) {
        for (TransportType type : values()) {
          if (type.name.equalsIgnoreCase(requestedTransport)
              || type.name().equalsIgnoreCase(requestedTransport)) {
            if (type.isAvailable()) {
              return type;
            }
            break;
          }
        }
      }
    }

    if (Epoll.isAvailable()) {
      return EPOLL;
    }

    if (IoUring.isAvailable()) {
      return IO_URING;
    }

    if (KQueue.isAvailable()) {
      return KQUEUE;
    }

    return NIO;
  }

  /**
   * Returns whether this transport can run on the current host.
   *
   * @return {@code true} if available
   */
  public boolean isAvailable() {
    return switch (this) {
      case NIO -> true;
      case EPOLL -> Epoll.isAvailable();
      case KQUEUE -> KQueue.isAvailable();
      case IO_URING -> IoUring.isAvailable();
    };
  }

  /**
   * Returns the concrete Netty server channel class used by this transport.
   *
   * @return server channel class name
   */
  public String serverChannelClassName() {
    return switch (this) {
      case NIO -> NioServerSocketChannel.class.getSimpleName();
      case EPOLL -> EpollServerSocketChannel.class.getSimpleName();
      case KQUEUE -> KQueueServerSocketChannel.class.getSimpleName();
      case IO_URING -> IoUringServerSocketChannel.class.getSimpleName();
    };
  }

  /**
   * Returns the concrete Netty socket channel class used by this transport.
   *
   * @return socket channel class name
   */
  public String socketChannelClassName() {
    return switch (this) {
      case NIO -> NioSocketChannel.class.getSimpleName();
      case EPOLL -> EpollSocketChannel.class.getSimpleName();
      case KQUEUE -> KQueueSocketChannel.class.getSimpleName();
      case IO_URING -> IoUringSocketChannel.class.getSimpleName();
    };
  }

  /**
   * Returns why this transport is unavailable on the current host.
   *
   * @return unavailability reason
   */
  public String unavailabilityReason() {
    Throwable cause = switch (this) {
      case NIO -> null;
      case EPOLL -> Epoll.unavailabilityCause();
      case KQUEUE -> KQueue.unavailabilityCause();
      case IO_URING -> IoUring.unavailabilityCause();
    };
    return cause == null ? "available" : cause.getMessage();
  }

  /**
   * Event loop group types.
   */
  public enum Type {
    /**
     * Accepts connections and distributes them to workers.
     */
    BOSS("Boss"),
    /**
     * Thread that handles connections.
     */
    WORKER("Worker");

    private final String name;

    Type(final String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }
}
