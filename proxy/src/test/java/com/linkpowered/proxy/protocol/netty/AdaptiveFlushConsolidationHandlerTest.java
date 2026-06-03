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

package com.linkpowered.proxy.protocol.netty;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdaptiveFlushConsolidationHandlerTest {

  private AdaptiveFlushConsolidationHandler handler;
  private ChannelHandlerContext context;
  private Channel channel;
  private EventLoop eventLoop;

  @BeforeEach
  void setUp() {
    handler = new AdaptiveFlushConsolidationHandler();
    context = mock(ChannelHandlerContext.class);
    channel = mock(Channel.class);
    eventLoop = mock(EventLoop.class);
    when(context.channel()).thenReturn(channel);
    when(channel.eventLoop()).thenReturn(eventLoop);
  }

  @Test
  void consolidatesFlushesUntilEventLoopTail() throws Exception {
    write("first");
    write("second");

    handler.flush(context);
    handler.flush(context);
    verify(context, never()).flush();

    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(eventLoop).execute(task.capture());
    task.getValue().run();

    verify(context).flush();
  }

  @Test
  void flushesImmediatelyWhenWriteLimitHit() throws Exception {
    handler = new AdaptiveFlushConsolidationHandler(2);

    write("first");
    verify(context, never()).flush();
    write("second");

    verify(context).flush();
  }

  private void write(String msg) throws Exception {
    handler.write(context, msg, mock(ChannelPromise.class));
  }
}
