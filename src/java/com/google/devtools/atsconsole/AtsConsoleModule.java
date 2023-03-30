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

package com.google.devtools.atsconsole;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.atsconsole.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.deviceinfra.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.util.List;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

/** Guice Module for ATS console. */
final class AtsConsoleModule extends AbstractModule {

  private final ImmutableList<String> deviceInfraServiceFlags;

  AtsConsoleModule(List<String> deviceInfraServiceFlags) {
    this.deviceInfraServiceFlags = ImmutableList.copyOf(deviceInfraServiceFlags);
  }

  @Provides
  @Singleton
  ConsoleInfo provideConsoleInfo() {
    return ConsoleInfo.getInstance();
  }

  @Provides
  @DeviceInfraServiceFlags
  ImmutableList<String> provideDeviceInfraServiceFlags() {
    return deviceInfraServiceFlags;
  }

  @Provides
  @Singleton
  ListeningScheduledExecutorService provideThreadPool() {
    return MoreExecutors.listeningDecorator(
        Executors.newScheduledThreadPool(
            /* corePoolSize= */ 5,
            ThreadFactoryUtil.createThreadFactory("ats-console-main-thread")));
  }

  @Provides
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }
}
