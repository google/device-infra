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

package com.google.wireless.qa.mobileharness.shared.api;

import com.google.common.base.Suppliers;
import com.google.common.base.Ticker;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Module for binding common libraries for Driver/Decorator. */
public class CommonLibraryModule extends AbstractModule {

  private static final Supplier<ListeningExecutorService> THREAD_POOL =
      Suppliers.memoize(CommonLibraryModule::createThreadPool);

  @Provides
  Clock provideClock() {
    return Clock.systemUTC();
  }

  @Provides
  DeviceCache provideDeviceCache() {
    return DeviceCache.getInstance();
  }

  @Provides
  Executor provideExecutor() {
    return THREAD_POOL.get();
  }

  @Provides
  ExecutorService provideExecutorService() {
    return THREAD_POOL.get();
  }

  @Provides
  ListeningExecutorService provideListeningExecutorService() {
    return THREAD_POOL.get();
  }

  @Provides
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }

  @Provides
  Ticker provideTicker() {
    return Ticker.systemTicker();
  }

  private static ListeningExecutorService createThreadPool() {
    return MoreExecutors.listeningDecorator(
        Executors.newCachedThreadPool(
            ThreadFactoryUtil.createThreadFactory("mh-driver-thread-pool", /* daemon= */ true)));
  }
}
