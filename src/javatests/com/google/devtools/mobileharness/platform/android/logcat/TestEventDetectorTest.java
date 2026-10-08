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
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashedProcess;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.TestEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.TestEventType;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestEventDetectorTest {

  private static final ImmutableList<String> REPORT_AS_FAILURE_PACKAGES =
      ImmutableList.of(
          "com.google.samples.apps.my.topeka",
          "androidx.test.tools.crawler.crawlertest",
          "com.unity.app",
          "com.pairip.app");

  private TestEventDetector testEventDetector;

  @Before
  public void setUp() {
    MonitoringConfig monitoringConfig =
        new MonitoringConfig(REPORT_AS_FAILURE_PACKAGES, ImmutableList.of(), ImmutableList.of());
    testEventDetector = new TestEventDetector(monitoringConfig);
  }

  @Test
  public void process_startupTimes_detectsFirstDefaultAndFullyDrawnEvents() {
    processLines(
        ImmutableList.of(
            "02-05 18:59:50.962  3491  3491 I ActivityManager: Displayed"
                + " com.google.samples.apps.my.topeka/.QuizActivity: +128ms",
            "02-05 19:00:08.535  3491  3491 I ActivityManager: Displayed"
                + " androidx.test.tools.crawler.crawlertest/.MainActivity: +1s970ms (total"
                + " +1m50s136ms)",
            "02-05 19:00:08.536  3491  3491 I ActivityManager: Fully drawn"
                + " androidx.test.tools.crawler.crawlertest/.MainActivity: +1s970ms (total"
                + " +1m50s136ms)",
            "02-05 19:00:10.100  3491  3491 I ActivityManager: Fully drawn"
                + " com.google.samples.apps.my.topeka/.QuizActivity: +500ms"));

    assertThat(testEventDetector.getEvents())
        .containsExactly(
            new TestEvent(
                TestEventType.STARTUP_TIME_DEFAULT,
                3491,
                "ActivityManager",
                "Displayed com.google.samples.apps.my.topeka/.QuizActivity: +128ms"),
            new TestEvent(
                TestEventType.STARTUP_TIME_FULLY_DRAWN,
                3491,
                "ActivityManager",
                "Fully drawn androidx.test.tools.crawler.crawlertest/.MainActivity: +1s970ms (total"
                    + " +1m50s136ms)"))
        .inOrder();
  }

  @Test
  public void process_unityException_detectsUnityExceptionEvent() {
    processLines(
        ImmutableList.of(
            "05-23 20:00:29.000  1142  1150 I ActivityManager: Start proc"
                + " 12371:com.unity.app/u0a100 for activity com.unity.app/.MainActivity",
            "05-23 20:00:30.212 12371 12371 E Unity   : NullReferenceException: Object reference"
                + " not set to an instance of an object",
            "05-23 20:00:30.212 12371 12371 E Unity   :   at TestScript.Update () [0x0001a] in"
                + " <716fb7fd283d4f1294a9cfa91a8f4246>:0"));

    assertThat(testEventDetector.getEvents())
        .containsExactly(
            new TestEvent(
                TestEventType.UNITY_EXCEPTION,
                12371,
                "Unity",
                "NullReferenceException: Object reference not set to an instance of an object"));
  }

  @Test
  public void process_licensingProtectionTermination_detectsEvent() {
    processLines(
        ImmutableList.of(
            "05-13 14:00:59.000  1142  1150 I ActivityManager: Start proc"
                + " 1000:com.pairip.app/u0a101 for activity com.pairip.app/.MainActivity",
            "05-13 14:01:00.000  1000  1001 I ActivityTaskManager: Start Activity:"
                + " LicenseActivity",
            "05-13 14:01:01.000  1000  1001 I SomeOtherTag: Some other log line",
            "05-13 14:01:02.000  1000  1001 E SomeLongTag: BIND_DEKU_BROKER_SERVICE failed."));

    assertThat(testEventDetector.getEvents())
        .containsExactly(
            new TestEvent(
                TestEventType.LICENSING_PROTECTION_TERMINATION,
                1000,
                "SomeLongTag",
                "Start Activity: LicenseActivity"
                    + System.lineSeparator()
                    + "BIND_DEKU_BROKER_SERVICE failed."));
  }

  @Test
  public void process_unmonitoredPackageOrPid_ignoresEvents() {
    processLines(
        ImmutableList.of(
            "02-05 18:59:50.962  3491  3491 I ActivityManager: Displayed"
                + " com.unmonitored.app/.MainActivity: +128ms",
            "02-05 19:00:08.536  3491  3491 I ActivityManager: Fully drawn"
                + " com.unmonitored.app/.MainActivity: +1s970ms",
            "07-13 16:48:29.371  1142  1150 I ActivityManager: Start proc"
                + " 9999:com.unmonitored.app/u0a61 for activity com.unmonitored.app/.MainActivity",
            "05-23 20:00:30.212  9999  9999 E Unity   : NullReferenceException: Object reference"
                + " not set to an instance of an object",
            "05-13 14:01:00.000  9999  9999 I ActivityTaskManager: Start Activity:"
                + " LicenseActivity",
            "05-13 14:01:02.000  9999  9999 E SomeLongTag: BIND_DEKU_BROKER_SERVICE failed."));

    assertThat(testEventDetector.getEvents()).isEmpty();
  }

  @Test
  public void processCrashEvent_pairipJavaCrash_returnsAntiTamperingTerminationEvent() {
    String crashLogs =
        String.join(
            System.lineSeparator(),
            "FATAL EXCEPTION: main",
            "Process: com.zsyj.autotap.autoclicker, PID: 1000",
            "com.pairip.VMRunnerException: Security Check Exception",
            "\tat com.pairip.VMRunner.invoke(VMRunner.java:1)");
    CrashEvent crashEvent =
        new CrashEvent(
            new CrashedProcess(
                "com.zsyj.autotap.autoclicker",
                1000,
                ProcessCategory.FAILURE,
                CrashType.ANDROID_RUNTIME),
            crashLogs);

    assertThat(TestEventDetector.processCrashEvent(crashEvent))
        .hasValue(
            new TestEvent(
                TestEventType.ANTI_TAMPERING_TERMINATION, 1000, "AndroidRuntime", crashLogs));
  }

  @Test
  public void processCrashEvent_jiaguNativeCrash_returnsAntiTamperingTerminationEvent() {
    String crashLogs =
        String.join(
            System.lineSeparator(),
            "pid: 1000, tid: 1001, name: ocrtrans  >>> com.hawsoft.mobile.ocrtrans <<<",
            "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0",
            "backtrace:",
            "    #00 pc 0000000000012345 "
                + " /data/app/com.hawsoft.mobile.ocrtrans/lib/arm64/libjiagu.so");
    CrashEvent crashEvent =
        new CrashEvent(
            new CrashedProcess(
                "com.hawsoft.mobile.ocrtrans", 1000, ProcessCategory.FAILURE, CrashType.NATIVE),
            crashLogs);

    assertThat(TestEventDetector.processCrashEvent(crashEvent))
        .hasValue(
            new TestEvent(TestEventType.ANTI_TAMPERING_TERMINATION, 1000, "DEBUG", crashLogs));
  }

  @Test
  public void processCrashEvent_pairipNativeCrash_returnsAntiTamperingTerminationEvent() {
    String crashLogs =
        String.join(
            System.lineSeparator(),
            "pid: 2000, tid: 2001, name: example  >>> com.example.pairip <<<",
            "    #00 pc 0000000000012345 "
                + " /data/app/com.example.pairip/lib/arm64/libpairipcore.so");
    CrashEvent crashEvent =
        new CrashEvent(
            new CrashedProcess(
                "com.example.pairip", 2000, ProcessCategory.FAILURE, CrashType.NATIVE),
            crashLogs);

    assertThat(TestEventDetector.processCrashEvent(crashEvent))
        .hasValue(
            new TestEvent(TestEventType.ANTI_TAMPERING_TERMINATION, 2000, "DEBUG", crashLogs));
  }

  @Test
  public void processCrashEvent_regularCrashes_returnsEmpty() {
    CrashEvent regularJavaCrash =
        new CrashEvent(
            new CrashedProcess(
                "com.example.app", 1234, ProcessCategory.FAILURE, CrashType.ANDROID_RUNTIME),
            String.join(
                System.lineSeparator(),
                "FATAL EXCEPTION: main",
                "Process: com.example.app, PID: 1234",
                "java.lang.NullPointerException"));
    CrashEvent regularNativeCrash =
        new CrashEvent(
            new CrashedProcess("com.example.app", 1234, ProcessCategory.FAILURE, CrashType.NATIVE),
            "pid: 1234, tid: 1234, name: app  >>> com.example.app <<<");
    CrashEvent anrCrash =
        new CrashEvent(
            new CrashedProcess("com.example.app", 1234, ProcessCategory.FAILURE, CrashType.ANR),
            "ANR in com.example.app\nPID: 1234\nReason: Input dispatching timed out");

    assertThat(TestEventDetector.processCrashEvent(regularJavaCrash)).isEmpty();
    assertThat(TestEventDetector.processCrashEvent(regularNativeCrash)).isEmpty();
    assertThat(TestEventDetector.processCrashEvent(anrCrash)).isEmpty();
  }

  @Test
  public void processCrashEvent_nonFailureCategory_returnsEmpty() {
    CrashEvent ignoredJavaCrash =
        new CrashEvent(
            new CrashedProcess(
                "com.example.ignored", 1234, ProcessCategory.IGNORED, CrashType.ANDROID_RUNTIME),
            "com.pairip.VMRunnerException: Security Check Exception");
    CrashEvent otherNativeCrash =
        new CrashEvent(
            new CrashedProcess("com.example.other", 1234, ProcessCategory.OTHER, CrashType.NATIVE),
            "/data/app/com.example.other/lib/arm64/libpairipcore.so");

    assertThat(TestEventDetector.processCrashEvent(ignoredJavaCrash)).isEmpty();
    assertThat(TestEventDetector.processCrashEvent(otherNativeCrash)).isEmpty();
  }

  @Test
  public void process_benignLogsAndRegularCrashes_returnsEmpty() {
    processLines(
        ImmutableList.of(
            "05-13 14:01:00.000  1000  1001 I ActivityManager: granted { execute } for"
                + " path=/data/app/com.hawsoft.mobile.ocrtrans/lib/arm64/libjiagu_64.so",
            "05-13 14:01:01.000  1234  1234 E AndroidRuntime: FATAL EXCEPTION: main",
            "05-13 14:01:01.000  1234  1234 E AndroidRuntime: java.lang.NullPointerException",
            "05-13 14:01:02.000   506   506 F DEBUG   : pid: 1234, tid: 1234, name: app  >>>"
                + " com.example.app <<<"));

    assertThat(testEventDetector.getEvents()).isEmpty();
  }

  private void processLines(List<String> lines) {
    for (String line : lines) {
      LogcatParser.parse(line).ifPresent(testEventDetector::process);
    }
  }
}
