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

package com.linkpowered.proxy.plugin.loader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linkpowered.api.plugin.PluginDescription;
import java.util.Optional;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class LinkPluginContainerTest {

  @Test
  void pluginExecutorUsesVirtualThreads() throws Exception {
    LinkPluginContainer container = new LinkPluginContainer(new TestPluginDescription());
    Future<Boolean> virtual = container.getExecutorService().submit(() -> Thread.currentThread().isVirtual());

    assertTrue(virtual.get());
    container.getExecutorService().shutdown();
  }

  private static final class TestPluginDescription implements PluginDescription {

    @Override
    public String getId() {
      return "test";
    }

    @Override
    public Optional<String> getName() {
      return Optional.of("Test");
    }
  }
}
