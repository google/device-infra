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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPageRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Row;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;

/**
 * Serves the flat fleet search view: filter, then sort, then paginate, then build typed result
 * rows.
 *
 * <p>This is the Java port of the search prototype's flat branch of {@code search_devices}. It runs
 * entirely over an in-memory {@link SearchCorpus}: {@link FleetFilterEngine} resolves the filters
 * to matching record indices, records are ordered by the requested sort column's display value, a
 * page slice is cut with an opaque offset cursor, and the corpus turns the slice into columns and
 * rows.
 */
public final class FleetFlatSearcher {

  /** Page size used when the request leaves it unset (or non-positive). */
  private static final int DEFAULT_PAGE_SIZE = 25;

  /** Prefix stamped into the opaque page token so a foreign token decodes to the first page. */
  private static final String TOKEN_PREFIX = "o:";

  private final FleetFilterEngine filterEngine;

  @Inject
  FleetFlatSearcher(FleetFilterEngine filterEngine) {
    this.filterEngine = filterEngine;
  }

  /**
   * Runs a flat search over the corpus and returns one page of results.
   *
   * @param corpus the corpus to search
   * @param filters the filter chips, AND'd together; empty matches every record
   * @param columnKeys the column keys to include in each row, in display order
   * @param sort the sort order; when null or with an empty key, sorts by the corpus identifier key
   *     ascending
   * @param page the page request; when null or with a non-positive size, uses the default size
   */
  public FleetFlatResults searchFlat(
      SearchCorpus corpus,
      List<Filter> filters,
      List<String> columnKeys,
      FleetColumnSort sort,
      FleetPageRequest page) {
    List<Integer> ordered = new ArrayList<>(filterEngine.match(corpus, filters));
    sortInPlace(ordered, corpus, sort);

    int total = ordered.size();
    int pageSize = pageSize(page);
    int offset = clamp(decodeToken(page == null ? "" : page.getPageToken()), total);
    int end = Math.min(offset + pageSize, total);
    List<Integer> pageIndices = ordered.subList(offset, end);

    FleetFlatResults.Builder result = FleetFlatResults.newBuilder();
    for (String keyId : columnKeys) {
      result.addColumns(corpus.column(keyId));
    }
    for (int recordIndex : pageIndices) {
      result.addRows(buildRow(corpus, recordIndex, columnKeys));
    }
    result
        .setTotal(total)
        .setRangeStart(pageIndices.isEmpty() ? 0 : offset + 1)
        .setRangeEnd(offset + pageIndices.size());
    if (end < total) {
      result.setNextPageToken(encodeToken(offset + pageSize));
    }
    if (offset > 0) {
      result.setPrevPageToken(encodeToken(Math.max(0, offset - pageSize)));
    }
    return result.build();
  }

  /** Builds one result row: the record id as id, plus one typed cell per requested column key. */
  private static Row buildRow(SearchCorpus corpus, int recordIndex, List<String> columnKeys) {
    Row.Builder row = Row.newBuilder().setId(corpus.recordId(recordIndex));
    for (String keyId : columnKeys) {
      row.addCells(corpus.cell(recordIndex, keyId));
    }
    return row.build();
  }

  /**
   * Orders the matched record indices by the sort column's lowercased first display value, breaking
   * ties by record id so the order is stable. A descending sort reverses the whole comparison,
   * matching the prototype.
   */
  private static void sortInPlace(
      List<Integer> indices, SearchCorpus corpus, FleetColumnSort sort) {
    String sortKey =
        (sort == null || sort.getKey().isEmpty()) ? corpus.identifierKey() : sort.getKey();
    boolean ascending = sort == null || sort.getKey().isEmpty() || sort.getAscending();

    Comparator<Integer> comparator =
        Comparator.<Integer, String>comparing(i -> sortValue(corpus, i, sortKey))
            .thenComparing(corpus::recordId);
    if (!ascending) {
      comparator = comparator.reversed();
    }
    indices.sort(comparator);
  }

  private static String sortValue(SearchCorpus corpus, int recordIndex, String sortKey) {
    ImmutableList<String> values = corpus.displayValues(recordIndex, sortKey);
    return values.isEmpty() ? "" : Ascii.toLowerCase(values.get(0));
  }

  private static int pageSize(FleetPageRequest page) {
    if (page == null || page.getPageSize() <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return page.getPageSize();
  }

  private static int clamp(int offset, int total) {
    return Math.max(0, Math.min(offset, total));
  }

  private static String encodeToken(int offset) {
    return Base64.getUrlEncoder().encodeToString((TOKEN_PREFIX + offset).getBytes(UTF_8));
  }

  /**
   * Decodes the opaque page token back to a row offset. A missing, malformed, or foreign token
   * decodes to 0 (the first page), so a stale token never throws.
   */
  private static int decodeToken(String token) {
    if (token.isEmpty()) {
      return 0;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(token), UTF_8);
      if (raw.startsWith(TOKEN_PREFIX)) {
        return Integer.parseInt(raw.substring(TOKEN_PREFIX.length()));
      }
    } catch (RuntimeException e) {
      // Fall through to the first page.
    }
    return 0;
  }
}
