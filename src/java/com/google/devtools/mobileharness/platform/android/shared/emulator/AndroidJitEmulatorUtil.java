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

package com.google.devtools.mobileharness.platform.android.shared.emulator;

/** Utility class for Android JIT emulator. */
public final class AndroidJitEmulatorUtil {

  private AndroidJitEmulatorUtil() {}

  public static final int EMULATOR_BASE_PORT = 6520;
  public static final int EMULATOR_INSTANCE_ID_BASE = 1;

  public static String getAcloudInstanceId(String deviceId) {
    int instanceIndex = getInstanceIndex(deviceId);
    if (instanceIndex < 0) {
      return "";
    }
    return String.valueOf(instanceIndex + EMULATOR_INSTANCE_ID_BASE);
  }

  public static String getVirtualDeviceNameInTradefed(String deviceId) {
    int instanceIndex = getInstanceIndex(deviceId);
    if (instanceIndex < 0) {
      return "";
    }
    return String.format("local-virtual-device-%s", String.valueOf(instanceIndex));
  }

  private static int getInstanceIndex(String deviceId) {
    return Integer.parseInt(deviceId.substring(deviceId.indexOf(':') + 1)) - EMULATOR_BASE_PORT;
  }
}
