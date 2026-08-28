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

import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetLandingConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.KeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeys;
import javax.inject.Inject;

/**
 * Builds the page-load {@link FleetSearchConfig} for GetFleetSearchConfig: the column setup and
 * landing content the frontend needs before its first query.
 *
 * <p>The config draws its keys from the request's {@link ScenarioCuration}: the recommended columns
 * offered in the column selector, and the default columns shown on a first visit.
 *
 * <p>The landing count is the size of the searched entity: the device count for a device search and
 * the host count for a host search.
 */
public final class FleetSearchConfigProvider {

  @Inject
  FleetSearchConfigProvider() {}

  /**
   * Returns the page-load config for one request.
   *
   * @param snapshot the fleet snapshot to size the landing count from
   * @param request the entity and fleet the page is loading for
   * @param curation the per-deployment curation supplying the recommended and default columns
   */
  public FleetSearchConfig getConfig(
      FleetSnapshot snapshot, FleetSearchConfigRequest request, ScenarioCuration curation) {
    boolean host = request.getEntity() == SearchEntity.SEARCH_ENTITY_HOST;
    FleetColumnConfig.Builder columns = FleetColumnConfig.newBuilder();

    if (host) {
      String identifierKey = HostKeys.HOST_NAME.id();
      for (HostKeyDescriptor descriptor : curation.hostRecommendedColumns()) {
        columns.addRecommended(
            KeyDescriptor.newBuilder()
                .setKey(descriptor.id())
                .setDisplayName(descriptor.display().name()));
      }
      for (HostKeyDescriptor descriptor : curation.hostDefaultColumns()) {
        columns.addDefaults(
            FleetColumnDescriptor.newBuilder()
                .setKey(descriptor.id())
                .setDisplayName(descriptor.display().name())
                .setLocked(descriptor.id().equals(identifierKey)));
      }
    } else {
      String identifierKey = DeviceKeys.UUID.id();
      for (DeviceKeyDescriptor descriptor : curation.deviceRecommendedColumns()) {
        columns.addRecommended(
            KeyDescriptor.newBuilder()
                .setKey(descriptor.id())
                .setDisplayName(descriptor.display().name()));
      }
      for (DeviceKeyDescriptor descriptor : curation.deviceDefaultColumns()) {
        columns.addDefaults(
            FleetColumnDescriptor.newBuilder()
                .setKey(descriptor.id())
                .setDisplayName(descriptor.display().name())
                .setLocked(descriptor.id().equals(identifierKey)));
      }
    }

    FleetLandingConfig landing =
        FleetLandingConfig.newBuilder()
            .setEnabled(curation.landingEnabled())
            .setBrowseAllCount(browseAllCount(snapshot, request.getEntity()))
            .build();

    return FleetSearchConfig.newBuilder().setColumns(columns).setLanding(landing).build();
  }

  /**
   * The total count for the "Browse all N" button: devices for a device search, hosts for a host
   * search.
   */
  private static int browseAllCount(FleetSnapshot snapshot, SearchEntity entity) {
    return switch (entity) {
      case SEARCH_ENTITY_HOST -> snapshot.hosts().size();
      default -> snapshot.deviceCount();
    };
  }
}
