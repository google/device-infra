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
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.HostValueExtractor;
import com.google.devtools.mobileharness.fe.v6.service.search.index.Postings;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * The host projection of a {@link FleetSnapshot} for the query classes.
 *
 * <p>Records are the snapshot's hosts, identified by host name. The value projection delegates to
 * {@link HostValueExtractor} (lowercased sets) and {@link HostCellMapper} (display values, headers,
 * typed cells), so it mirrors exactly what the host index builder recorded. Utilization is a device
 * concept, so a host group carries no utilization breakdown and {@link #utilization} returns empty.
 */
public final class HostCorpus implements SearchCorpus {

  private final FleetSnapshot snapshot;
  private final Postings postings;
  @Nullable private final ScenarioCuration curation;
  private final HostCellMapper cellMapper = new HostCellMapper();

  public HostCorpus(
      FleetSnapshot snapshot, Postings postings, @Nullable ScenarioCuration curation) {
    this.snapshot = snapshot;
    this.postings = postings;
    this.curation = curation;
  }

  @Override
  public FleetIndex index() {
    return snapshot.hostIndex();
  }

  @Override
  public Postings postings() {
    return postings;
  }

  @Override
  public int recordCount() {
    return snapshot.hostCount();
  }

  @Override
  public String recordId(int index) {
    return snapshot.hosts().get(index).hostName();
  }

  @Override
  public SearchEntity entity() {
    return SearchEntity.SEARCH_ENTITY_HOST;
  }

  @Override
  public String identifierKey() {
    return FleetSearchKeys.HOST_NAME;
  }

  @Override
  public boolean plainValueKey(String keyId) {
    return FleetSearchKeys.HOST_PLAIN_VALUE_KEYS.contains(keyId);
  }

  @Override
  public ImmutableSet<String> valuesForKey(int index, String keyId) {
    return HostValueExtractor.valuesForKey(snapshot.hosts().get(index), keyId);
  }

  @Override
  public ImmutableList<String> displayValues(int index, String keyId) {
    return HostCellMapper.displayValues(snapshot.hosts().get(index), keyId, snapshot);
  }

  @Override
  public Column column(String keyId) {
    return cellMapper.column(keyId, snapshot);
  }

  @Override
  public Cell cell(int index, String keyId) {
    return cellMapper.cell(keyId, snapshot.hosts().get(index), snapshot);
  }

  /**
   * Hosts have no utilization concept, so a host group carries no utilization breakdown. Returning
   * empty makes the group searcher omit the field, matching the proto contract that utilization is
   * present only for device search.
   */
  @Override
  public Optional<FleetUtilization> utilization(List<Integer> memberIndices) {
    return Optional.empty();
  }

  @Override
  @Nullable
  public ScenarioCuration curation() {
    return curation;
  }
}
