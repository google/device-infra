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

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.shared.context.InvocationContextExecutors;
import java.util.concurrent.Executors;

/** Utilities for creating thread pools. */
public class ThreadPools {

  /**
   * Creates a cached thread pool whose threads are daemon threads, have the given name prefix and
   * have a default uncaught exception handler, and which will automatically propagate invocation
   * context.
   *
   * @param threadNamePrefix if it is "foo", the thread names are like "foo-1", "foo-2", etc.
   */
  public static ListeningExecutorService createStandardThreadPool(String threadNamePrefix) {
    return InvocationContextExecutors.propagatingContext(
        MoreExecutors.listeningDecorator(
            Executors.newCachedThreadPool(
                ThreadFactoryUtil.createThreadFactory(threadNamePrefix, /* daemon= */ true))),
        ListeningExecutorService.class);
  }

  /**
   * Creates a scheduled thread pool with the given core pool size, whose threads are daemon threads
   * , have the given name prefix and have a default uncaught exception handler, and which will
   * automatically propagate invocation context.
   *
   * @param threadNamePrefix if it is "foo", the thread names are like "foo-1", "foo-2", etc.
   */
  public static ListeningScheduledExecutorService createStandardScheduledThreadPool(
      String threadNamePrefix, int corePoolSize) {
    return InvocationContextExecutors.propagatingContext(
        MoreExecutors.listeningDecorator(
            Executors.newScheduledThreadPool(
                corePoolSize,
                ThreadFactoryUtil.createThreadFactory(threadNamePrefix, /* daemon= */ true))),
        ListeningScheduledExecutorService.class);
  }

  private ThreadPools() {}
}
