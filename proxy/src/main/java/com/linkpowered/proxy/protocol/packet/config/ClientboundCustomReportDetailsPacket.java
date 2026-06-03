/*
 * Copyright (C) 2024 Link Contributors
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

package com.linkpowered.proxy.protocol.packet.config;

import com.linkpowered.api.network.ProtocolVersion;
import com.linkpowered.proxy.connection.MinecraftSessionHandler;
import com.linkpowered.proxy.protocol.MinecraftPacket;
import com.linkpowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import java.util.Map;

public class ClientboundCustomReportDetailsPacket implements MinecraftPacket {

    private Map<String, String> details;

    public ClientboundCustomReportDetailsPacket() {
    }

    public ClientboundCustomReportDetailsPacket(Map<String, String> details) {
        this.details = details;
    }

    @Override
    public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
        int detailsCount = ProtocolUtils.readVarInt(buf);

        this.details = ProtocolUtils.newMap(detailsCount);
        for (int i = 0; i < detailsCount; i++) {
            details.put(ProtocolUtils.readString(buf), ProtocolUtils.readString(buf));
        }
    }

    @Override
    public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
        ProtocolUtils.writeVarInt(buf, details.size());

        details.forEach((key, detail) -> {
            ProtocolUtils.writeString(buf, key);
            ProtocolUtils.writeString(buf, detail);
        });
    }

    @Override
    public boolean handle(MinecraftSessionHandler handler) {
        return handler.handle(this);
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
