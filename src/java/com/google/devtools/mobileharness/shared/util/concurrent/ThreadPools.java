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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.shared.context.InvocationContextExecutors;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
   * Creates a thread pool that scales from 0 to a given maximum number of threads.
   *
   * <p>When new tasks are submitted, new threads are created on demand up to {@code
   * maximumPoolSize}. If all threads are busy, subsequent tasks are queued. Idle threads are
   * terminated after 60 seconds, allowing the pool to shrink to 0 threads when not in use.
   *
   * <p>The threads are daemon threads, have the given name prefix, have a default uncaught
   * exception handler, and will automatically propagate invocation context.
   *
   * @param threadNamePrefix if it is "foo", the thread names are like "foo-1", "foo-2", etc.
   * @param maximumPoolSize the maximum number of threads in the pool
   */
  public static ListeningExecutorService createStandardThreadPoolWithMaxSize(
      String threadNamePrefix, int maximumPoolSize) {
    return createStandardThreadPoolWithMaxSize(threadNamePrefix, maximumPoolSize, 60L, SECONDS);
  }

  /**
   * Creates a thread pool that scales from 0 to a given maximum number of threads.
   *
   * <p>When new tasks are submitted, new threads are created on demand up to {@code
   * maximumPoolSize}. If all threads are busy, subsequent tasks are queued. Idle threads are
   * terminated after a timeout, allowing the pool to shrink to 0 threads when not in use.
   *
   * <p>The threads are daemon threads, have the given name prefix, have a default uncaught
   * exception handler, and will automatically propagate invocation context.
   *
   * @param threadNamePrefix if it is "foo", the thread names are like "foo-1", "foo-2", etc.
   * @param maximumPoolSize the maximum number of threads in the pool
   * @param keepAliveTime the maximum time that excess idle threads will wait for new tasks before
   *     terminating
   * @param unit the time unit for the {@code keepAliveTime} argument
   */
  @VisibleForTesting
  static ListeningExecutorService createStandardThreadPoolWithMaxSize(
      String threadNamePrefix, int maximumPoolSize, long keepAliveTime, TimeUnit unit) {
    // Creates a thread pool that can scale from 0 to maximumPoolSize. When all threads are busy,
    // new tasks are queued.
    //
    // This is achieved by a common configuration pattern:
    // 1. Set corePoolSize = maximumPoolSize: This makes the pool create new threads up to the
    //    maximum before it starts queuing tasks.
    // 2. Use allowCoreThreadTimeOut(true): This allows ALL threads to be terminated when idle,
    //    so the pool can shrink back to 0 threads, fulfilling the "start with 0 threads"
    // requirement.
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            /* corePoolSize= */ maximumPoolSize,
            /* maximumPoolSize= */ maximumPoolSize,
            keepAliveTime,
            unit,
            new LinkedBlockingQueue<>(), // Use a queue to hold tasks when all threads are busy
            ThreadFactoryUtil.createThreadFactory(threadNamePrefix, /* daemon= */ true));
    executor.allowCoreThreadTimeOut(true);
    return InvocationContextExecutors.propagatingContext(
        MoreExecutors.listeningDecorator(executor), ListeningExecutorService.class);
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
