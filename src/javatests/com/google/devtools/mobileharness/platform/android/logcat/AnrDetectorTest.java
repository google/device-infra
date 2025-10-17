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
public final class AnrDetectorTest {

  private static final String TEST_DATA_PREFIX =
      "javatests/com/google/devtools/mobileharness/platform/android/logcat/testdata/";
  private static final String ANR_LOG_FILE = "anr_crash.txt";

  private AnrDetector anrDetector;

  @Before
  public void setUp() {
    MonitoringConfig config =
        new MonitoringConfig(
            ImmutableList.of("com.app.anr", "com.app.anr2"),
            ImmutableList.of(),
            ImmutableList.of());
    anrDetector = new AnrDetector(config);
  }

  @Test
  public void process_anrCrash_detectsCrashEvent() throws Exception {
    String crashLogPath = RunfilesUtil.getRunfilesLocation(TEST_DATA_PREFIX + ANR_LOG_FILE);
    List<String> lines = Files.readAllLines(Path.of(crashLogPath));

    for (String line : lines) {
      anrDetector.process(LogcatParser.parse(line).get());
    }

    ImmutableList<LogcatEvent> events = anrDetector.getEvents();
    assertThat(events).hasSize(2);

    CrashEvent crashEvent1 = (CrashEvent) events.get(0);
    assertThat(crashEvent1.process().name()).isEqualTo("com.app.anr");
    assertThat(crashEvent1.process().pid()).isEqualTo(12345);
    assertThat(crashEvent1.process().category()).isEqualTo(ProcessCategory.FAILURE);
    assertThat(crashEvent1.process().type()).isEqualTo(CrashType.ANR);
    assertThat(crashEvent1.crashLogs()).contains("Reason: Test ANR reason 1");

    CrashEvent crashEvent2 = (CrashEvent) events.get(1);
    assertThat(crashEvent2.process().name()).isEqualTo("com.app.anr2");
    assertThat(crashEvent2.process().pid()).isEqualTo(67890);
    assertThat(crashEvent2.process().category()).isEqualTo(ProcessCategory.FAILURE);
    assertThat(crashEvent2.process().type()).isEqualTo(CrashType.ANR);
    assertThat(crashEvent2.crashLogs()).contains("Reason: Test ANR reason 2");
  }

  @Test
  public void process_noCrash_noEvents() throws Exception {
    anrDetector.process(
        LogcatParser.parse("10-13 12:54:02.123  1234  5678 I NotActivityManager: Some message")
            .get());
    anrDetector.process(
        LogcatParser.parse("10-13 12:54:03.123  4321  8765 E ActivityManager: Some other error")
            .get());

    assertThat(anrDetector.getEvents()).isEmpty();
  }
}
