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

package com.google.devtools.mobileharness.platform.android.shared.constant;

import com.google.common.collect.ImmutableList;

/** Holds Device related constants. */
public final class DeviceConstants {

  /**
   * Output of a devices's serial in "fastboot devices" and "adb devices" if the device uses the
   * default serial.
   */
  public static final ImmutableList<String> OUTPUT_DEVICE_DEFAULT_SERIALS =
      ImmutableList.of("0123456789ABCDEF", "0123456789ABCDEF000");

  private DeviceConstants() {}
}
