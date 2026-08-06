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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.util.Timer;
import java.util.TimerTask;
import javax.inject.Inject;

/** An {@link AndroidVideoRecorder} that captures video by taking periodic screenshots. */
public class ScreenshotRecorder implements AndroidVideoRecorder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final Adb adb;
  private final AndroidFileUtil androidFileUtil;
  private final LocalFileUtil localFileUtil;

  private Timer screenshotTimer;
  private int frameIndex = 0;
  private String deviceFolder;

  @Inject
  ScreenshotRecorder(Adb adb, AndroidFileUtil androidFileUtil, LocalFileUtil localFileUtil) {
    this.adb = adb;
    this.androidFileUtil = androidFileUtil;
    this.localFileUtil = localFileUtil;
  }

  @Override
  public void start(TestInfo testInfo, Device device, AndroidVideoDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Starting screenshot recorder...");
    deviceFolder = "/data/local/tmp/mh_screenshots_" + testInfo.locator().getId();
    try {
      androidFileUtil.makeDirectory(device.getDeviceId(), deviceFolder);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw e;
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to make directory for screenshots.");
    }

    int fps = spec.hasFps() && spec.getFps() > 0 ? spec.getFps() : 5;
    long period = 1000 / fps;
    screenshotTimer = new Timer("ScreenshotTimer");
    screenshotTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            try {
              String filename = String.format("%s/screenshot_%04d.png", deviceFolder, ++frameIndex);
              var unused = adb.runShell(device.getDeviceId(), "screencap -p " + filename);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              logger.atWarning().withCause(e).log("Interrupted while capturing screenshot.");
            } catch (MobileHarnessException e) {
              logger.atWarning().withCause(e).log("Failed to capture screenshot.");
            }
          }
        },
        0,
        period);
  }

  @Override
  public void stop(TestInfo testInfo, Device device)
      throws MobileHarnessException, InterruptedException {
    if (screenshotTimer != null) {
      screenshotTimer.cancel();
      screenshotTimer = null;
    }
    logger.atInfo().log("Stopping screenshot recorder and pulling files...");
    String hostFolder = testInfo.getGenFileDir() + "/screenshots";
    try {
      localFileUtil.prepareDir(hostFolder);
      androidFileUtil.pull(device.getDeviceId(), deviceFolder + "/.", hostFolder);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw e;
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to pull screenshots.");
    }
    try {
      androidFileUtil.removeFiles(device.getDeviceId(), deviceFolder);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw e;
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to remove screenshots on device.");
    }
  }
}
