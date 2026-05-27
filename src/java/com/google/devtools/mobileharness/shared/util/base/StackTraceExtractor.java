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

package com.google.devtools.mobileharness.shared.util.base;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Utility to extract structured stack traces from raw log strings. */
public final class StackTraceExtractor {

  /** Represents an extracted consecutive stack trace containing a list of frame locations. */
  public record StackTrace(ImmutableList<String> frames) {}

  private static final String STACK_TRACE_LINE_PREFIX = "\tat ";
  private static final Splitter LINE_SPLITTER = Splitter.on(Pattern.compile("\\R"));

  /**
   * Parses the input log string, splits it by line separators, groups consecutive lines starting
   * with "\tat " into a StackTrace object, and extracts the frame information after "\tat ".
   *
   * @param log the raw log text
   * @return a list of extracted StackTrace objects
   */
  public static ImmutableList<StackTrace> extract(String log) {
    if (isNullOrEmpty(log)) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<StackTrace> stackTraces = ImmutableList.builder();
    List<String> currentFrames = null;

    for (String line : LINE_SPLITTER.split(log)) {
      if (line.startsWith(STACK_TRACE_LINE_PREFIX)) {
        if (currentFrames == null) {
          currentFrames = new ArrayList<>();
        }
        // Strip "\tat " (4 characters) to get the frame location
        currentFrames.add(line.substring(STACK_TRACE_LINE_PREFIX.length()));
      } else {
        if (currentFrames != null) {
          stackTraces.add(new StackTrace(ImmutableList.copyOf(currentFrames)));
          currentFrames = null;
        }
      }
    }

    if (currentFrames != null) {
      stackTraces.add(new StackTrace(ImmutableList.copyOf(currentFrames)));
    }

    return stackTraces.build();
  }

  private StackTraceExtractor() {}
}
