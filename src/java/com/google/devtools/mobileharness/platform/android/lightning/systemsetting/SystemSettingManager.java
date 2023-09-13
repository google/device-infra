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

package com.google.devtools.mobileharness.platform.android.lightning.systemsetting;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/**
 * Class for managing Android device system settings and configuration.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 *
 * <p>Note: When adding new APIs, please confirm if they're supported in both full stack MH and M&M,
 * and use DeviceUtil#shouldManageDevices to separate different behaviors between them if needed.
 */
public class SystemSettingManager {

  private final AndroidSystemSettingUtil systemSettingUtil;

  public SystemSettingManager() {
    this(new AndroidSystemSettingUtil());
  }

  @VisibleForTesting
  SystemSettingManager(AndroidSystemSettingUtil systemSettingUtil) {
    this.systemSettingUtil = systemSettingUtil;
  }

  /**
   * Gets the SDK version of the device.
   *
   * @throws MobileHarnessException if fails to execute the commands or timeout, or fails to parse
   *     sdk version from device property.
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public int getDeviceSdkVersion(Device device)
      throws MobileHarnessException, InterruptedException {
    Integer deviceSdkVersion = null;
    if (device instanceof AndroidDevice) {
      deviceSdkVersion = ((AndroidDevice) device).getSdkVersion();
    }
    if (deviceSdkVersion == null) {
      deviceSdkVersion = systemSettingUtil.getDeviceSdkVersion(device.getDeviceId());
    }
    return deviceSdkVersion;
  }
}
