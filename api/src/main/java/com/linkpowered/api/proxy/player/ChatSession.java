/*
 * Copyright (C) 2018-2022 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.proxy.player;

import com.linkpowered.api.proxy.crypto.KeyIdentifiable;
import java.util.UUID;

/**
 * Represents a chat session held by a player.
 */
public interface ChatSession extends KeyIdentifiable {
  /**
   * Returns the {@link UUID} of the session.
   *
   * @return the session UUID
   */
  UUID getSessionId();
}
