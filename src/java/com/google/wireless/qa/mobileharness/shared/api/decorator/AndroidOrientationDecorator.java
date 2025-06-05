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
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.platform.android.media.ScreenOrientation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidOrientationDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Locale;

/** Decorator for setting the orientation of the device when the test starts. */
@DecoratorAnnotation(help = "For setting screen orientation of the device when test starts.")
public class AndroidOrientationDecorator extends BaseDecorator
    implements AndroidOrientationDecoratorSpec {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidMediaUtil androidMediaUtil;

  public AndroidOrientationDecorator(Driver decorated, TestInfo testInfo) {
    this(decorated, testInfo, new AndroidMediaUtil());
  }

  @VisibleForTesting
  AndroidOrientationDecorator(
      Driver decorated, TestInfo testInfo, AndroidMediaUtil androidMediaUtil) {
    super(decorated, testInfo);
    this.androidMediaUtil = androidMediaUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    String orientationStr =
        jobInfo
            .params()
            .get(
                AndroidOrientationDecoratorSpec.PARAM_ORIENTATION,
                AndroidOrientationDecoratorSpec.DEFAULT_ORIENTATION)
            .toUpperCase(Locale.ROOT);
    ScreenOrientation orientation = ScreenOrientation.valueOf(orientationStr);
    testInfo.log().atInfo().alsoTo(logger).log("Rotate to %s", orientationStr);
    // Disables accelerometer rotation, otherwise we can not rotate the screen using commands.
    testInfo.log().atInfo().alsoTo(logger).log("Disable accelerometer rotation");
    androidMediaUtil.setAccelerometerRotation(deviceId, false);
    // Rotates to the target orientation.
    androidMediaUtil.rotateScreen(deviceId, orientation);

    // Starts the actual tests.
    getDecorated().run(testInfo);
  }
}
