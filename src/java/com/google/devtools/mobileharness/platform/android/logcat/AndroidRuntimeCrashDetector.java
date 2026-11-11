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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
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

  private final List<PidCrashBuffer> crashBuffers = new ArrayList<>();
  private final Map<Integer, PidCrashBuffer> activeBuffersByPid = new HashMap<>();

  private final TestInfo testInfo;
  private final Device device;
  private final MonitoringConfig monitoringConfig;
  private final ExecutorService executorService;
  private final CrashDialogDetector crashDialogDetector;

  public AndroidRuntimeCrashDetector(
      TestInfo testInfo,
      Device device,
      MonitoringConfig config,
      CrashDialogDetector crashDialogDetector,
      ExecutorService executorService) {
    this.testInfo = testInfo;
    this.device = device;
    this.monitoringConfig = config;
    this.crashDialogDetector = crashDialogDetector;
    this.executorService = executorService;
  }

  @Override
  public void process(LogcatLine line) {
    if (!line.tag().equals(ANDROID_RUNTIME_TAG)) {
      return;
    }
    String message = line.message();
    PidCrashBuffer buffer = activeBuffersByPid.get(line.pid());
    if (buffer == null
        || (buffer.hasStartedException() && FATAL_EXCEPTION_PATTERN.matcher(message).matches())) {
      buffer = new PidCrashBuffer();
      crashBuffers.add(buffer);
      activeBuffersByPid.put(line.pid(), buffer);
    }
    if (appendLine(buffer, message)) {
      executorService.execute(() ->
          crashDialogDetector.scan(testInfo, device, monitoringConfig.dialogPackagesToIgnore()));
    }
  }

  /**
   * Appends {@code message} to {@code buffer} and returns {@code true} if a crash dialog scan
   * should be scheduled for this crash.
   */
  private boolean appendLine(PidCrashBuffer buffer, String message) {
    buffer.addLine(message);
    if (buffer.hasMatchedHeader()) {
      return false;
    }
    if (!buffer.hasStartedException()) {
      if (FATAL_EXCEPTION_PATTERN.matcher(message).matches()) {
        buffer.markExceptionStarted();
      }
      return false;
    }
    Matcher matcher = CRASHED_PROCESS_PATTERN.matcher(message);
    if (!matcher.matches()) {
      return false;
    }
    return buffer.recordCrashedProcess(
        matcher.group(1), matcher.group(2), ANDROID_RUNTIME, monitoringConfig);
  }

  @Override
  public ImmutableList<LogcatEvent> getEvents() {
    return crashBuffers.stream()
        .map(PidCrashBuffer::toCrashEvent)
        .flatMap(Optional::stream)
        .collect(toImmutableList());
  }
}
