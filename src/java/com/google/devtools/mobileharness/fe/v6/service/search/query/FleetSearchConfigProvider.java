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

import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_UUID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;

import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetLandingConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchConfigRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.KeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import javax.inject.Inject;

/**
 * Builds the page-load {@link FleetSearchConfig} for GetFleetSearchConfig: the column setup and
 * landing content the frontend needs before its first query.
 *
 * <p>The config draws its keys from the request's {@link ScenarioCuration}: the recommended columns
 * offered in the column selector, and the default columns shown on a first visit. A key's display
 * name is the fleet index's name for it when the fleet carries the key, and a name derived from the
 * key namespace otherwise, matching {@code FleetCellMapper} and {@code FleetColumnCataloger} so a
 * curated key that no device currently carries still names its column.
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
    // A host search reads its column names from the host index and its curated column lists from
    // the
    // host curation methods; a device search reads the device index and the device curation
    // methods.
    // The identifier and landing count are entity-aware below on their own.
    FleetIndex index = host ? snapshot.hostIndex() : snapshot.index();
    String identifierKey = identifierKey(request.getEntity());

    FleetColumnConfig.Builder columns = FleetColumnConfig.newBuilder();
    for (String keyId : host ? curation.hostRecommendedColumns() : curation.recommendedColumns()) {
      columns.addRecommended(
          KeyDescriptor.newBuilder().setKey(keyId).setDisplayName(displayName(index, keyId)));
    }
    for (String keyId : host ? curation.hostDefaultColumns() : curation.defaultColumns()) {
      columns.addDefaults(
          FleetColumnDescriptor.newBuilder()
              .setKey(keyId)
              .setDisplayName(displayName(index, keyId))
              .setLocked(keyId.equals(identifierKey)));
    }

    FleetLandingConfig landing =
        FleetLandingConfig.newBuilder()
            .setEnabled(curation.landingEnabled())
            .setBrowseAllCount(browseAllCount(snapshot, request.getEntity()))
            .build();

    return FleetSearchConfig.newBuilder().setColumns(columns).setLanding(landing).build();
  }

  /**
   * The key locked as an always-shown, non-removable column: the searched entity's primary
   * identifier. A device is identified by its UUID and a host by its name.
   */
  private static String identifierKey(SearchEntity entity) {
    return switch (entity) {
      case SEARCH_ENTITY_HOST -> HOST_NAME;
      default -> FIELD_UUID;
    };
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

  /**
   * The key display name from the fleet index, falling back to a namespace-derived name when
   * absent.
   */
  private static String displayName(FleetIndex index, String keyId) {
    return index.displayNames().getOrDefault(keyId, deriveDisplayName(keyId));
  }

  /**
   * Derives a display name from a key id for keys absent from the fleet index. Mirrors the
   * namespace derivation in {@code FleetCellMapper} and {@code FleetColumnCataloger}: {@code dim::}
   * and {@code prop::} keys are prefixed, and every other namespace shows its bare name.
   */
  private static String deriveDisplayName(String keyId) {
    int separator = keyId.indexOf("::");
    String namespace = separator >= 0 ? keyId.substring(0, separator) : "";
    String name = separator >= 0 ? keyId.substring(separator + 2) : keyId;
    return switch (namespace) {
      case "dim" -> "Dimension " + name;
      case "prop" -> "Host Property " + name;
      default -> name;
    };
  }
}
