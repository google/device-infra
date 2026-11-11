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
  private static final String TOMBSTONE_BANNER_PREFIX = "*** *** ***";
  private static final String SIGNAL_LINE_PREFIX = "signal ";
  private static final String BACKTRACE_LINE = "backtrace:";
  // Sits above the ~10-line tombstone preamble before the `pid:` header (since exceeding the
  // threshold before the header matches also scans IGNORED processes), while still triggering a
  // fallback dialog scan if the tombstone `pid:` header format changes on newer Android builds.
  private static final int FALLBACK_SCAN_LINE_THRESHOLD = 15;

  private static final Pattern NATIVE_CRASH_MATCH_PATTERN =
      Pattern.compile("pid: (\\d+), (?:ppid: \\d+, )?tid: \\d+, name: .+  >>> (.+) <<<");

  // Native crashes log under debuggerd / crash_dump's reporting PID rather than the crashed app's
  // PID. Store buffers chronologically while tracking the active buffer per reporting PID so
  // multiple tombstones from a shared or recycled reporting PID stay separated.
  private final List<PidCrashBuffer> crashBuffers = new ArrayList<>();
  private final Map<Integer, PidCrashBuffer> activeBuffersByReportingPid = new HashMap<>();

  private final TestInfo testInfo;
  private final Device device;
  private final MonitoringConfig monitoringConfig;
  private final ExecutorService executorService;
  private final CrashDialogDetector crashDialogDetector;

  public NativeCrashDetector(
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
    if (!line.tag().equals(NATIVE_CRASH_LOG_TAG) || line.level() != LogcatParser.Level.FATAL) {
      return;
    }
    String message = line.message();
    PidCrashBuffer buffer = activeBuffersByReportingPid.get(line.pid());
    if (buffer == null || shouldStartNewTombstoneBuffer(buffer, message)) {
      buffer = new PidCrashBuffer();
      crashBuffers.add(buffer);
      activeBuffersByReportingPid.put(line.pid(), buffer);
    }
    if (appendLine(buffer, message)) {
      executorService.execute(() ->
          crashDialogDetector.scan(testInfo, device, monitoringConfig.dialogPackagesToIgnore()));
    }
  }

  /**
   * Appends {@code message} to {@code buffer} and returns {@code true} if a crash dialog scan
   * should be scheduled (either upon matching a non-ignored crash header or reaching a headerless
   * fallback trigger).
   */
  private boolean appendLine(PidCrashBuffer buffer, String message) {
    buffer.addLine(message);
    if (!buffer.hasMatchedHeader()) {
      Matcher matcher = NATIVE_CRASH_MATCH_PATTERN.matcher(message);
      if (matcher.matches()) {
        return buffer.recordCrashedProcess(
            matcher.group(2), matcher.group(1), NATIVE, monitoringConfig);
      }
    }
    return isPostHeaderFallbackTrigger(message, buffer.lineCount())
        && buffer.triggerFallbackDialogScan();
  }

  private static boolean isPostHeaderFallbackTrigger(String message, int bufferedLineCount) {
    String trimmed = message.trim();
    return trimmed.startsWith(SIGNAL_LINE_PREFIX)
        || trimmed.equals(BACKTRACE_LINE)
        || bufferedLineCount > FALLBACK_SCAN_LINE_THRESHOLD;
  }

  private static boolean shouldStartNewTombstoneBuffer(PidCrashBuffer buffer, String message) {
    if (message.startsWith(TOMBSTONE_BANNER_PREFIX)) {
      return true;
    }
    return buffer.hasMatchedHeader() && NATIVE_CRASH_MATCH_PATTERN.matcher(message).matches();
  }

  @Override
  public ImmutableList<LogcatEvent> getEvents() {
    return crashBuffers.stream()
        .map(PidCrashBuffer::toCrashEvent)
        .flatMap(Optional::stream)
        .collect(toImmutableList());
  }
}
