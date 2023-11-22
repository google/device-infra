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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.testbed.config.BaseTestbedConfig;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceInfo;
import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceKey;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@link com.google.devtools.mobileharness.platform.testbed.config.TestbedConfig} class used in
 * ad hoc testbed creation.
 */
public class AdhocTestbedConfig extends BaseTestbedConfig {

  // NOTE: TestbedConfigs (and AdhocTestbedConfigs) in particular must preserve the order of
  // subdevices, so implementation and API should always err on the side of order preserving data
  // types (e.g., Lists, Guava's Immutable*, etc.).

  public static final String ADHOC_TESTBED_PROPERTY_KEY = "is_adhoc";

  private AdhocTestbedConfig(String name, Map<SubDeviceKey, SubDeviceInfo> devices)
      throws MobileHarnessException {
    super(
        name,
        devices,
        /* dimensions= */ ImmutableMap.of(),
        /* properties= */ ImmutableMap.of(ADHOC_TESTBED_PROPERTY_KEY, "true"));
  }

  /** Creates an testbed config from a set of devices. */
  public static AdhocTestbedConfig create(String name, List<Device> subdevices)
      throws MobileHarnessException {
    // TODO: Add dimension and property expansion for subdevices that are
    // CompositeDevices behavior once intended behavior is determined.
    return new AdhocTestbedConfig(name, createDeviceInfoMap(subdevices));
  }

  private static ImmutableMap<SubDeviceKey, SubDeviceInfo> createDeviceInfoMap(List<Device> devices)
      throws MobileHarnessException {
    ImmutableMap.Builder<SubDeviceKey, SubDeviceInfo> deviceInfos = new ImmutableMap.Builder<>();
    for (Device device : devices) {

      String deviceId = device.getDeviceId();
      SubDeviceKey key = SubDeviceKey.create(deviceId, device.getClass());
      ImmutableSet<StrPair> allDimensions =
          ImmutableSet.<StrPair>builder()
              .addAll(device.getDimensions())
              .addAll(device.getRequiredDimensions())
              .build();
      SubDeviceInfo value =
          new AdhocSubDeviceInfo(
              deviceId,
              device.getClass(),
              getDimensionsMap(allDimensions),
              ImmutableMap.copyOf(device.getProperties()));
      deviceInfos.put(key, value);
    }
    return deviceInfos.buildOrThrow();
  }

  private static ImmutableMultimap<String, String> getDimensionsMap(Set<StrPair> dimensions) {
    ImmutableMultimap.Builder<String, String> dimensionsMap = new ImmutableMultimap.Builder<>();
    for (StrPair dimension : dimensions) {
      dimensionsMap.put(dimension.getName(), dimension.getValue());
    }
    return dimensionsMap.build();
  }
}
