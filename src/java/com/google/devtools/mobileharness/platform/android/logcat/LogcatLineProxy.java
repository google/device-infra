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

package com.google.devtools.mobileharness.platform.android.logcat;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Proxy class to parse logcat lines and pass them to registered line processors. */
public class LogcatLineProxy implements LineCallback {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final List<LineProcessor> lineProcessors = new ArrayList<>();
  private final List<String> unparsedLines = new ArrayList<>();

  public LogcatLineProxy() {}

  @Override
  public Response onLine(String line) {
    Optional<LogcatLine> logLine = LogcatParser.parse(line);
    if (logLine.isEmpty()) {
      unparsedLines.add(line);
      return Response.empty();
    }
    LogcatLine parsedLine = logLine.get();
    for (LineProcessor processor : lineProcessors) {
      try {
        processor.process(parsedLine);
      } catch (RuntimeException e) {
        logger.atWarning().withCause(e).log(
            "%s failed while processing logcat line.", processor.getClass().getSimpleName());
      }
    }
    return Response.empty();
  }

  /** Adds a line processor to the proxy. */
  public void addLineProcessor(LineProcessor processor) {
    lineProcessors.add(processor);
  }

  /** Get logcat events from all the registered line processors. */
  public ImmutableList<LogcatEvent> getLogcatEventsFromProcessors() {
    ImmutableList.Builder<LogcatEvent> events = ImmutableList.builder();
    for (LineProcessor processor : lineProcessors) {
      try {
        events.addAll(processor.getEvents());
      } catch (RuntimeException e) {
        logger.atWarning().withCause(e).log(
            "%s failed while retrieving detected events.", processor.getClass().getSimpleName());
      }
    }
    return events.build();
  }

  /** Return all the unparsed logcat lines. */
  public ImmutableList<String> getUnparsedLines() {
    return ImmutableList.copyOf(unparsedLines);
  }
}
