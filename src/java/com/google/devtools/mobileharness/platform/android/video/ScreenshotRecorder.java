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

package com.google.devtools.mobileharness.platform.android.video;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.inject.Inject;

/** An {@link AndroidVideoRecorder} that captures video by taking periodic screenshots. */
public class ScreenshotRecorder implements AndroidVideoRecorder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final Duration SCREENSHOT_PERIOD =
      Duration.ofMillis(500); // Half a second intervals
  private final Adb adb;
  private final AndroidFileUtil androidFileUtil;
  private final LocalFileUtil localFileUtil;
  private final Sleeper sleeper;

  private Timer screenshotTimer;
  private int frameIndex = 0;
  private String deviceId;
  private Path genFileDir;
  private String deviceFolder;

  @Inject
  ScreenshotRecorder(
      Adb adb, AndroidFileUtil androidFileUtil, LocalFileUtil localFileUtil, Sleeper sleeper) {
    this.adb = adb;
    this.androidFileUtil = androidFileUtil;
    this.localFileUtil = localFileUtil;
    this.sleeper = sleeper;
  }

  @Override
  public void start(String deviceId, String testId, Path genFileDir, AndroidVideoDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Starting screenshot recorder...");
    this.deviceId = deviceId;
    this.genFileDir = genFileDir;
    deviceFolder = "/data/local/tmp/mh_screenshots_" + testId;
    androidFileUtil.makeDirectory(deviceId, deviceFolder);

    screenshotTimer = new Timer("ScreenshotTimer");
    screenshotTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            try {
              String filename = String.format("%s/screenshot_%08d.png", deviceFolder, ++frameIndex);
              var unused = adb.runShell(deviceId, "screencap -p " + filename);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              logger.atWarning().withCause(e).log("Interrupted while capturing screenshot.");
              cancel();
            } catch (MobileHarnessException e) {
              logger.atWarning().withCause(e).log("Failed to capture screenshot.");
            }
          }
        },
        0,
        SCREENSHOT_PERIOD.toMillis());
  }

  @Override
  public List<Path> stop() throws MobileHarnessException, InterruptedException {
    if (screenshotTimer != null) {
      screenshotTimer.cancel();
      screenshotTimer = null;
      sleeper.sleep(Duration.ofSeconds(2));
    }
    logger.atInfo().log("Stopping screenshot recorder and pulling files...");
    Path hostFolder = genFileDir.resolve("screenshots");
    localFileUtil.prepareDir(hostFolder.toString());
    // The "/." suffix ensures files are pulled directly into hostFolder rather than creating
    // a redundant nested subdirectory (e.g., hostFolder/mh_screenshots_.../).
    androidFileUtil.pull(deviceId, deviceFolder + "/.", hostFolder.toString());
    androidFileUtil.removeFiles(deviceId, deviceFolder);

    File[] files = localFileUtil.listFilesOrDirs(hostFolder.toString());
    if (files != null) {
      Arrays.sort(files);
      return Arrays.stream(files).map(File::toPath).collect(toImmutableList());
    }
    return ImmutableList.of();
  }
}
