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

package com.google.wireless.qa.mobileharness.shared.api.validator.env;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/** Validator for the {@code AndroidHdVideoDecorator}. */
public class AndroidHdVideoDecoratorEnvValidator extends AndroidDeviceVersionEnvValidator {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Android supports screenrecord command since API level 19. */
  @VisibleForTesting static final int VERSION_REQUIREMENT = 19;

  /**
   * Some devices do not come with the screenrecord command, despite using a higher API level, so we
   * do extra validation via `$ adb shell`.
   */
  @VisibleForTesting static final String ADB_SHELL_VALIDATE = "screenrecord --help";

  private final Adb adb;

  public AndroidHdVideoDecoratorEnvValidator() {
    this(new Adb());
  }

  public AndroidHdVideoDecoratorEnvValidator(Adb adb) {
    super(VERSION_REQUIREMENT);
    this.adb = adb;
  }

  @Override
  public void validate(Device device) throws MobileHarnessException, InterruptedException {
    super.validate(device);
    try {
      String output = adb.runShell(device.getDeviceId(), ADB_SHELL_VALIDATE);
      logger.atInfo().log("'%s' output: %s", ADB_SHELL_VALIDATE, output);
    } catch (MobileHarnessException e) {
      logger.atWarning().log("Failed to run screenrecord: %s.", e.getMessage());
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_HD_VIDEO_DECORATOR_ENV_VALIDATOR_DEVICE_NOT_SUPPORTED,
          String.format("Device %s does not support 'screenrecord' command.", device.getDeviceId()),
          e);
    }
  }
}
