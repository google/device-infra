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
import com.google.common.base.Suppliers;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.errorprone.annotations.Keep;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.function.Supplier;

/** Env validator for {@code AndroidDeviceSettingsDecorator}. */
public class AndroidDeviceSettingsDecoratorEnvValidator implements EnvValidator {

  private final Supplier<AndroidSystemSettingUtil> androidSystemSettingUtilSupplier;

  @Keep
  public AndroidDeviceSettingsDecoratorEnvValidator() {
    this(Suppliers.memoize(AndroidSystemSettingUtil::new));
  }

  @VisibleForTesting
  AndroidDeviceSettingsDecoratorEnvValidator(
      Supplier<AndroidSystemSettingUtil> androidSystemSettingUtilSupplier) {
    this.androidSystemSettingUtilSupplier = androidSystemSettingUtilSupplier;
  }

  @Override
  public void validate(Device device) throws MobileHarnessException, InterruptedException {
    if (!(device instanceof AndroidDevice)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_ENV_VALIDATOR_DEVICE_NOT_SUPPORTED,
          "Only support android devices.");
    }

    if (device.getClass().getSimpleName().equals("AndroidVirtualDevice")
        || device.getClass().getSimpleName().equals("OxygenDevice")) {
      return;
    }
    int sdkVersion =
        androidSystemSettingUtilSupplier.get().getDeviceSdkVersion(device.getDeviceId());
    if (sdkVersion < AndroidVersion.LOLLIPOP.getEndSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_ENV_VALIDATOR_DEVICE_NOT_SUPPORTED,
          "Only support device with API 22+.");
    }

    boolean isRooted = false;
    try {
      isRooted = ((AndroidDevice) device).isRooted();
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_ENV_VALIDATOR_DEVICE_NOT_SUPPORTED,
          "Failed to get rooted status.",
          e);
    }
    if (!isRooted) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_ENV_VALIDATOR_DEVICE_NOT_SUPPORTED,
          "Only support rooted device.");
    }
  }
}
