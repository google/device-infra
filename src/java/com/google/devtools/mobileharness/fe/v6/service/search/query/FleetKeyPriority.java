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

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;

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
          "dimension::model",
          "dimension::os",
          "dimension::sdk_version",
          HOST_NAME,
          "dimension::device_class_name",
          "dimension::manufacturer",
          FIELD_OWNER,
          DIM_QUARANTINED,
          HOST_LAB_TYPE,
          HOST_RELEASE_STATUS);

  /**
   * The secondary device keys: useful but ranked below tier 1. These are the finer-grained lab,
   * host, and capability keys, again deployment independent.
   */
  public static final ImmutableSet<String> KEY_TIER2 =
      ImmutableSet.of(
          FIELD_DRIVER,
          FIELD_DECORATOR,
          HOST_IP,
          HOST_OS,
          HOST_CONNECTIVITY,
          HOST_LAB_SERVER_VERSION,
          FIELD_EXECUTOR,
          "dimension::software_version",
          "dimension::device_form",
          "host_field::lab_server_activity",
          HOST_DAEMON_STATUS,
          HOST_RELEASE_TYPE);

  /**
   * The most important host keys: always ranked highest for the host entity. Ported from the
   * prototype's {@code HOST_KEY_TIER1}.
   */
  public static final ImmutableSet<String> HOST_KEY_TIER1 =
      ImmutableSet.of(
          HOST_NAME,
          HOST_CONNECTIVITY,
          HOST_DEVICE_COUNT,
          HOST_OS,
          HOST_LAB_SERVER_VERSION,
          HOST_LAB_TYPE,
          HOST_RELEASE_STATUS,
          "host_field::lab_server_activity");

  /**
   * The secondary host keys: useful but ranked below host tier 1. Ported from the prototype's
   * {@code HOST_KEY_TIER2}.
   */
  public static final ImmutableSet<String> HOST_KEY_TIER2 =
      ImmutableSet.of(HOST_IP, HOST_RELEASE_TYPE, HOST_DAEMON_STATUS);

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

  /**
   * The suggester priority for {@code keyId} in {@code scenario} for the given {@code entity}:
   * higher means offered earlier.
   *
   * <p>For the device entity this is exactly {@link #priority(String, Scenario)}, so device ranking
   * is unchanged. For the host entity a separate host tier table applies, mirroring the prototype
   * {@code _key_priority} host branch:
   *
   * <ul>
   *   <li>{@code host::ats_controller}: 3 in ats-all (its defining axis) else 1.
   *   <li>a host tier 1 key: 3.
   *   <li>a host tier 2 key: 1 in ats-one (which keeps its list short) else 2.
   *   <li>any other key: 1 in 1p else 0 (kept out of the way in both ATS scenarios).
   * </ul>
   */
  public static int priority(String keyId, Scenario scenario, SearchEntity entity) {
    if (entity != SearchEntity.SEARCH_ENTITY_HOST) {
      return priority(keyId, scenario);
    }
    if (keyId.equals(HOST_ATS_CONTROLLER)) {
      return scenario == Scenario.ATS_ALL ? 3 : 1;
    }
    if (HOST_KEY_TIER1.contains(keyId)) {
      return 3;
    }
    if (HOST_KEY_TIER2.contains(keyId)) {
      return scenario == Scenario.ATS_ONE ? 1 : 2;
    }
    return scenario == Scenario.ONE_P ? 1 : 0;
  }
}
