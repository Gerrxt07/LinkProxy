/*
 * Copyright (C) 2021-2022 Link Contributors
 *
 * The Link API is licensed under the terms of the MIT License. For more details,
 * reference the LICENSE file in the api top-level directory.
 */

package com.linkpowered.api.event.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Marks an event as an event the proxy will wait on to completely fire (including any
 * {@link com.linkpowered.api.event.EventTask}s) before continuing handling it. Annotated
 * classes are suitable candidates for using EventTasks for handling complex asynchronous
 * operations in a non-blocking matter.
 */
@Target(ElementType.TYPE)
@Documented
public @interface AwaitingEvent {

}
