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

import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPageRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Row;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import javax.inject.Inject;

/**
 * Serves the flat fleet search view: filter, then select the requested sorted page, then build
 * typed result rows.
 *
 * <p>This is the Java port of the search prototype's flat branch of {@code search_devices}. It runs
 * entirely over an in-memory {@link SearchCorpus}: {@link FleetFilterEngine} resolves the filters
 * to matching record indices, a bounded max-heap selects the top {@code offset + pageSize} records
 * ordered by the requested sort column's lowercased first value (breaking ties by record id), the
 * requested page slice is extracted with an opaque offset cursor, and the corpus turns the slice
 * into columns and rows.
 */
public final class FleetFlatSearcher {

  /** Page size used when the request leaves it unset (or non-positive). */
  private static final int DEFAULT_PAGE_SIZE = 25;

  /** Prefix stamped into the opaque page token so a foreign token decodes to the first page. */
  private static final String TOKEN_PREFIX = "o:";

  private static final int[] EMPTY_INDICES = new int[0];

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
    BitSet matched = filterEngine.matchBits(corpus, filters);
    int total = matched.cardinality();
    int pageSize = pageSize(page);
    int offset = clamp(decodeToken(page == null ? "" : page.getPageToken()), total);
    int end = Math.min(offset + pageSize, total);
    int[] pageIndices = selectPage(matched, corpus, sort, offset, end);

    FleetFlatResults.Builder result = FleetFlatResults.newBuilder();
    for (String keyId : columnKeys) {
      result.addColumns(corpus.column(keyId));
    }
    for (int recordIndex : pageIndices) {
      result.addRows(buildRow(corpus, recordIndex, columnKeys));
    }
    result.setTotal(total).setRangeStart(pageIndices.length == 0 ? 0 : offset + 1).setRangeEnd(end);
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
   * Selects the sorted slice {@code [offset, end)} of {@code matched} record indices using a
   * bounded binary max-heap of capacity {@code end}.
   *
   * <p>Each matched record's sort key is extracted at most once via {@link
   * SearchCorpus#firstSortKey}, and only the top {@code end} records are retained in primitive
   * arrays before popping the trailing {@code end - offset} elements in reverse order.
   */
  private static int[] selectPage(
      BitSet matched, SearchCorpus corpus, FleetColumnSort sort, int offset, int end) {
    if (offset == end) {
      return EMPTY_INDICES;
    }
    String sortKey =
        (sort == null || sort.getKey().isEmpty()) ? corpus.identifierKey() : sort.getKey();
    boolean ascending = sort == null || sort.getKey().isEmpty() || sort.getAscending();

    int[] heapIndices = new int[end];
    String[] heapKeys = new String[end];
    int heapSize = 0;

    for (int i = matched.nextSetBit(0); i >= 0; i = matched.nextSetBit(i + 1)) {
      String key = corpus.firstSortKey(i, sortKey);
      if (heapSize < end) {
        heapIndices[heapSize] = i;
        heapKeys[heapSize] = key;
        siftUp(heapIndices, heapKeys, heapSize, corpus, ascending);
        heapSize++;
      } else if (isBefore(i, key, heapIndices[0], heapKeys[0], corpus, ascending)) {
        heapIndices[0] = i;
        heapKeys[0] = key;
        siftDown(heapIndices, heapKeys, end, corpus, ascending);
      }
    }

    int[] pageIndices = new int[end - offset];
    for (int pos = end - 1; pos > offset; pos--) {
      pageIndices[pos - offset] = heapIndices[0];
      heapSize--;
      heapIndices[0] = heapIndices[heapSize];
      heapKeys[0] = heapKeys[heapSize];
      siftDown(heapIndices, heapKeys, heapSize, corpus, ascending);
    }
    pageIndices[0] = heapIndices[0];
    return pageIndices;
  }

  private static void siftUp(
      int[] heapIndices, String[] heapKeys, int child, SearchCorpus corpus, boolean ascending) {
    int index = heapIndices[child];
    String key = heapKeys[child];
    while (child > 0) {
      int parent = (child - 1) >>> 1;
      if (!isBefore(heapIndices[parent], heapKeys[parent], index, key, corpus, ascending)) {
        break;
      }
      heapIndices[child] = heapIndices[parent];
      heapKeys[child] = heapKeys[parent];
      child = parent;
    }
    heapIndices[child] = index;
    heapKeys[child] = key;
  }

  private static void siftDown(
      int[] heapIndices, String[] heapKeys, int heapSize, SearchCorpus corpus, boolean ascending) {
    int parent = 0;
    int index = heapIndices[0];
    String key = heapKeys[0];
    int half = heapSize >>> 1;
    while (parent < half) {
      int child = (parent << 1) + 1;
      int right = child + 1;
      if (right < heapSize
          && isBefore(
              heapIndices[child],
              heapKeys[child],
              heapIndices[right],
              heapKeys[right],
              corpus,
              ascending)) {
        child = right;
      }
      if (!isBefore(index, key, heapIndices[child], heapKeys[child], corpus, ascending)) {
        break;
      }
      heapIndices[parent] = heapIndices[child];
      heapKeys[parent] = heapKeys[child];
      parent = child;
    }
    heapIndices[parent] = index;
    heapKeys[parent] = key;
  }

  /**
   * Returns true if record {@code indexA} strictly precedes {@code indexB} in the requested sort
   * order. Ties on {@code key} break by {@link SearchCorpus#recordId}; descending reverses the
   * comparison on {@code (key, recordId)}, and remaining ties (duplicate record ids) break by
   * encounter index ({@code indexA < indexB}).
   */
  private static boolean isBefore(
      int indexA, String keyA, int indexB, String keyB, SearchCorpus corpus, boolean ascending) {
    int cmp = keyA.compareTo(keyB);
    if (cmp == 0) {
      cmp = corpus.recordId(indexA).compareTo(corpus.recordId(indexB));
    }
    if (cmp != 0) {
      return ascending ? cmp < 0 : cmp > 0;
    }
    return indexA < indexB;
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
