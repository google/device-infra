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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType;
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

  private static final MonitoringConfig MONITORING_CONFIG =
      new MonitoringConfig(
          ImmutableList.of("com.example.app"), ImmutableList.of(), ImmutableList.of());

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
  public void process_nativeCrash_detectsCrashEvent() throws Exception {
    Path crashLogPath =
        Path.of(RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + CRASH_LOG_FILE));
    List<String> lines = Files.readAllLines(crashLogPath);

    for (String line : lines) {
      LogcatParser.parse(line).ifPresent(crashDetector::process);
    }

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process().name()).isEqualTo("com.example.app");
    assertThat(crashEvent.process().pid()).isEqualTo(12345);
    assertThat(crashEvent.process().category()).isEqualTo(ProcessCategory.FAILURE);
    assertThat(crashEvent.process().type()).isEqualTo(CrashType.NATIVE);
    assertThat(crashEvent.crashLogs())
        .contains("pid: 12345, tid: 12345, name: com.example.app  >>> com.example.app <<<");
    verify(crashDialogDetector, atLeastOnce())
        .scan(testInfo, device, MONITORING_CONFIG.reportAsFailurePackages());
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
}
