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

package com.google.devtools.mobileharness.fe.v6.service.search.schema;

import com.google.common.collect.ImmutableList;

/**
 * Standard open-source device key descriptors for MobileHarness search.
 *
 * <p>Contains Group 1 (Universal Common Device Keys) and Group 2 (Standalone ATS WiFi SSID).
 */
public final class DeviceKeys {

  public static final String PREFIX_DEVICE_FIELD = "device_field::";
  public static final String PREFIX_DIMENSION = "dimension::";
  public static final String PREFIX_DEVICE_CONFIG = "device_config::";

  // Group 1: Universal Common Device Keys (Typed Fields)
  public static final DeviceKeyDescriptor UUID =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "uuid")
          .setSource(DeviceSource.deviceInfoField("device_locator.id"))
          .setDisplayName("UUID")
          .build();

  public static final DeviceKeyDescriptor STATUS =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "status")
          .setSource(DeviceSource.deviceInfoField("device_status"))
          .setDisplayName("Status")
          .build();

  public static final DeviceKeyDescriptor TYPE =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "type")
          .setSource(DeviceSource.deviceInfoField("device_feature.type"))
          .setDisplayName("Type")
          .build();

  public static final DeviceKeyDescriptor DRIVER =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "driver")
          .setSource(DeviceSource.deviceInfoField("device_feature.driver"))
          .setDisplayName("Supported Drivers")
          .setIsPlural(true)
          .build();

  public static final DeviceKeyDescriptor DECORATOR =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_FIELD + "decorator")
          .setSource(DeviceSource.deviceInfoField("device_feature.decorator"))
          .setDisplayName("Supported Decorators")
          .setIsPlural(true)
          .build();

  // Group 1: Universal Common Core Dimensions
  public static final DeviceKeyDescriptor MODEL =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DIMENSION + "model")
          .setSource(DeviceSource.dimension("model"))
          .setDisplayName("Model")
          .build();

  public static final DeviceKeyDescriptor SOFTWARE_VERSION =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DIMENSION + "software_version")
          .setSource(DeviceSource.dimension("software_version"))
          .setDisplayName("Software Version")
          .build();

  public static final DeviceKeyDescriptor SDK_VERSION =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DIMENSION + "sdk_version")
          .setSource(DeviceSource.dimension("sdk_version"))
          .setDisplayName("SDK Version")
          .build();

  public static final DeviceKeyDescriptor DEVICE_FORM =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DIMENSION + "device_form")
          .setSource(DeviceSource.dimension("device_form"))
          .setDisplayName("Form")
          .build();

  public static final DeviceKeyDescriptor DEVICE_CLASS_NAME =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DIMENSION + "device_class_name")
          .setSource(DeviceSource.dimension("device_class_name"))
          .setDisplayName("Device Class")
          .build();

  public static final DeviceKeyDescriptor MANUFACTURER =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DIMENSION + "manufacturer")
          .setSource(DeviceSource.dimension("manufacturer"))
          .setDisplayName("Manufacturer")
          .build();

  public static final DeviceKeyDescriptor QUARANTINED =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DIMENSION + "quarantined")
          .setSource(DeviceSource.dimension("quarantined"))
          .setDisplayName("Quarantine")
          .build();

  // Group 2: Standalone ATS Exclusive Key (WiFi SSID)
  public static final DeviceKeyDescriptor WIFI_SSID =
      DeviceKeyDescriptor.builder()
          .setId(PREFIX_DEVICE_CONFIG + "wifi_ssid")
          .setSource(DeviceSource.config("wifi_ssid"))
          .setDisplayName("WiFi SSID")
          .build();

  /** Standard Group 1 common device keys (present in every deployment). */
  public static final ImmutableList<DeviceKeyDescriptor> COMMON_DEVICE_KEYS =
      ImmutableList.of(
          UUID,
          STATUS,
          TYPE,
          DRIVER,
          DECORATOR,
          MODEL,
          SOFTWARE_VERSION,
          SDK_VERSION,
          DEVICE_FORM,
          DEVICE_CLASS_NAME,
          MANUFACTURER,
          QUARANTINED);

  private DeviceKeys() {}
}
