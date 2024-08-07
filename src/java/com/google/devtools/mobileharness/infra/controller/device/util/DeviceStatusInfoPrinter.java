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

package com.google.devtools.mobileharness.infra.controller.device.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Map;
import java.util.Map.Entry;

/** Utility for printing {@link DeviceStatusInfo}. */
public class DeviceStatusInfoPrinter {

  public static String printDeviceStatusInfos(Map<Device, DeviceStatusInfo> deviceStatusInfos) {
    // <device_class_simple_name, <device_status_name, list<device_id>>>
    Map<String, Map<String, ImmutableList<String>>> devicesToPrint =
        deviceStatusInfos.entrySet().stream()
            .collect(
                groupingBy(
                    entry -> entry.getKey().getClass().getSimpleName(),
                    groupingBy(
                        entry -> entry.getValue().getDeviceStatusWithTimestamp().getStatus().name(),
                        mapping(entry -> entry.getKey().getDeviceId(), toImmutableList()))));

    StringBuilder result = new StringBuilder("Device count: ").append(deviceStatusInfos.size());
    if (!devicesToPrint.isEmpty()) {
      result.append(", status:");
      for (Entry<String, Map<String, ImmutableList<String>>> entry : devicesToPrint.entrySet()) {
        // Prints the device type and the numbers of the devices of different status.
        result.append("\n======== ");
        result.append(entry.getKey());
        result.append(" ========");
        for (Entry<String, ImmutableList<String>> deviceStatusAndIds :
            entry.getValue().entrySet()) {
          // Prints the status.
          result.append('\n');
          result.append(deviceStatusAndIds.getKey());
          result.append('(');
          result.append(deviceStatusAndIds.getValue().size());
          result.append("):");
          if (deviceStatusAndIds.getValue().size() > 3) {
            result.append("\n");
          }
          // Prints the device id. Up to 3 id in one row.
          int colIdx = 0;
          for (String deviceId : deviceStatusAndIds.getValue()) {
            if (colIdx == 3) {
              result.append('\n');
              colIdx = 0;
            }
            result.append('\t');
            result.append(deviceId);
            colIdx++;
          }
        }
      }
    }
    return result.toString();
  }

  private DeviceStatusInfoPrinter() {}
}
