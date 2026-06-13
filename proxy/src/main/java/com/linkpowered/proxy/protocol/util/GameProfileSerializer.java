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

package com.linkpowered.proxy.protocol.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.linkpowered.api.util.GameProfile;
import com.linkpowered.api.util.GameProfile.Property;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes {@link GameProfile} instances into JSON.
 */
public final class GameProfileSerializer implements JsonSerializer<GameProfile>,
    JsonDeserializer<GameProfile> {

  public static final GameProfileSerializer INSTANCE = new GameProfileSerializer();

  private GameProfileSerializer() {

  }

  @Override
  public GameProfile deserialize(JsonElement json, Type typeOfT,
      JsonDeserializationContext context) {
    JsonObject obj = json.getAsJsonObject();
    return new GameProfile(obj.get("id").getAsString(), obj.get("name").getAsString(),
        deserializeProperties(obj.get("properties")));
  }

  @Override
  public JsonElement serialize(GameProfile src, Type typeOfSrc, JsonSerializationContext context) {
    JsonObject obj = new JsonObject();
    obj.add("id", new JsonPrimitive(src.getUndashedId()));
    obj.add("name", new JsonPrimitive(src.getName()));
    obj.add("properties", serializeProperties(src.getProperties()));
    return obj;
  }

  private static JsonElement serializeProperties(List<Property> properties) {
    JsonArray result = new JsonArray(properties.size());
    for (Property property : properties) {
      JsonObject propertyJson = new JsonObject();
      propertyJson.add("name", new JsonPrimitive(property.getName()));
      propertyJson.add("value", new JsonPrimitive(property.getValue()));
      propertyJson.add("signature", new JsonPrimitive(property.getSignature()));
      result.add(propertyJson);
    }
    return result;
  }

  private static List<Property> deserializeProperties(JsonElement json) {
    List<Property> result = new ArrayList<>();
    for (JsonElement element : json.getAsJsonArray()) {
      JsonObject propertyJson = element.getAsJsonObject();
      result.add(new Property(
          propertyJson.get("name").getAsString(),
          propertyJson.get("value").getAsString(),
          propertyJson.get("signature").getAsString()));
    }
    return result;
  }
}
