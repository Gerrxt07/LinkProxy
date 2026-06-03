/*
 * Copyright (C) 2018-2021 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.permission;

/**
 * Provides {@link PermissionFunction}s for {@link PermissionSubject}s.
 */
@FunctionalInterface
public interface PermissionProvider {

  /**
   * Creates a {@link PermissionFunction} for the subject.
   *
   * @param subject the subject
   * @return the function
   */
  PermissionFunction createFunction(PermissionSubject subject);
}
