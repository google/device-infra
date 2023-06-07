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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static com.google.devtools.deviceinfra.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.deviceinfra.shared.util.shell.ShellUtils.tokenize;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.util.Arrays.asList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.atsconsole.Annotations.ConsoleLineReader;
import com.google.devtools.atsconsole.Annotations.ConsoleOutput;
import com.google.devtools.atsconsole.Annotations.MainArgs;
import com.google.devtools.atsconsole.command.RootCommand;
import com.google.devtools.atsconsole.controller.olcserver.ServerLogPrinter;
import com.google.devtools.atsconsole.controller.olcserver.ServerPreparer;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import com.google.devtools.deviceinfra.shared.util.shell.ShellUtils;
import com.google.devtools.deviceinfra.shared.util.shell.ShellUtils.TokenizationException;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
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
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.inject.Inject;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;

/** ATS Console. */
public class AtsConsole implements Callable<Void> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String APPNAME = "AtsConsole";
  private static final String HELP_PATTERN = "h|help";

  private static final String MOBLY_TESTCASES_DIR = System.getProperty("MOBLY_TESTCASES_DIR");
  private static final String TEST_RESULTS_DIR = System.getProperty("TEST_RESULTS_DIR");
  private static final String XTS_ROOT_DIR = System.getProperty("XTS_ROOT");
  private static final String MOBLY_TEST_ZIP_SUITE_MAIN_FILE =
      System.getProperty("MOBLY_TEST_ZIP_SUITE_MAIN_FILE");
  private static final String DEVICE_INFRA_SERVICE_FLAGS =
      System.getProperty("DEVICE_INFRA_SERVICE_FLAGS");

  public static void main(String[] args) throws IOException {
    MobileHarnessLogger.init();

    // Parses flags.
    ImmutableList<String> deviceInfraServiceFlags = parseFlags();

    // Creates line reader.
    LineReader lineReader = createLineReader();

    // Creates Guice injector.
    Injector injector =
        Guice.createInjector(
            new AtsConsoleModule(
                deviceInfraServiceFlags,
                asList(args),
                lineReader,
                System.out,
                System.err,
                AtsConsole::getOlcServerBinary));

    // Creates ATS console.
    AtsConsole atsConsole = injector.getInstance(AtsConsole.class);
    atsConsole.injector = injector;

    // Starts ATS console.
    logFailure(
        newDirectExecutorService()
            .submit(threadRenaming(atsConsole, () -> "ats-console-main-thread")),
        Level.SEVERE,
        "Console received an unexpected exception (shown below); shutting down ATS Console.");
  }

  private final ImmutableList<String> mainArgs;
  private final LineReader lineReader;
  private final PrintWriter outWriter;
  private final PrintWriter errWriter;
  private final Sleeper sleeper;
  private final ConsoleUtil consoleUtil;
  private final ConsoleInfo consoleInfo;
  private final ServerPreparer serverPreparer;
  private final ServerLogPrinter serverLogPrinter;

  /** Set before {@link #call}; */
  @VisibleForTesting public volatile Injector injector;

  @Inject
  AtsConsole(
      @MainArgs ImmutableList<String> mainArgs,
      @ConsoleLineReader LineReader lineReader,
      @ConsoleOutput(ConsoleOutput.Type.OUT_WRITER) PrintWriter outWriter,
      @ConsoleOutput(ConsoleOutput.Type.ERR_WRITER) PrintWriter errWriter,
      Sleeper sleeper,
      ConsoleUtil consoleUtil,
      ConsoleInfo consoleInfo,
      ServerPreparer serverPreparer,
      ServerLogPrinter serverLogPrinter) {
    this.mainArgs = mainArgs;
    this.lineReader = lineReader;
    this.outWriter = outWriter;
    this.errWriter = errWriter;
    this.sleeper = sleeper;
    this.consoleUtil = consoleUtil;
    this.consoleInfo = consoleInfo;
    this.serverPreparer = serverPreparer;
    this.serverLogPrinter = serverLogPrinter;
  }

  @Override
  public Void call() throws MobileHarnessException, InterruptedException {
    CommandLine commandLine =
        new CommandLine(RootCommand.class, new GuiceFactory(injector))
            .setOut(outWriter)
            .setErr(errWriter);
    commandLine.setUnmatchedOptionsArePositionalParams(true);

    initializeConsoleInfo();

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
    try {
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

        sleeper.sleep(Duration.ofMillis(100));
      } while (!consoleInfo.getShouldExitConsole());

      return null;
    } finally {
      // Makes sure that we don't quit with messages still in the buffers.
      consoleUtil.flushConsoleOutput();
    }
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

  private void initializeConsoleInfo() {
    if (MOBLY_TESTCASES_DIR != null) {
      consoleInfo.setMoblyTestCasesDir(MOBLY_TESTCASES_DIR);
    }
    if (TEST_RESULTS_DIR != null) {
      consoleInfo.setResultsDirectory(TEST_RESULTS_DIR);
    }
    if (MOBLY_TEST_ZIP_SUITE_MAIN_FILE != null) {
      consoleInfo.setMoblyTestZipSuiteMainFile(MOBLY_TEST_ZIP_SUITE_MAIN_FILE);
    }
    if (XTS_ROOT_DIR != null) {
      consoleInfo.setXtsRootDirectory(XTS_ROOT_DIR);
    }
  }

  private static Path getOlcServerBinary() {
    return Path.of(Flags.instance().atsConsoleOlcServerPath.getNonNull());
  }

  private static ImmutableList<String> parseFlags() {
    List<String> deviceInfraServiceFlags = new ArrayList<>();
    if (!isNullOrEmpty(DEVICE_INFRA_SERVICE_FLAGS)) {
      try {
        ShellUtils.tokenize(deviceInfraServiceFlags, DEVICE_INFRA_SERVICE_FLAGS);
      } catch (TokenizationException e) {
        logger.atSevere().withCause(e).log(
            "Failed to parse flags for device infra service: [%s]", DEVICE_INFRA_SERVICE_FLAGS);
      }
      if (!deviceInfraServiceFlags.isEmpty()) {
        logger.atInfo().log("Device infra service flags: %s", deviceInfraServiceFlags);
        String[] deviceInfraServiceFlagsArray = deviceInfraServiceFlags.toArray(new String[0]);
        Flags.parse(deviceInfraServiceFlagsArray);
      }
    }
    return ImmutableList.copyOf(deviceInfraServiceFlags);
  }

  /**
   * Returns a new LineReader, or {@code null} if an IOException occurs. Note that this function
   * must be static so that we can run it before the superclass constructor.
   */
  private static LineReader createLineReader() throws IOException {
    return LineReaderBuilder.builder()
        .appName(APPNAME)
        .terminal(TerminalBuilder.builder().system(true).dumb(true).build())
        .history(new DefaultHistory())
        .build();
  }
}
