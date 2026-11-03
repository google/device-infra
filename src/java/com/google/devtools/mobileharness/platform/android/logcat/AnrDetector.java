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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashedProcess;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/**
 * A {@link LineProcessor} that detects ANR events reported by {@code ActivityManager}.
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
  private String pendingProcessName = "";
  private int pendingPid = 0;

  // Chronological list of detected ANR events, preserving repeated ANRs on the same PID.
  private final List<CrashEvent> detectedAnrEvents = new ArrayList<>();
  private final TestInfo testInfo;
  private final Device device;
  private final MonitoringConfig monitoringConfig;
  private final ExecutorService executorService;
  private final CrashDialogDetector crashDialogDetector;

  public AnrDetector(
      TestInfo testInfo,
      Device device,
      MonitoringConfig monitoringConfig,
      CrashDialogDetector crashDialogDetector,
      ExecutorService executorService) {
    this.testInfo = testInfo;
    this.device = device;
    this.monitoringConfig = monitoringConfig;
    this.crashDialogDetector = crashDialogDetector;
    this.executorService = executorService;
  }

  @Override
  public void process(LogcatLine line) {
    if (!line.tag().equals(ACTIVITY_MANAGER_TAG)) {
      return;
    }
    String message = line.message();
    var anrDetectedMatcher = ANR_DETECTION_PATTERN.matcher(message);
    if (anrDetectedMatcher.matches()) {
      anrLines.clear(); // New ANR, clear any incomplete sequence.
      pendingProcessName = anrDetectedMatcher.group(1);
      anrLines.add(message);
      return;
    }
    if (anrLines.size() == 1) {
      var anrPidMatcher = ANR_PID_PATTERN.matcher(message);
      if (anrPidMatcher.matches()) {
        OptionalInt pid = tryParsePid(anrPidMatcher.group(1));
        if (pid.isEmpty()) {
          anrLines.clear();
          return;
        }
        pendingPid = pid.getAsInt();
        anrLines.add(message);
      }
      return;
    }
    if (anrLines.size() == 2 && message.startsWith(ANR_REASON_PREFIX)) {
      anrLines.add(message);
      ProcessCategory category = monitoringConfig.categorizeProcess(pendingProcessName);
      CrashedProcess process =
          new CrashedProcess(pendingProcessName, pendingPid, category, CrashType.ANR);
      detectedAnrEvents.add(
          new CrashEvent(process, Joiner.on(System.lineSeparator()).join(anrLines)));
      if (category != ProcessCategory.IGNORED) {
        executorService.execute(
            () ->
                crashDialogDetector.scan(
                    testInfo, device, monitoringConfig.dialogPackagesToIgnore()));
      }
      anrLines.clear();
    }
  }

  private static OptionalInt tryParsePid(String pidText) {
    try {
      return OptionalInt.of(Integer.parseInt(pidText));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  @Override
  public ImmutableList<LogcatEvent> getEvents() {
    return ImmutableList.copyOf(detectedAnrEvents);
  }
}
