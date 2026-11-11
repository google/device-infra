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
    verify(crashDialogDetector).scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
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
    verify(crashDialogDetector).scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_sequentialCrashesOnSharedReportingPid_emitsBothAndScansOnlyNonIgnored() {
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

    verify(crashDialogDetector).scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_sequentialHeadersWithoutBanner_rotatesBufferAndEmitsBothEvents() {
    processLines(
        ImmutableList.of(
            "10-13 12:54:02.102  9000  9000 F DEBUG   : pid: 1111, tid: 1111, name:"
                + " com.ignored.app  >>> com.ignored.app <<<",
            "10-13 12:54:02.103  9000  9000 F DEBUG   : signal 11 (SIGSEGV), fault addr 0x1",
            "10-13 12:55:00.202  9000  9000 F DEBUG   : pid: 2222, tid: 2222, name:"
                + " com.error.service  >>> com.error.service <<<",
            "10-13 12:55:00.203  9000  9000 F DEBUG   : signal 6 (SIGABRT), fault addr 0x2"));

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(2);
    assertThat(((CrashEvent) events.get(0)).process())
        .isEqualTo(
            new CrashedProcess("com.ignored.app", 1111, ProcessCategory.IGNORED, CrashType.NATIVE));
    assertThat(((CrashEvent) events.get(0)).crashLogs()).doesNotContain("signal 6 (SIGABRT)");
    assertThat(((CrashEvent) events.get(1)).process())
        .isEqualTo(
            new CrashedProcess("com.error.service", 2222, ProcessCategory.ERROR, CrashType.NATIVE));
    assertThat(((CrashEvent) events.get(1)).crashLogs()).doesNotContain("signal 11 (SIGSEGV)");
    verify(crashDialogDetector).scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_unrecognizedTombstoneHeader_triggersFallbackScanOnce() {
    processLines(
        ImmutableList.of(
            "10-13 12:54:02.123  4444  4444 F DEBUG   : *** *** *** *** *** *** *** *** *** ***",
            "10-13 12:54:02.124  4444  4444 F DEBUG   : signal 11 (SIGSEGV), code 1"
                + " (SEGV_MAPERR)",
            "10-13 12:54:02.125  4444  4444 F DEBUG   : backtrace:"));

    assertThat(crashDetector.getEvents()).isEmpty();
    verify(crashDialogDetector).scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_noCrash_noEvents() {
    crashDetector.process(
        LogcatParser.parse("10-13 12:54:02.123  1234  5678 I ActivityManager: Some message").get());
    crashDetector.process(
        LogcatParser.parse("10-13 12:54:03.123  4321  8765 E NotDebug: Some error").get());
    crashDetector.process(
        LogcatParser.parse("10-13 12:54:04.123  5000  5000 D DEBUG   : *** *** *** non-fatal log")
            .get());

    assertThat(crashDetector.getEvents()).isEmpty();
    verifyNoInteractions(crashDialogDetector);
  }

  private void processLines(List<String> lines) {
    for (String line : lines) {
      LogcatParser.parse(line).ifPresent(crashDetector::process);
    }
  }
}
