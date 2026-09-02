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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;

/**
 * Presentation helper for formatting key display names and special UI category prefixes across
 * query and BFF layers.
 */
final class FleetKeyDisplays {

  private static final ImmutableMap<String, String> BUILTIN_DISPLAY_NAMES =
      ImmutableMap.<String, String>builder()
          .put(DeviceKeys.UUID.id(), "UUID")
          .put(DeviceKeys.STATUS.id(), "Status")
          .put(DeviceKeys.TYPE.id(), "Type")
          .put(DeviceKeys.DRIVER.id(), "Supported Drivers")
          .put(DeviceKeys.DECORATOR.id(), "Supported Decorators")
          .put(DeviceKeys.PREFIX_DEVICE_FIELD + "owner", "Owners")
          .put(DeviceKeys.PREFIX_DEVICE_FIELD + "executor", "Executors")
          .put(DeviceKeys.PREFIX_DEVICE_FIELD + "quarantined", "Quarantine")
          .put(DeviceKeys.MODEL.id(), "Model")
          .put(DeviceKeys.OS.id(), "OS")
          .put(DeviceKeys.SDK_VERSION.id(), "SDK Version")
          .put(DeviceKeys.SOFTWARE_VERSION.id(), "Software Version")
          .put(DeviceKeys.DEVICE_FORM.id(), "Form")
          .put(DeviceKeys.DEVICE_CLASS_NAME.id(), "Device Class")
          .put(DeviceKeys.MANUFACTURER.id(), "Manufacturer")
          .put(AtsDeviceKeys.WIFI_SSID.id(), "Wi-Fi SSID")
          .put(HostKeys.HOST_NAME.id(), "Host Name")
          .put(HostKeys.HOST_IP.id(), "Host IP")
          .put(HostKeys.CONNECTIVITY.id(), "Host Lab Server Connectivity")
          .put(HostKeys.HOST_OS.id(), "Host OS")
          .put(HostKeys.LAB_SERVER_VERSION.id(), "Host Lab Server Version")
          .put(HostKeys.DEVICE_COUNT.id(), "Device Count")
          .put(HostKeys.PREFIX_HOST_FIELD + "lab_type", "Host Lab Type")
          .put(HostKeys.PREFIX_HOST_FIELD + "daemon_status", "Host Daemon Server Status")
          .put(HostKeys.PREFIX_HOST_FIELD + "daemon_server_version", "Host Daemon Server Version")
          .put(HostKeys.PREFIX_HOST_FIELD + "release_status", "Host Release Status")
          .put(HostKeys.PREFIX_HOST_FIELD + "release_type", "Host Release Type")
          .put(HostKeys.PREFIX_HOST_FIELD + "ats_controller", "ATS Lab")
          .buildOrThrow();

  /** Returns the bare name of a namespaced key (e.g. "dimension::model" -> "model"). */
  static String bareName(String keyId) {
    int separator = keyId.lastIndexOf("::");
    return separator >= 0 ? keyId.substring(separator + 2) : keyId;
  }

  /** Standard display name for a key (clean name for tables, column headers). */
  static String standardDisplayName(String keyId) {
    String builtin = BUILTIN_DISPLAY_NAMES.get(keyId);
    if (builtin != null) {
      return builtin;
    }
    return bareName(keyId);
  }

  /**
   * Title display name for dialogs and suggestion main texts (Special Cases 1 & 2): prepends
   * "Dimension " / "Host Property " for discovered long-tail keys.
   */
  static String titleDisplayName(String keyId) {
    String builtin = BUILTIN_DISPLAY_NAMES.get(keyId);
    if (builtin != null) {
      return builtin;
    }
    if (keyId.startsWith(DeviceKeys.PREFIX_DIMENSION)) {
      return "Dimension " + bareName(keyId);
    }
    if (keyId.startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
      return "Host Property " + bareName(keyId);
    }
    return bareName(keyId);
  }

  /**
   * Short chip label for pills: built-in display name for built-ins, bare name for dimensions, and
   * "Host " prefix for host properties.
   */
  static String pillKey(String keyId) {
    String builtin = BUILTIN_DISPLAY_NAMES.get(keyId);
    if (builtin != null) {
      return builtin;
    }
    if (keyId.startsWith(DeviceKeys.PREFIX_DIMENSION)) {
      return bareName(keyId);
    }
    if (keyId.startsWith(HostKeys.PREFIX_HOST_PROPERTY)) {
      return "Host " + bareName(keyId);
    }
    return bareName(keyId);
  }

  private FleetKeyDisplays() {}
}
