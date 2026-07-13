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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

/** Driver decorator for taking screenshot on Android device when test finish. */
@DecoratorAnnotation(help = "For taking screenshot when the test finish.")
public class AndroidScreenshotDecorator extends LifecycleDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @ParamAnnotation(
      required = false,
      help = "Whether to take screenshot when test pass. By default, it is true.")
  public static final String PARAM_SCREENSHOT_ON_PASS = "screenshot_on_pass";

  /**
   * The path of temp screenshot file on device, which will be cleaned after being pulled by host.
   */
  @VisibleForTesting static final String TEMP_SCREEN_SHOT_PATH = "/data/local/tmp/";

  /** Util for controlling Android files. */
  private final AndroidFileUtil androidFileUtil;

  /** Util for controlling Android media files. */
  private final AndroidMediaUtil androidMediaUtil;

  /** Util for host side file operations. */
  private final LocalFileUtil fileUtil;

  public AndroidScreenshotDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new AndroidFileUtil(),
        new AndroidMediaUtil(),
        new LocalFileUtil());
  }

  @VisibleForTesting
  AndroidScreenshotDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidFileUtil androidFileUtil,
      AndroidMediaUtil androidMediaUtil,
      LocalFileUtil fileUtil) {
    super(decoratedDriver, testInfo);
    this.androidFileUtil = androidFileUtil;
    this.androidMediaUtil = androidMediaUtil;
    this.fileUtil = fileUtil;
  }

  @Override
  protected void setUp(TestInfo testInfo) throws MobileHarnessException, InterruptedException {}

  @Override
  protected void tearDown(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = testInfo.jobInfo();
    boolean screenshotOnPass = true;
    String value = jobInfo.params().get(PARAM_SCREENSHOT_ON_PASS);
    if (value != null && value.equalsIgnoreCase("false")) {
      screenshotOnPass = false;
    }

    // Checks whether to take screenshot
    if (screenshotOnPass || testInfo.resultWithCause().get().type() != TestResult.PASS) {
      String deviceId = getDevice().getDeviceId();
      Timestamp timestamp = new Timestamp(System.currentTimeMillis());

      // Generates related file path
      String screensShotFilePathOnDevice =
          PathUtil.join(
              TEMP_SCREEN_SHOT_PATH
                  + new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss").format(timestamp)
                  + ".png");
      // Takes screenshot on device and pulls to test gen file dir
      try {
        String desFileDirOnHost = testInfo.getGenFileDir();
        String desFilePathOnHost = PathUtil.join(desFileDirOnHost, "test_end_screenshot.png");

        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Save screen shot on device: %s", screensShotFilePathOnDevice);
        androidMediaUtil.takeScreenshot(deviceId, screensShotFilePathOnDevice);
        fileUtil.prepareDir(desFileDirOnHost);
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Pull screen shot to host machine: %s", desFilePathOnHost);
        String pullMsg =
            androidFileUtil.pull(deviceId, screensShotFilePathOnDevice, desFilePathOnHost);
        testInfo.log().atInfo().alsoTo(logger).log("%s", pullMsg);
        fileUtil.grantFileOrDirFullAccess(desFilePathOnHost);
      } catch (MobileHarnessException e) {
        testInfo.warnings().addAndLog(e, logger);
      } finally {
        // Always removes the files on the device.
        try {
          androidFileUtil.removeFiles(deviceId, screensShotFilePathOnDevice);
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Failed to remove tmp screen shot on %s: %s", deviceId, e.getMessage());
        }
      }
    }
  }
}
