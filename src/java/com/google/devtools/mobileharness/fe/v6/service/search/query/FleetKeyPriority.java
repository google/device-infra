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

package com.google.devtools.mobileharness.fe.v6.service.search.query;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;

/**
 * Universal common key priority tiers and default standalone ATS KeyPriority implementation.
 *
 * <p>Contains only deployment-independent Group 1 (common) and Group 2 (ATS) keys.
 * Deployment-specific curations (such as 1P internal master or Partner ATS) define their own
 * rankings using typed descriptors.
 */
public final class FleetKeyPriority implements ScenarioCuration.KeyPriority {

  public static final FleetKeyPriority INSTANCE = new FleetKeyPriority();

  public FleetKeyPriority() {}

  /** Universal common device keys ranked highest across all deployments. */
  public static final ImmutableSet<DeviceKeyDescriptor> COMMON_KEY_TIER1 =
      ImmutableSet.of(
          DeviceKeys.UUID,
          DeviceKeys.STATUS,
          DeviceKeys.TYPE,
          DeviceKeys.MODEL,
          DeviceKeys.OS,
          DeviceKeys.SDK_VERSION,
          DeviceKeys.HOST_NAME,
          DeviceKeys.DEVICE_CLASS_NAME,
          DeviceKeys.MANUFACTURER);

  /** Universal secondary device keys ranked below tier 1. */
  public static final ImmutableSet<DeviceKeyDescriptor> COMMON_KEY_TIER2 =
      ImmutableSet.of(
          DeviceKeys.DRIVER,
          DeviceKeys.DECORATOR,
          DeviceKeys.HOST_IP,
          DeviceKeys.HOST_OS,
          DeviceKeys.HOST_CONNECTIVITY,
          DeviceKeys.HOST_LAB_SERVER_VERSION,
          DeviceKeys.SOFTWARE_VERSION,
          DeviceKeys.DEVICE_FORM);

  /** Universal common host keys ranked highest for the host entity. */
  public static final ImmutableSet<HostKeyDescriptor> COMMON_HOST_KEY_TIER1 =
      ImmutableSet.of(
          HostKeys.HOST_NAME,
          HostKeys.CONNECTIVITY,
          HostKeys.DEVICE_COUNT,
          HostKeys.HOST_OS,
          HostKeys.LAB_SERVER_VERSION);

  /** Universal secondary host keys ranked below host tier 1. */
  public static final ImmutableSet<HostKeyDescriptor> COMMON_HOST_KEY_TIER2 =
      ImmutableSet.of(HostKeys.HOST_IP);

  @Override
  public int devicePriority(DeviceKeyDescriptor key) {
    if (key.equals(AtsDeviceKeys.WIFI_SSID) || COMMON_KEY_TIER1.contains(key)) {
      return 3;
    }
    if (COMMON_KEY_TIER2.contains(key)) {
      return 1;
    }
    return 0;
  }

  @Override
  public int hostPriority(HostKeyDescriptor key) {
    if (COMMON_HOST_KEY_TIER1.contains(key)) {
      return 3;
    }
    if (COMMON_HOST_KEY_TIER2.contains(key)) {
      return 1;
    }
    return 0;
  }
}
