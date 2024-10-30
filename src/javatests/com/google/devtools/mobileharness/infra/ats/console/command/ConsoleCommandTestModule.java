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

package com.google.devtools.mobileharness.infra.ats.console.command;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.OlcServerModule;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.NoOpServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer.ServerEnvironment;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ParseCommandOnly;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.RunCommandParsingResultFuture;
import com.google.devtools.mobileharness.infra.ats.console.ConsoleInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportModule;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.function.Consumer;

/** Console command module for testing. */
public class ConsoleCommandTestModule extends AbstractModule {

  private final ConsoleInfo consoleInfo;
  private final ServerEnvironment serverEnvironment;

  ConsoleCommandTestModule(ConsoleInfo consoleInfo, ServerEnvironment serverEnvironment) {
    this.consoleInfo = consoleInfo;
    this.serverEnvironment = serverEnvironment;
  }

  @Override
  protected void configure() {
    install(
        new OlcServerModule(
            FlagsString.of("", ImmutableList.of()), "ATS console", "fake_client_id"));
    install(new CompatibilityReportModule());
  }

  @Provides
  @Singleton
  ServerEnvironmentPreparer provideServerEnvironmentPreparer() {
    return new NoOpServerEnvironmentPreparer(serverEnvironment);
  }

  @Provides
  ConsoleInfo provideConsoleInfo() {
    return consoleInfo;
  }

  @Provides
  ListeningExecutorService provideThreadPool() {
    return ThreadPools.createStandardThreadPool("main-thread");
  }

  @Provides
  ListeningScheduledExecutorService provideScheduledThreadPool() {
    return ThreadPools.createStandardScheduledThreadPool("main-scheduled-thread", 10);
  }

  @Provides
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }

  @Provides
  @RunCommandParsingResultFuture
  Consumer<ListenableFuture<SessionRequestInfo.Builder>> provideResultFuture() {
    return resultFuture -> {};
  }

  @Provides
  @ParseCommandOnly
  Boolean provideParseCommandOnly() {
    return false;
  }
}
