/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.mobileharness.shared.util.concurrent;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ThreadFactory;

/** Utility for creating {@link ThreadFactory}. */
public class ThreadFactoryUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Returns a {@link ThreadFactory} which {@link Thread#setDefaultUncaughtExceptionHandler} with a
   * logging handler and {@link Thread#setDaemon} with {@code false}.
   *
   * @param threadNamePrefix e.g., "foo-thread" will generate thread names like "foo-thread-1",
   *     "foo-thread-2", etc.
   */
  public static ThreadFactory createThreadFactory(String threadNamePrefix) {
    return createThreadFactory(threadNamePrefix, /* daemon= */ false);
  }

  /** See {@link #createThreadFactory(String)}. */
  public static ThreadFactory createThreadFactory(String threadNamePrefix, boolean daemon) {
    return new ThreadFactoryBuilder()
        .setNameFormat(threadNamePrefix + "-%d")
        .setDaemon(daemon)
        .setUncaughtExceptionHandler(
            (thread, throwable) ->
                logger.atSevere().withCause(throwable).log(
                    "Uncaught exception from thread [%s]", thread.getName()))
        .build();
  }

  private ThreadFactoryUtil() {}
}
