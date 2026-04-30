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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class for Android JIT emulator. */
public final class AndroidJitEmulatorUtil {

  private AndroidJitEmulatorUtil() {}

  // Acloud creates cuttlefish emulator with port 6520 + index, where index is instance id minus
  // one. Example: "acloud create --local-instance 1" will create a local emulator with port 6520.
  // And instance 2 will be 6521, so on and so forth.
  public static final int EMULATOR_BASE_PORT = 6520;
  public static final int EMULATOR_INSTANCE_ID_BASE = 1;
  public static final Pattern LOCAL_NOOP_EMULATOR_ID_PATTERN =
      Pattern.compile("^([\\w\\.]+):local-virtual-device-(\\d+)$");
  public static final String TF_GLOBAL_CONFIG_PATH = "/mtt/scripts/host-config.xml";

  public static String getVirtualDeviceNameInTradefed(String deviceId) {
    if (!Flags.instance().virtualDeviceServerIp.getNonNull().isEmpty()
        && !Flags.instance().virtualDeviceServerUsername.getNonNull().isEmpty()) {
      return deviceId;
    }
    Matcher matcher = LOCAL_NOOP_EMULATOR_ID_PATTERN.matcher(deviceId);
    if (matcher.find()) {
      return String.format("local-virtual-device-%s", matcher.group(2));
    } else {
      return "";
    }
  }

  /**
   * Extracts the port from the device ID.
   *
   * @param deviceId the device ID (e.g., "127.0.0.1:6520")
   * @return the parsed port
   * @throws MobileHarnessException if the device ID format is invalid or the port is not a positive
   *     integer
   */
  public static int getPortFromDeviceId(String deviceId) throws MobileHarnessException {
    int colonIndex = deviceId.indexOf(':');
    if (colonIndex == -1) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_JIT_EMULATOR_INVALID_DEVICE_ID_ERROR,
          "Invalid device ID format (missing ':'): " + deviceId);
    }
    String portString = deviceId.substring(colonIndex + 1);
    try {
      int port = Integer.parseInt(portString);
      if (port <= 0) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_JIT_EMULATOR_INVALID_DEVICE_ID_ERROR,
            "Invalid port in device ID (must be > 0): " + deviceId);
      }
      return port;
    } catch (NumberFormatException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_JIT_EMULATOR_INVALID_DEVICE_ID_ERROR,
          "Failed to parse port from device ID: " + deviceId,
          e);
    }
  }

  /**
   * Gets all virtual device IDs based on flags.
   *
   * @return the list of virtual device IDs
   */
  public static ImmutableList<String> getAllVirtualDeviceIds() {
    int emulatorNumber = Flags.instance().androidJitEmulatorNum.getNonNull();
    List<String> emulatorIds = new ArrayList<>();

    if (Flags.instance().noopJitEmulator.getNonNull()) {
      if (!Flags.instance().virtualDeviceServerIp.getNonNull().isEmpty()
          && !Flags.instance().virtualDeviceServerUsername.getNonNull().isEmpty()) {
        for (int i = 0; i < emulatorNumber; i++) {
          emulatorIds.add(
              String.format(
                  "gce-device-%s-%d-%s",
                  Flags.instance().virtualDeviceServerIp.getNonNull(),
                  i,
                  Flags.instance().virtualDeviceServerUsername.getNonNull()));
        }
      } else {
        for (int i = 0; i < emulatorNumber; i++) {
          emulatorIds.add(String.format("0.0.0.0:local-virtual-device-%d", i));
        }
      }
    } else {
      for (int i = 0; i < emulatorNumber; i++) {
        emulatorIds.add(String.format("127.0.0.1:%d", EMULATOR_BASE_PORT + i));
      }
    }
    return ImmutableList.copyOf(emulatorIds);
  }

  public static String getHostConfigPath() {
    return TF_GLOBAL_CONFIG_PATH;
  }
}
