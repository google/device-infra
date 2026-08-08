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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetColumnSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetFlatResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroup;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupSort;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupSortField;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetGroupedResults;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetPageRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FleetUtilization;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesExactly;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.NoValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceRecord;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.devtools.mobileharness.fe.v6.service.search.index.LazyPostings;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/**
 * Serves the grouped fleet search view: the collapsed group headers, and the rows inside one
 * expanded group.
 *
 * <p>This is the Java port of the search prototype's grouped branch of {@code search_devices},
 * namely {@code _partition}, {@code _grouped_page}, {@code _utilization}, and the group-id
 * encode/decode pair. It runs entirely over an in-memory {@link FleetSnapshot}.
 *
 * <p>Two operations back the two grouped request views:
 *
 * <ul>
 *   <li>{@link #searchGrouped} partitions the base filtered device set by the combination of the
 *       group-by keys' values, one group per distinct combination, and returns a page of group
 *       headers with counts, utilization, and an opaque group id.
 *   <li>{@link #expandGroup} takes a group id handed out by {@link #searchGrouped} plus the base
 *       filters and returns the device rows inside that one group, paginated. The membership test
 *       is exact-set on the group's value combination, so a group's expanded rows always agree with
 *       the count shown on its header.
 * </ul>
 *
 * <p>A device's value for a group-by key is its whole value SET, and the group key is the
 * combination of the group-by keys' sets. A device owned by alice and bob forms the single group
 * "alice, bob" rather than joining alice's group and bob's group, so every device lands in exactly
 * one group and the header counts sum to the filtered device count. Devices that lack a key
 * entirely fall into a "(no value)" bucket for that key.
 */
public final class FleetGroupSearcher {

  /** Maximum number of group-by keys, matching the prototype's {@code [:3]} cap. */
  private static final int MAX_GROUP_BY_KEYS = 3;

  /**
   * Maximum number of distinct groups a grouping may produce before it is refused. Ported from the
   * prototype's {@code MAX_GROUPS}. Beyond this a grouped view is not useful, so the response is an
   * empty group list rather than an unbounded card wall.
   */
  private static final int MAX_GROUPS = 10_000;

  /** Header display string for the bucket of devices that lack a group-by key entirely. */
  private static final String NO_VALUE_DISPLAY = "(no value)";

  /** Page size used for a group-header page when the request leaves it unset (or non-positive). */
  private static final int DEFAULT_PAGE_SIZE = 25;

  /**
   * Page size for an expanded group's rows. Fixed server-side and not overridable by the client: a
   * group card shows a bounded peek, not an arbitrarily long list.
   */
  private static final int EXPAND_PAGE_SIZE = 100;

  /** Prefix stamped into the opaque page token so a foreign token decodes to the first page. */
  private static final String TOKEN_PREFIX = "o:";

  /** Group-id record delimiters. None appear in the base64url alphabet, so they never collide. */
  private static final String ENTRY_DELIMITER = ";";

  private static final String FIELD_DELIMITER = ":";
  private static final String VALUE_DELIMITER = ",";
  private static final String NO_VALUE_MARKER = "-";
  private static final String VALUES_MARKER = "+";

  /**
   * Device type keywords that mark a device as recovering rather than serving. A device reporting
   * IDLE or BUSY while carrying one of these types is counted under "other" utilization. Ported
   * from the prototype's {@code _ABNORMAL_TYPE_KEYWORDS}.
   */
  private static final ImmutableSet<String> ABNORMAL_TYPE_KEYWORDS =
      ImmutableSet.of(
          "FAILED",
          "ABNORMAL",
          "DISCONNECTED",
          "OFFLINE",
          "UNAUTHORIZED",
          "FASTBOOT",
          "FASTBOOTDMODE");

  private final FleetFilterEngine filterEngine;
  private final FleetCellMapper cellMapper;
  private final FleetFlatSearcher flatSearcher;

  @Inject
  FleetGroupSearcher(
      FleetFilterEngine filterEngine, FleetCellMapper cellMapper, FleetFlatSearcher flatSearcher) {
    this.filterEngine = filterEngine;
    this.cellMapper = cellMapper;
    this.flatSearcher = flatSearcher;
  }

