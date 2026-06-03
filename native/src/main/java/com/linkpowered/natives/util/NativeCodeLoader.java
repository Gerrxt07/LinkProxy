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

package com.linkpowered.natives.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A loader for native code.
 *
 * @param <T> the interface of the instance to load
 */
public final class NativeCodeLoader<T> implements Supplier<T> {

  private final Variant<T> selected;
  private final List<LoadFailure> loadFailures = new ArrayList<>();

  NativeCodeLoader(List<Variant<T>> variants) {
    this.selected = getVariant(variants);
  }

  @Override
  public T get() {
    return selected.constructed;
  }

  private Variant<T> getVariant(List<Variant<T>> variants) {
    for (Variant<T> variant : variants) {
      T got = variant.get();
      if (got == null) {
        if (variant.failure != null) {
          loadFailures.add(new LoadFailure(variant.name, variant.failure));
        }
        continue;
      }
      return variant;
    }
    throw new IllegalArgumentException("Can't find any suitable variants");
  }

  public String getLoadedVariant() {
    return selected.name;
  }

  /**
   * Returns the first failed native setup reason when the loader had to use the Java fallback.
   *
   * @return a fallback reason, or empty when a native variant loaded or no native variant matched
   */
  public Optional<String> getFallbackReason() {
    if (!selected.isJavaFallback() || loadFailures.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(loadFailures.get(0).toMessage());
  }

  static class Variant<T> {

    private Status status;
    private final Runnable setup;
    private final String name;
    private final Supplier<T> object;
    private T constructed;
    private @Nullable Throwable failure;

    Variant(BooleanSupplier possiblyAvailable, Runnable setup, String name, T object) {
      this(possiblyAvailable, setup, name, () -> object);
    }

    Variant(BooleanSupplier possiblyAvailable, Runnable setup, String name, Supplier<T> object) {
      this.status =
          possiblyAvailable.getAsBoolean() ? Status.POSSIBLY_AVAILABLE : Status.NOT_AVAILABLE;
      this.setup = setup;
      this.name = name;
      this.object = object;
    }

    public @Nullable T get() {
      if (status == Status.NOT_AVAILABLE || status == Status.SETUP_FAILURE) {
        return null;
      }

      // Make sure setup happens only once
      if (status == Status.POSSIBLY_AVAILABLE) {
        try {
          setup.run();
          constructed = object.get();
          status = Status.SETUP;
        } catch (Throwable e) {
          failure = e;
          status = Status.SETUP_FAILURE;
          return null;
        }
      }

      return constructed;
    }

    private boolean isJavaFallback() {
      return "Java".equals(name);
    }
  }

  private record LoadFailure(String variant, Throwable failure) {

    private String toMessage() {
      String message = failure.getMessage();
      if (message == null || message.isBlank()) {
        message = failure.getClass().getSimpleName();
      }
      return "First failed native variant: " + variant + " (" + message + ")";
    }
  }

  private enum Status {
    NOT_AVAILABLE,
    POSSIBLY_AVAILABLE,
    SETUP,
    SETUP_FAILURE
  }

  static final BooleanSupplier ALWAYS = () -> true;
}
