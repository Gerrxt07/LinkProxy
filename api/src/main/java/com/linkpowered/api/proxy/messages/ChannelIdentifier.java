/*
 * Copyright (C) 2018-2021 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.proxy.messages;

/**
 * Represents a channel identifier for use with plugin messaging.
 */
public interface ChannelIdentifier {

  /**
   * Returns the textual representation of this identifier.
   *
   * @return the textual representation of the identifier
   */
  String getId();
}
