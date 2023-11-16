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
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleOutput;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.MainArgs;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.SystemProperties;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.OlcServerModule;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportModule;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.jline.reader.LineReader;

/** Guice Module for ATS console. */
@SuppressWarnings("CloseableProvides")
public class AtsConsoleModule extends AbstractModule {

  private final ImmutableList<String> deviceInfraServiceFlags;
  private final ImmutableList<String> mainArgs;
  private final ImmutableMap<String, String> systemProperties;
  private final LineReader consoleLineReader;
  private final PrintStream consoleOutputOut;
  private final PrintStream consoleOutputErr;
  private final Provider<Path> olcServerBinary;

  public AtsConsoleModule(
      List<String> deviceInfraServiceFlags,
      List<String> mainArgs,
      Map<String, String> systemProperties,
      LineReader consoleLineReader,
      PrintStream consoleOutputOut,
      PrintStream consoleOutputErr,
      Provider<Path> olcServerBinary) {
    this.deviceInfraServiceFlags = ImmutableList.copyOf(deviceInfraServiceFlags);
    this.mainArgs = ImmutableList.copyOf(mainArgs);
    this.systemProperties = ImmutableMap.copyOf(systemProperties);
    this.consoleLineReader = consoleLineReader;
    this.consoleOutputOut = consoleOutputOut;
    this.consoleOutputErr = consoleOutputErr;
    this.olcServerBinary = olcServerBinary;
  }

  @Override
  protected void configure() {
    install(new OlcServerModule(olcServerBinary));
    install(new CompatibilityReportModule());
  }

  @Provides
  @DeviceInfraServiceFlags
  ImmutableList<String> provideDeviceInfraServiceFlags() {
    return deviceInfraServiceFlags;
  }

  @Provides
  @MainArgs
  ImmutableList<String> provideMainArgs() {
    return mainArgs;
  }

  @Provides
  @SystemProperties
  ImmutableMap<String, String> provideSystemProperties() {
    return systemProperties;
  }

  @Provides
  @ConsoleLineReader
  LineReader provideConsoleLineReader() {
    return consoleLineReader;
  }

  @Provides
  @Singleton
  @ConsoleOutput(ConsoleOutput.Type.OUT_STREAM)
  PrintStream provideConsoleOutputOutStream() {
    return consoleOutputOut;
  }

  @Provides
  @Singleton
  @ConsoleOutput(ConsoleOutput.Type.ERR_STREAM)
  PrintStream provideConsoleOutputErrStream() {
    return consoleOutputErr;
  }

  @Provides
  @Singleton
  @ConsoleOutput(ConsoleOutput.Type.OUT_WRITER)
  PrintWriter provideConsoleOutputOutWriter(
      @ConsoleOutput(ConsoleOutput.Type.OUT_STREAM) PrintStream outStream) {
    return new PrintWriter(outStream, /* autoFlush= */ true, UTF_8);
  }

  @Provides
  @Singleton
  @ConsoleOutput(ConsoleOutput.Type.ERR_WRITER)
  PrintWriter provideConsoleOutputErrWriter(
      @ConsoleOutput(ConsoleOutput.Type.ERR_STREAM) PrintStream errStream) {
    return new PrintWriter(errStream, /* autoFlush= */ true, UTF_8);
  }

  @Provides
  @Singleton
  ListeningExecutorService provideThreadPool() {
    return MoreExecutors.listeningDecorator(
        Executors.newCachedThreadPool(
            ThreadFactoryUtil.createThreadFactory("ats-console-thread-pool", /* daemon= */ true)));
  }

  @Provides
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }
}
