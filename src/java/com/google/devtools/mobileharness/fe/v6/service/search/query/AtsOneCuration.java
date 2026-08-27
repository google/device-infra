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
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_UUID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_CONNECTIVITY;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_DEVICE_COUNT;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_IP;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_LAB_SERVER_VERSION;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_OS;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetKeyPriority.Scenario;
import javax.inject.Inject;

/**
 * The ats-one {@link ScenarioCuration}: the OSS build's curation for a single local ATS controller.
 *
 * <p>This is the {@link Fleet#FLEET_SELF} curation in the OSS build. It promotes and defaults the
 * WiFi SSID key ({@code config::wifi_ssid}) that ats-one enriches devices with, and omits the
 * ats-all-only controller axis. Its {@link #keyPriority} delegates to the shared {@link
 * FleetKeyPriority} with {@link Scenario#ATS_ONE}, so the ats-one versus 1p distinction stays here
 * in the build-specific impl rather than in a fleet switch.
 */
public final class AtsOneCuration implements ScenarioCuration {

  @Inject
  AtsOneCuration() {}

  @Override
  public ImmutableList<String> deviceFilterByRow() {
    return ImmutableList.of(
        FIELD_UUID,
        HOST_NAME,
        FIELD_STATUS,
        "dimension::model",
        "dimension::sdk_version",
        CONFIG_WIFI_SSID);
  }

  @Override
  public ImmutableList<String> deviceGroupByRow() {
    return ImmutableList.of(HOST_NAME, CONFIG_WIFI_SSID);
  }

  @Override
  public ImmutableList<String> deviceDefaultColumns() {
    return ImmutableList.of(
        FIELD_UUID, HOST_NAME, FIELD_STATUS, "dimension::model", "dimension::os", CONFIG_WIFI_SSID);
  }

  @Override
  public ImmutableList<String> deviceRecommendedColumns() {
    return ImmutableList.of(
        HOST_NAME,
        FIELD_STATUS,
        FIELD_TYPE,
        "dimension::model",
        "dimension::sdk_version",
        "dimension::device_class_name",
        "dimension::manufacturer",
        CONFIG_WIFI_SSID);
  }

  // ---- Host-entity curation ----
  //
  // Ported verbatim from the prototype's ats-one HOST_* lists (suggest_engine.py). ats-one promotes
  // nothing into the host "Group by:" row, so its group-by list is empty.

  @Override
  public ImmutableList<String> hostFilterByRow() {
    return ImmutableList.of(HOST_NAME, HOST_CONNECTIVITY, HOST_DEVICE_COUNT);
  }

  @Override
  public ImmutableList<String> hostGroupByRow() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableList<String> hostDefaultColumns() {
    return ImmutableList.of(HOST_NAME, HOST_CONNECTIVITY, HOST_DEVICE_COUNT, HOST_OS);
  }

  @Override
  public ImmutableList<String> hostRecommendedColumns() {
    return ImmutableList.of(
        HOST_NAME, HOST_CONNECTIVITY, HOST_DEVICE_COUNT, HOST_OS, HOST_LAB_SERVER_VERSION, HOST_IP);
  }

  @Override
  public int keyPriority(String keyId) {
    return FleetKeyPriority.priority(keyId, Scenario.ATS_ONE);
  }

  @Override
  public int keyPriority(String keyId, SearchEntity entity) {
    return FleetKeyPriority.priority(keyId, Scenario.ATS_ONE, entity);
  }

  @Override
  public boolean landingEnabled() {
    // A single local ATS controller is small enough to browse directly, so no landing page.
    return false;
  }
}
