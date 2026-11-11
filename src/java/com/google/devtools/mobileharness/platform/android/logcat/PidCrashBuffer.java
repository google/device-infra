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
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashedProcess;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Buffers logcat lines for a single crash on a reporting PID and tracks process identification and
 * crash-dialog scan state.
 */
final class PidCrashBuffer {
  private final List<String> logLines = new ArrayList<>();
  private Optional<CrashedProcess> crashedProcess = Optional.empty();
  private boolean exceptionStarted = false;
  private boolean headerMatched = false;
  private boolean dialogScanTriggered = false;

  void addLine(String message) {
    logLines.add(message);
  }

  int lineCount() {
    return logLines.size();
  }

  boolean hasStartedException() {
    return exceptionStarted;
  }

  void markExceptionStarted() {
    this.exceptionStarted = true;
  }

  boolean hasMatchedHeader() {
    return headerMatched;
  }

  /**
   * Records a matched crash process header and returns {@code true} if a crash dialog scan should
   * be triggered for this crash.
   */
  boolean recordCrashedProcess(
      String processName, String pidText, CrashType crashType, MonitoringConfig monitoringConfig) {
    headerMatched = true;
    OptionalInt pid = tryParsePid(pidText);
    if (pid.isEmpty()) {
      return false;
    }
    ProcessCategory category = monitoringConfig.categorizeProcess(processName);
    crashedProcess =
        Optional.of(new CrashedProcess(processName, pid.getAsInt(), category, crashType));
    if (category == ProcessCategory.IGNORED || dialogScanTriggered) {
      return false;
    }
    dialogScanTriggered = true;
    return true;
  }

  /**
   * Marks that a fallback crash dialog scan was triggered before a process header matched. Returns
   * {@code true} if no header has matched and no scan had been triggered for this buffer yet.
   */
  boolean triggerFallbackDialogScan() {
    if (headerMatched || dialogScanTriggered) {
      return false;
    }
    dialogScanTriggered = true;
    return true;
  }

  Optional<CrashEvent> toCrashEvent() {
    return crashedProcess.map(
        process -> new CrashEvent(process, Joiner.on(System.lineSeparator()).join(logLines)));
  }

  static OptionalInt tryParsePid(String pidText) {
    try {
      return OptionalInt.of(Integer.parseInt(pidText));
    } catch (NumberFormatException e) {
      return OptionalInt.empty();
    }
  }
}
