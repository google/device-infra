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

package com.google.devtools.deviceinfra.platform.android.sdk.fastboot;

/** Enums for fastboot library. */
public final class Enums {

  /** Partitions of the Android ROM. The order matters. */
  public enum Partition {
    BOOTLOADER,
    // Used in gobo, same as BOOTLOADER.
    SINGLEBOOTLOADER,
    OEM,
    MOTOBOOT,
    RADIO,
    VENDOR,
    BOOT,
    RECOVERY,
    SYSTEM,
    SYSTEM_OTHER,
    CACHE,
    // First appeared in muskie/walleye/taimen.
    DTBO,
    // Used in gobo, same as DTBO.
    ODMDTBO,
    // First appeared in muskie/walleye/taimen.
    VBMETA,
    // First appeared in blueline/crosshatch
    PRODUCT,
    USERDATA,
    // Appeared in dynamic partitions
    SUPER_EMPTY,
  }

  /** Android device properties in fastboot mode. */
  public enum FastbootProperty {
    PRODUCT,
    UNLOCKED,
    HARDWARE,
    CURRENT_SLOT,
    IS_USERSPACE,
    SERIALNO,
  }

  /**
   * Slot (partition sets) names.
   *
   * <p>Currently only slots A and B exist. Originally used seamless system updates but on some
   * devices also used to optimize first boot time.
   *
   * <p>Usually all images should be flashed to default slot. In some cases (like system_other.img),
   * they can be flashed to a non-default slot.
   *
   * <p>Also @see https://source.android.com/devices/tech/ota/ab/#slots.
   */
  public enum Slot {
    A,
    B
  }

  /** Device state information of an Android device when running `fastboot devices`. */
  public enum FastbootDeviceState {
    FASTBOOT,
    FASTBOOTD;
  }

  private Enums() {}
}
