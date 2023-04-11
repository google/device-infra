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

/** A config for a single testbed. */
public interface TestbedConfig {

  /**
   * Gets the name of this test bed.
   *
   * @return The name of the testbed.
   */
  String getName();

  /**
   * Gets info on all devices belonging to this test bed.
   *
   * <p>Uses a ImmutableMap in order to preserve insertion order.
   *
   * @return A map of {@link SubDeviceKey} -> {@link SubDeviceInfo} for all devices that are a part
   *     of this test bed.
   */
  ImmutableMap<SubDeviceKey, SubDeviceInfo> getDevices();

  /**
   * Gets all dimensions for this testbed.
   *
   * @return A mapping of dimension name to value.
   */
  ImmutableMap<String, String> getDimensions();

  /**
   * Gets all properties for this testbed. Values can be any value represented in a JSONObject.
   *
   * @return A mapping of property name to property value.
   */
  ImmutableMap<String, Object> getProperties();

  /** Gets a YAML representation of the testbed config. */
  String toYaml();
}
