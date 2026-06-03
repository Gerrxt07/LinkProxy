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

package com.linkpowered.proxy.plugin.loader;

import com.linkpowered.api.plugin.PluginContainer;
import com.linkpowered.api.plugin.PluginDescription;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Implements {@link PluginContainer}.
 */
public class LinkPluginContainer implements PluginContainer {

  private final PluginDescription description;
  private Object instance;
  private volatile ExecutorService service;

  public LinkPluginContainer(PluginDescription description) {
    this.description = description;
  }

  @Override
  public PluginDescription getDescription() {
    return this.description;
  }

  @Override
  public Optional<?> getInstance() {
    return Optional.ofNullable(instance);
  }

  public void setInstance(Object instance) {
    this.instance = instance;
  }

  @Override
  public ExecutorService getExecutorService() {
    if (this.service == null) {
      synchronized (this) {
        if (this.service == null) {
          String name = this.description.getName().orElse(this.description.getId());
          ThreadFactory factory = Thread.ofVirtual()
              .name(name + " - Virtual Task Executor #", 0)
              .factory();
          this.service = Executors.unconfigurableExecutorService(
              Executors.newThreadPerTaskExecutor(factory)
          );
        }
      }
    }

    return this.service;
  }

  public boolean hasExecutorService() {
    return this.service != null;
  }
}
