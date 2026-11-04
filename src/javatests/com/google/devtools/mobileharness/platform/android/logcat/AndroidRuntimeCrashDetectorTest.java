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
public final class AndroidRuntimeCrashDetectorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/platform/android/logcat/testdata/";
  private static final String CRASH_LOG_FILE = "android_runtime_crash.txt";
  private static final String MULTIPLE_CRASH_LOG_FILE = "multiple_android_runtime_crashes.txt";
  private static final MonitoringConfig MONITORING_CONFIG =
      new MonitoringConfig(
          ImmutableList.of("com.example.app", "com.another.app"),
          ImmutableList.of("com.error.service"),
          ImmutableList.of("com.ignored.app"));

  @Mock TestInfo testInfo;
  @Mock Device device;
  @Mock CrashDialogDetector crashDialogDetector;
  private AndroidRuntimeCrashDetector crashDetector;

  @Before
  public void setUp() {
    crashDetector =
        new AndroidRuntimeCrashDetector(
            testInfo, device, MONITORING_CONFIG, crashDialogDetector, newDirectExecutorService());
  }

  @Test
  public void process_androidRuntimeCrash_detectsCrashEventAndScansOnce() throws Exception {
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
                "com.example.app", 12345, ProcessCategory.FAILURE, CrashType.ANDROID_RUNTIME));
    assertThat(crashEvent.crashLogs()).contains("FATAL EXCEPTION: main");
    assertThat(crashEvent.crashLogs()).contains("... 11 more");
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_multipleAndroidRuntimeCrashes_detectsAllCrashEventsAndScansOncePerCrash()
      throws Exception {
    Path crashLogPath =
        Path.of(RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + MULTIPLE_CRASH_LOG_FILE));
    List<String> lines = Files.readAllLines(crashLogPath);

    processLines(lines);

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(2);
    assertThat(events.stream().map(e -> ((CrashEvent) e).process().name()))
        .containsExactly("com.example.app", "com.another.app")
        .inOrder();
    verify(crashDialogDetector, times(2))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_ignoredPackageCrash_retainsEventWithoutDialogScan() {
    processLines(
        ImmutableList.of(
            "10-13 16:27:10.100  4321  4321 D AndroidRuntime: Shutting down VM",
            "10-13 16:27:10.123  4321  4321 E AndroidRuntime: FATAL EXCEPTION: main",
            "10-13 16:27:10.123  4321  4321 E AndroidRuntime: Process: com.ignored.app, PID: 4321",
            "10-13 16:27:10.123  4321  4321 E AndroidRuntime: java.lang.IllegalStateException:"
                + " Ignored"));

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process())
        .isEqualTo(
            new CrashedProcess(
                "com.ignored.app", 4321, ProcessCategory.IGNORED, CrashType.ANDROID_RUNTIME));
    assertThat(crashEvent.crashLogs()).contains("Shutting down VM");
    assertThat(crashEvent.crashLogs()).contains("java.lang.IllegalStateException: Ignored");
    verifyNoInteractions(crashDialogDetector);
  }

  @Test
  public void process_preExceptionLineAndMismatchedReportingPid_joinsBufferedLinesUnderParsedPid() {
    processLines(
        ImmutableList.of(
            "10-13 16:27:10.100  9999  9999 D AndroidRuntime: Shutting down VM",
            "10-13 16:27:10.123  9999  9999 E AndroidRuntime: FATAL EXCEPTION: WorkerThread",
            "10-13 16:27:10.124  9999  9999 E AndroidRuntime: Process: com.example.app, PID: 12345",
            "10-13 16:27:10.125  9999  9999 E AndroidRuntime: java.lang.RuntimeException: Boom",
            "10-13 16:27:10.126  9999  9999 E AndroidRuntime: \tat"
                + " com.example.app.Worker.run(Worker.java:42)"));

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process())
        .isEqualTo(
            new CrashedProcess(
                "com.example.app", 12345, ProcessCategory.FAILURE, CrashType.ANDROID_RUNTIME));
    assertThat(crashEvent.crashLogs())
        .isEqualTo(
            String.join(
                System.lineSeparator(),
                "Shutting down VM",
                "FATAL EXCEPTION: WorkerThread",
                "Process: com.example.app, PID: 12345",
                "java.lang.RuntimeException: Boom",
                "\tat com.example.app.Worker.run(Worker.java:42)"));
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_processLineWithoutPriorFatalException_producesNoEventAndNoScan() {
    processLines(
        ImmutableList.of(
            "10-13 16:27:10.123 12345 12345 E AndroidRuntime: Process: com.example.app, PID: 12345",
            "10-13 16:27:10.124 12345 12345 E AndroidRuntime: java.lang.RuntimeException: Orphan"));

    assertThat(crashDetector.getEvents()).isEmpty();
    verifyNoInteractions(crashDialogDetector);
  }

  @Test
  public void process_sequentialCrashesOnSamePid_emitsBothEventsInOrder() {
    processLines(
        ImmutableList.of(
            "10-13 16:27:10.100  5000  5000 E AndroidRuntime: FATAL EXCEPTION: Thread-1",
            "10-13 16:27:10.101  5000  5000 E AndroidRuntime: Process: com.ignored.app, PID: 5000",
            "10-13 16:27:10.102  5000  5000 E AndroidRuntime: java.lang.RuntimeException: First",
            "10-13 16:28:10.200  5000  5000 E AndroidRuntime: FATAL EXCEPTION: Thread-2",
            "10-13 16:28:10.201  5000  5000 E AndroidRuntime: Process: com.error.service, PID:"
                + " 5000",
            "10-13 16:28:10.202  5000  5000 E AndroidRuntime: java.lang.RuntimeException: Second"));

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(2);
    CrashEvent first = (CrashEvent) events.get(0);
    CrashEvent second = (CrashEvent) events.get(1);

    assertThat(first.process())
        .isEqualTo(
            new CrashedProcess(
                "com.ignored.app", 5000, ProcessCategory.IGNORED, CrashType.ANDROID_RUNTIME));
    assertThat(first.crashLogs()).contains("java.lang.RuntimeException: First");
    assertThat(first.crashLogs()).doesNotContain("java.lang.RuntimeException: Second");

    assertThat(second.process())
        .isEqualTo(
            new CrashedProcess(
                "com.error.service", 5000, ProcessCategory.ERROR, CrashType.ANDROID_RUNTIME));
    assertThat(second.crashLogs()).contains("java.lang.RuntimeException: Second");
    assertThat(second.crashLogs()).doesNotContain("java.lang.RuntimeException: First");

    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void
      process_overflowingPidFollowedByValidCrashOnSamePid_ignoresOverflowAndCapturesSecond() {
    processLines(
        ImmutableList.of(
            "10-13 16:27:10.100  6000  6000 E AndroidRuntime: FATAL EXCEPTION: main",
            "10-13 16:27:10.101  6000  6000 E AndroidRuntime: Process: com.example.app, PID:"
                + " 99999999999",
            "10-13 16:27:11.200  6000  6000 E AndroidRuntime: FATAL EXCEPTION: Thread-2",
            "10-13 16:27:11.201  6000  6000 E AndroidRuntime: Process: com.example.app, PID: 6000",
            "10-13 16:27:11.202  6000  6000 E AndroidRuntime: java.lang.RuntimeException: Valid"));

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process())
        .isEqualTo(
            new CrashedProcess(
                "com.example.app", 6000, ProcessCategory.FAILURE, CrashType.ANDROID_RUNTIME));
    assertThat(crashEvent.crashLogs()).doesNotContain("99999999999");
    verify(crashDialogDetector, times(1))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_noCrash_noEvents() {
    crashDetector.process(
        LogcatParser.parse("10-13 12:54:02.123  1234  5678 I ActivityManager: Some message").get());
    crashDetector.process(
        LogcatParser.parse("10-13 12:54:03.123  4321  8765 E NotAndroidRuntime: Some error").get());

    assertThat(crashDetector.getEvents()).isEmpty();
    verifyNoInteractions(crashDialogDetector);
  }

  private void processLines(List<String> lines) {
    for (String line : lines) {
      LogcatParser.parse(line).ifPresent(crashDetector::process);
    }
  }
}
