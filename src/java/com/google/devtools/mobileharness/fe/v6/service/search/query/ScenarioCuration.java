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
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.HostKeyRegistry;

/**
 * The per-deployment curation of fleet search keys: which keys to promote in the query bar, which
 * columns to show by default, and how to rank keys in the suggester.
 *
 * <p>Every fact here is deployment dependent, bound per {@link
 * com.google.devtools.mobileharness.fe.v6.service.proto.search.Fleet}. Consumers inject {@code
 * Map<Fleet, ScenarioCuration>} and look up the curation for the request's fleet.
 */
public interface ScenarioCuration {

  /** The keys promoted into the device "Filter by:" row of the query bar, in display order. */
  ImmutableList<DeviceKeyDescriptor> deviceFilterByRow();

  /** The keys promoted into the device "Group by:" row of the query bar, in display order. */
  ImmutableList<DeviceKeyDescriptor> deviceGroupByRow();

  /** The default column set for the device flat search results table, in display order. */
  ImmutableList<DeviceKeyDescriptor> deviceDefaultColumns();

  /** The recommended device columns offered in the column catalog, in display order. */
  ImmutableList<DeviceKeyDescriptor> deviceRecommendedColumns();

  /** The keys promoted into the host "Filter by:" row of the query bar, in display order. */
  ImmutableList<HostKeyDescriptor> hostFilterByRow();

  /** The keys promoted into the host "Group by:" row of the query bar, in display order. */
  ImmutableList<HostKeyDescriptor> hostGroupByRow();

  /** The default column set for the host flat search results table, in display order. */
  ImmutableList<HostKeyDescriptor> hostDefaultColumns();

  /** The recommended host columns offered in the column catalog, in display order. */
  ImmutableList<HostKeyDescriptor> hostRecommendedColumns();

  /** Candidate group-by keys offered when typing "group by" in device search. */
  default ImmutableList<DeviceKeyDescriptor> deviceGroupByCandidates() {
    return deviceGroupByRow();
  }

  /** Starter keys offered when the device search bar input is empty. */
  default ImmutableList<DeviceKeyDescriptor> deviceEmptyStateKeys() {
    return deviceFilterByRow();
  }

  /** Candidate group-by keys offered when typing "group by" in host search. */
  default ImmutableList<HostKeyDescriptor> hostGroupByCandidates() {
    return hostGroupByRow();
  }

  /** Starter keys offered when the host search bar input is empty. */
  default ImmutableList<HostKeyDescriptor> hostEmptyStateKeys() {
    return hostFilterByRow();
  }

  /** Suggester ranking and pattern eligibility policy for this scenario. */
  KeyPriority keyPriority();

  /**
   * Symmetrical, strongly-typed ranking and suggestion eligibility policy for device and host keys.
   */
  interface KeyPriority {
    /** Ranking for a device key: higher value means offered earlier. */
    int devicePriority(DeviceKeyDescriptor key);

    /** Ranking for a host key: higher value means offered earlier. */
    int hostPriority(HostKeyDescriptor key);
  }

  /** Returns the registry of valid keys and parser for device search in this deployment. */
  DeviceKeyRegistry deviceKeyRegistry();

  /** Returns the registry of valid keys and parser for host search in this deployment. */
  HostKeyRegistry hostKeyRegistry();

  /**
   * Whether the search page shows a landing page before the first query. A deployment whose fleet
   * is small enough to browse directly, such as a single local ATS controller, returns false so the
   * page opens straight into results.
   */
  boolean landingEnabled();
}
