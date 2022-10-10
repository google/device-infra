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

package com.google.devtools.mobileharness.api.model.lab;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.lab.in.CompositeDimensions;
import com.google.devtools.mobileharness.api.model.lab.in.Decorators;
import com.google.devtools.mobileharness.api.model.lab.in.Drivers;
import com.google.devtools.mobileharness.api.model.lab.in.Types;
import com.google.devtools.mobileharness.api.model.lab.out.Properties;

/** Data model containing all information of a local Mobile Harness device. */
@AutoValue
public abstract class DeviceInfo {

  public abstract DeviceId deviceId();

  public abstract CompositeDimensions dimensions();

  public abstract Properties properties();

  /**
   * Does not work in container/sandbox mode. In these modes, this will return an empty instance.
   */
  public abstract Types deviceTypes();

  /**
   * Does not work in container/sandbox mode. In these modes, this will return an empty instance.
   */
  public abstract Drivers supportedDecorators();

  /**
   * Does not work in container/sandbox mode. In these modes, this will return an empty instance.
   */
  public abstract Decorators supportedDrivers();

  /**
   * Do <b>not</b> make it public. Device info instances are created and managed by Mobile Harness
   * device manager and test runner. If you still want to create a device info instance, please use
   * {@link LiteDeviceInfoFactory} instead.
   */
  static DeviceInfo create(
      DeviceId deviceId, CompositeDimensions dimensions, Properties properties) {
    return new AutoValue_DeviceInfo(
        deviceId, dimensions, properties, new Types(), new Drivers(), new Decorators());
  }
}
