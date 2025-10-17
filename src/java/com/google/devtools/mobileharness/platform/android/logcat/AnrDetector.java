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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashedProcess;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A {@link LineProcessor} that detects crash events from the Android Runtime.
 *
 * <p>This processor looks for lines with the tag "ActivityManager" and processes them to detect
 * ANRs. Sample ANRs:
 *
 * <pre>{@code
 * 10-16 11:40:19.517 100 201 E ActivityManager: ANR in com.app.anr (com.app.anr/.MainActivity)
 * 10-16 10:00:00.200 100 201 E ActivityManager: PID: 12345
 * 10-16 10:00:00.300 100 201 E ActivityManager: Reason: Test ANR reason 1
 * }</pre>
 */
public class AnrDetector implements LineProcessor {

  private static final String ACTIVITY_MANAGER_TAG = "ActivityManager";

  // Both of the below sample logcat lines are valid.
  // 11-30 11:40:19.517 12 4 E ActivityManager: ANR in com.app.anr (com.app.anr/.MainActivity)
  // 11-30 11:40:19.517 12 4 E ActivityManager: ANR in com.app.anr
  private static final Pattern ANR_DETECTION_PATTERN =
      Pattern.compile("^ANR in ([a-zA-Z0-9_\\.]+)( \\(.+\\))?");
  private static final Pattern ANR_PID_PATTERN = Pattern.compile("^PID: (\\d+)");
  private static final String ANR_REASON_PREFIX = "Reason: ";

  // Reusable list to store ANR lines sequences. ANRs generally have 3 lines.
  private final List<String> anrLines = new ArrayList<>();

  // Mapping the crashed process to the log lines of the ANR event.
  private final LinkedHashMap<CrashedProcess, List<String>> detectedAnrEvents =
      new LinkedHashMap<>();
  private final MonitoringConfig monitoringConfig;

  public AnrDetector(MonitoringConfig monitoringConfig) {
    this.monitoringConfig = monitoringConfig;
  }

  @Override
  public void process(LogcatLine line) {
    if (!line.tag().equals(ACTIVITY_MANAGER_TAG)) {
      return;
    }
    if (ANR_DETECTION_PATTERN.matcher(line.message()).matches()) {
      anrLines.clear(); // New ANR, clear the old lines.
      anrLines.add(line.message());
      return;
    }
    if (ANR_PID_PATTERN.matcher(line.message()).matches()
        || line.message().startsWith(ANR_REASON_PREFIX)) {
      anrLines.add(line.message());
    }

    if (anrLines.size() == 3) {
      /* ANR log message complete. */
      var crashedProcess = processAnrLines(anrLines);
      if (crashedProcess.isEmpty()) {
        anrLines.clear(); // Processing didn't determine ANR, clear the stored lines.
        return;
      }
      detectedAnrEvents.put(crashedProcess.get(), ImmutableList.copyOf(anrLines));
      anrLines.clear();
    }
  }

  private Optional<CrashedProcess> processAnrLines(List<String> anrLines) {
    var anrDetectedMatcher = ANR_DETECTION_PATTERN.matcher(anrLines.get(0));
    var anrPidMatcher = ANR_PID_PATTERN.matcher(anrLines.get(1));
    if (!(anrDetectedMatcher.matches()
        && anrPidMatcher.matches()
        && anrLines.get(2).startsWith(ANR_REASON_PREFIX))) {
      return Optional.empty();
    }
    var processName = anrDetectedMatcher.group(1);
    var pid = Integer.parseInt(anrPidMatcher.group(1));
    var category = monitoringConfig.categorizeProcess(processName);
    return Optional.of(new CrashedProcess(processName, pid, category, CrashType.ANR));
  }

  @Override
  public ImmutableList<LogcatEvent> getEvents() {
    return detectedAnrEvents.entrySet().stream()
        .map(
            event ->
                new CrashEvent(
                    event.getKey(), Joiner.on(System.lineSeparator()).join(event.getValue())))
        .collect(toImmutableList());
  }
}
