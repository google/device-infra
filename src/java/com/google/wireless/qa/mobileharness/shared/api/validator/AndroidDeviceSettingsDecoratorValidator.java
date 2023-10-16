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

package com.google.wireless.qa.mobileharness.shared.api.validator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.errorprone.annotations.Keep;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDeviceSettingsDecoratorSpec;
import java.util.List;
import java.util.function.Supplier;

/** Validator for {@code AndroidDeviceSettingsDecorator}. */
public class AndroidDeviceSettingsDecoratorValidator extends BaseValidator
    implements SpecConfigable<AndroidDeviceSettingsDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Supplier<AndroidSystemSettingUtil> androidSystemSettingUtilSupplier;

  @Keep
  public AndroidDeviceSettingsDecoratorValidator() {
    this(Suppliers.memoize(AndroidSystemSettingUtil::new));
  }

  @VisibleForTesting
  AndroidDeviceSettingsDecoratorValidator(
      Supplier<AndroidSystemSettingUtil> androidSystemSettingUtilSupplier) {
    this.androidSystemSettingUtilSupplier = androidSystemSettingUtilSupplier;
  }

  @Override
  public void validateEnv(Device device) throws MobileHarnessException, InterruptedException {
    if (!(device instanceof AndroidDevice)) {
      throw new MobileHarnessException(
          ErrorCode.DEVICE_NOT_SUPPORTED, "Only support android devices.");
    }

    if (device.getClass().getSimpleName().equals("AndroidVirtualDevice")
        || device.getClass().getSimpleName().equals("OxygenDevice")) {
      return;
    }
    int sdkVersion =
        androidSystemSettingUtilSupplier.get().getDeviceSdkVersion(device.getDeviceId());
    if (sdkVersion < AndroidVersion.LOLLIPOP.getEndSdkVersion()) {
      throw new MobileHarnessException(
          ErrorCode.ANDROID_DEVICE_TOO_OLD, "Only support device with API 22+.");
    }

    if (!((AndroidDevice) device).isRooted()) {
      throw new MobileHarnessException(
          ErrorCode.DEVICE_NOT_SUPPORTED, "Only support rooted device.");
    }
  }

  @Override
  public List<String> validateJob(JobInfo job) throws InterruptedException {
    ImmutableList.Builder<String> errors = ImmutableList.builder();

    List<AndroidDeviceSettingsDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidDeviceSettingsDecorator"));
    } catch (MobileHarnessException e) {
      errors.add(e.getMessage());
      return errors.build();
    }

    for (AndroidDeviceSettingsDecoratorSpec spec : specs) {
      try {
        validateSpec(spec);

        if (spec.getEnforceCpuStatus() || spec.getStopInterferingServices()) {
          job.log()
              .atWarning()
              .alsoTo(logger)
              .log(
                  "Enforce cpu status and stop interfering service may not work properly on your"
                      + " device. Please use at your own risk.");
        }

        if (!spec.getCpuRuntimeFreqList().isEmpty()) {
          job.log()
              .atWarning()
              .alsoTo(logger)
              .log(
                  "cpu runtime frequency lock may not work properly on your device. Please use at"
                      + " your own risk. It is recommended to use AndroidPerformanceLockDecorator"
                      + " if you are not sure about the behavior.");
        }
      } catch (MobileHarnessException e) {
        errors.add(e.getMessage());
      }
    }

    return errors.build();
  }

  /** Validates {@code spec}. Throws exceptions if it contains any error. */
  public static void validateSpec(AndroidDeviceSettingsDecoratorSpec spec)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    if (spec.hasScreenBrightness()
        && !(spec.hasScreenAdaptiveBrightness() && !spec.getScreenAdaptiveBrightness())) {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_SETTINGS_CONFLICT,
          "screen_adaptive_brightness must be set to false explicitly while screen_brightness "
              + "is set.");
    }
    if (spec.hasScreenAlwaysOn()
        && spec.hasScreenTimeoutSec()
        && (spec.getScreenAlwaysOn() && spec.getScreenTimeoutSec() > 0)) {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_SETTINGS_CONFLICT,
          "screen_always_on with value true and screen_timeout_sec cannot be set at the same"
              + " time.");
    }
  }
}
