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
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType.ANDROID_RUNTIME;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashedProcess;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link LineProcessor} that detects crash events from the Android Runtime.
 *
 * <p>This processor looks for lines with the tag "AndroidRuntime" and processes them to detect
 * crashes. It groups lines by PID and attempts to match fatal exceptions with the process name and
 * PID that caused the crash.
 */
public class AndroidRuntimeCrashDetector implements LineProcessor {
  private static final String ANDROID_RUNTIME_TAG = "AndroidRuntime";
  private static final Pattern FATAL_EXCEPTION_PATTERN = Pattern.compile("^FATAL EXCEPTION: .+");
  private static final Pattern CRASHED_PROCESS_PATTERN =
      Pattern.compile("Process: (.+), PID: (\\d+)");

  private final LinkedListMultimap<Integer, String> androidRuntimeLinesToPid =
      LinkedListMultimap.create();

  private final ConcurrentHashMap<Integer, CrashedProcess> detectedCrashEvents =
      new ConcurrentHashMap<>();

  private final MonitoringConfig monitoringConfig;

  public AndroidRuntimeCrashDetector(MonitoringConfig config) {
    this.monitoringConfig = config;
  }

  @Override
  public void process(LogcatLine line) {
    if (!line.tag().equals(ANDROID_RUNTIME_TAG)) {
      return;
    }
    androidRuntimeLinesToPid.put(line.pid(), line.message());
    var crashedProcess = processArtLines(androidRuntimeLinesToPid.get(line.pid()));

    if (crashedProcess.isEmpty()) {
      return;
    }
    detectedCrashEvents.put(line.pid(), crashedProcess.get());
  }

  private Optional<CrashedProcess> processArtLines(List<String> lines) {
    if (lines.stream().noneMatch(FATAL_EXCEPTION_PATTERN.asMatchPredicate())) {
      return Optional.empty();
    }
    Optional<String> matched =
        lines.stream().filter(CRASHED_PROCESS_PATTERN.asMatchPredicate()).findFirst();
    if (matched.isEmpty()) {
      return Optional.empty();
    }
    Matcher matcher = CRASHED_PROCESS_PATTERN.matcher(matched.get());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    var processName = matcher.group(1);
    var pid = matcher.group(2);
    var category = monitoringConfig.categorizeProcess(processName);
    return Optional.of(
        new CrashedProcess(processName, Integer.parseInt(pid), category, ANDROID_RUNTIME));
  }

  @Override
  public ImmutableList<LogcatEvent> getEvents() {
    return detectedCrashEvents.values().stream()
        .map(
            process ->
                new CrashEvent(
                    process,
                    Joiner.on(System.lineSeparator())
                        .join(androidRuntimeLinesToPid.get(process.pid()))))
        .collect(toImmutableList());
  }
}
