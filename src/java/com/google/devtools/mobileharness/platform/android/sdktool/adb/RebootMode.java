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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

/** Mode options for adb reboot commands. */
public enum RebootMode {
  /** Reboots to system image. */
  SYSTEM_IMAGE("", DeviceConnectionState.DEVICE),
  /** Reboots to bootloader/fastboot mode. */
  BOOTLOADER("bootloader", DeviceConnectionState.DISCONNECT),
  /** Reboots to recovery mode. */
  RECOVERY("recovery", DeviceConnectionState.RECOVERY),
  /** Reboots into recovery and automatically starts sideload mode. */
  SIDELOAD("sideload", DeviceConnectionState.SIDELOAD),
  /**
   * Reboots into recovery and automatically starts sideload mode. Will reboot after sideloading.
   */
  SIDELOAD_AUTO_REBOOT("sideload-auto-reboot", DeviceConnectionState.SIDELOAD);

  private static final String ADB_ARG_REBOOT = "reboot";
  private final String arg;
  private final DeviceConnectionState targetState;

  /**
   * Constructs enum.
   *
   * @param arg the boot mode as an arg in the {@code adb reboot} command.
   * @param targetState the target state that will boot into.
   */
  private RebootMode(String arg, DeviceConnectionState targetState) {
    this.arg = arg;
    this.targetState = targetState;
  }

  /** Gets {@code adb reboot} command args for this {@link RebootMode}. */
  @SuppressWarnings("AvoidObjectArrays")
  public String[] getRebootArgs() {
    return this.equals(RebootMode.SYSTEM_IMAGE)
        ? new String[] {ADB_ARG_REBOOT}
        : new String[] {ADB_ARG_REBOOT, arg};
  }

  /** Gets the {@link DeviceConnectionState} in adb after booting to this reboot mode. */
  public DeviceConnectionState getTargetState() {
    return targetState;
  }
}
