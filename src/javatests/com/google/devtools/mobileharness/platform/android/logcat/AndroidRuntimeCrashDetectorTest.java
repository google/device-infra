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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashType;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AndroidRuntimeCrashDetectorTest {

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/platform/android/logcat/testdata/";
  private static final String CRASH_LOG_FILE = "android_runtime_crash.txt";
  private static final String MULTIPLE_CRASH_LOG_FILE = "multiple_android_runtime_crashes.txt";

  private AndroidRuntimeCrashDetector crashDetector;

  @Before
  public void setUp() {
    MonitoringConfig config =
        new MonitoringConfig(
            ImmutableList.of("com.example.app", "com.another.app"),
            ImmutableList.of(),
            ImmutableList.of());
    crashDetector = new AndroidRuntimeCrashDetector(config);
  }

  @Test
  public void process_androidRuntimeCrash_detectsCrashEvent() throws Exception {
    Path crashLogPath =
        Path.of(RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + CRASH_LOG_FILE));
    List<String> lines = Files.readAllLines(crashLogPath);

    for (String line : lines) {
      crashDetector.process(LogcatParser.parse(line).get());
    }

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(1);
    CrashEvent crashEvent = (CrashEvent) events.get(0);
    assertThat(crashEvent.process().name()).isEqualTo("com.example.app");
    assertThat(crashEvent.process().pid()).isEqualTo(12345);
    assertThat(crashEvent.process().category()).isEqualTo(ProcessCategory.FAILURE);
    assertThat(crashEvent.process().type()).isEqualTo(CrashType.ANDROID_RUNTIME);
    assertThat(crashEvent.crashLogs()).contains("FATAL EXCEPTION: main");
  }

  @Test
  public void process_multipleAndroidRuntimeCrashes_detectsAllCrashEvents() throws Exception {
    Path crashLogPath =
        Path.of(RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + MULTIPLE_CRASH_LOG_FILE));
    List<String> lines = Files.readAllLines(crashLogPath);

    for (String line : lines) {
      crashDetector.process(LogcatParser.parse(line).get());
    }

    ImmutableList<LogcatEvent> events = crashDetector.getEvents();
    assertThat(events).hasSize(2);
    assertThat(events.stream().map(e -> ((CrashEvent) e).process().name()))
        .containsExactly("com.example.app", "com.another.app");
  }

  @Test
  public void process_noCrash_noEvents() throws Exception {
    crashDetector.process(
        LogcatParser.parse("10-13 12:54:02.123  1234  5678 I ActivityManager: Some message").get());
    crashDetector.process(
        LogcatParser.parse("10-13 12:54:03.123  4321  8765 E NotAndroidRuntime: Some error").get());

    assertThat(crashDetector.getEvents()).isEmpty();
  }
}
