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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatView;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupExpandView;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupHeaderView;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupedResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetSearchResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SearchEntity;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyDescriptor;
import com.google.devtools.mobileharness.fe.v6.service.search.schema.DeviceKeyRegistry;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Orchestrates fleet search across view modes (flat, grouped headers, expanded group rows) and
 * extracts required overlay keys from search requests.
 */
@Singleton
public final class FleetSearcher {

  private final FleetFlatSearcher flatSearcher;
  private final FleetGroupSearcher groupSearcher;

  @Inject
  FleetSearcher(FleetFlatSearcher flatSearcher, FleetGroupSearcher groupSearcher) {
    this.flatSearcher = checkNotNull(flatSearcher);
    this.groupSearcher = checkNotNull(groupSearcher);
  }

  /**
   * Executes a fleet search synchronously against the provided {@link SearchCorpus}, dispatching to
   * flat or grouped searchers based on the request's view.
   */
  public FleetSearchResults search(SearchCorpus corpus, FleetSearchRequest request) {
    return switch (request.getViewCase()) {
      case FLAT -> {
        FleetFlatView flat = request.getFlat();
        FleetFlatResults results =
            flatSearcher.searchFlat(
                corpus,
                request.getFiltersList(),
                flat.getColumnsList(),
                flat.getSort(),
                flat.getPage());
        yield FleetSearchResults.newBuilder().setFlat(results).build();
      }
      case GROUP_HEADER -> {
        FleetGroupHeaderView header = request.getGroupHeader();
        FleetGroupedResults results =
            groupSearcher.searchGrouped(
                corpus,
                request.getFiltersList(),
                header.getGroupByList(),
                header.getSort(),
                header.getPage());
        yield FleetSearchResults.newBuilder().setGrouped(results).build();
      }
      case GROUP_EXPAND -> {
        FleetGroupExpandView expand = request.getGroupExpand();
        FleetFlatResults results =
            groupSearcher.expandGroup(
                corpus,
                request.getFiltersList(),
                expand.getGroupId(),
                expand.getColumnsList(),
                expand.getPageToken());
        yield FleetSearchResults.newBuilder().setFlat(results).build();
      }
      case VIEW_NOT_SET -> FleetSearchResults.getDefaultInstance();
    };
  }

  /**
   * Extracts all dimension keys that require on-demand overlay loading for the given search
   * request.
   */
  public static ImmutableSet<DeviceKeyDescriptor> extractOverlayKeys(
      @Nullable DeviceKeyRegistry registry, FleetSearchRequest request) {
    if (request.getEntity() == SearchEntity.SEARCH_ENTITY_HOST || registry == null) {
      return ImmutableSet.of();
    }
    Stream<String> filterKeys = request.getFiltersList().stream().map(Filter::getKey);
    Stream<String> viewKeys =
        switch (request.getViewCase()) {
          case FLAT -> {
            FleetFlatView flat = request.getFlat();
            Stream<String> cols = flat.getColumnsList().stream();
            yield flat.hasSort() ? Stream.concat(cols, Stream.of(flat.getSort().getKey())) : cols;
          }
          case GROUP_HEADER -> {
            FleetGroupHeaderView header = request.getGroupHeader();
            Stream<String> groupBys = header.getGroupByList().stream();
            yield header.hasSort()
                ? Stream.concat(groupBys, Stream.of(header.getSort().getField().getGroupKey()))
                : groupBys;
          }
          case GROUP_EXPAND -> request.getGroupExpand().getColumnsList().stream();
          case VIEW_NOT_SET -> Stream.empty();
        };
    return Stream.concat(filterKeys, viewKeys)
        .map(registry::getKey)
        .flatMap(Optional::stream)
        .filter(DeviceKeyDescriptor::isOverlay)
        .collect(toImmutableSet());
  }
}
