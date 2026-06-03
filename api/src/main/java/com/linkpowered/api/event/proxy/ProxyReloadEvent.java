/*
 * Copyright (C) 2018-2021 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.event.proxy;

/**
 * This event is fired when the proxy is reloaded by the user using {@code /link reload}.
 */
public class ProxyReloadEvent {

  @Override
  public String toString() {
    return "ProxyReloadEvent";
  }
}
