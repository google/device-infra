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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserState;
import com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidSwitchUserDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.AndroidSwitchUserDecorator.UserType;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidSwitchUserDecoratorSpec;
import java.util.List;
import javax.annotation.Nullable;

/** Validator for {@link AndroidSwitchUserDecorator} */
public class AndroidSwitchUserDecoratorValidator extends BaseValidator
    implements SpecConfigable<AndroidSwitchUserDecoratorSpec> {
  private static final int SDK_VERSION_MIN = 28;

  @Override
  public void validateEnv(Device device) throws MobileHarnessException, InterruptedException {
    @Nullable Integer sdkVersion = ((AndroidDevice) device).getSdkVersion();

    String error = null;
    if (sdkVersion == null) {
      error = "'sdk_version' dimension is not available";
    } else if (sdkVersion.intValue() < SDK_VERSION_MIN) {
      error =
          String.format(
              "AndroidSwitchUserDecorator only supports devices with sdkVersion >= %d, found %s",
              SDK_VERSION_MIN, sdkVersion);
    }

    if (error != null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SWITCH_USER_DECORATOR_NOT_SUPPORTED, error);
    }
  }

  @Override
  public List<String> validateJob(JobInfo job) throws InterruptedException {
    ImmutableList.Builder<String> errors = ImmutableList.builder();

    List<AndroidSwitchUserDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidSwitchUserDecorator"));
    } catch (MobileHarnessException e) {
      errors.add(e.getMessage());
      return errors.build();
    }

    for (AndroidSwitchUserDecoratorSpec spec : specs) {
      String error = validateSwitchUserParam(spec);
      if (error != null) {
        errors.add(error);
      }

      error = validateWaitStateparam(spec);
      if (error != null) {
        errors.add(error);
      }
    }

    return errors.build();
  }

  @Nullable
  private static String validateSwitchUserParam(AndroidSwitchUserDecoratorSpec spec) {
    String switchUserParam = spec.getSwitchUser();

    try {
      UserType unused = UserType.fromParam(switchUserParam);
      return null;
    } catch (IllegalArgumentException e) {
      return String.format(
          "%s: %s",
          AndroidSwitchUserDecoratorSpec.getDescriptor()
              .findFieldByNumber(AndroidSwitchUserDecoratorSpec.SWITCH_USER_FIELD_NUMBER)
              .getName(),
          e.getMessage());
    }
  }

  @Nullable
  private static String validateWaitStateparam(AndroidSwitchUserDecoratorSpec spec) {
    String waitStateParam = spec.getSwitchUserWaitState();
    AndroidUserState state = AndroidSwitchUserDecorator.convertWaitState(waitStateParam);
    if (state == AndroidUserState.STATE_UNKNOWN) {
      return String.format(
          "Unknown %s: %s",
          AndroidSwitchUserDecoratorSpec.getDescriptor()
              .findFieldByNumber(AndroidSwitchUserDecoratorSpec.SWITCH_USER_WAIT_STATE_FIELD_NUMBER)
              .getName(),
          waitStateParam);
    }
    return null;
  }
}
