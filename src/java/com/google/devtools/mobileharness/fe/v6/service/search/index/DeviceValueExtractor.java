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

import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.CONFIG_WIFI_SSID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_PREFIX;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_QUARANTINED;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DECORATOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DRIVER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_EXECUTOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_OWNER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_UUID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_ATS_CONTROLLER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_CONNECTIVITY;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DAEMON_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_IP;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_SERVER_VERSION;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_OS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_RELEASE_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_RELEASE_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PROP_PREFIX;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;

/**
 * Extracts the lowercased value set for a given key from a {@link DeviceRecord}'s forward store.
 *
 * <p>This mirrors what the index builder records, so posting lists built from the forward store
 * match the index's sorted values and counts. Used by both {@link LazyPostings} (to build posting
 * lists on demand) and the filter engine (for exact-set matching).
 */
public final class DeviceValueExtractor {

  private DeviceValueExtractor() {}

  /** Returns the device's lowercased value set for the key. */
  public static ImmutableSet<String> valuesForKey(DeviceRecord device, String keyId) {
    return switch (keyId) {
      case FIELD_UUID -> singletonLower(device.deviceId());
      case FIELD_STATUS -> singletonLower(device.status());
      case FIELD_TYPE -> lowercasedSet(device.types());
      case FIELD_OWNER -> lowercasedSet(device.owners());
      case FIELD_DRIVER -> lowercasedSet(device.drivers());
      case FIELD_DECORATOR -> lowercasedSet(device.decorators());
      case FIELD_EXECUTOR -> lowercasedSet(device.executors());
      case DIM_QUARANTINED -> ImmutableSet.of(device.quarantined() ? "yes" : "no");
      case HOST_NAME -> singletonLower(device.hostName());
      case HOST_IP -> singletonLower(device.hostIp());
      case CONFIG_WIFI_SSID ->
          device.wifiSsid().isPresent()
              ? ImmutableSet.of(Ascii.toLowerCase(device.wifiSsid().get()))
              : ImmutableSet.of();
      case HOST_ATS_CONTROLLER ->
          device.atsController().isPresent()
              ? ImmutableSet.of(Ascii.toLowerCase(device.atsController().get()))
              : ImmutableSet.of();
      case HOST_LAB_TYPE -> lowercasedSet(device.labTypes());
      case HOST_OS -> singletonLower(device.hostOs());
      case HOST_CONNECTIVITY -> singletonLower(device.hostConnectivity());
      case HOST_DAEMON_STATUS -> optionalLower(device.daemonStatus());
      case HOST_RELEASE_STATUS -> optionalLower(device.releaseStatus());
      case HOST_RELEASE_TYPE -> optionalLower(device.releaseType());
      case HOST_LAB_SERVER_VERSION -> optionalLower(device.labServerVersion());
      default -> valuesForPrefixedKey(device, keyId);
    };
  }

  private static ImmutableSet<String> valuesForPrefixedKey(DeviceRecord device, String keyId) {
    if (keyId.startsWith(DIM_PREFIX)) {
      return lowercasedSet(
          device
              .dimensions()
              .getOrDefault(keyId.substring(DIM_PREFIX.length()), ImmutableList.of()));
    }
    if (keyId.startsWith(PROP_PREFIX)) {
      String value = device.hostProperties().get(keyId.substring(PROP_PREFIX.length()));
      return value == null ? ImmutableSet.of() : singletonLower(value);
    }
    return ImmutableSet.of();
  }

  private static ImmutableSet<String> singletonLower(String value) {
    return value.isEmpty() ? ImmutableSet.of() : ImmutableSet.of(Ascii.toLowerCase(value));
  }

  private static ImmutableSet<String> optionalLower(Optional<String> value) {
    return value.map(DeviceValueExtractor::singletonLower).orElse(ImmutableSet.of());
  }

  private static ImmutableSet<String> lowercasedSet(List<String> values) {
    ImmutableSet.Builder<String> result = ImmutableSet.builder();
    for (String value : values) {
      // Skip empty values so a device with an empty entry counts as no-value for
      // the key, matching singletonLower and the index builder.
      if (value.isEmpty()) {
        continue;
      }
      result.add(Ascii.toLowerCase(value));
    }
    return result.build();
  }
}
