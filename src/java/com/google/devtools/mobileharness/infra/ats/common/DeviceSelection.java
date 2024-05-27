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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.common.collect.ImmutableSet;

/** Util to select devices with selection criteria. */
public class DeviceSelection {

  private DeviceSelection() {}

  /** Checks if the given device with {@code deviceId} is a match for the provided options. */
  public static boolean matches(String deviceId, DeviceSelectionOptions options) {
    ImmutableSet<String> serials = ImmutableSet.copyOf(options.serials());
    if (!serials.isEmpty() && !serials.contains(deviceId)) {
      return false;
    }
    // TODO: Enable to check options "product types" and "device properties".
    return true;
  }
}
