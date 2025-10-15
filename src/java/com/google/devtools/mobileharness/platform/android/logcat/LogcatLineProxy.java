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
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Proxy class to parse logcat lines and pass them to registered line processors. */
public class LogcatLineProxy implements LineCallback {
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
    for (LineProcessor processor : lineProcessors) {
      processor.process(logLine.get());
    }
    return Response.empty();
  }

  /** Adds a line processor to the proxy. */
  public void addLineProcessor(LineProcessor processor) {
    lineProcessors.add(processor);
  }

  /** Return all the unparsed logcat lines. */
  public ImmutableList<String> getUnparsedLines() {
    return ImmutableList.copyOf(unparsedLines);
  }
}
