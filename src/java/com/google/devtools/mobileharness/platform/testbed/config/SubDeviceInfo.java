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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;

/** Info about a device on a test bed. */
public interface SubDeviceInfo {
  /**
   * Gets the id of the testbed device.
   *
   * @return The device id.
   */
  String getId();

  /**
   * Gets the mobile harness device type to use for this device.
   *
   * @return The device type name.
   */
  Class<? extends Device> getDeviceType();

  /**
   * Gets the mobile harness device alias type for this device.
   *
   * <p>The set of aliases this sub-device needs to have. Each alias type can only be specified
   * once, duplicates within any implementation will be ignored.
   *
   * @return The set of aliases needed for this sub-device.
   */
  ImmutableSet<String> getDeviceAliasType();

  /**
   * Gets dimensions for this subdevice.
   *
   * @return A map of dimension name to value.
   */
  ImmutableMultimap<String, String> getDimensions();

  /**
   * Gets properties about the device. Values are any value that can be represented in a JSONObject.
   *
   * @return A map of property names and values for the device.
   */
  ImmutableMap<String, ?> getProperties();
}
