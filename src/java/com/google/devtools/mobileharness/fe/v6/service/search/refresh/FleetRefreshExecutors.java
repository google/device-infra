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

package com.google.devtools.mobileharness.fe.v6.service.search.refresh;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for the dedicated {@link FleetRefreshExecutor} background pool.
 *
 * <p>Backed by a fixed-parallelism {@link ForkJoinPool} with daemon worker threads so {@code
 * FleetIndexBuilder.build}'s internal {@code parallelStream()} stages execute inside this pool
 * rather than spilling onto {@link ForkJoinPool#commonPool()} or competing with live request
 * handlers.
 */
public final class FleetRefreshExecutors {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int DEFAULT_PARALLELISM = 2;

  /** Creates the default 2-worker daemon {@link ForkJoinPool} for background fleet refreshes. */
  public static ListeningExecutorService create() {
    return create(DEFAULT_PARALLELISM);
  }

  static ListeningExecutorService create(int parallelism) {
    AtomicInteger threadCounter = new AtomicInteger();
    ForkJoinWorkerThreadFactory threadFactory =
        pool -> {
          ForkJoinWorkerThread worker =
              ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
          worker.setName("fleet-search-refresh-worker-" + threadCounter.getAndIncrement());
          worker.setDaemon(true);
          return worker;
        };
    return MoreExecutors.listeningDecorator(
        new ForkJoinPool(
            parallelism,
            threadFactory,
            (thread, throwable) ->
                logger.atSevere().withCause(throwable).log(
                    "Uncaught exception from thread [%s]", thread.getName()),
            /* asyncMode= */ false));
  }

  private FleetRefreshExecutors() {}
}
