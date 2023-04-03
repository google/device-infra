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
import com.google.devtools.atsconsole.Annotations.ConsoleLineReader;
import com.google.devtools.atsconsole.Annotations.ConsoleOutput;
import com.google.devtools.atsconsole.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.atsconsole.Annotations.MainArgs;
import com.google.devtools.atsconsole.controller.olcserver.OlcServerModule;
import com.google.devtools.deviceinfra.shared.util.concurrent.ThreadFactoryUtil;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.jline.reader.LineReader;

/** Guice Module for ATS console. */
final class AtsConsoleModule extends AbstractModule {

  private final ImmutableList<String> deviceInfraServiceFlags;
  private final ImmutableList<String> mainArgs;
  private final LineReader consoleLineReader;
  private final PrintStream consoleOutput;
  private final Provider<Path> olcServerBinary;

  AtsConsoleModule(
      List<String> deviceInfraServiceFlags,
      List<String> mainArgs,
      LineReader consoleLineReader,
      PrintStream consoleOutput,
      Provider<Path> olcServerBinary) {
    this.deviceInfraServiceFlags = ImmutableList.copyOf(deviceInfraServiceFlags);
    this.mainArgs = ImmutableList.copyOf(mainArgs);
    this.consoleLineReader = consoleLineReader;
    this.consoleOutput = consoleOutput;
    this.olcServerBinary = olcServerBinary;
  }

  @Override
  protected void configure() {
    install(new OlcServerModule(olcServerBinary));
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
  @MainArgs
  ImmutableList<String> provideMainArgs() {
    return mainArgs;
  }

  @Provides
  @ConsoleLineReader
  LineReader provideConsoleLineReader() {
    return consoleLineReader;
  }

  @SuppressWarnings("CloseableProvides")
  @Provides
  @ConsoleOutput
  PrintStream provideConsoleOutput() {
    return consoleOutput;
  }

  @Provides
  @Singleton
  ListeningScheduledExecutorService provideThreadPool() {
    return MoreExecutors.listeningDecorator(
        Executors.newScheduledThreadPool(
            /* corePoolSize= */ 5,
            ThreadFactoryUtil.createThreadFactory("ats-console-thread-pool", /* daemon= */ true)));
  }

  @Provides
  Sleeper provideSleeper() {
    return Sleeper.defaultSleeper();
  }
}