  /**
   * Partitions the base filtered device set into groups and returns one page of group headers.
   *
   * @param snapshot the fleet snapshot to search
   * @param baseFilters the filter chips, AND'd together; empty matches every device
   * @param groupByKeys the group-by keys; unknown keys are dropped and at most the first three are
   *     used, matching the prototype
   * @param sort the group sort order; when null or unset, sorts by item count descending
   * @param page the page request over the groups; when null or with a non-positive size, uses the
   *     default size
   */
  public FleetGroupedResults searchGrouped(
      FleetSnapshot snapshot,
      List<Filter> baseFilters,
      List<String> groupByKeys,
      FleetGroupSort sort,
      FleetPageRequest page,
      LazyPostings postings) {
    FleetIndex index = snapshot.index();
    ImmutableList<String> keys = cappedGroupByKeys(index, groupByKeys);

    FleetGroupedResults.Builder result = FleetGroupedResults.newBuilder();
    for (String keyId : keys) {
      result.addGroupByKeys(cellMapper.column(keyId, snapshot));
    }
    if (keys.isEmpty()) {
      return result.build();
    }

    Map<ImmutableList<ImmutableList<String>>, List<Integer>> buckets =
        partition(snapshot, baseFilters, keys, postings);
    if (buckets.isEmpty()) {
      // Either nothing matched or the grouping exceeded MAX_GROUPS. The proto has no error field on
      // grouped results, so both cases are represented as an empty group list with zero totals.
      return result.build();
    }

    ImmutableList<DeviceRecord> devices = snapshot.devices();
    List<GroupHolder> groups = new ArrayList<>();
    int totalItems = 0;
    for (Map.Entry<ImmutableList<ImmutableList<String>>, List<Integer>> entry :
        buckets.entrySet()) {
      ImmutableList<ImmutableList<String>> combo = entry.getKey();
      List<Integer> members = entry.getValue();
      totalItems += members.size();
      groups.add(
          new GroupHolder(
              encodeGroupId(keys, combo),
              displayValues(index, keys, combo),
              members.size(),
              utilization(members, devices)));
    }

    sortGroups(groups, keys, sort);

    int totalGroups = groups.size();
    int pageSize = pageSize(page);
    int offset = clamp(decodeToken(page == null ? "" : page.getPageToken()), totalGroups);
    int end = Math.min(offset + pageSize, totalGroups);
    for (GroupHolder group : groups.subList(offset, end)) {
      result.addGroups(
          FleetGroup.newBuilder()
              .setGroupId(group.groupId())
              .addAllValues(group.displayValues())
              .setItemCount(group.itemCount())
              .setUtilization(group.utilization()));
    }
    result
        .setTotalGroups(totalGroups)
        .setTotalItems(totalItems)
        .setRangeStart(end > offset ? offset + 1 : 0)
        .setRangeEnd(end);
    if (end < totalGroups) {
      result.setNextPageToken(encodeToken(offset + pageSize));
    }
    if (offset > 0) {
      result.setPrevPageToken(encodeToken(Math.max(0, offset - pageSize)));
    }
    return result.build();
  }

  /**
   * Returns one page of device rows inside a single group.
   *
   * <p>The group id encodes the group's value combination, which is turned back into an exact-set
   * predicate per group-by key and applied on top of the base filters. The rows are then produced
   * by the flat searcher, so paging and cell typing are identical to the flat view. The page size
   * is fixed at {@value #EXPAND_PAGE_SIZE} and the rows are ordered by device UUID, so the group
   * expansion is a bounded, stable peek rather than a client-sortable list; the request supplies
   * only a cursor.
   *
   * @param snapshot the fleet snapshot to search
   * @param baseFilters the same base filters the grouped headers were computed under
   * @param groupId an opaque group id from a {@link #searchGrouped} response
   * @param columnKeys the column keys to include in each row, in display order
   * @param pageToken the cursor within this group; empty selects the first page
   */
  public FleetFlatResults expandGroup(
      FleetSnapshot snapshot,
      List<Filter> baseFilters,
      String groupId,
      List<String> columnKeys,
      String pageToken,
      LazyPostings postings) {
    ImmutableList<GroupEntry> entries = decodeGroupId(groupId);
    if (entries.isEmpty()) {
      // A missing, malformed, or foreign group id names no group, so it expands to no rows rather
      // than falling back to the whole base filtered set.
      return emptyFlatResults(snapshot, columnKeys);
    }

    List<Filter> filters = new ArrayList<>(baseFilters);
    for (GroupEntry entry : entries) {
      filters.add(groupEntryFilter(entry));
    }

    FleetPageRequest page =
        FleetPageRequest.newBuilder().setPageSize(EXPAND_PAGE_SIZE).setPageToken(pageToken).build();
    return flatSearcher.searchFlat(
        snapshot, filters, columnKeys, FleetColumnSort.getDefaultInstance(), page, postings);
  }

