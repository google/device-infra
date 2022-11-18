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

import com.google.auto.value.AutoValue;

/** Locator of a USB device. */
@AutoValue
public abstract class UsbDeviceLocator {

  public static UsbDeviceLocator of(int hostBus, String hostPort) {
    return new AutoValue_UsbDeviceLocator(hostBus, hostPort);
  }

  /**
   * USB host bus number of the device.
   *
   * <p>"lsusb -t" or "adb devices -l" can return this information.
   *
   * <p>For example, if "adb devices -l" returns:
   *
   * <pre>
   * List of devices attached
   * 014994B00D014014  device  usb:1-2 ...
   * 363005DC750400EC  device  usb:3-11.4 ...
   * ...</pre>
   *
   * <p>The hostBus for "014994B00D014014" is 1.
   *
   * <p>The hostBus for "363005DC750400EC" is 3.
   */
  public abstract int hostBus();

  /**
   * USB host bus port number of the device.
   *
   * <p>"lsusb -t" or "adb devices -l" can return this information.
   *
   * <p>For example, if "adb devices -l" returns:
   *
   * <pre>
   * List of devices attached
   * 014994B00D014014  device  usb:1-2 ...
   * 363005DC750400EC  device  usb:3-11.4 ...
   * ...</pre>
   *
   * <p>The hostPort for "014994B00D014014" is "2".
   *
   * <p>The hostPort for "363005DC750400EC" is "11.4", while "11.4" means port 11, port 4. Between
   * the two ports there must be a hub.
   */
  public abstract String hostPort();
}
