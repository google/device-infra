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

package com.google.devtools.mobileharness.platform.android.parser;

import static com.google.devtools.mobileharness.platform.android.shared.constant.Splitters.LINE_SPLITTER;

import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.function.Consumer;

/** Base for a Parser to scan files line by line. */
public abstract class LineParser {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  abstract void parseLine(String line);

  /**
   * Parses {@code fileContent}.
   *
   * @param fileContent the input
   * @param onFailure actions to take on failures caused by illegal format of {@code fileContent}
   * @return the success status of parsing, which is true if no failure
   */
  public boolean parse(String fileContent, Consumer<Throwable> onFailure) {
    try {
      LINE_SPLITTER.splitToStream(fileContent).forEachOrdered(this::parseLine);
      return true;
    } catch (IllegalArgumentException e) {
      onFailure.accept(e);
      return false;
    }
  }

  /**
   * Parses {@code fileContent} and logs warnings on any failure.
   *
   * @param fileContent the input
   * @return the success status of parsing, which is true if no failure
   */
  @CanIgnoreReturnValue
  public boolean parse(String fileContent) {
    return parse(
        fileContent, e -> logger.atWarning().withCause(e).log("Failed to parse the fileContent."));
  }
}
