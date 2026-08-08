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

import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.CONFIG_WIFI_SSID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_QUARANTINED;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DECORATOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DRIVER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_EXECUTOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_OWNER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_UUID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_ATS_CONTROLLER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_IP;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;

import com.google.common.collect.ImmutableSet;

/**
 * The shared, deployment-independent core of the suggester key ranking: the global tier sets plus
 * the base tier-to-priority mapping that every {@link ScenarioCuration} reuses.
 *
 * <p>Every curation impl delegates its {@link ScenarioCuration#keyPriority} to {@link
 * #priority(String, Scenario)}, passing the {@link Scenario} it represents. The scenario, not the
 * {@link com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet}, drives the rules,
 * because ats-one and 1p share the {@code FLEET_SELF} fleet and differ only by build. This shared
 * helper cannot know the build, so the impl (which does) chooses the scenario and keeps the ats-one
 * versus 1p distinction out of any fleet switch.
 */
public final class FleetKeyPriority {

  private FleetKeyPriority() {}

  /**
   * The deployment a curation represents. {@link #ONE_P} and {@link #ATS_ONE} both map to {@code
   * FLEET_SELF} but differ by build, so the impl passes the right one rather than deriving it from
   * the fleet enum.
   */
  public enum Scenario {
    /** 1p (MH master), internal build. */
    ONE_P,
    /** ats-all: the aggregated view across all partner ATS controllers, internal build. */
    ATS_ALL,
    /** ats-one: a single local ATS controller, OSS build. */
    ATS_ONE
  }

  /**
   * The most important device keys: always ranked highest. These are the identity, status, and
   * headline hardware keys an operator reaches for first, true across every deployment.
   */
  public static final ImmutableSet<String> KEY_TIER1 =
      ImmutableSet.of(
          FIELD_UUID,
          FIELD_STATUS,
          FIELD_TYPE,
          "dim::model",
          "dim::os",
          "dim::sdk_version",
          HOST_NAME,
          "dim::device_class_name",
          "dim::manufacturer",
          FIELD_OWNER,
          DIM_QUARANTINED,
          HOST_LAB_TYPE,
          "host::release_status");

  /**
   * The secondary device keys: useful but ranked below tier 1. These are the finer-grained lab,
   * host, and capability keys, again deployment independent.
   */
  public static final ImmutableSet<String> KEY_TIER2 =
      ImmutableSet.of(
          FIELD_DRIVER,
          FIELD_DECORATOR,
          HOST_IP,
          "host::host_os",
          "host::connectivity",
          "host::lab_server_version",
          FIELD_EXECUTOR,
          "dim::software_version",
          "dim::device_form",
          "host::lab_server_activity",
          "host::daemon_status",
          "host::release_type");

  /**
   * The suggester priority for {@code keyId} in {@code scenario}: higher means offered earlier.
   *
   * <p>The rules mirror the prototype {@code _key_priority} for the device entity:
   *
   * <ul>
   *   <li>{@code host::ats_controller}: 3 in ats-all (its defining axis) else 1.
   *   <li>{@code config::wifi_ssid}: 3 in 1p and ats-one (where WiFi is curated) else 1.
   *   <li>a tier 1 key: 3.
   *   <li>a tier 2 key: 1 in ats-one (which keeps its list short) else 2.
   *   <li>any other key (a raw discovered dimension or property): 0 in ats-all and ats-one (kept
   *       out of the way) else 1.
   * </ul>
   */
  public static int priority(String keyId, Scenario scenario) {
    if (keyId.equals(HOST_ATS_CONTROLLER)) {
      return scenario == Scenario.ATS_ALL ? 3 : 1;
    }
    if (keyId.equals(CONFIG_WIFI_SSID)) {
      return scenario != Scenario.ATS_ALL ? 3 : 1;
    }
    if (KEY_TIER1.contains(keyId)) {
      return 3;
    }
    if (KEY_TIER2.contains(keyId)) {
      return scenario == Scenario.ATS_ONE ? 1 : 2;
    }
    return scenario == Scenario.ONE_P ? 1 : 0;
  }
}
