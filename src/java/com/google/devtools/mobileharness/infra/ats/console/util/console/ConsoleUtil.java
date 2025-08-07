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

package com.google.devtools.mobileharness.infra.ats.console.util.console;

import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.IMPORTANCE;
import static com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance.IMPORTANT;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.LogImportanceScope;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.FormatMethod;
import java.io.PrintStream;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.jline.reader.LineReader;

/** Utility for printing information to ATS console. */
@Singleton
public class ConsoleUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> HIDDEN_LOGGER_NAMES =
      ImmutableSet.of(ConsoleUtil.class.getName(), "com.google.protobuf.TextFormat");

  private final int minLogRecordImportance =
      Flags.instance().atsConsoleMinLogRecordImportance.getNonNull();
  private final LogHandler logHandler = new LogHandler();
  @Nullable private final LineReader lineReader;
  private final boolean printAbove;

  @Inject
  ConsoleUtil(@Nullable @ConsoleLineReader LineReader lineReader) {
    this.lineReader = lineReader;
    this.printAbove = Flags.instance().atsConsolePrintAboveInput.getNonNull();
  }

  /**
   * Displays a text (command output or user-requested help) on the console.
   *
   * <p>No matter if the text has a trailing '\n' (prefer not to), it will create a new line.
   *
   * <p>The text will be displayed on the line above the input line.
   *
   * <p>The text will be also logged to file.
   *
   * <p>The method is thread safe.
   */
  @FormatMethod
  public void printlnStdout(String format, Object... args) {
    String line = String.format(format, args);
    doPrintlnStdout(line);
    logger.atInfo().with(IMPORTANCE, IMPORTANT).logVarargs(format, args);
  }

  /**
   * Displays a text (command output or user-requested help) on the console.
   *
   * <p>No matter if the text has a trailing '\n' (prefer not to), it will create a new line.
   *
   * <p>The text will be displayed on the line above the input line.
   *
   * <p>The text will be also logged to file.
   *
   * <p>The method is thread safe.
   */
  public void printlnStdout(String text) {
    doPrintlnStdout(text);
    logger.atInfo().with(IMPORTANCE, IMPORTANT).log("%s", text);
  }

  /**
   * Displays a text (error messages or log) on the console.
   *
   * <p>No matter if the text has a trailing '\n' (prefer not to), it will create a new line.
   *
   * <p>The text will be displayed on the line above the input line.
   *
   * <p>The text will be also logged to file.
   *
   * <p>The method is thread safe.
   */
  @FormatMethod
  public void printlnStderr(String format, Object... args) {
    doPrintlnStderr(String.format(format, args));
    logger.atInfo().with(IMPORTANCE, IMPORTANT).logVarargs(format, args);
  }

  /**
   * Displays a text (error messages or log) on the console.
   *
   * <p>No matter if the text has a trailing '\n' (prefer not to), it will create a new line.
   *
   * <p>The text will be displayed on the line above the input line.
   *
   * <p>The text will be also logged to file.
   *
   * <p>The method is thread safe.
   */
  public void printlnStderr(String text) {
    doPrintlnStderr(text);
    logger.atInfo().with(IMPORTANCE, IMPORTANT).log("%s", text);
  }

  /**
   * Displays an attributed text directly on the console.
   *
   * <p>No matter if the text has a trailing '\n' (prefer not to), it will create a new line.
   *
   * <p>The text will be displayed on the line above the input line.
   *
   * <p>The text will NOT be logged to file.
   *
   * <p>The method is thread safe.
   */
  public void printlnDirect(String text, ConsoleTextStyle style, PrintStream secondaryStream) {
    if (printAbove) {
      if (!text.isEmpty() && lineReader != null) {
        style.printAbove(lineReader, text);
      }
    } else {
      secondaryStream.print(addLineTerminatorIfNecessary(text));
    }
  }

  private void doPrintlnStdout(String text) {
    printlnDirect(text, ConsoleTextStyle.CONSOLE_STDOUT, System.out);
  }

  private void doPrintlnStderr(String text) {
    printlnDirect(text, ConsoleTextStyle.CONSOLE_STDERR, System.err);
  }

  public Handler getLogHandler() {
    return logHandler;
  }

  private class LogHandler extends Handler {

    private LogHandler() {
      setFilter(
          record ->
              !HIDDEN_LOGGER_NAMES.contains(record.getLoggerName())
                  && LogRecordImportance.getLogRecordImportance(
                              record, LogImportanceScope.getCurrentScope())
                          .value()
                      >= minLogRecordImportance);
    }

    @Override
    public void publish(LogRecord logRecord) {
      if (isLoggable(logRecord)) {
        try {
          String text = getFormatter().format(logRecord);
          doPrintlnStderr(text);
        } catch (RuntimeException e) {
          reportError(/* msg= */ null, e, ErrorManager.WRITE_FAILURE);
        }
      }
    }

    @Override
    public void flush() {
      // Does nothing.
    }

    @Override
    public void close() {
      // Does nothing.
    }
  }

  private static String addLineTerminatorIfNecessary(String line) {
    return line.endsWith("\n") || line.isEmpty() ? line : line + "\n";
  }
}
