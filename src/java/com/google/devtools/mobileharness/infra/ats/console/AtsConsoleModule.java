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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.OlcServerConnectorModule;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleOutput;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ParseCommandOnly;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.RunCommandParsingResultFuture;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.XtsServerEnvironmentPreparer;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportModule;
import com.google.devtools.mobileharness.infra.client.longrunningservice.OlcServerRunner;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import org.jline.reader.LineReader;

/** Guice Module for ATS console. */
@SuppressWarnings("CloseableProvides")
public class AtsConsoleModule extends AbstractModule {

  private final String consoleId;
  private final FlagsString deviceInfraServiceFlags;
  @Nullable private final LineReader consoleLineReader;
  private final PrintStream consoleOutputOut;
  private final PrintStream consoleOutputErr;
  private final Consumer<ListenableFuture<SessionRequestInfo.Builder>> resultFuture;
  private final boolean parseCommandOnly;

  public AtsConsoleModule(
      String consoleId,
      FlagsString deviceInfraServiceFlags,
      @Nullable LineReader consoleLineReader,
      PrintStream consoleOutputOut,
      PrintStream consoleOutputErr,
      Consumer<ListenableFuture<SessionRequestInfo.Builder>> resultFuture,
      boolean parseCommandOnly) {
    this.consoleId = consoleId;
    this.deviceInfraServiceFlags = deviceInfraServiceFlags;
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

    install(new OlcServerConnectorModule(deviceInfraServiceFlags, "ATS console", consoleId));
    install(new CompatibilityReportModule());

    OptionalBinder.newOptionalBinder(binder(), OlcServerRunner.class);
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
