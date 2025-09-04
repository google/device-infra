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

package com.google.devtools.mobileharness.infra.ats.console.util.log;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.console.constant.AtsConsoleDirs;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.OlcServerDirs;
import com.google.devtools.mobileharness.shared.util.flags.Flags;

/** Utility for dumping logs. */
public class LogDumper {

  /** Type of log dirs to dump. */
  public enum LogDirsType {
    ALL,
    USED,
  }

  /** Gets log dirs of ATS console and OLC server. */
  public static ImmutableList<String> getLogDirs(LogDirsType type) {
    boolean allLogDirs =
        type == LogDirsType.ALL || !Flags.instance().atsConsoleOlcServerEmbeddedMode.getNonNull();
    return allLogDirs
        ? ImmutableList.of(OlcServerDirs.getLogDir(), AtsConsoleDirs.getLogDir())
        : ImmutableList.of(AtsConsoleDirs.getLogDir());
  }

  /** Prints information about log dirs of ATS console and OLC server. */
  public static String dumpLog(LogDirsType type) {
    return getLogDirs(type).stream()
        .map(logDir -> String.format("Saved log to %s/", logDir))
        .collect(joining("\n"));
  }

  private LogDumper() {}
}
