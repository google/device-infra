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
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Class to check if a crash/ANR dialog appears on the screen. */
public class CrashDialogDetector {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration CMD_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration RETRY_SLEEP_SECS = Duration.ofSeconds(1);
  private static final Duration RENDER_DELAY = Duration.ofSeconds(1);

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

  private record DetectedDialog(String packageName, Optional<String> screenshotPath) {}

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicReference<DetectedDialog> detectedDialog = new AtomicReference<>();
  private final Adb adb;

  private final Sleeper sleeper;

  @Inject
  CrashDialogDetector(Adb adb, Sleeper sleeper) {
    this.adb = adb;
    this.sleeper = sleeper;
  }

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
      Optional<String> candidatePackage = getCrashDialog(testInfo, device);
      if (candidatePackage.isEmpty()) {
        return;
      }
      String pkgName = candidatePackage.get();
      testInfo.log().atWarning().alsoTo(logger).log("Crash dialog detected: %s", pkgName);
      if (packagesToIgnore.contains(pkgName)) {
        return;
      }
      // Record the dialog before the render delay so an interrupted sleep or failed screencap still
      // reports the dialog package.
      detectedDialog.set(new DetectedDialog(pkgName, Optional.empty()));
      // Small delay to ensure the dialog is fully rendered.
      sleeper.sleep(RENDER_DELAY);
      String deviceScreenshotPath = generateScreenshot(device, pkgName);
      detectedDialog.set(new DetectedDialog(pkgName, Optional.of(deviceScreenshotPath)));
    } catch (MobileHarnessException | InterruptedException e) {
      if (MoreThrowables.isInterruption(e)) {
        Thread.currentThread().interrupt();
      }
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Error scanning for crash dialog.");
    } finally {
      // Once a non-ignored dialog is recorded, keep `running` set so the first dialog wins.
      if (detectedDialog.get() == null) {
        running.set(false);
      }
    }
  }

  /** Get the path of the crash dialog screenshot. */
  public Optional<String> crashDialogScreenshot() {
    DetectedDialog dialog = detectedDialog.get();
    return dialog == null ? Optional.empty() : dialog.screenshotPath();
  }

  /** Get the packageName/process causing the crash dialog. */
  public Optional<String> crashDialogPackageName() {
    return Optional.ofNullable(detectedDialog.get()).map(DetectedDialog::packageName);
  }

  /**
   * Scans the current UI windows {@link #MAX_ATTEMPTS} times at intervals of {@link
   * #RETRY_SLEEP_SECS} to look for the crash dialog.
   */
  private Optional<String> getCrashDialog(TestInfo testInfo, Device device)
      throws InterruptedException {
    for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      var lineCallback = new DumpsysWindowLineCallback();
      try {
        var unused =
            adb.runShell(device.getDeviceId(), DUMPSYS_WINDOW_CMD, CMD_TIMEOUT, lineCallback);
        if (lineCallback.packageName.isPresent()) {
          return lineCallback.packageName;
        }
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log(
                "Error running '%s' on attempt %d/%d.",
                DUMPSYS_WINDOW_CMD, attempt + 1, MAX_ATTEMPTS);
      }
      if (attempt < MAX_ATTEMPTS - 1) {
        sleeper.sleep(RETRY_SLEEP_SECS);
      }
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
      if (CURRENT_FOCUS_DIALOG_PATTERN.matcher(trimmedLine).matches()) {
        var crashMatcher = CRASH_DIALOG_PATTERN.matcher(trimmedLine);
        if (crashMatcher.matches()) {
          packageName = Optional.of(crashMatcher.group(1));
          return Response.stopReadingOutput();
        }
        var anrMatcher = ANR_DIALOG_PATTERN.matcher(trimmedLine);
        if (anrMatcher.matches()) {
          packageName = Optional.of(anrMatcher.group(1));
          return Response.stopReadingOutput();
        }
      }
      return Response.empty();
    }
  }
}
