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
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashedProcess;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
    if (buffer.append(message, monitoringConfig)) {
      executorService.execute(
          () ->
              crashDialogDetector.scan(
                  testInfo, device, monitoringConfig.dialogPackagesToIgnore()));
    }
  }

  @Override
  public ImmutableList<LogcatEvent> getEvents() {
    return crashBuffers.stream()
        .map(PidCrashBuffer::toCrashEvent)
        .flatMap(Optional::stream)
        .collect(toImmutableList());
  }

  private static OptionalInt tryParsePid(String pidText) {
    try {
      return OptionalInt.of(Integer.parseInt(pidText));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }

  /** Buffers {@code AndroidRuntime} lines for a single fatal exception on a reporting PID. */
  private static final class PidCrashBuffer {
    private final List<String> logLines = new ArrayList<>();
    private Optional<CrashedProcess> crashedProcess = Optional.empty();
    private boolean hasFatalException = false;
    private boolean processLineHandled = false;

    boolean append(String message, MonitoringConfig monitoringConfig) {
      logLines.add(message);
      if (processLineHandled) {
        return false;
      }
      if (!hasFatalException) {
        hasFatalException = FATAL_EXCEPTION_PATTERN.matcher(message).matches();
        return false;
      }
      Matcher matcher = CRASHED_PROCESS_PATTERN.matcher(message);
      if (!matcher.matches()) {
        return false;
      }
      processLineHandled = true;
      OptionalInt pid = tryParsePid(matcher.group(2));
      if (pid.isEmpty()) {
        return false;
      }
      String processName = matcher.group(1);
      ProcessCategory category = monitoringConfig.categorizeProcess(processName);
      crashedProcess =
          Optional.of(new CrashedProcess(processName, pid.getAsInt(), category, ANDROID_RUNTIME));
      return category != ProcessCategory.IGNORED;
    }

    boolean hasStartedException() {
      return hasFatalException;
    }

    Optional<CrashEvent> toCrashEvent() {
      return crashedProcess.map(
          process -> new CrashEvent(process, Joiner.on(System.lineSeparator()).join(logLines)));
    }
  }
}
