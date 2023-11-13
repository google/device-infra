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

package com.google.wireless.qa.mobileharness.shared.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceScheduleUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

/** Utilities related to devices. */
public final class DeviceUtil {

  /**
   * Returns whether the lab server is in Shared Lab.
   *
   * <p>Note that this method should <b>only</b> be called in a LabServer process (or an equivalent
   * process like a DeviceMaster process which enables UTRS).
   *
   * <p>In detail, this method return <b>true</b> if and only if the value of the flag
   * "<b>should_manage_devices</b>" is <b>false</b> (the default value of the flag is true).
   *
   * <p>If this method returns true (in Satellite Lab), it means the lab server should actively
   * manage devices. If this method returns false (in Shared Lab), it means in the lab there are
   * some other components managing devices.
   *
   * <p>This method returns a <b>negative</b> boolean value of the legacy {@link
   * #shouldManageDevices()} method.
   *
   * @return true if the lab server is in Shared Lab, false if the lab server is in Satellite Lab
   * @since lab server 4.158.0
   */
  public static boolean inSharedLab() {
    return !Flags.instance().shouldManageDevices.getNonNull();
  }

  /**
   * Returns whether the lab server is allowed to create FailedDevice.
   *
   * <p>Note that this method should <b>only</b> be called in a LabServer process (or an equivalent
   * process like a DeviceMaster process which enables UTRS).
   *
   * <p>In detail, this method return <b>true</b> if and only if the value of the flag
   * "<b>create_failed_device</b>" is <b>True</b> (the default value of the flag is true).
   *
   * <p>If this method returns true (in Satellite Lab), it means the lab server will turn a device
   * into FailedDevice when the device frequently fails initialization.
   */
  public static boolean isFailedDeviceCreationEnabled() {
    return Flags.instance().createFailedDevice.getNonNull();
  }

  /**
   * Deprecated. Use {@link
   * com.google.wireless.qa.mobileharness.shared.model.lab.in.CompositeDimensions#supportAndSatisfied(Map)}
   * instead.
   */
  @Deprecated
  public static boolean deviceSupportsDimensions(
      Multimap<String, String> deviceDimensions, Map<String, String> jobDimensions) {
    if (jobDimensions.isEmpty()) {
      return true;
    }
    for (Entry<String, String> jobDimension : jobDimensions.entrySet()) {
      String dimensionName = jobDimension.getKey();
      String jobDimensionValue = jobDimensions.get(dimensionName);
      // The exclude value means request the device excluding the dimension.
      if (jobDimensionValue.equals(Dimension.Value.EXCLUDE)) {
        if (deviceDimensions.containsKey(dimensionName)) {
          return false;
        }
        continue;
      }

      boolean match = false;
      for (String deviceDimensionValue : deviceDimensions.get(dimensionName)) {
        if (deviceDimensionValue.equals(Dimension.Value.ALL_VALUE_FOR_DEVICE)
            || jobDimensionValue.equals(deviceDimensionValue)
            || (jobDimensionValue.startsWith(Dimension.Value.PREFIX_REGEX)
                && deviceDimensionValue.matches(
                    jobDimensionValue.substring(Dimension.Value.PREFIX_REGEX.length())))) {
          match = true;
          break;
        }
      }
      if (!match) {
        return false;
      }
    }
    return true;
  }

  /**
   * Merges M&M-style dimensions into a DeviceScheduleUnit, recognizing special dimensions:
   * "devicetype", "driver", "decorator" and setting the respective DeviceScheduleUnit attributes.
   */
  public static void mergeDimensionsToDeviceScheduleUnit(
      DeviceScheduleUnit deviceUnit, Multimap<String, String> dimensions) {
    // Iterate through dimensions, special-process special ones, add others as MH dimensions.
    // Each entry value is a Collection<String> - all values for the given dimension name.
    ListMultimap<String, String> mobileHarnessDimensions = ArrayListMultimap.create();
    for (Map.Entry<String, Collection<String>> entry : dimensions.asMap().entrySet()) {
      switch (entry.getKey()) {
        case "devicetype":
          deviceUnit.setTypes(entry.getValue());
          break;
        case "driver":
          deviceUnit.setDrivers(entry.getValue());
          break;
        case "decorator":
          deviceUnit.setDecorators(entry.getValue());
          break;
        default:
          mobileHarnessDimensions.putAll(entry.getKey(), entry.getValue());
      }
    }
    deviceUnit.setDimensions(mobileHarnessDimensions);
  }

  /**
   * Returns whether the {@code id} is a valid TCP address.
   *
   * <p>This is used to distinguish devices connected with ADB-over-TCP from USB-connected ones.
   *
   * @param id Device id, usually a serial number of the device or TCP address.
   */
  public static boolean isOverTcpDevice(String id) {
    try {
      HostAndPort parsedAddress = HostAndPort.fromString(id);
      return InetAddresses.isInetAddress(parsedAddress.getHost());
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** Returns whether the ate dual stack is enabled. */
  public static boolean isAteDualStackEnabled() {
    return Flags.instance().enableAteDualStack.get()
        && !inSharedLab()
        && new SystemUtil().isOnLinux();
  }

  private DeviceUtil() {}
}
