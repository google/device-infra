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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.shell.ShellUtils.tokenize;
import static java.util.Arrays.asList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.DeviceInfraServiceUtil;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleOutput;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.MainArgs;
import com.google.devtools.mobileharness.infra.ats.console.command.RootCommand;
import com.google.devtools.mobileharness.infra.ats.console.constant.AtsConsoleDirs;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.ServerLogPrinter;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.log.LogDumper;
import com.google.devtools.mobileharness.infra.ats.console.util.notice.NoticeMessageUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.version.VersionMessageUtil;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessLogger;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

/** ATS Console. */
public class AtsConsole {

  private static final String APPNAME = "AtsConsole";
  private static final String HELP_PATTERN = "h|help";

  public static void main(String[] args) throws IOException {
    // Gets system properties.
    ImmutableMap<String, String> systemProperties =
        System.getProperties().entrySet().stream()
            .collect(toImmutableMap(e -> (String) e.getKey(), e -> (String) e.getValue()));

    // Parses flags.
    ImmutableList<String> deviceInfraServiceFlags =
        DeviceInfraServiceUtil.parseDeviceInfraServiceFlagsFromSystemProperty();
    DeviceInfraServiceUtil.parseFlags(deviceInfraServiceFlags);

    // Initializes line reader and stdout/stderr.
    LineReader lineReader = initializeLineReaderAndStdout();

    // Creates Guice injector.
    Injector injector =
        Guice.createInjector(
            new AtsConsoleModule(
                deviceInfraServiceFlags,
                asList(args),
                systemProperties,
                lineReader,
                System.out,
                System.err,
                AtsConsole::getOlcServerBinary));

    // Creates ATS console.
    AtsConsole atsConsole = injector.getInstance(AtsConsole.class);
    atsConsole.injector = injector;

    // Initializes logger.
    MobileHarnessLogger.init(
        AtsConsoleDirs.getLogDir(),
        ImmutableList.of(atsConsole.consoleUtil.getLogHandler()),
        /* disableConsoleHandler= */ true);

    // Adds shutdown hook.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> System.out.println(LogDumper.dumpLog()), // Dump logs.
                "ats-console-shutdown-hook"));

    // Starts ATS console.
    try (NonThrowingAutoCloseable ignored = threadRenaming("ats-console-main-thread")) {
      atsConsole.run();
    } catch (MobileHarnessException | InterruptedException | RuntimeException | Error e) {
      atsConsole.consoleUtil.printlnStderr(
          "Console received an unexpected exception (shown below); shutting down ATS Console.\n%s",
          Throwables.getStackTraceAsString(e));
      System.exit(1);
    }
  }

  private final ImmutableList<String> mainArgs;
  private final ImmutableList<String> deviceInfraServiceFlags;
  private final LineReader lineReader;
  private final PrintWriter outWriter;
  private final PrintWriter errWriter;
  private final Sleeper sleeper;
  private final ConsoleUtil consoleUtil;
  private final ConsoleInfo consoleInfo;
  private final ServerPreparer serverPreparer;
  private final ServerLogPrinter serverLogPrinter;
  private final VersionMessageUtil versionMessageUtil;

  /** Set before {@link #run}; */
  @VisibleForTesting public volatile Injector injector;

  @Inject
  AtsConsole(
      @MainArgs ImmutableList<String> mainArgs,
      @DeviceInfraServiceFlags ImmutableList<String> deviceInfraServiceFlags,
      @ConsoleLineReader LineReader lineReader,
      @ConsoleOutput(ConsoleOutput.Type.OUT_WRITER) PrintWriter outWriter,
      @ConsoleOutput(ConsoleOutput.Type.ERR_WRITER) PrintWriter errWriter,
      Sleeper sleeper,
      ConsoleUtil consoleUtil,
      ConsoleInfo consoleInfo,
      ServerPreparer serverPreparer,
      ServerLogPrinter serverLogPrinter,
      VersionMessageUtil versionMessageUtil) {
    this.mainArgs = mainArgs;
    this.deviceInfraServiceFlags = deviceInfraServiceFlags;
    this.lineReader = lineReader;
    this.outWriter = outWriter;
    this.errWriter = errWriter;
    this.sleeper = sleeper;
    this.consoleUtil = consoleUtil;
    this.consoleInfo = consoleInfo;
    this.serverPreparer = serverPreparer;
    this.serverLogPrinter = serverLogPrinter;
    this.versionMessageUtil = versionMessageUtil;
  }

  public void run() throws MobileHarnessException, InterruptedException {
    // Prints notice message.
    consoleUtil.printlnStdout(NoticeMessageUtil.getNoticeMessage());

    // Prints version information.
    consoleUtil.printlnStdout(versionMessageUtil.getVersionMessage());

    // Prints help information.
    consoleUtil.printlnStdout("Use \"help\" to get more information on running commands.");

    // Prints arguments.
    if (!mainArgs.isEmpty()) {
      consoleUtil.printlnStderr("Args: %s", mainArgs);
    }
    if (!deviceInfraServiceFlags.isEmpty()) {
      consoleUtil.printlnStderr("Flags: %s", deviceInfraServiceFlags);
    }

    // Initializes CLI.
    CommandLine commandLine =
        new CommandLine(RootCommand.class, new GuiceFactory(injector))
            .setCaseInsensitiveEnumValuesAllowed(true)
            .setOut(outWriter)
            .setErr(errWriter)
            .setUnmatchedOptionsArePositionalParams(true);

    // Prepares OLC server.
    if (Flags.instance().enableAtsConsoleOlcServer.getNonNull()) {
      serverPreparer.prepareOlcServer();
    }

    // Prints OLC server streaming log.
    if (Flags.instance().enableAtsConsoleOlcServerLog.getNonNull()) {
      serverLogPrinter.enable(true);
    }

    // Starts to read input from console.
    ImmutableList<String> args = mainArgs;
    String input;
    List<String> tokens;

    do {
      if (args.isEmpty()) {
        input = getConsoleInput().orElse(null);
        if (input == null) {
          consoleUtil.printlnStderr("Input interrupted; quitting...");
          consoleInfo.setShouldExitConsole(true);
          break;
        }

        tokens = new ArrayList<>();
        try {
          tokenize(tokens, input);
        } catch (TokenizationException e) {
          consoleUtil.printlnStderr("Invalid input: %s", input);
          continue;
        }
        if (tokens.isEmpty()) {
          continue;
        }
      } else {
        consoleUtil.printlnStdout("Using commandline arguments as starting command: %s", args);
        lineReader.getHistory().add(Joiner.on(" ").join(args));
        tokens = args;
        if (args.get(0).matches(HELP_PATTERN)) {
          // If starts from command line with args "--help", "-h", returns to shell.
          consoleInfo.setShouldExitConsole(true);
        }
        args = ImmutableList.of();
      }

      commandLine.execute(tokens.toArray(new String[0]));

      sleeper.sleep(Duration.ofMillis(100L));
    } while (!consoleInfo.getShouldExitConsole());
  }

  /**
   * Gets input from the console.
   *
   * <p>Returns a {@link String} containing the input to parse and run. Will return {@code null} if
   * console is not available, readLine was interrupted (for example Ctrl-C), or an EOF has been
   * found (for example Ctrl-D).
   */
  private Optional<String> getConsoleInput() {
    try {
      return Optional.of(lineReader.readLine("ats-console > "));
    } catch (UserInterruptException e) {
      consoleUtil.printlnStderr("\nInterrupted by the user.");
    } catch (EndOfFileException e) {
      consoleUtil.printlnStderr("\nReceived EOF.");
    }
    return Optional.empty();
  }

  private static Path getOlcServerBinary() {
    return Path.of(Flags.instance().atsConsoleOlcServerPath.getNonNull());
  }

  /** Initializes line reader. */
  private static LineReader initializeLineReaderAndStdout() throws IOException {
    return LineReaderBuilder.builder()
        .appName(APPNAME)
        .terminal(TerminalBuilder.builder().system(true).dumb(true).build())
        .history(new DefaultHistory())
        .build();
  }
}
