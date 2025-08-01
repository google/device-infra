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

package com.google.devtools.mobileharness.infra.ats.console;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.OlcServerModule;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleOutput;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.MainArgs;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ParseCommandOnly;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.RunCommandParsingResultFuture;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.SystemProperties;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.XtsServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import org.jline.reader.LineReader;

/** Guice Module for ATS console. */
@SuppressWarnings("CloseableProvides")
public class AtsConsoleModule extends AbstractModule {

  private final String consoleId;
  private final FlagsString deviceInfraServiceFlags;
  private final ImmutableList<String> mainArgs;
  private final ImmutableMap<String, String> systemProperties;
  @Nullable private final LineReader consoleLineReader;
  private final PrintStream consoleOutputOut;
  private final PrintStream consoleOutputErr;
  private final Consumer<ListenableFuture<SessionRequestInfo.Builder>> resultFuture;
  private final boolean parseCommandOnly;

  public AtsConsoleModule(
      String consoleId,
      FlagsString deviceInfraServiceFlags,
      List<String> mainArgs,
      Map<String, String> systemProperties,
      @Nullable LineReader consoleLineReader,
      PrintStream consoleOutputOut,
      PrintStream consoleOutputErr,
      Consumer<ListenableFuture<SessionRequestInfo.Builder>> resultFuture,
      boolean parseCommandOnly) {
    this.consoleId = consoleId;
    this.deviceInfraServiceFlags = deviceInfraServiceFlags;
    this.mainArgs = ImmutableList.copyOf(mainArgs);
    this.systemProperties = ImmutableMap.copyOf(systemProperties);
    this.consoleLineReader = consoleLineReader;
    this.consoleOutputOut = consoleOutputOut;
    this.consoleOutputErr = consoleOutputErr;
    this.resultFuture = resultFuture;
    this.parseCommandOnly = parseCommandOnly;
  }

  @Override
  protected void configure() {
    bind(ServerEnvironmentPreparer.class)
        .to(XtsServerEnvironmentPreparer.class)
        .in(Singleton.class);

    install(new OlcServerModule(deviceInfraServiceFlags, "ATS console", consoleId));
    install(new CompatibilityReportModule());
  }

  @Provides
  @MainArgs
  ImmutableList<String> provideMainArgs() {
    return mainArgs;
  }

  @Provides
  @RunCommandParsingResultFuture
  Consumer<ListenableFuture<SessionRequestInfo.Builder>> provideResultFuture() {
    return resultFuture;
  }

  @Provides
  @ParseCommandOnly
  boolean provideParseCommandOnly() {
    return parseCommandOnly;
  }

  @Provides
  @SystemProperties
  ImmutableMap<String, String> provideSystemProperties() {
    return systemProperties;
  }

  @Provides
  @Nullable
  @ConsoleLineReader
  LineReader provideConsoleLineReader() {
    return consoleLineReader;
  }

  @Provides
  @Singleton
  @ConsoleOutput(ConsoleOutput.Type.OUT_WRITER)
  PrintWriter provideConsoleOutputOutWriter() {
    return new PrintWriter(consoleOutputOut, /* autoFlush= */ true, UTF_8);
  }

  @Provides
  @Singleton
  @ConsoleOutput(ConsoleOutput.Type.ERR_WRITER)
  PrintWriter provideConsoleOutputErrWriter() {
    return new PrintWriter(consoleOutputErr, /* autoFlush= */ true, UTF_8);
  }
}
