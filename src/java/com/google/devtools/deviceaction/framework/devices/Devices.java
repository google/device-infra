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

package com.google.devtools.deviceaction.framework.devices;

import com.google.common.base.Ascii;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.BundletoolUtil;
import com.google.devtools.deviceaction.framework.proto.AndroidPhoneSpec;
import com.google.devtools.deviceaction.framework.proto.Operand;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;

/** A utility class for {@link Device}. */
public class Devices {

  private static final String DELIMITER = "_";
  private static final String CONNECTOR = "-";
  private static final String SPACE = "\\s+";
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidSystemStateUtil androidSystemStateUtil;
  private final BundletoolUtil bundletoolUtil;
  private final Sleeper sleeper;

  public Devices(
      AndroidAdbUtil androidAdbUtil,
      AndroidFileUtil androidFileUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      BundletoolUtil bundletoolUtil,
      Sleeper sleeper) {
    this.androidAdbUtil = androidAdbUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.bundletoolUtil = bundletoolUtil;
    this.sleeper = sleeper;
  }

  public String getDeviceKey(Operand operand) throws DeviceActionException, InterruptedException {
    switch (operand.getDeviceType()) {
      case ANDROID_PHONE:
        try {
          String brand = androidAdbUtil.getProperty(operand.getUuid(), AndroidProperty.BRAND);
          String model = androidAdbUtil.getProperty(operand.getUuid(), AndroidProperty.MODEL);
          int sdk = androidAdbUtil.getIntProperty(operand.getUuid(), AndroidProperty.SDK_VERSION);
          String buildType =
              androidAdbUtil.getProperty(operand.getUuid(), AndroidProperty.BUILD_TYPE);
          return Ascii.toLowerCase(
              String.join(
                      DELIMITER,
                      brand,
                      model,
                      AndroidVersion.toCodeNameInitial(sdk).orElseThrow(),
                      buildType)
                  .replaceAll(SPACE, CONNECTOR));
        } catch (MobileHarnessException e) {
          throw new DeviceActionException(e, "Failed to get property");
        }
      default:
        throw new DeviceActionException(
            "ILLEGAL_DEVICE_TYPE",
            ErrorType.CUSTOMER_ISSUE,
            "Device key method not implemented for device type " + operand.getDeviceType());
    }
  }

  /** Creates an Android Phone from uuid and device spec. */
  public AndroidPhone createAndroidPhone(String uuid, AndroidPhoneSpec deviceSpec) {
    return new AndroidPhone(
        androidAdbUtil,
        androidFileUtil,
        androidPackageManagerUtil,
        androidSystemSettingUtil,
        androidSystemStateUtil,
        bundletoolUtil,
        sleeper,
        uuid,
        deviceSpec);
  }
}
