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

import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_ATS_CONTROLLER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_CONNECTIVITY;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DAEMON_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DEVICE_COUNT;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_IP;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_SERVER_VERSION;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_OS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_RELEASE_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_RELEASE_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PROP_PREFIX;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;

/**
 * Extracts the lowercased value set for a given key from a {@link HostRecord}'s forward store.
 *
 * <p>This mirrors exactly what {@link FleetIndexBuilder} stamps for a host, so posting lists built
 * from the host forward store match the host index's sorted values and counts. It is the
 * host-entity analogue of {@link DeviceValueExtractor}, applying the same empty-value skip so a
 * host with an absent attribute counts as no-value for the key.
 */
public final class HostValueExtractor {

  private HostValueExtractor() {}

  /** Returns the host's lowercased value set for the key. */
  public static ImmutableSet<String> valuesForKey(HostRecord host, String keyId) {
    return switch (keyId) {
      case HOST_NAME -> singletonLower(host.hostName());
      case HOST_IP -> singletonLower(host.hostIp());
      case HOST_OS -> singletonLower(host.hostOs());
      case HOST_CONNECTIVITY -> singletonLower(host.hostConnectivity());
      case HOST_LAB_TYPE -> lowercasedSet(host.labTypes());
      case HOST_DAEMON_STATUS -> optionalLower(host.daemonStatus());
      case HOST_RELEASE_STATUS -> optionalLower(host.releaseStatus());
      case HOST_RELEASE_TYPE -> optionalLower(host.releaseType());
      case HOST_LAB_SERVER_VERSION -> optionalLower(host.labServerVersion());
      case HOST_ATS_CONTROLLER -> optionalLower(host.atsController());
      case HOST_DEVICE_COUNT -> singletonLower(String.valueOf(host.deviceCount()));
      default -> valuesForPrefixedKey(host, keyId);
    };
  }

  private static ImmutableSet<String> valuesForPrefixedKey(HostRecord host, String keyId) {
    if (keyId.startsWith(PROP_PREFIX)) {
      String value = host.hostProperties().get(keyId.substring(PROP_PREFIX.length()));
      return value == null ? ImmutableSet.of() : singletonLower(value);
    }
    return ImmutableSet.of();
  }

  private static ImmutableSet<String> singletonLower(String value) {
    return value.isEmpty() ? ImmutableSet.of() : ImmutableSet.of(Ascii.toLowerCase(value));
  }

  private static ImmutableSet<String> optionalLower(Optional<String> value) {
    return value.map(HostValueExtractor::singletonLower).orElse(ImmutableSet.of());
  }

  private static ImmutableSet<String> lowercasedSet(List<String> values) {
    ImmutableSet.Builder<String> result = ImmutableSet.builder();
    for (String value : values) {
      // Skip empty values so a host with an empty entry counts as no-value for the key, matching
      // singletonLower and the index builder.
      if (value.isEmpty()) {
        continue;
      }
      result.add(Ascii.toLowerCase(value));
    }
    return result.build();
  }
}
