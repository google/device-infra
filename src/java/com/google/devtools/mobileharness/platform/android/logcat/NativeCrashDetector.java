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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType.NATIVE;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashedProcess;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link LineProcessor} to detect native crash events in logcat.
 *
 * <p>This processor looks for lines with the "DEBUG" tag to detect native crashes. Sample lines:
 *
 * <pre>{@code
 * 10-13 12:54:02.123 1234 1234 F DEBUG : Build fingerprint: 'google/cheetah/cheetah:13/TQ1A.221205.011/9244662:user/release-keys'
 * 10-13 12:54:02.123 1234 1234 F DEBUG : Revision: 'DVT1.0'
 * 10-13 12:54:02.123 1234 1234 F DEBUG : ABI: 'arm64' 10-13 1234 123412:54:02.123 1234 1234 F DEBUG : Timestamp: 2023-10-13 12:54:02.123+0800
 * 10-13 12:54:02.123 1234 1234 F DEBUG : pid: 12345, tid: 12345, name: com.example.app >>> com.example.app <<<
 * 10-13 12:54:02.123 1234 1234 F DEBUG : uid: 10166
 * 10-13 12:54:02.123 1234 1234 F DEBUG : signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
 * 10-13 12:54:02.123 1234 1234 F DEBUG : Cause: null pointer dereference
 * 10-13 12:54:02.123 1234 1234 F DEBUG : x0 0000000000000000 x1 0000007b8b7b78f0 x2 0000000000000001 x3 0000000000000000
 * }</pre>
 */
public class NativeCrashDetector implements LineProcessor {

  private static final String NATIVE_CRASH_LOG_TAG = "DEBUG";

  private static final Pattern NATIVE_CRASH_MATCH_PATTERN =
      Pattern.compile("pid: (\\d+), tid: \\d+, name: .+  >>> (.+) <<<");

  // The 'DEBUG' tag used for native crashes has a log pid which is different from the crashed
  // process' pid. This multimap is used to store the lines from the 'DEBUG' tag by pid so that we
  // can match the crash event with the pid and the process name.
  private final LinkedListMultimap<Integer, String> debugTagLinesToPid =
      LinkedListMultimap.create();

  // Mapping the crashed process to the pid of the 'DEBUG' tag line where the crash was logged.
  private final LinkedHashMap<CrashedProcess, Integer> detectedCrashEvents = new LinkedHashMap<>();

  private final MonitoringConfig monitoringConfig;

  public NativeCrashDetector(MonitoringConfig monitoringConfig) {
    this.monitoringConfig = monitoringConfig;
  }

  @Override
  public void process(LogcatLine line) {
    if (!line.tag().equals(NATIVE_CRASH_LOG_TAG)) {
      return;
    }
    debugTagLinesToPid.put(line.pid(), line.message());
    var crashedProcess = processDebugLines(debugTagLinesToPid.get(line.pid()));
    if (crashedProcess.isEmpty()) {
      return;
    }
    detectedCrashEvents.put(crashedProcess.get(), line.pid());
  }

  private Optional<CrashedProcess> processDebugLines(List<String> lines) {
    if (lines.stream().noneMatch(NATIVE_CRASH_MATCH_PATTERN.asMatchPredicate())) {
      return Optional.empty();
    }
    Optional<String> matched =
        lines.stream().filter(NATIVE_CRASH_MATCH_PATTERN.asMatchPredicate()).findFirst();
    if (matched.isEmpty()) {
      return Optional.empty();
    }
    Matcher matcher = NATIVE_CRASH_MATCH_PATTERN.matcher(matched.get());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    var pid = Integer.parseInt(matcher.group(1));
    var processName = matcher.group(2);
    var category = monitoringConfig.categorizeProcess(processName);
    return Optional.of(new CrashedProcess(processName, pid, category, NATIVE));
  }

  @Override
  public ImmutableList<LogcatEvent> getEvents() {
    return detectedCrashEvents.entrySet().stream()
        .map(
            crashEvent ->
                new CrashEvent(
                    crashEvent.getKey(),
                    Joiner.on(System.lineSeparator())
                        .join(debugTagLinesToPid.get(crashEvent.getValue()))))
        .collect(toImmutableList());
  }
}
