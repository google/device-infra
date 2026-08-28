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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;

/**
 * The shared, deployment-independent core of the suggester key ranking: the global tier sets plus
 * the base tier-to-priority mapping that every {@link ScenarioCuration} reuses.
 *
 * <p>Every curation impl delegates its {@link ScenarioCuration#keyPriority} to {@link
 * #priority(String, Scenario)}, passing the {@link Scenario} it represents. The scenario, not the
 * {@link com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet}, drives the rules,
 * because ats and internal share the {@code FLEET_SELF} fleet and differ only by build. This shared
 * helper cannot know the build, so the impl (which does) chooses the scenario and keeps the ats
 * versus internal distinction out of any fleet switch.
 */
public final class FleetKeyPriority {

  private FleetKeyPriority() {}

  /**
   * The deployment a curation represents. {@link #INTERNAL} and {@link #ATS} both map to {@code
   * FLEET_SELF} but differ by build, so the impl passes the right one rather than deriving it from
   * the fleet enum.
   */
  public enum Scenario {
    /** 1p (MH master), internal build. */
    INTERNAL,
    /** partner-ats: the aggregated view across all partner ATS controllers, internal build. */
    PARTNER_ATS,
    /** ats: a single local ATS controller, OSS build. */
    ATS
  }

  /**
   * The most important device keys: always ranked highest. These are the identity, status, and
   * headline hardware keys an operator reaches for first, true across every deployment.
   */
  public static final ImmutableSet<String> KEY_TIER1 =
      ImmutableSet.of(
          DeviceKeys.UUID.id(),
          DeviceKeys.STATUS.id(),
          DeviceKeys.TYPE.id(),
          DeviceKeys.MODEL.id(),
          DeviceKeys.OS.id(),
          DeviceKeys.SDK_VERSION.id(),
          HostKeys.HOST_NAME.id(),
          DeviceKeys.DEVICE_CLASS_NAME.id(),
          DeviceKeys.MANUFACTURER.id(),
          DeviceKeys.PREFIX_DEVICE_FIELD + "owner",
          DeviceKeys.PREFIX_DEVICE_FIELD + "quarantined",
          HostKeys.PREFIX_HOST_FIELD + "lab_type",
          HostKeys.PREFIX_HOST_FIELD + "release_status");

  /**
   * The secondary device keys: useful but ranked below tier 1. These are the finer-grained lab,
   * host, and capability keys, again deployment independent.
   */
  public static final ImmutableSet<String> KEY_TIER2 =
      ImmutableSet.of(
          DeviceKeys.DRIVER.id(),
          DeviceKeys.DECORATOR.id(),
          HostKeys.HOST_IP.id(),
          HostKeys.HOST_OS.id(),
          HostKeys.CONNECTIVITY.id(),
          HostKeys.LAB_SERVER_VERSION.id(),
          DeviceKeys.PREFIX_DEVICE_FIELD + "executor",
          DeviceKeys.SOFTWARE_VERSION.id(),
          DeviceKeys.DEVICE_FORM.id(),
          "host_field::lab_server_activity",
          HostKeys.PREFIX_HOST_FIELD + "daemon_status",
          HostKeys.PREFIX_HOST_FIELD + "release_type");

  /**
   * The most important host keys: always ranked highest for the host entity. Ported from the
   * prototype's {@code HOST_KEY_TIER1}.
   */
  public static final ImmutableSet<String> HOST_KEY_TIER1 =
      ImmutableSet.of(
          HostKeys.HOST_NAME.id(),
          HostKeys.CONNECTIVITY.id(),
          HostKeys.DEVICE_COUNT.id(),
          HostKeys.HOST_OS.id(),
          HostKeys.LAB_SERVER_VERSION.id(),
          HostKeys.PREFIX_HOST_FIELD + "lab_type",
          HostKeys.PREFIX_HOST_FIELD + "release_status",
          "host_field::lab_server_activity");

  /**
   * The secondary host keys: useful but ranked below host tier 1. Ported from the prototype's
   * {@code HOST_KEY_TIER2}.
   */
  public static final ImmutableSet<String> HOST_KEY_TIER2 =
      ImmutableSet.of(
          HostKeys.HOST_IP.id(),
          HostKeys.PREFIX_HOST_FIELD + "release_type",
          HostKeys.PREFIX_HOST_FIELD + "daemon_status");

  /**
   * The suggester priority for {@code keyId} in {@code scenario}: higher means offered earlier.
   *
   * <p>The rules mirror the prototype {@code _key_priority} for the device entity:
   *
   * <ul>
   *   <li>{@code host::ats_controller}: 3 in partner-ats (its defining axis) else 1.
   *   <li>{@code config::wifi_ssid}: 3 in internal and ats (where WiFi is curated) else 1.
   *   <li>a tier 1 key: 3.
   *   <li>a tier 2 key: 1 in ats (which keeps its list short) else 2.
   *   <li>any other key (a raw discovered dimension or property): 0 in partner-ats and ats (kept
   *       out of the way) else 1.
   * </ul>
   */
  public static int priority(String keyId, Scenario scenario) {
    if (keyId.equals(HostKeys.PREFIX_HOST_FIELD + "ats_controller")) {
      return scenario == Scenario.PARTNER_ATS ? 3 : 1;
    }
    if (keyId.equals(AtsDeviceKeys.WIFI_SSID.id())) {
      return scenario != Scenario.PARTNER_ATS ? 3 : 1;
    }
    if (KEY_TIER1.contains(keyId)) {
      return 3;
    }
    if (KEY_TIER2.contains(keyId)) {
      return scenario == Scenario.ATS ? 1 : 2;
    }
    return scenario == Scenario.INTERNAL ? 1 : 0;
  }

  /**
   * The suggester priority for {@code keyId} in {@code scenario} for the given {@code entity}:
   * higher means offered earlier.
   *
   * <p>For the device entity this is exactly {@link #priority(String, Scenario)}, so device ranking
   * is unchanged. For the host entity a separate host tier table applies, mirroring the prototype
   * {@code _key_priority} host branch:
   *
   * <ul>
   *   <li>{@code host::ats_controller}: 3 in partner-ats (its defining axis) else 1.
   *   <li>a host tier 1 key: 3.
   *   <li>a host tier 2 key: 1 in ats (which keeps its list short) else 2.
   *   <li>any other key: 1 in internal else 0 (kept out of the way in both ATS scenarios).
   * </ul>
   */
  public static int priority(String keyId, Scenario scenario, SearchEntity entity) {
    if (entity != SearchEntity.SEARCH_ENTITY_HOST) {
      return priority(keyId, scenario);
    }
    if (keyId.equals(HostKeys.PREFIX_HOST_FIELD + "ats_controller")) {
      return scenario == Scenario.PARTNER_ATS ? 3 : 1;
    }
    if (HOST_KEY_TIER1.contains(keyId)) {
      return 3;
    }
    if (HOST_KEY_TIER2.contains(keyId)) {
      return scenario == Scenario.ATS ? 1 : 2;
    }
    return scenario == Scenario.INTERNAL ? 1 : 0;
  }
}