  /**
   * Buckets the base filtered devices by the combination of the group-by keys' value sets. Returns
   * null when the number of distinct combinations exceeds {@link #MAX_GROUPS}, signalling refusal.
   *
   * <p>Each key contributes its whole lowercased value set, sorted so that ["bob", "alice"] and
   * ["alice", "bob"] name the same group. An empty set marks a device that lacks the key, which is
   * the "(no value)" bucket for that key. This mirrors the prototype's {@code _partition}.
   */
  private Map<ImmutableList<ImmutableList<String>>, List<Integer>> partition(
      FleetSnapshot snapshot,
      List<Filter> baseFilters,
      ImmutableList<String> keys,
      LazyPostings postings) {
    ImmutableList<DeviceRecord> devices = snapshot.devices();
    ListMultimap<ImmutableList<ImmutableList<String>>, Integer> buckets =
        MultimapBuilder.linkedHashKeys().arrayListValues().build();
    for (int deviceIndex : filterEngine.match(snapshot, baseFilters, postings)) {
      DeviceRecord device = devices.get(deviceIndex);
      ImmutableList.Builder<ImmutableList<String>> combo = ImmutableList.builder();
      for (String keyId : keys) {
        combo.add(ImmutableList.sortedCopyOf(FleetFilterEngine.valuesForKey(device, keyId)));
      }
      buckets.put(combo.build(), deviceIndex);
      if (buckets.keySet().size() > MAX_GROUPS) {
        // Refuse a grouping that is too large to be useful. An empty map signals the refusal, which
        // the caller renders as an empty grouped result (the proto carries no error field).
        return ImmutableMap.of();
      }
    }
    return Multimaps.asMap(buckets);
  }

  /**
   * The three utilization counts for one group, collapsing each device to idle, busy, or other.
   * Ported from the prototype's {@code _utilization} plus {@code _util_bucket}. Counts are
   * returned, not percentages, because the frontend renders a bar and thrice-rounded percentages no
   * longer sum to the total.
   */
  private static FleetUtilization utilization(
      List<Integer> members, ImmutableList<DeviceRecord> devices) {
    int idle = 0;
    int busy = 0;
    for (int deviceIndex : members) {
      switch (utilBucket(devices.get(deviceIndex))) {
        case IDLE -> idle++;
        case BUSY -> busy++;
        case OTHER -> {}
      }
    }
    int total = members.size();
    return FleetUtilization.newBuilder()
        .setIdle(idle)
        .setBusy(busy)
        .setOther(total - idle - busy)
        .setTotal(total)
        .build();
  }

  /**
   * Which utilization bucket a device is in. Serving requires more than a status: a device
   * reporting IDLE or BUSY while carrying an abnormal type is recovering, not working, and one with
   * no type at all cannot run anything. Quarantined-and-idle is held back deliberately, so it is
   * not available capacity however idle it looks.
   */
  private static UtilBucket utilBucket(DeviceRecord device) {
    String status = Ascii.toUpperCase(device.status());
    if ((!status.equals("IDLE") && !status.equals("BUSY")) || device.types().isEmpty()) {
      return UtilBucket.OTHER;
    }
    for (String type : device.types()) {
      String upper = Ascii.toUpperCase(type);
      for (String keyword : ABNORMAL_TYPE_KEYWORDS) {
        if (upper.contains(keyword)) {
          return UtilBucket.OTHER;
        }
      }
    }
    if (status.equals("IDLE")) {
      return device.quarantined() ? UtilBucket.OTHER : UtilBucket.IDLE;
    }
    return UtilBucket.BUSY;
  }

