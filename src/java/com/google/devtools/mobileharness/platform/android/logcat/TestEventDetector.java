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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.CrashEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.TestEvent;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.TestEventType;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatParser.LogcatLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link LineProcessor} that detects {@link TestEvent}s in logcat such as app startup times,
 * process starts, Unity exceptions, and licensing/anti-tampering terminations.
 */
public class TestEventDetector implements LineProcessor {

  private static final String ACTIVITY_MANAGER_TAG = "ActivityManager";
  private static final String ANDROID_RUNTIME_TAG = "AndroidRuntime";
  private static final String NATIVE_CRASH_TAG = "DEBUG";
  private static final String UNITY_TAG = "Unity";

  private static final String LICENSE_ACTIVITY_SIGNATURE = "LicenseActivity";
  private static final String BIND_DEKU_SERVICE_SIGNATURE = "BIND_DEKU_BROKER_SERVICE";
  private static final String VMRUNNER_EXCEPTION_SIGNATURE =
      "com.pairip.VMRunnerException: Security Check Exception";
  private static final String JIAGU_SIGNATURE = "libjiagu";
  private static final String PAIRIP_NATIVE_SIGNATURE = "libpairip";

  private static final Pattern START_TIME_DEFAULT_PATTERN =
      Pattern.compile(
          "Displayed (\\S+)/(\\S+): \\+(?<defaultTime>[\\dms]*\\d+ms)( \\(total \\+"
              + "(?<totalTime>[\\dms]*\\d+ms)\\))?");

  private static final Pattern START_TIME_FULLY_DRAWN_PATTERN =
      Pattern.compile(
          "Fully drawn (\\S+)/(\\S+): \\+(?<defaultTime>[\\dms]*\\d+ms)( \\(total \\+"
              + "(?<totalTime>[\\dms]*\\d+ms)\\))?");

  private static final Pattern START_PROC_PATTERN =
      Pattern.compile("Start proc (?<pid>\\d+):(?<package>[^/]+)/.*");

  private static final Pattern UNITY_EXCEPTION_PATTERN =
      Pattern.compile("(?<exception>\\w+Exception): (?<message>.*)");

  private final List<TestEvent> detectedEvents = new ArrayList<>();

  private LogcatLine licenseActivityLine = null;

  public TestEventDetector() {}

  @Override
  public void process(LogcatLine line) {
    processLicensingProtectionTermination(line);
    processStartupTime(line);
    processStartProc(line);
    processUnityException(line);
  }

  /**
   * Processes a {@link CrashEvent} and returns a {@link TestEvent} if the crash corresponds to an
   * anti-tampering termination.
   */
  public Optional<TestEvent> processCrashEvent(CrashEvent crashEvent) {
    return switch (crashEvent.process().type()) {
      case ANDROID_RUNTIME ->
          crashEvent.crashLogs().contains(VMRUNNER_EXCEPTION_SIGNATURE)
              ? Optional.of(
                  new TestEvent(
                      TestEventType.ANTI_TAMPERING_TERMINATION,
                      crashEvent.process().pid(),
                      ANDROID_RUNTIME_TAG,
                      crashEvent.crashLogs()))
              : Optional.empty();
      case NATIVE ->
          crashEvent.crashLogs().contains(JIAGU_SIGNATURE)
                  || crashEvent.crashLogs().contains(PAIRIP_NATIVE_SIGNATURE)
              ? Optional.of(
                  new TestEvent(
                      TestEventType.ANTI_TAMPERING_TERMINATION,
                      crashEvent.process().pid(),
                      NATIVE_CRASH_TAG,
                      crashEvent.crashLogs()))
              : Optional.empty();
      case ANR, UNKNOWN -> Optional.empty();
    };
  }

  private void processLicensingProtectionTermination(LogcatLine line) {
    if (line.message().contains(LICENSE_ACTIVITY_SIGNATURE)) {
      licenseActivityLine = line;
    }
    if (licenseActivityLine != null && line.message().contains(BIND_DEKU_SERVICE_SIGNATURE)) {
      String logLines =
          licenseActivityLine.equals(line)
              ? line.message()
              : licenseActivityLine.message() + System.lineSeparator() + line.message();
      detectedEvents.add(
          new TestEvent(
              TestEventType.LICENSING_PROTECTION_TERMINATION, line.pid(), line.tag(), logLines));
      licenseActivityLine = null;
    }
  }

  private void processStartupTime(LogcatLine line) {
    if (START_TIME_DEFAULT_PATTERN.matcher(line.message()).matches()) {
      detectedEvents.add(
          new TestEvent(
              TestEventType.STARTUP_TIME_DEFAULT, line.pid(), line.tag(), line.message()));
      return;
    }
    if (START_TIME_FULLY_DRAWN_PATTERN.matcher(line.message()).matches()) {
      detectedEvents.add(
          new TestEvent(
              TestEventType.STARTUP_TIME_FULLY_DRAWN, line.pid(), line.tag(), line.message()));
    }
  }

  private void processStartProc(LogcatLine line) {
    if (!line.tag().equals(ACTIVITY_MANAGER_TAG)) {
      return;
    }
    Matcher matcher = START_PROC_PATTERN.matcher(line.message());
    if (matcher.matches()) {
      int targetPid = Integer.parseInt(matcher.group("pid"));
      detectedEvents.add(
          new TestEvent(TestEventType.START_PROC, targetPid, line.tag(), line.message()));
    }
  }

  private void processUnityException(LogcatLine line) {
    if (!line.tag().equals(UNITY_TAG)) {
      return;
    }
    if (UNITY_EXCEPTION_PATTERN.matcher(line.message()).matches()) {
      detectedEvents.add(
          new TestEvent(TestEventType.UNITY_EXCEPTION, line.pid(), line.tag(), line.message()));
    }
  }

  @Override
  public ImmutableList<LogcatEvent> getEvents() {
    return ImmutableList.copyOf(detectedEvents);
  }
}
