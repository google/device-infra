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

package com.google.devtools.mobileharness.platform.testbed.config;

import com.google.auto.value.AutoValue;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/** Container for the ID and device type of a subdevice. */
@AutoValue
public abstract class SubDeviceKey {

  /** The id of the testbed device. */
  public abstract String deviceId();

  /** The Mobile Harness device type. */
  public abstract Class<? extends Device> deviceType();

  public static SubDeviceKey create(String id, Class<? extends Device> deviceType) {
    return new AutoValue_SubDeviceKey(id, deviceType);
  }
}
