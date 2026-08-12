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

import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.CONFIG_WIFI_SSID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_PREFIX;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.DIM_QUARANTINED;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DECORATOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_DRIVER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_EXECUTOR;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_OWNER;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_STATUS;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_TYPE;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.FIELD_UUID;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_IP;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.HOST_NAME;
import static com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSearchKeys.PROP_PREFIX;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ComplexMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.ContainsSubstring;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.Filter;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.FilterValue;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesAtLeast;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesExactly;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.MatchesRegex;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.SimpleMatch;
import com.google.devtools.mobileharness.fe.v6.service.proto.search.StartsWith;
import com.google.devtools.mobileharness.fe.v6.service.search.index.DeviceRecord;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetIndex;
import com.google.devtools.mobileharness.fe.v6.service.search.index.FleetSnapshot;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;

/**
 * Resolves a list of proto {@link Filter}s against a {@link FleetSnapshot} into the set of matching
 * device indices, using the fleet's inverted index.
 *
 * <p>This is the Java port of the search prototype's {@code filter_devices} and {@code _chip_set}.
 * Filters are AND'd together: a device matches only if it satisfies every filter. Within a single
 * simple filter the listed values are OR'd. An empty filter list matches every device.
 *
 * <p>The index stores all values lowercased, so every value the caller supplies is lowercased
 * before lookup and regular expressions are compiled case-insensitively. Posting lists hold device
 * indices into {@link FleetSnapshot#devices()} in ascending order; the returned list is likewise
 * ascending.
 */
public final class FleetFilterEngine {

  @Inject
  FleetFilterEngine() {}

  /**
   * Returns the device indices matching all of the given filters, in ascending order.
   *
   * <p>Indices point into {@link FleetSnapshot#devices()}. An empty filter list returns every
   * device index (0 to deviceCount - 1).
   */
  public ImmutableList<Integer> match(FleetSnapshot snapshot, List<Filter> filters) {
    int deviceCount = snapshot.deviceCount();
    BitSet result = null;
    for (Filter filter : filters) {
      BitSet filterSet = matchFilter(snapshot, filter);
      if (result == null) {
        result = filterSet;
      } else {
        result.and(filterSet);
      }
      if (result.isEmpty()) {
        break;
      }
    }
    if (result == null) {
      result = allDevices(deviceCount);
    }
    return toSortedList(result);
  }

  /** Device-index set for one filter. A filter with no mode set imposes no constraint. */
  private static BitSet matchFilter(FleetSnapshot snapshot, Filter filter) {
    String keyId = filter.getKey();
    return switch (filter.getModeCase()) {
      case SIMPLE -> matchSimple(snapshot, keyId, filter.getSimple());
      case COMPLEX -> matchComplex(snapshot, keyId, filter.getComplex());
      case MODE_NOT_SET -> allDevices(snapshot.deviceCount());
    };
  }

  /**
   * Simple match: the union of the listed values (OR). A {@code no_value} entry contributes the
   * devices that lack the key entirely. When {@code negated} is set the whole result is inverted.
   */
  private static BitSet matchSimple(FleetSnapshot snapshot, String keyId, SimpleMatch simple) {
    FleetIndex index = snapshot.index();
    BitSet include = new BitSet();
    for (FilterValue value : simple.getValuesList()) {
      switch (value.getKindCase()) {
        case VALUE ->
            orInto(include, index.postingList(keyId, Ascii.toLowerCase(value.getValue())));
        case NO_VALUE -> include.or(noValueSet(snapshot, keyId));
        case KIND_NOT_SET -> {}
      }
    }
    return negateIfNeeded(include, simple.getNegated(), snapshot.deviceCount());
  }

  /** Complex match: exactly one advanced mode. */
  private static BitSet matchComplex(FleetSnapshot snapshot, String keyId, ComplexMatch complex) {
    return switch (complex.getKindCase()) {
      case STARTS_WITH -> matchStartsWith(snapshot, keyId, complex.getStartsWith());
      case CONTAINS_SUBSTRING -> matchContains(snapshot, keyId, complex.getContainsSubstring());
      case MATCHES_REGEX -> matchRegex(snapshot, keyId, complex.getMatchesRegex());
      case MATCHES_EXACTLY -> matchExactly(snapshot, keyId, complex.getMatchesExactly());
      case MATCHES_AT_LEAST -> matchAtLeast(snapshot, keyId, complex.getMatchesAtLeast());
      case KIND_NOT_SET -> allDevices(snapshot.deviceCount());
    };
  }