  /**
   * Orders the groups by the requested sort, breaking ties by the group's value tuple so equal
   * groups keep a stable order. A descending sort reverses the whole comparison, tie-break
   * included, matching the prototype. The default, used when no sort is set, is item count
   * descending.
   */
  private static void sortGroups(
      List<GroupHolder> groups, ImmutableList<String> keys, FleetGroupSort sort) {
    Comparator<GroupHolder> primary;
    boolean ascending;
    FleetGroupSortField field =
        sort == null ? FleetGroupSortField.getDefaultInstance() : sort.getField();
    switch (field.getKindCase()) {
      case GROUP_KEY -> {
        int index = keys.indexOf(field.getGroupKey());
        if (index >= 0) {
          primary = Comparator.comparing(group -> group.displayValues().get(index));
          ascending = sort.getAscending();
        } else {
          primary = Comparator.comparingInt(GroupHolder::itemCount);
          ascending = false;
        }
      }
      case ITEM_COUNT -> {
        primary = Comparator.comparingInt(GroupHolder::itemCount);
        ascending = sort.getAscending();
      }
      default -> {
        primary = Comparator.comparingInt(GroupHolder::itemCount);
        ascending = false;
      }
    }
    Comparator<GroupHolder> comparator = primary.thenComparing(GroupHolder::sortName);
    if (!ascending) {
      comparator = comparator.reversed();
    }
    groups.sort(comparator);
  }

  /**
   * One header display string per group-by key, joining that key's whole display-cased value set.
   */
  private static ImmutableList<String> displayValues(
      FleetIndex index, ImmutableList<String> keys, ImmutableList<ImmutableList<String>> combo) {
    ImmutableList.Builder<String> shown = ImmutableList.builder();
    for (int i = 0; i < keys.size(); i++) {
      ImmutableList<String> values = combo.get(i);
      if (values.isEmpty()) {
        shown.add(NO_VALUE_DISPLAY);
        continue;
      }
      ImmutableMap<String, String> displays =
          index.valueDisplays().getOrDefault(keys.get(i), ImmutableMap.of());
      List<String> parts = new ArrayList<>();
      for (String value : values) {
        parts.add(displays.getOrDefault(value, value));
      }
      shown.add(String.join(", ", parts));
    }
    return shown.build();
  }

  /** Keeps only keys known in this fleet and caps the list at {@link #MAX_GROUP_BY_KEYS}. */
  private static ImmutableList<String> cappedGroupByKeys(
      FleetIndex index, List<String> groupByKeys) {
    ImmutableList.Builder<String> kept = ImmutableList.builder();
    int count = 0;
    for (String keyId : groupByKeys) {
      if (count >= MAX_GROUP_BY_KEYS) {
        break;
      }
      if (index.keyIds().contains(keyId)) {
        kept.add(keyId);
        count++;
      }
    }
    return kept.build();
  }

  /** Turns one decoded group entry back into the filter that selects its devices. */
  private static Filter groupEntryFilter(GroupEntry entry) {
    if (entry.noValue()) {
      return Filter.newBuilder()
          .setKey(entry.key())
          .setSimple(
              SimpleMatch.newBuilder()
                  .addValues(FilterValue.newBuilder().setNoValue(NoValue.getDefaultInstance())))
          .build();
    }
    return Filter.newBuilder()
        .setKey(entry.key())
        .setComplex(
            ComplexMatch.newBuilder()
                .setMatchesExactly(MatchesExactly.newBuilder().addAllValues(entry.values())))
        .build();
  }

  private FleetFlatResults emptyFlatResults(FleetSnapshot snapshot, List<String> columnKeys) {
    FleetFlatResults.Builder result = FleetFlatResults.newBuilder();
    for (String keyId : columnKeys) {
      result.addColumns(cellMapper.column(keyId, snapshot));
    }
    return result.build();
  }

