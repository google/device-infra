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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashedProcess;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class NativeCrashDetectorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/platform/android/logcat/testdata/";
  private static final String CRASH_LOG_FILE = "native_crash.txt";
  private static final String CRASH_LOG_37_FILE = "native_crash_37.txt";

  private static final MonitoringConfig MONITORING_CONFIG =
      new MonitoringConfig(
          ImmutableList.of("com.example.app"),
          ImmutableList.of("com.error.service"),
          ImmutableList.of("com.ignored.app"));

  @Mock TestInfo testInfo;
  @Mock Device device;
  @Mock CrashDialogDetector crashDialogDetector;
  private NativeCrashDetector crashDetector;

  @Before
  public void setUp() {
    crashDetector =
        new NativeCrashDetector(
            testInfo, device, MONITORING_CONFIG, crashDialogDetector, newDirectExecutorService());
  }

  @Test
  public void process_nativeCrash_detectsCrashEventAndScansOnce() throws Exception {
    Path crashLogPath =
        Path.of(RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + CRASH_LOG_FILE));
    List<String> lines = Files.readAllLines(crashLogPath);

    processLines(lines);

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process())
        .isEqualTo(
            new CrashedProcess(
                "com.example.app", 12345, ProcessCategory.FAILURE, CrashType.NATIVE));
    assertThat(crashEvent.crashLogs())
        .contains("*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***");
    assertThat(crashEvent.crashLogs())
        .contains("pid: 12345, tid: 12345, name: com.example.app  >>> com.example.app <<<");
    assertThat(crashEvent.crashLogs())
        .contains("signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0");
    assertThat(crashEvent.crashLogs()).doesNotContain("Some message before crash");
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_nativeCrashWithPpid_detectsCrashEventAndScansOnce() throws Exception {
    Path crashLogPath =
        Path.of(RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + CRASH_LOG_37_FILE));
    List<String> lines = Files.readAllLines(crashLogPath);

    processLines(lines);

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process())
        .isEqualTo(
            new CrashedProcess(
                "com.example.app", 31139, ProcessCategory.FAILURE, CrashType.NATIVE));
    assertThat(crashEvent.crashLogs())
        .contains("*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***");
    assertThat(crashEvent.crashLogs())
        .contains(
            "pid: 31139, ppid: 708, tid: 12345, name: com.example.app  >>> com.example.app <<<");
    assertThat(crashEvent.crashLogs()).contains("Cause: null pointer dereference");
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_ignoredPackageTombstoneWithPreamble_retainsEventWithoutDialogScan() {
    processLines(
        ImmutableList.of(
            "10-13 12:54:02.123  1234  1234 F DEBUG   : *** *** *** *** *** *** *** *** *** ***",
            "10-13 12:54:02.123  1234  1234 F DEBUG   : Build fingerprint: 'google/cheetah/14'",
            "10-13 12:54:02.123  1234  1234 F DEBUG   : Revision: 'DVT1.0'",
            "10-13 12:54:02.123  1234  1234 F DEBUG   : ABI: 'arm64'",
            "10-13 12:54:02.123  1234  1234 F DEBUG   : Timestamp: 2026-10-13 12:54:02.123+0000",
            "10-13 12:54:02.123  1234  1234 F DEBUG   : Cmdline: com.ignored.app",
            "10-13 12:54:02.123  1234  1234 F DEBUG   : pid: 22222, ppid: 700, tid: 22222, name:"
                + " com.ignored.app  >>> com.ignored.app <<<",
            "10-13 12:54:02.123  1234  1234 F DEBUG   : signal 6 (SIGABRT), code -1 (SI_QUEUE)",
            "10-13 12:54:02.123  1234  1234 F DEBUG   : backtrace:",
            "10-13 12:54:02.123  1234  1234 F DEBUG   :       #00 pc 0000000000089abc "
                + " /apex/libc"));

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process())
        .isEqualTo(
            new CrashedProcess(
                "com.ignored.app", 22222, ProcessCategory.IGNORED, CrashType.NATIVE));
    assertThat(crashEvent.crashLogs()).contains("Build fingerprint: 'google/cheetah/14'");
    assertThat(crashEvent.crashLogs()).contains("backtrace:");
    assertThat(crashEvent.crashLogs()).contains("#00 pc 0000000000089abc");
    verifyNoInteractions(crashDialogDetector);
  }

  @Test
  public void process_sequentialCrashesOnSharedReportingPid_emitsBothEventsInOrder() {
    processLines(
        ImmutableList.of(
            "10-13 12:54:02.100  9000  9000 F DEBUG   : *** *** *** *** *** *** *** *** *** ***",
            "10-13 12:54:02.101  9000  9000 F DEBUG   : Build fingerprint: 'crash-1'",
            "10-13 12:54:02.102  9000  9000 F DEBUG   : pid: 1111, tid: 1111, name:"
                + " com.ignored.app  >>> com.ignored.app <<<",
            "10-13 12:54:02.103  9000  9000 F DEBUG   : signal 11 (SIGSEGV), fault addr 0x1",
            "10-13 12:55:00.200  9000  9000 F DEBUG   : *** *** *** *** *** *** *** *** *** ***",
            "10-13 12:55:00.201  9000  9000 F DEBUG   : Build fingerprint: 'crash-2'",
            "10-13 12:55:00.202  9000  9000 F DEBUG   : pid: 2222, tid: 2222, name:"
                + " com.error.service  >>> com.error.service <<<",
            "10-13 12:55:00.203  9000  9000 F DEBUG   : signal 6 (SIGABRT), fault addr 0x2"));

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(2);
    CrashEvent first = (CrashEvent) events.get(0);
    CrashEvent second = (CrashEvent) events.get(1);

    assertThat(first.process())
        .isEqualTo(
            new CrashedProcess("com.ignored.app", 1111, ProcessCategory.IGNORED, CrashType.NATIVE));
    assertThat(first.crashLogs()).contains("Build fingerprint: 'crash-1'");
    assertThat(first.crashLogs()).doesNotContain("Build fingerprint: 'crash-2'");

    assertThat(second.process())
        .isEqualTo(
            new CrashedProcess("com.error.service", 2222, ProcessCategory.ERROR, CrashType.NATIVE));
    assertThat(second.crashLogs()).contains("Build fingerprint: 'crash-2'");
    assertThat(second.crashLogs()).doesNotContain("Build fingerprint: 'crash-1'");

    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_unrecognizedHeaderReachingSignalOnly_triggersFallbackScan() {
    processLines(
        ImmutableList.of(
            "10-13 12:54:02.123  4444  4444 F DEBUG   : *** *** *** *** *** *** *** *** *** ***",
            "10-13 12:54:02.123  4444  4444 F DEBUG   : signal 11 (SIGSEGV), code 1"
                + " (SEGV_MAPERR)"));

    assertThat(crashDetector.getEvents()).isEmpty();
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_unrecognizedHeaderReachingBacktraceOnly_triggersFallbackScan() {
    processLines(
        ImmutableList.of(
            "10-13 12:54:02.123  4444  4444 F DEBUG   : *** *** *** *** *** *** *** *** *** ***",
            "10-13 12:54:02.123  4444  4444 F DEBUG   : backtrace:"));

    assertThat(crashDetector.getEvents()).isEmpty();
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_lineThresholdBoundaryThenLateHeader_scansExactlyOnceOnSixteenthLine() {
    ImmutableList.Builder<String> fifteenLines = ImmutableList.builder();
    for (int i = 1; i <= 15; i++) {
      fifteenLines.add("10-13 12:54:02.123  4444  4444 F DEBUG   : preamble " + i);
    }
    processLines(fifteenLines.build());
    verify(crashDialogDetector, never()).scan(any(), any(), any());

    processLines(
        ImmutableList.of(
            "10-13 12:54:02.123  4444  4444 F DEBUG   : preamble 16",
            "10-13 12:54:02.123  4444  4444 F DEBUG   : pid: 5555, tid: 5555, name: svc  >>>"
                + " com.error.service <<<"));

    assertThat(crashDetector.getEvents()).hasSize(1);
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_nonFatalDebugTagLogs_doNotTriggerFallbackScanOrPolluteSubsequentTombstone() {
    ImmutableList.Builder<String> appDebugLines = ImmutableList.builder();
    appDebugLines.add("10-13 12:54:02.099  5000  5000 D DEBUG   : *** *** *** app banner");
    appDebugLines.add("10-13 12:54:02.100  5000  5000 D DEBUG   : signal ready");
    appDebugLines.add("10-13 12:54:02.101  5000  5000 I DEBUG   : backtrace:");
    for (int i = 1; i <= 16; i++) {
      appDebugLines.add("10-13 12:54:02.102  5000  5000 D DEBUG   : app step " + i);
    }
    processLines(appDebugLines.build());

    assertThat(crashDetector.getEvents()).isEmpty();
    verifyNoInteractions(crashDialogDetector);

    processLines(
        ImmutableList.of(
            "10-13 12:55:00.202  5000  5000 F DEBUG   : pid: 8888, tid: 8888, name: svc  >>>"
                + " com.error.service <<<",
            "10-13 12:55:00.203  5000  5000 F DEBUG   : signal 6 (SIGABRT)",
            "10-13 12:56:00.100  5000  5000 D DEBUG   : recycled pid app log"));

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process())
        .isEqualTo(
            new CrashedProcess("com.error.service", 8888, ProcessCategory.ERROR, CrashType.NATIVE));
    assertThat(crashEvent.crashLogs()).doesNotContain("app banner");
    assertThat(crashEvent.crashLogs()).doesNotContain("signal ready");
    assertThat(crashEvent.crashLogs()).doesNotContain("app step");
    assertThat(crashEvent.crashLogs()).doesNotContain("recycled pid app log");
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_overflowingPidHeaderThenValidCrash_ignoresOverflowAndCapturesSecond() {
    processLines(
        ImmutableList.of(
            "10-13 12:54:02.123  7777  7777 F DEBUG   : pid: 99999999999, tid: 12345, name:"
                + " com.ignored.app  >>> com.ignored.app <<<",
            "10-13 12:54:02.124  7777  7777 F DEBUG   : signal 11 (SIGSEGV), code 1"
                + " (SEGV_MAPERR)"));

    assertThat(crashDetector.getEvents()).isEmpty();
    verifyNoInteractions(crashDialogDetector);

    processLines(
        ImmutableList.of(
            "10-13 12:54:03.123  7777  7777 F DEBUG   : pid: 3333, tid: 3333, name:"
                + " com.example.app  >>> com.example.app <<<",
            "10-13 12:54:03.124  7777  7777 F DEBUG   : signal 6 (SIGABRT)"));

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process())
        .isEqualTo(
            new CrashedProcess("com.example.app", 3333, ProcessCategory.FAILURE, CrashType.NATIVE));
    assertThat(crashEvent.crashLogs()).doesNotContain("99999999999");
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_noCrash_noEvents() {
    crashDetector.process(
        LogcatParser.parse("10-13 12:54:02.123  1234  5678 I ActivityManager: Some message").get());
    crashDetector.process(
        LogcatParser.parse("10-13 12:54:03.123  4321  8765 E NotDebug: Some error").get());

    assertThat(crashDetector.getEvents()).isEmpty();
    verifyNoInteractions(crashDialogDetector);
  }

  private void processLines(List<String> lines) {
    for (String line : lines) {
      LogcatParser.parse(line).ifPresent(crashDetector::process);
    }
  }
}
