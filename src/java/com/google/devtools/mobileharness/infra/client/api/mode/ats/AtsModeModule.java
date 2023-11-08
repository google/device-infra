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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.controller.scheduler.AbstractScheduler;
import com.google.devtools.mobileharness.infra.controller.scheduler.simple.SimpleScheduler;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

/** Module for {@code AtsMode}. */
public class AtsModeModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(DeviceQuerier.class).to(DeviceQuerierImpl.class);
    bind(AbstractScheduler.class).to(SimpleScheduler.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  ListeningExecutorService provideListeningExecutorService() {
    return MoreExecutors.listeningDecorator(
        Executors.newCachedThreadPool(
            ThreadFactoryUtil.createThreadFactory("ats-mode-thread-pool", /* daemon= */ true)));
  }

  @Provides
  ExecutorService provideExecutorService(ListeningExecutorService listeningExecutorService) {
    return listeningExecutorService;
  }

  @Provides
  @Singleton
  ListeningScheduledExecutorService provideListeningScheduledExecutorService() {
    return MoreExecutors.listeningDecorator(
        Executors.newScheduledThreadPool(
            /* corePoolSize= */ 5,
            ThreadFactoryUtil.createThreadFactory(
                "ats-mode-scheduled-thread-pool", /* daemon= */ true)));
  }

  @Provides
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }
}