  // --- Group id encoding ---

  /**
   * Encodes a group's value combination into an opaque, stable, url-safe id.
   *
   * <p>The id is a base64url record of one entry per group-by key. Each entry holds the key, a
   * marker for whether the device set is empty (the "(no value)" bucket) or carries values, and the
   * sorted lowercased values. Both the key and each value are themselves base64url encoded so the
   * record delimiters can never collide with their contents. The whole record is base64url encoded
   * once more so the client receives a single opaque token to hand back for expansion.
   */
  static String encodeGroupId(
      ImmutableList<String> keys, ImmutableList<ImmutableList<String>> combo) {
    StringBuilder raw = new StringBuilder();
    for (int i = 0; i < keys.size(); i++) {
      if (i > 0) {
        raw.append(ENTRY_DELIMITER);
      }
      raw.append(encodeField(keys.get(i))).append(FIELD_DELIMITER);
      ImmutableList<String> values = combo.get(i);
      if (values.isEmpty()) {
        raw.append(NO_VALUE_MARKER);
      } else {
        raw.append(VALUES_MARKER);
        for (int j = 0; j < values.size(); j++) {
          if (j > 0) {
            raw.append(VALUE_DELIMITER);
          }
          raw.append(encodeField(values.get(j)));
        }
      }
    }
    return encodeField(raw.toString());
  }

  /**
   * Decodes an opaque group id back into its entries. A missing, malformed, or foreign token
   * decodes to an empty list, so a stale token never throws and simply expands to no rows.
   */
  static ImmutableList<GroupEntry> decodeGroupId(String groupId) {
    if (isNullOrEmpty(groupId)) {
      return ImmutableList.of();
    }
    try {
      String raw = decodeField(groupId);
      if (raw.isEmpty()) {
        return ImmutableList.of();
      }
      ImmutableList.Builder<GroupEntry> entries = ImmutableList.builder();
      for (String part : raw.split(ENTRY_DELIMITER, -1)) {
        int separator = part.indexOf(FIELD_DELIMITER);
        if (separator < 0) {
          return ImmutableList.of();
        }
        String key = decodeField(part.substring(0, separator));
        String rest = part.substring(separator + 1);
        if (rest.isEmpty()) {
          return ImmutableList.of();
        }
        String marker = rest.substring(0, 1);
        String body = rest.substring(1);
        if (marker.equals(NO_VALUE_MARKER)) {
          entries.add(new GroupEntry(key, true, ImmutableList.of()));
        } else if (marker.equals(VALUES_MARKER)) {
          ImmutableList.Builder<String> values = ImmutableList.builder();
          if (!body.isEmpty()) {
            for (String value : body.split(VALUE_DELIMITER, -1)) {
              values.add(decodeField(value));
            }
          }
          entries.add(new GroupEntry(key, false, values.build()));
        } else {
          return ImmutableList.of();
        }
      }
      return entries.build();
    } catch (RuntimeException e) {
      return ImmutableList.of();
    }
  }

  private static String encodeField(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(UTF_8));
  }

  private static String decodeField(String value) {
    int padding = (4 - value.length() % 4) % 4;
    String padded = value + "====".substring(0, padding);
    return new String(Base64.getUrlDecoder().decode(padded), UTF_8);
  }

  // --- Group-page cursor (mirrors FleetFlatSearcher's offset token) ---

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

  /** Utilization bucket a device falls into within a group. */
  private enum UtilBucket {
    IDLE,
    BUSY,
    OTHER
  }

  /** One group's header data, held while the page of groups is sorted and sliced. */
  private record GroupHolder(
      String groupId,
      ImmutableList<String> displayValues,
      int itemCount,
      FleetUtilization utilization) {

    /** Stable tie-break key: the group's display value tuple joined with a delimiter. */
    String sortName() {
      return String.join("\u0000", displayValues);
    }
  }

  /** One decoded group-id entry: a group-by key plus the value set that defines the group. */
  record GroupEntry(String key, boolean noValue, ImmutableList<String> values) {}
}
