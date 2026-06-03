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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

/**
 * Holds a packet payload that can be forwarded without packet decode or re-encode.
 */
public final class RawMinecraftPacket extends DefaultByteBufHolder {

  public RawMinecraftPacket(ByteBuf data) {
    super(data);
  }

  @Override
  public RawMinecraftPacket replace(ByteBuf content) {
    return new RawMinecraftPacket(content);
  }

  @Override
  public RawMinecraftPacket retain() {
    super.retain();
    return this;
  }

  @Override
  public RawMinecraftPacket retain(int increment) {
    super.retain(increment);
    return this;
  }
}
