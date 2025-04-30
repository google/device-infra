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

package com.google.devtools.mobileharness.platform.testbed.adhoc;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.config.TestbedConfig;
import com.google.devtools.mobileharness.platform.testbed.config.TestbedLoader;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.List;
import java.util.Map;

/** Loader class for a single ad hoc testbed config, which won't change during its lifecycle. */
public class AdhocTestbedLoader implements TestbedLoader {

  private final String id;
  private final TestbedConfig testbedConfig;

  private AdhocTestbedLoader(TestbedConfig config) {
    testbedConfig = config;
    id = config.getName();
  }

  public static AdhocTestbedLoader create(String id, List<Device> devices)
      throws MobileHarnessException {
    return new AdhocTestbedLoader(AdhocTestbedConfig.create(id, devices));
  }

  /**
   * Loads all testbed configs.
   *
   * @return All testbed configs merged into a single map of testbed name -> testbed config
   */
  @Override
  public Map<String, TestbedConfig> getTestbedConfigs() throws MobileHarnessException {
    return ImmutableMap.of(id, testbedConfig);
  }
}
