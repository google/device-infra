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
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Cell;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Column;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetUtilization;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * An entity-neutral projection of one fleet's searchable records for the query classes.
 *
 * <p>Every query class (filter engine, flat searcher, group searcher, value lister, column
 * cataloger, promoted keys provider, suggester) runs one generic code path over a corpus rather
 * than over a concrete {@link
 * com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot} plus record type. The
 * corpus supplies the value index and posting lists, the record identity and count, and the per-key
 * value projection (both the lowercased set used for filtering, partitioning, redundancy, and
 * combination counting, and the display-cased list used for sorting), plus the typed header and
 * cell that the record type dictates.
 *
 * <p>Keeping the projection inside the corpus, rather than behind a shared record interface,
 * confines all entity specifics to the corpus implementations: the device corpus projects a {@code
 * DeviceRecord} onto {@code FleetSearchKeys.FIELD_UUID} identity, device status cells, and device
 * utilization, while a future host corpus projects a host record onto host name identity, host
 * connectivity cells, and no utilization. The records stay pure AutoValue types with no proto
 * dependency.
 */
public interface SearchCorpus {

  /** The value index over the corpus records: sorted values, counts, displays, and key catalog. */
  FleetIndex index();

  /** The lazily built posting lists over the corpus records. */
  LazyPostings postings();

  /** Number of records in the corpus. */
  int recordCount();

  /** The stable id of the record at {@code index} (device UUID or host name). */
  String recordId(int index);

  /** The entity this corpus projects. */
  SearchEntity entity();

  /** The identifier key used as the default sort column (device UUID or host name). */
  String identifierKey();

  /**
   * Whether {@code keyId} is identifier-like, so its value list is shown without facet counts.
   * Mirrors {@code PLAIN_VALUE_KEYS} for devices and {@code HOST_PLAIN_VALUE_KEYS} for hosts.
   */
  boolean plainValueKey(String keyId);

  /**
   * The lowercased value set the record at {@code index} carries for {@code keyId}. Used for
   * filtering, partitioning, redundancy detection, and combination counting.
   */
  ImmutableSet<String> valuesForKey(int index, String keyId);

  /**
   * The display-cased values the record at {@code index} carries for {@code keyId}, in the record's
   * own order. Used to derive sort values.
   */
  ImmutableList<String> displayValues(int index, String keyId);

  /** The index-driven header for {@code keyId}. */
  Column column(String keyId);

  /** The typed cell for the record at {@code index} and {@code keyId}. */
  Cell cell(int index, String keyId);

  /**
   * The utilization breakdown for a group's member record indices, or empty when the entity has no
   * utilization concept (hosts). Present for devices.
   */
  Optional<FleetUtilization> utilization(List<Integer> memberIndices);

  /**
   * The scenario curation bound to this corpus's fleet, or null when none is installed yet. The
   * promoted keys provider reads its candidate rows from here.
   */
  @Nullable
  ScenarioCuration curation();
}
