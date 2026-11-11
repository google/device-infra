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
public final class AnrDetectorTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/platform/android/logcat/testdata/";
  private static final String ANR_LOG_FILE = "anr_crash.txt";

  private static final MonitoringConfig MONITORING_CONFIG =
      new MonitoringConfig(
          ImmutableList.of("com.app.anr", "com.app.anr2"),
          ImmutableList.of("com.error.anr"),
          ImmutableList.of("com.ignored.anr"));

  @Mock TestInfo testInfo;
  @Mock Device device;
  @Mock CrashDialogDetector crashDialogDetector;

  private AnrDetector anrDetector;

  @Before
  public void setUp() {
    anrDetector =
        new AnrDetector(
            testInfo, device, MONITORING_CONFIG, crashDialogDetector, newDirectExecutorService());
  }

  @Test
  public void process_anrCrash_detectsCrashEventsAndScansOncePerAnr() throws Exception {
    String crashLogPath = RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + ANR_LOG_FILE);
    List<String> lines = Files.readAllLines(Path.of(crashLogPath));

    processLines(lines);

    ImmutableList<LogcatEvent> events = anrDetector.getEvents();
    assertThat(events).hasSize(2);

    CrashEvent crashEvent1 = (CrashEvent) events.get(0);
    assertThat(crashEvent1.process())
        .isEqualTo(
            new CrashedProcess("com.app.anr", 12345, ProcessCategory.FAILURE, CrashType.ANR));
    assertThat(crashEvent1.crashLogs()).contains("Reason: Test ANR reason 1");

    CrashEvent crashEvent2 = (CrashEvent) events.get(1);
    assertThat(crashEvent2.process())
        .isEqualTo(
            new CrashedProcess("com.app.anr2", 67890, ProcessCategory.FAILURE, CrashType.ANR));
    assertThat(crashEvent2.crashLogs()).contains("Reason: Test ANR reason 2");
    verify(crashDialogDetector, times(2))
        .scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_sequentialAnrsOnSamePid_emitsBothAndScansOnlyNonIgnored() {
    processLines(
        ImmutableList.of(
            "10-16 10:00:00.100 100 101 E ActivityManager: ANR in com.ignored.anr",
            "10-16 10:00:00.200 100 101 E ActivityManager: PID: 5555",
            "10-16 10:00:00.300 100 101 E ActivityManager: Reason: Ignored ANR",
            "10-16 10:01:00.100 100 101 E ActivityManager: ANR in com.error.anr",
            "10-16 10:01:00.200 100 101 E ActivityManager: PID: 5555",
            "10-16 10:01:00.300 100 101 E ActivityManager: Reason: Error ANR"));

    ImmutableList<LogcatEvent> events = anrDetector.getEvents();
    assertThat(events).hasSize(2);
    CrashEvent first = (CrashEvent) events.get(0);
    CrashEvent second = (CrashEvent) events.get(1);

    assertThat(first.process())
        .isEqualTo(
            new CrashedProcess("com.ignored.anr", 5555, ProcessCategory.IGNORED, CrashType.ANR));
    assertThat(first.crashLogs()).contains("Reason: Ignored ANR");
    assertThat(first.crashLogs()).doesNotContain("Reason: Error ANR");

    assertThat(second.process())
        .isEqualTo(new CrashedProcess("com.error.anr", 5555, ProcessCategory.ERROR, CrashType.ANR));
    assertThat(second.crashLogs()).contains("Reason: Error ANR");
    assertThat(second.crashLogs()).doesNotContain("Reason: Ignored ANR");

    verify(crashDialogDetector).scan(testInfo, device, MONITORING_CONFIG.dialogPackagesToIgnore());
  }

  @Test
  public void process_noCrash_noEvents() {
    anrDetector.process(
        LogcatParser.parse("10-13 12:54:02.123  1234  5678 I NotActivityManager: Some message")
            .get());
    anrDetector.process(
        LogcatParser.parse("10-13 12:54:03.123  4321  8765 E ActivityManager: Some other error")
            .get());

    assertThat(anrDetector.getEvents()).isEmpty();
    verifyNoInteractions(crashDialogDetector);
  }

  private void processLines(List<String> lines) {
    for (String line : lines) {
      LogcatParser.parse(line).ifPresent(anrDetector::process);
    }
  }
}
