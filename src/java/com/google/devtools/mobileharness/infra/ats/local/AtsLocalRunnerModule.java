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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.OlcServerModule;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer.ServerStartingLogger;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.List;

/** Guice Module for ATS local runner. */
public final class AtsLocalRunnerModule extends AbstractModule {
  private final Provider<Path> olcServerBinary;
  private final ImmutableList<String> deviceInfraServiceFlags;

  public AtsLocalRunnerModule(
      Provider<Path> olcServerBinary, List<String> deviceInfraServiceFlags) {
    this.olcServerBinary = olcServerBinary;
    this.deviceInfraServiceFlags = ImmutableList.copyOf(deviceInfraServiceFlags);
  }

  @Override
  protected void configure() {
    install(new OlcServerModule(olcServerBinary, deviceInfraServiceFlags, "ATS local runner"));
  }

  @Provides
  @Singleton
  ListeningExecutorService provideThreadPool() {
    return ThreadPools.createStandardThreadPool("ats-local-runner-thread-pool");
  }

  @Provides
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }

  @Provides
  ServerStartingLogger provideOlcServerStartingLogger() {
    return (format, args) -> System.out.printf(format + "%n", args);
  }
}