  /**
   * Prefix match over the key's sorted distinct values. Locates the contiguous run of values that
   * begin with the prefix via two binary searches, then unions their posting lists. Not negatable.
   */
  private static BitSet matchStartsWith(
      FleetSnapshot snapshot, String keyId, StartsWith startsWith) {
    FleetIndex index = snapshot.index();
    ImmutableList<String> sorted = index.sortedValues().getOrDefault(keyId, ImmutableList.of());
    String prefix = Ascii.toLowerCase(startsWith.getValue());
    int lo = lowerBound(sorted, prefix);
    // '\uffff' is the largest basic-plane code unit, so prefix + '\uffff' bounds the prefix run.
    int hi = lowerBound(sorted, prefix + '\uffff');
    BitSet result = new BitSet();
    for (int i = lo; i < hi; i++) {
      orInto(result, index.postingList(keyId, sorted.get(i)));
    }
    return result;
  }

  /** Substring match: scans distinct values for the needle and unions their postings. Negatable. */
  private static BitSet matchContains(
      FleetSnapshot snapshot, String keyId, ContainsSubstring contains) {
    FleetIndex index = snapshot.index();
    String needle = Ascii.toLowerCase(contains.getValue());
    BitSet matched = new BitSet();
    for (String value : index.sortedValues().getOrDefault(keyId, ImmutableList.of())) {
      if (value.contains(needle)) {
        orInto(matched, index.postingList(keyId, value));
      }
    }
    return negateIfNeeded(matched, contains.getNegated(), snapshot.deviceCount());
  }

