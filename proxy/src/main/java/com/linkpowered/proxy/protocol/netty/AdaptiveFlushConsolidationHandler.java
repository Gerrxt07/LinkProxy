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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Defers flushes to the end of the current event loop task and flushes early under write bursts.
 */
public class AdaptiveFlushConsolidationHandler extends ChannelDuplexHandler {

  private static final int DEFAULT_MAX_PENDING_WRITES = 256;

  private final int maxPendingWrites;
  private int pendingWrites;
  private boolean flushScheduled;

  public AdaptiveFlushConsolidationHandler() {
    this(DEFAULT_MAX_PENDING_WRITES);
  }

  AdaptiveFlushConsolidationHandler(int maxPendingWrites) {
    this.maxPendingWrites = maxPendingWrites;
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
      throws Exception {
    pendingWrites++;
    ctx.write(msg, promise);
    if (pendingWrites >= maxPendingWrites) {
      flushNow(ctx);
    }
  }

  @Override
  public void flush(ChannelHandlerContext ctx) {
    if (pendingWrites == 0) {
      return;
    }
    if (flushScheduled) {
      return;
    }
    flushScheduled = true;
    ctx.channel().eventLoop().execute(() -> flushNow(ctx));
  }

  @Override
  public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
    flushNow(ctx);
    super.close(ctx, promise);
  }

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    flushNow(ctx);
    super.handlerRemoved(ctx);
  }

  private void flushNow(ChannelHandlerContext ctx) {
    if (pendingWrites == 0 && !flushScheduled) {
      return;
    }
    pendingWrites = 0;
    flushScheduled = false;
    ctx.flush();
  }
}
