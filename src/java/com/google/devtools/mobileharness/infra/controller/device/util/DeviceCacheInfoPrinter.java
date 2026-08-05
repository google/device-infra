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

import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCacheManager.CacheType;
import java.util.Map;
import java.util.Set;

/** Utility for printing device cache info. */
public class DeviceCacheInfoPrinter {

  public static String printDeviceCacheInfos(Map<CacheType, ? extends Set<String>> cachedDevices) {
    int totalCount = cachedDevices.values().stream().mapToInt(Set::size).sum();
    StringBuilder result = new StringBuilder("Cached device count: ").append(totalCount);
    if (!cachedDevices.isEmpty()) {
      result.append(", cache types:");
      for (Map.Entry<CacheType, ? extends Set<String>> entry : cachedDevices.entrySet()) {
        result.append("\n======== ");
        result.append(entry.getKey().name());
        result.append(" (");
        result.append(entry.getValue().size());
        result.append(") ========\n");
        int colIdx = 0;
        for (String deviceId : entry.getValue()) {
          if (colIdx == 3) {
            result.append('\n');
            colIdx = 0;
          }
          result.append("  ");
          result.append(deviceId);
          colIdx++;
        }
      }
    }
    return result.toString();
  }

  private DeviceCacheInfoPrinter() {}
}
