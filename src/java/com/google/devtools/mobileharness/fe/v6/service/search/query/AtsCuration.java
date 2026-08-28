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

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.query.FleetKeyPriority.Scenario;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.AtsDeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import javax.inject.Inject;

/**
 * The ats {@link ScenarioCuration}: the OSS build's curation for a single local ATS controller.
 *
 * <p>This is the {@link Fleet#FLEET_SELF} curation in the OSS build. It promotes and defaults the
 * WiFi SSID key ({@code device_config::wifi_ssid}) that ats enriches devices with, and omits the
 * partner-ats-only controller axis. Its {@link #keyPriority} delegates to the shared {@link
 * FleetKeyPriority} with {@link Scenario#ATS}, so the ats versus internal distinction stays here in
 * the build-specific impl rather than in a fleet switch.
 */
public final class AtsCuration implements ScenarioCuration {

  @Inject
  AtsCuration() {}

  @Override
  public ImmutableList<String> deviceFilterByRow() {
    return ImmutableList.of(
        DeviceKeys.UUID.id(),
        HostKeys.HOST_NAME.id(),
        DeviceKeys.STATUS.id(),
        DeviceKeys.MODEL.id(),
        DeviceKeys.SDK_VERSION.id(),
        AtsDeviceKeys.WIFI_SSID.id());
  }

  @Override
  public ImmutableList<String> deviceGroupByRow() {
    return ImmutableList.of(HostKeys.HOST_NAME.id(), AtsDeviceKeys.WIFI_SSID.id());
  }

  @Override
  public ImmutableList<String> deviceDefaultColumns() {
    return ImmutableList.of(
        DeviceKeys.UUID.id(),
        HostKeys.HOST_NAME.id(),
        DeviceKeys.STATUS.id(),
        DeviceKeys.MODEL.id(),
        DeviceKeys.OS.id(),
        AtsDeviceKeys.WIFI_SSID.id());
  }

  @Override
  public ImmutableList<String> deviceRecommendedColumns() {
    return ImmutableList.of(
        HostKeys.HOST_NAME.id(),
        DeviceKeys.STATUS.id(),
        DeviceKeys.TYPE.id(),
        DeviceKeys.MODEL.id(),
        DeviceKeys.SDK_VERSION.id(),
        DeviceKeys.DEVICE_CLASS_NAME.id(),
        DeviceKeys.MANUFACTURER.id(),
        AtsDeviceKeys.WIFI_SSID.id());
  }

  // ---- Host-entity curation ----
  //
  // Ported verbatim from the prototype's ats HOST_* lists (suggest_engine.py). ats promotes
  // nothing into the host "Group by:" row, so its group-by list is empty.

  @Override
  public ImmutableList<String> hostFilterByRow() {
    return ImmutableList.of(
        HostKeys.HOST_NAME.id(), HostKeys.CONNECTIVITY.id(), HostKeys.DEVICE_COUNT.id());
  }

  @Override
  public ImmutableList<String> hostGroupByRow() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableList<String> hostDefaultColumns() {
    return ImmutableList.of(
        HostKeys.HOST_NAME.id(),
        HostKeys.CONNECTIVITY.id(),
        HostKeys.DEVICE_COUNT.id(),
        HostKeys.HOST_OS.id());
  }

  @Override
  public ImmutableList<String> hostRecommendedColumns() {
    return ImmutableList.of(
        HostKeys.HOST_NAME.id(),
        HostKeys.CONNECTIVITY.id(),
        HostKeys.DEVICE_COUNT.id(),
        HostKeys.HOST_OS.id(),
        HostKeys.LAB_SERVER_VERSION.id(),
        HostKeys.HOST_IP.id());
  }

  @Override
  public int keyPriority(String keyId) {
    return FleetKeyPriority.priority(keyId, Scenario.ATS);
  }

  @Override
  public int keyPriority(String keyId, SearchEntity entity) {
    return FleetKeyPriority.priority(keyId, Scenario.ATS, entity);
  }

  @Override
  public boolean landingEnabled() {
    // A single local ATS controller is small enough to browse directly, so no landing page.
    return false;
  }
}
