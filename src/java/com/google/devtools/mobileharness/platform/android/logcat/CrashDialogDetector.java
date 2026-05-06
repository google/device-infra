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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Class to check if a crash/ANR dialog appears on the screen. */
public class CrashDialogDetector {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration CMD_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration RETRY_SLEEP_SECS = Duration.ofSeconds(1);

  // Dump the current window details of the device.
  private static final String DUMPSYS_WINDOW_CMD = "dumpsys window";
  // Template for the crash dialog.
  private static final Pattern CURRENT_FOCUS_DIALOG_PATTERN =
      Pattern.compile("^mCurrentFocus=.*Application.*");
  // Indicates an android runtime or native crash.
  private static final Pattern CRASH_DIALOG_PATTERN =
      Pattern.compile("^mCurrentFocus=Window\\{.+ .+ Application Error: (.+)}");
  // Indicates an ANR happened.
  private static final Pattern ANR_DIALOG_PATTERN =
      Pattern.compile("^mCurrentFocus=Window\\{.+ .+ Application Not Responding: (.+)}");

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final Adb adb;

  private final Sleeper sleeper;

  @Inject
  CrashDialogDetector(Adb adb, Sleeper sleeper) {
    this.adb = adb;
    this.sleeper = sleeper;
  }

  private Optional<String> screenshotPath = Optional.empty();
  private Optional<String> packageName = Optional.empty();

  /** Check if a crash dialog appears on the screen. */
  void scan(TestInfo testInfo, Device device, List<String> packagesToIgnore) {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("----------------- Starting crash dialog detection --------------------");
    try {
      packageName = getCrashDialog(device);
      if (packageName.isEmpty()) {
        running.set(false);
        return;
      }
      testInfo.log().atWarning().alsoTo(logger).log("Crash dialog detected: %s", packageName);
      if (packagesToIgnore.contains(packageName.get())) {
        // Reset internal state.
        running.set(false);
        packageName = Optional.empty();
        return;
      }
      // Small delay to ensure the dialog is fully rendered.
      sleeper.sleep(Duration.ofSeconds(1));
      screenshotPath = Optional.of(generateScreenshot(device, packageName.get()));
    } catch (MobileHarnessException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      testInfo.log().atWarning().alsoTo(logger).log("Error scanning for crash dialog: %s", e);
    }
  }

  /** Get the path of the crash dialog screenshot. */
  public Optional<String> crashDialogScreenshot() {
    return screenshotPath;
  }

  /** Get the packageName/process causing the crash dialog. */
  public Optional<String> crashDialogPackageName() {
    return packageName;
  }

  /**
   * Scans the current UI windows [MAX_ATTEMPTS] times at intervals of [RETRY_DELAY_MS] to look for
   * the crash dialog.
   */
  private Optional<String> getCrashDialog(Device device)
      throws InterruptedException, MobileHarnessException {
    for (var i = 0; i < MAX_ATTEMPTS; i++) {
      var lineCallback = new DumpsysWindowLineCallback();
      var unused =
          adb.runShell(device.getDeviceId(), DUMPSYS_WINDOW_CMD, CMD_TIMEOUT, lineCallback);
      if (lineCallback.packageName.isPresent()) {
        return lineCallback.packageName;
      }
      sleeper.sleep(RETRY_SLEEP_SECS);
    }
    return Optional.empty();
  }

  private String generateScreenshot(Device device, String pkgName)
      throws InterruptedException, MobileHarnessException {
    var screenshotFileName = String.format("crash-dialog-%s.png", pkgName.replace('.', '_'));
    String devicePath = String.format("/data/local/tmp/%s", screenshotFileName);
    var unused = adb.runShell(device.getDeviceId(), "screencap -p " + devicePath, CMD_TIMEOUT);
    return devicePath;
  }

  private static class DumpsysWindowLineCallback implements LineCallback {
    private Optional<String> packageName = Optional.empty();

    @Override
    public Response onLine(String line) {
      var trimmedLine = line.trim();
      if (trimmedLine.matches(CURRENT_FOCUS_DIALOG_PATTERN.pattern())) {
        var crashMatcher = CRASH_DIALOG_PATTERN.matcher(trimmedLine);
        if (crashMatcher.matches()) {
          packageName = Optional.of(crashMatcher.group(1));
          return Response.empty();
        }
        var anrMatcher = ANR_DIALOG_PATTERN.matcher(trimmedLine);
        if (anrMatcher.matches()) {
          packageName = Optional.of(anrMatcher.group(1));
        }
      }
      return Response.empty();
    }
  }
}
