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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.FormatMethod;
import java.util.Objects;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

/** Utility for printing information to ATS console. */
@Singleton
public class ConsoleUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String LOGGER_NAME = ConsoleUtil.class.getName();

  private static final AttributedStyle STDERR_STYLE =
      AttributedStyle.DEFAULT.italic().foreground(AttributedStyle.MAGENTA);

  private final LogHandler logHandler = new LogHandler();
  private final LineReader lineReader;
  private final boolean printAbove;

  @Inject
  ConsoleUtil(@ConsoleLineReader LineReader lineReader) {
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
    if (printAbove) {
      lineReader.printAbove(line);
    } else {
      System.out.print(addLineTerminatorIfNecessary(line));
    }
    logger.atInfo().logVarargs(format, args);
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
    if (printAbove) {
      lineReader.printAbove(text);
    } else {
      System.out.print(addLineTerminatorIfNecessary(text));
    }
    logger.atInfo().log("%s", text);
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
    logger.atInfo().logVarargs(format, args);
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
    logger.atInfo().log("%s", text);
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
  public void printlnDirect(AttributedString attributedString) {
    if (printAbove) {
      lineReader.printAbove(attributedString);
    } else {
      System.err.print(addLineTerminatorIfNecessary(attributedString.toString()));
    }
  }

  private void doPrintlnStderr(String text) {
    printlnDirect(createStderr(text));
  }

  public Handler getLogHandler() {
    return logHandler;
  }

  private class LogHandler extends Handler {

    @Override
    public void publish(LogRecord logRecord) {
      if (isLoggable(logRecord) && !Objects.equals(logRecord.getLoggerName(), LOGGER_NAME)) {
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

  private static AttributedString createStderr(String text) {
    return new AttributedString(text, STDERR_STYLE);
  }

  private static String addLineTerminatorIfNecessary(String line) {
    return line.endsWith("\n") ? line : line + "\n";
  }
}
