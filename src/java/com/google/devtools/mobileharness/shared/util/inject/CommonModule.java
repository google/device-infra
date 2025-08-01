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

package com.google.devtools.mobileharness.shared.util.inject;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.time.Clock;
import java.time.InstantSource;
import java.util.concurrent.ExecutorService;
import javax.inject.Singleton;

/**
 * Common module for binding the following classes:
 *
 * <ul>
 *   <li>{@link Clock}
 *   <li>{@link ExecutorService}
 *   <li>{@link InstantSource}
 *   <li>{@link ListeningExecutorService}
 *   <li>{@link ListeningScheduledExecutorService}
 *   <li>{@link Sleeper}
 * </ul>
 */
public class CommonModule extends AbstractModule {

  @Provides
  @Singleton
  Clock provideClock() {
    return Clock.systemUTC();
  }

  @Provides
  @Singleton
  ExecutorService provideExecutorService(ListeningExecutorService listeningExecutorService) {
    return listeningExecutorService;
  }

  @Provides
  @Singleton
  InstantSource provideInstantSource() {
    return InstantSource.system();
  }

  @Provides
  @Singleton
  ListeningExecutorService provideThreadPool() {
    return ThreadPools.createStandardThreadPool("main-thread-pool");
  }

  @Provides
  @Singleton
  ListeningScheduledExecutorService provideScheduledThreadPool() {
    return ThreadPools.createStandardScheduledThreadPool(
        "main-scheduled-thread-pool", /* corePoolSize= */ 10);
  }

  @Provides
  @Singleton
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }
}
