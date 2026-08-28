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
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;

/**
 * The per-deployment curation of fleet search keys: which keys to promote in the query bar, which
 * columns to show by default, and how to rank keys in the suggester.
 *
 * <p>Every fact here is deployment dependent, bound per {@link
 * com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet}. Consumers inject {@code
 * Map<Fleet, ScenarioCuration>} and pick the entry for the request's fleet.
 *
 * <p>The OSS build binds a single ats-one curation under {@code FLEET_SELF}. The internal build
 * binds a 1p curation under {@code FLEET_SELF} and an ats-all curation under {@code FLEET_ATS}. The
 * ats-one and 1p curations share the same fleet key but differ by build, so each build installs its
 * own module rather than switching on the fleet enum.
 *
 * <p>All key lists return typed {@link DeviceKeyDescriptor} and {@link HostKeyDescriptor} objects
 * directly from the schema layer, preserving display names, grammatical number, and key attributes
 * with compile-time type safety.
 */
public interface ScenarioCuration {

  /** The keys promoted into the "Filter by:" row of the query bar, in display order. */
  ImmutableList<DeviceKeyDescriptor> deviceFilterByRow();

  /** The keys promoted into the "Group by:" row of the query bar, in display order. */
  ImmutableList<DeviceKeyDescriptor> deviceGroupByRow();

  /** The default column set for the flat search results table, in display order. */
  ImmutableList<DeviceKeyDescriptor> deviceDefaultColumns();

  /** The recommended columns offered in the column catalog, in display order. */
  ImmutableList<DeviceKeyDescriptor> deviceRecommendedColumns();

  // ---- Host-entity curation ----

  /** The keys promoted into the host "Filter by:" row of the query bar, in display order. */
  ImmutableList<HostKeyDescriptor> hostFilterByRow();

  /** The keys promoted into the host "Group by:" row of the query bar, in display order. */
  ImmutableList<HostKeyDescriptor> hostGroupByRow();

  /** The default column set for the host flat search results table, in display order. */
  ImmutableList<HostKeyDescriptor> hostDefaultColumns();

  /** The recommended host columns offered in the column catalog, in display order. */
  ImmutableList<HostKeyDescriptor> hostRecommendedColumns();

  /**
   * The suggester ranking for {@code keyId}: a higher value means the key is offered earlier. The
   * mapping is scenario aware, so the same key can rank differently in different deployments.
   */
  int keyPriority(String keyId);

  /**
   * The suggester ranking for {@code keyId} in the given {@code entity}: a higher value means the
   * key is offered earlier. Defaults to the device ranking ({@link #keyPriority(String)}).
   */
  default int keyPriority(String keyId, SearchEntity entity) {
    return keyPriority(keyId);
  }

  /**
   * Whether the search page shows a landing page before the first query. A deployment whose fleet
   * is small enough to browse directly, such as a single local ATS controller, returns false so the
   * page opens straight into results.
   */
  boolean landingEnabled();
}
