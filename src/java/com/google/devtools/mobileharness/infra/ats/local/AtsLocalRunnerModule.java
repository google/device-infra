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

package com.google.devtools.mobileharness.infra.ats.local;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.OlcServerModule;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.NoOpServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.ServerEnvironment;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.nio.file.Path;

/** Guice Module for ATS local runner. */
public final class AtsLocalRunnerModule extends AbstractModule {
  private final Path olcServerBinary;
  private final FlagsString deviceInfraServiceFlags;
  private final String runnerId;

  public AtsLocalRunnerModule(
      Path olcServerBinary, FlagsString deviceInfraServiceFlags, String runnerId) {
    this.olcServerBinary = olcServerBinary;
    this.deviceInfraServiceFlags = deviceInfraServiceFlags;
    this.runnerId = runnerId;
  }

  @Override
  protected void configure() {
    install(new OlcServerModule(deviceInfraServiceFlags, "ATS local runner", runnerId));
  }

  @Provides
  @Singleton
  ServerEnvironmentPreparer provideServerEnvironmentPreparer(SystemUtil systemUtil) {
    return new NoOpServerEnvironmentPreparer(
        ServerEnvironment.of(
            olcServerBinary,
            Path.of(systemUtil.getJavaBin()),
            Path.of(System.getProperty("user.dir"))));
  }

  @Provides
  @Singleton
  ListeningExecutorService provideThreadPool() {
    return ThreadPools.createStandardThreadPool("ats-local-runner-thread-pool");
  }

  @Provides
  @Singleton
  ListeningScheduledExecutorService provideScheduledThreadPool() {
    return ThreadPools.createStandardScheduledThreadPool(
        "ats-local-runner-scheduled-thread-pool", /* corePoolSize= */ 10);
  }

  @Provides
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }
}
