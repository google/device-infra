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

import com.google.devtools.mobileharness.infra.ats.console.Annotations.ConsoleLineReader;
import com.google.errorprone.annotations.FormatMethod;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

/** Utility for printing information to ATS console. */
@Singleton
public class ConsoleUtil {

  private static final AttributedStyle STDERR_STYLE =
      AttributedStyle.DEFAULT.italic().foreground(AttributedStyle.MAGENTA);

  private final LineReader lineReader;

  @Inject
  ConsoleUtil(@ConsoleLineReader LineReader lineReader) {
    this.lineReader = lineReader;
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
    lineReader.printAbove(String.format(format, args));
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
    lineReader.printAbove(text);
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
    lineReader.printAbove(createStderr(String.format(format, args)));
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
    lineReader.printAbove(createStderr(text));
  }

  /**
   * Displays a text (e.g., logs from another process) directly on the console.
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
    lineReader.printAbove(attributedString);
  }

  private static AttributedString createStderr(String text) {
    return new AttributedString(text, STDERR_STYLE);
  }
}
