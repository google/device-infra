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

package com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb;

import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry for translating device serial numbers to local device IDs. */
final class DeviceRegistry {
  private static final DeviceRegistry INSTANCE = new DeviceRegistry();

  private final Map<String, String> serialToLocalIdMap = new ConcurrentHashMap<>();

  private DeviceRegistry() {}

  public static DeviceRegistry getInstance() {
    return INSTANCE;
  }

  public void register(String realSerial, String localId) {
    serialToLocalIdMap.put(realSerial, localId);
  }

  public void unregister(String realSerial) {
    serialToLocalIdMap.remove(realSerial);
  }

  public String getLocalId(String realSerial) {
    return serialToLocalIdMap.get(realSerial);
  }

  public boolean containsSerial(String realSerial) {
    return serialToLocalIdMap.containsKey(realSerial);
  }

  public boolean isEmpty() {
    return serialToLocalIdMap.isEmpty();
  }

  @VisibleForTesting
  void reset() {
    serialToLocalIdMap.clear();
  }
}
