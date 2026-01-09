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

package com.google.devtools.mobileharness.infra.ats.console.command.parser;

import static com.google.devtools.mobileharness.shared.util.shell.ShellUtils.tokenize;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.OlcServerGrpcInProcessChannelModule;
import com.google.devtools.mobileharness.infra.ats.console.AtsConsoleModule;
import com.google.devtools.mobileharness.infra.ats.console.GuiceFactory;
import com.google.devtools.mobileharness.infra.ats.console.command.RunCommand;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.OlcServices;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.inject.CommonModule;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import com.google.devtools.mobileharness.shared.util.system.SystemPropertiesUtil;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.grpc.BindableService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import picocli.CommandLine;

/** A parser that parses a xTS Tradefed command. */
public final class CommandLineParser {

  @SuppressWarnings("NonFinalStaticField")
  private static volatile CommandLineParser instance;

  private static final Object INSTANCE_LOCK = new Object();

  /**
   * A class that stores the result of parsing a xTS Tradefed command.
   *
   * <p>The result is stored in a {@link ListenableFuture} and is set by {@link RunCommand#call()}.
   */
  private static class RunCommandParseResult
      implements Consumer<ListenableFuture<SessionRequestInfo.Builder>> {
    private volatile ListenableFuture<SessionRequestInfo.Builder> resultFuture;

    @Override
    public void accept(ListenableFuture<SessionRequestInfo.Builder> resultFuture) {
      this.resultFuture = resultFuture;
    }

    /**
     * Polls the result of parsing a xTS Tradefed command.
     *
     * <p>This method should be called after {@link #accept(ListenableFuture)} is called.
     */
    private ListenableFuture<SessionRequestInfo.Builder> poll() {
      ListenableFuture<SessionRequestInfo.Builder> resultFuture = this.resultFuture;
      this.resultFuture = null;
      return resultFuture;
    }
  }

  /** Gets the singleton instance of CommandLineParser. */
  public static CommandLineParser getInstance() {
    if (instance == null) {
      synchronized (INSTANCE_LOCK) {
        if (instance == null) {
          instance = new CommandLineParser();
        }
      }
    }
    return instance;
  }

  @GuardedBy("parseCommandLock")
  private final Injector injector;

  @GuardedBy("parseCommandLock")
  private final RunCommandParseResult runCommandParseResult;

  private final Object parseCommandLock = new Object();

  @VisibleForTesting
  CommandLineParser() {
    // Parses flags.
    runCommandParseResult = new RunCommandParseResult();

    List<Module> modules = new ArrayList<>();
    modules.add(
        new AtsConsoleModule(
            "command-line-parser-" + UUID.randomUUID(),
            FlagsString.of("", ImmutableList.of()),
            /* consoleLineReader= */ null,
            System.out,
            System.err,
            runCommandParseResult,
            /* parseCommandOnly= */ true));
    modules.add(
        new CommonModule(
            ImmutableList.of(), System.getenv(), SystemPropertiesUtil.getSystemProperties()));
    if (Flags.instance().atsConsoleOlcServerEmbeddedMode.getNonNull()) {
      modules.add(
          new AbstractModule() {
            @Provides
            @OlcServices
            ImmutableList<BindableService> provideOlcServices() {
              return ImmutableList.of();
            }
          });
      modules.add(new OlcServerGrpcInProcessChannelModule());
    }
    injector = Guice.createInjector(modules);
  }

  /**
   * Parses a xTS Tradefed command.
   *
   * <p>This method takes in a tradefed command and parse it into SessionRequestInfo.Builder.
   * Example tradefed command: "cts -m Gesture --shard-count 1";
   *
   * @param tfCommand the string of xTS tradefed command to parse
   * @return the SessionRequestInfo.Builder that has parsed information from the command.
   * @throws MobileHarnessException if the command cannot be parsed due to syntax or validation
   *     error.
   */
  public SessionRequestInfo.Builder parseCommandLine(String tfCommand)
      throws MobileHarnessException {
    synchronized (parseCommandLock) {
      ImmutableList<String> tokens;
      try {
        tokens = tokenize(tfCommand);
      } catch (TokenizationException e) {
        throw new MobileHarnessException(
            InfraErrorId.ATS_SERVER_INVALID_REQUEST_ERROR,
            "Failed to tokenize the xTS command line: " + tfCommand,
            e);
      }

      // TODO: Reuse CommandLine for each call to parse command once this issue is
      // fixed.
      CommandLine commandLine =
          new CommandLine(RunCommand.class, new GuiceFactory(injector))
              .setCaseInsensitiveEnumValuesAllowed(true)
              .setUnmatchedOptionsArePositionalParams(true);

      commandLine.execute(tokens.toArray(new String[0]));
      ListenableFuture<SessionRequestInfo.Builder> resultFuture = runCommandParseResult.poll();
      SessionRequestInfo.Builder result;
      if (resultFuture != null && resultFuture.isDone()) {
        try {
          result = Futures.getDone(resultFuture);
        } catch (ExecutionException e) {
          throw new MobileHarnessException(
              InfraErrorId.ATS_SERVER_INVALID_REQUEST_ERROR,
              "Failed to parse command line: " + tfCommand,
              e.getCause());
        }
      } else {
        throw new MobileHarnessException(
            InfraErrorId.ATS_SERVER_INVALID_REQUEST_ERROR,
            "The command line contains syntax error: " + tfCommand);
      }
      return result;
    }
  }
}
