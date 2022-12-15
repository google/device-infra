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

package com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.realdevice;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.BaseDevice;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.util.List;

/** Helper utility for {@link AndroidRealDeviceDelegate}. */
public class AndroidRealDeviceDelegateHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Allowlist for unrooted devices which support the {@code AndroidPerformanceLockDecorator}.
  private static final ImmutableSet<String> UNROOTED_DEVICE_ALLOWLIST = ImmutableSet.of("nokia 1");

  private static final String PROPERTY_NAME_REBOOT_TO_STATE =
      AndroidRealDeviceConstants.PROPERTY_NAME_REBOOT_TO_STATE;

  public static void setRebootToStateProperty(AndroidDevice device, DeviceState deviceState) {
    String oldValue = device.getProperty(PROPERTY_NAME_REBOOT_TO_STATE);
    device.setProperty(PROPERTY_NAME_REBOOT_TO_STATE, deviceState.name());
    logger.atInfo().log(
        "Setting REBOOT_TO_STATE property for device %s. New value: %s. Old value: %s",
        device.getDeviceId(), deviceState.name(), oldValue);
  }

  public static String getRebootToStateProperty(AndroidDevice device) {
    return device.getProperty(PROPERTY_NAME_REBOOT_TO_STATE);
  }

  /** Checks if the device is the allowlist for the performance lock decorator. */
  public static boolean isInSupportedAllowlistForPerformanceLockDecorator(BaseDevice device) {
    List<String> model = device.getDimension(Ascii.toLowerCase(Dimension.Name.MODEL.name()));
    if (model.size() == 1) {
      return UNROOTED_DEVICE_ALLOWLIST.contains(model.get(0));
    }
    return false;
  }

  private AndroidRealDeviceDelegateHelper() {}
}
