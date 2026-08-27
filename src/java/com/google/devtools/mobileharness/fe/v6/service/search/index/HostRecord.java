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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * One host's indexed fields in the forward store.
 *
 * <p>Used by the host-entity search index. Keyed by canonical namespaced key IDs (e.g. {@code
 * host_field::*}, {@code host_property::*}).
 */
@AutoValue
public abstract class HostRecord {

  /** Host name. Primary identifier. */
  public abstract String hostName();

  /**
   * Unified key-to-values forward store.
   *
   * <p>Keyed by canonical namespaced key IDs (e.g. {@code host_field::*}, {@code
   * host_property::*}).
   */
  public abstract ImmutableMap<String, ImmutableList<String>> values();

  /** Returns the list of display values for the given canonical key ID, or empty list if absent. */
  public ImmutableList<String> values(String keyId) {
    return values().getOrDefault(keyId, ImmutableList.of());
  }

  /** Returns the lowercased normalized value set for indexing and search matching. */
  public ImmutableSet<String> normalizedValues(String keyId) {
    return values(keyId).stream()
        .filter(value -> !value.isEmpty())
        .map(Ascii::toLowerCase)
        .collect(toImmutableSet());
  }

  /** Number of devices on this host. Computed from LabInfo device list. */
  public abstract int deviceCount();

  public static HostRecord create(
      String hostName, ImmutableMap<String, ImmutableList<String>> values, int deviceCount) {
    return new AutoValue_HostRecord(hostName, values, deviceCount);
  }
}