  /**
   * Regular expression match: scans distinct values with a case-insensitive unanchored search and
   * unions their postings. Negatable. An invalid pattern matches nothing (so a negated invalid
   * pattern matches everything), mirroring the prototype.
   */
  private static BitSet matchRegex(FleetSnapshot snapshot, String keyId, MatchesRegex regex) {
    FleetIndex index = snapshot.index();
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex.getValue(), Pattern.CASE_INSENSITIVE);
    } catch (PatternSyntaxException e) {
      return negateIfNeeded(new BitSet(), regex.getNegated(), snapshot.deviceCount());
    }
    BitSet matched = new BitSet();
    for (String value : index.sortedValues().getOrDefault(keyId, ImmutableList.of())) {
      if (pattern.matcher(value).find()) {
        orInto(matched, index.postingList(keyId, value));
      }
    }
    return negateIfNeeded(matched, regex.getNegated(), snapshot.deviceCount());
  }

  /**
   * Exact set match: the device's value set for the key equals the given set. Intersects the
   * postings of the wanted values to find candidates that carry all of them, then keeps only those
   * whose full value set equals the wanted set. Not negatable.
   */
  private static BitSet matchExactly(FleetSnapshot snapshot, String keyId, MatchesExactly exactly) {
    ImmutableList<String> wanted = lowercased(exactly.getValuesList());
    BitSet candidates = intersectPostings(snapshot.index(), keyId, wanted);
    if (candidates.isEmpty()) {
      return candidates;
    }
    Set<String> want = new HashSet<>(wanted);
    BitSet result = new BitSet();
    for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
      if (valuesForKey(snapshot.devices().get(i), keyId).equals(want)) {
        result.set(i);
      }
    }
    return result;
  }

  /**
   * Superset match: the device's value set for the key contains all the given values. Equivalent to
   * intersecting the postings of the wanted values. Not negatable.
   */
  private static BitSet matchAtLeast(FleetSnapshot snapshot, String keyId, MatchesAtLeast atLeast) {
    return intersectPostings(snapshot.index(), keyId, lowercased(atLeast.getValuesList()));
  }

  /** Devices that lack the key entirely: all devices minus those carrying any value for it. */
  private static BitSet noValueSet(FleetSnapshot snapshot, String keyId) {
    BitSet result = allDevices(snapshot.deviceCount());
    result.andNot(devicesWithKey(snapshot.index(), keyId));
    return result;
  }

  /** Union of every posting list for the key: the devices that carry at least one value for it. */
  private static BitSet devicesWithKey(FleetIndex index, String keyId) {
    BitSet withKey = new BitSet();
    ImmutableMap<String, ImmutableList<Integer>> values = index.postings().get(keyId);
    if (values != null) {
      for (ImmutableList<Integer> posting : values.values()) {
        orInto(withKey, posting);
      }
    }
    return withKey;
  }

  /**
   * Intersection (AND) of the posting lists of the given values. Empty values yield an empty set.
   */
  private static BitSet intersectPostings(FleetIndex index, String keyId, List<String> values) {
    BitSet clause = null;
    for (String value : values) {
      BitSet posting = new BitSet();
      orInto(posting, index.postingList(keyId, value));
      if (clause == null) {
        clause = posting;
      } else {
        clause.and(posting);
      }
      if (clause.isEmpty()) {
        return clause;
      }
    }
    return clause == null ? new BitSet() : clause;
  }

  /**
   * The device's lowercased value set for the key, derived from the forward store so it mirrors
   * exactly what the index builder recorded. Used by exact set matching.
   */
  static ImmutableSet<String> valuesForKey(DeviceRecord device, String keyId) {
    return switch (keyId) {
      case FIELD_UUID -> singletonLower(device.deviceId());
      case FIELD_STATUS -> singletonLower(device.status());
      case FIELD_TYPE -> lowercasedSet(device.types());
      case FIELD_OWNER -> lowercasedSet(device.owners());
      case FIELD_DRIVER -> lowercasedSet(device.drivers());
      case FIELD_DECORATOR -> lowercasedSet(device.decorators());
      case FIELD_EXECUTOR -> lowercasedSet(device.executors());
      case DIM_QUARANTINED -> ImmutableSet.of(device.quarantined() ? "yes" : "no");
      case HOST_NAME -> singletonLower(device.hostName());
      case HOST_IP -> singletonLower(device.hostIp());
      case CONFIG_WIFI_SSID ->
          device.wifiSsid().isPresent()
              ? ImmutableSet.of(Ascii.toLowerCase(device.wifiSsid().get()))
              : ImmutableSet.of();
      default -> valuesForPrefixedKey(device, keyId);
    };
  }

  private static ImmutableSet<String> valuesForPrefixedKey(DeviceRecord device, String keyId) {
    if (keyId.startsWith(DIM_PREFIX)) {
      return lowercasedSet(
          device
              .dimensions()
              .getOrDefault(keyId.substring(DIM_PREFIX.length()), ImmutableList.of()));
    }
    if (keyId.startsWith(PROP_PREFIX)) {
      String value = device.hostProperties().get(keyId.substring(PROP_PREFIX.length()));
      return value == null ? ImmutableSet.of() : singletonLower(value);
    }
    return ImmutableSet.of();
  }

  private static ImmutableSet<String> singletonLower(String value) {
    return value.isEmpty() ? ImmutableSet.of() : ImmutableSet.of(Ascii.toLowerCase(value));
  }

  private static ImmutableSet<String> lowercasedSet(List<String> values) {
    ImmutableSet.Builder<String> result = ImmutableSet.builder();
    for (String value : values) {
      result.add(Ascii.toLowerCase(value));
    }
    return result.build();
  }

  private static ImmutableList<String> lowercased(List<String> values) {
    ImmutableList.Builder<String> result = ImmutableList.builder();
    for (String value : values) {
      result.add(Ascii.toLowerCase(value));
    }
    return result.build();
  }

  /** Inverts the set over the device space when negated, otherwise returns it unchanged. */
  @CanIgnoreReturnValue
  private static BitSet negateIfNeeded(BitSet matched, boolean negated, int deviceCount) {
    if (negated) {
      matched.flip(0, deviceCount);
    }
    return matched;
  }

  private static BitSet allDevices(int deviceCount) {
    BitSet all = new BitSet(deviceCount);
    all.set(0, deviceCount);
    return all;
  }

  private static void orInto(BitSet target, ImmutableList<Integer> posting) {
    for (int index : posting) {
      target.set(index);
    }
  }

  private static ImmutableList<Integer> toSortedList(BitSet set) {
    ImmutableList.Builder<Integer> result =
        ImmutableList.builderWithExpectedSize(set.cardinality());
    for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
      result.add(i);
    }
    return result.build();
  }

  /**
   * Locates the first index in the sorted list whose value is greater than or equal to the key. The
   * list must be sorted ascending, matching {@link FleetIndex#sortedValues()}.
   */
  private static int lowerBound(List<String> sorted, String key) {
    int lo = 0;
    int hi = sorted.size();
    while (lo < hi) {
      int mid = (lo + hi) >>> 1;
      if (sorted.get(mid).compareTo(key) < 0) {
        lo = mid + 1;
      } else {
        hi = mid;
      }
    }
    return lo;
  }
}
