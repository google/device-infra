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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;

/**
 * Common fleet search key constants reading directly from the authoritative {@code schema/} layer.
 */
public final class FleetSearchKeys {

  private FleetSearchKeys() {}

  // ---- Namespace prefixes ----

  public static final String FIELD_PREFIX = DeviceKeys.PREFIX_DEVICE_FIELD;
  public static final String DIM_PREFIX = DeviceKeys.PREFIX_DIMENSION;
  public static final String PROP_PREFIX = HostKeys.PREFIX_HOST_PROPERTY;
  public static final String HOST_PREFIX = HostKeys.PREFIX_HOST_FIELD;
  public static final String CONFIG_PREFIX = DeviceKeys.PREFIX_DEVICE_CONFIG;

  // ---- Built-in device field keys ----

  public static final String FIELD_UUID = DeviceKeys.UUID.id();
  public static final String FIELD_STATUS = DeviceKeys.STATUS.id();
  public static final String FIELD_TYPE = DeviceKeys.TYPE.id();
  public static final String FIELD_OWNER = DeviceKeys.PREFIX_DEVICE_FIELD + "owner";
  public static final String FIELD_DRIVER = DeviceKeys.DRIVER.id();
  public static final String FIELD_DECORATOR = DeviceKeys.DECORATOR.id();
  public static final String FIELD_EXECUTOR = DeviceKeys.PREFIX_DEVICE_FIELD + "executor";

  // ---- Built-in dimension keys referenced by logic ----

  public static final String DIM_QUARANTINED = DeviceKeys.PREFIX_DEVICE_FIELD + "quarantined";

  // ---- Built-in host keys ----

  public static final String HOST_NAME = HostKeys.HOST_NAME.id();
  public static final String HOST_IP = HostKeys.HOST_IP.id();
  public static final String HOST_LAB_TYPE = HostKeys.PREFIX_HOST_FIELD + "lab_type";
  public static final String HOST_OS = HostKeys.HOST_OS.id();
  public static final String HOST_CONNECTIVITY = HostKeys.CONNECTIVITY.id();
  public static final String HOST_DAEMON_STATUS = HostKeys.PREFIX_HOST_FIELD + "daemon_status";
  public static final String HOST_RELEASE_STATUS = HostKeys.PREFIX_HOST_FIELD + "release_status";
  public static final String HOST_RELEASE_TYPE = HostKeys.PREFIX_HOST_FIELD + "release_type";
  public static final String HOST_LAB_SERVER_VERSION = HostKeys.LAB_SERVER_VERSION.id();
  public static final String HOST_ATS_CONTROLLER = HostKeys.PREFIX_HOST_FIELD + "ats_controller";
  public static final String HOST_DEVICE_COUNT = HostKeys.DEVICE_COUNT.id();

  // ---- Built-in config keys ----

  public static final String CONFIG_WIFI_SSID = AtsDeviceKeys.WIFI_SSID.id();

  // ---- Cross-cutting key sets (deployment independent) ----

  public static final ImmutableSet<String> PLURAL_DISPLAY_KEYS =
      ImmutableSet.of(FIELD_OWNER, FIELD_DRIVER, FIELD_DECORATOR, FIELD_EXECUTOR);

  public static final ImmutableSet<String> VALUE_DISPLAY_KEYS =
      ImmutableSet.of(HOST_ATS_CONTROLLER);

  public static final ImmutableSet<String> MULTI_VALUE_KEYS =
      ImmutableSet.of(
          FIELD_TYPE,
          FIELD_OWNER,
          FIELD_DRIVER,
          FIELD_DECORATOR,
          FIELD_EXECUTOR,
          DeviceKeys.OS.id(),
          DeviceKeys.MODEL.id(),
          DeviceKeys.SDK_VERSION.id(),
          DeviceKeys.SOFTWARE_VERSION.id(),
          HOST_LAB_TYPE);

  public static final ImmutableSet<String> PLAIN_VALUE_KEYS =
      ImmutableSet.of(
          FIELD_UUID,
          DeviceKeys.PREFIX_DIMENSION + "uuid",
          DeviceKeys.PREFIX_DIMENSION + "id",
          DeviceKeys.PREFIX_DIMENSION + "serial",
          DeviceKeys.PREFIX_DIMENSION + "control_id",
          DeviceKeys.PREFIX_DIMENSION + "mac_address",
          DeviceKeys.PREFIX_DIMENSION + "bluetooth_mac_address",
          DeviceKeys.PREFIX_DIMENSION + "soc_id",
          DeviceKeys.PREFIX_DIMENSION + "network_address",
          DeviceKeys.PREFIX_DIMENSION + "gservices_android_id",
          DeviceKeys.PREFIX_DIMENSION + "iccid",
          DeviceKeys.PREFIX_DIMENSION + "iccids",
          DeviceKeys.PREFIX_DIMENSION + "imei",
          DeviceKeys.PREFIX_DIMENSION + "ecid",
          DeviceKeys.PREFIX_DIMENSION + "wifi_address",
          DeviceKeys.PREFIX_DIMENSION + "testbed_name");

  public static final ImmutableSet<String> HOST_PLAIN_VALUE_KEYS =
      ImmutableSet.of(HOST_NAME, HOST_DEVICE_COUNT);

  public static final ImmutableSet<String> IDENTIFIER_KEYS =
      ImmutableSet.<String>builder().addAll(PLAIN_VALUE_KEYS).add(HOST_NAME).add(HOST_IP).build();

  public static final ImmutableSet<String> EXCLUDED_DIMENSIONS =
      ImmutableSet.of("subdevice_dimensions");
}
