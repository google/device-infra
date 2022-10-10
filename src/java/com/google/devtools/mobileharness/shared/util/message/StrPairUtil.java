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

package com.google.devtools.mobileharness.shared.util.message;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Math.min;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Utility class for processing the {@link StrPair}s. */
public final class StrPairUtil {

  private StrPairUtil() {}

  /**
   * Finds the {@link StrPair} value in the given list according to the given name. If there is
   * multiple values in the collection shares the same name, the first one will be returned(if the
   * collection is in order, or any one will be returned).
   *
   * @param strPairs {@link StrPair}s to search
   * @param name name of the target {@code StrPair}
   * @return {@code StrPair} value wrapped in an {@link Optional}, or {@link Optional#empty()} if
   *     not found
   */
  public static Optional<String> getValueOpt(Collection<StrPair> strPairs, String name) {
    if (isCollectionNullOrEmpty(strPairs)) {
      return Optional.empty();
    }
    Optional<StrPair> matchedPair =
        strPairs.stream().filter(strPair -> strPair.getName().equals(name)).findFirst();
    return matchedPair.map(StrPair::getValue);
  }

  /** Finds all the values in the given collection of {@link StrPair}s by the given name. */
  public static ImmutableList<String> getValues(Collection<StrPair> strPairs, String name) {
    if (isCollectionNullOrEmpty(strPairs)) {
      return ImmutableList.of();
    }
    return strPairs.stream()
        .filter(strPair -> strPair.getName().equals(name))
        .map(StrPair::getValue)
        .collect(toImmutableList());
  }

  /**
   * Converts the map to a list of {@link StrPair}s.
   *
   * @param toConvert the map to be converted
   * @return a list of {@link StrPair}s converted by the given map, returns an empty list if the map
   *     is null or empty
   */
  public static ImmutableList<StrPair> convertMapToList(Map<String, String> toConvert) {
    if (isMapNullOrEmpty(toConvert)) {
      return ImmutableList.of();
    }
    return toConvert.entrySet().stream()
        .map(StrPairUtil::mapEntryToStrPair)
        .collect(toImmutableList());
  }

  /**
   * Converts the multi map to a list of {@link StrPair}s.
   *
   * @param toConvert the multi map to be converted
   * @return a list of {@link StrPair}s converted by the given multi map, or an empty list if the
   *     multimap is null or empty
   */
  public static ImmutableList<StrPair> convertMultimapToList(Multimap<String, String> toConvert) {
    if (toConvert == null || toConvert.isEmpty()) {
      return ImmutableList.of();
    }
    return toConvert.entries().stream()
        .map(StrPairUtil::mapEntryToStrPair)
        .collect(toImmutableList());
  }

  /**
   * Converts a collection of {@link StrPair}s to a map. If there is values in the collection shares
   * the same name, the last last one will be put into the returned map.
   *
   * @param toConvert the collection of {@link StrPair}s to be converted
   * @return the map converted by the given {@link StrPair} collection, or an empty map if the
   *     collection is null or empty
   */
  public static Map<String, String> convertCollectionToMap(Collection<StrPair> toConvert) {
    if (isCollectionNullOrEmpty(toConvert)) {
      return new HashMap<>();
    }
    Map<String, String> converted = Maps.newHashMapWithExpectedSize(toConvert.size());
    toConvert.forEach(strPair -> converted.put(strPair.getName(), strPair.getValue()));
    return converted;
  }

  /**
   * Converts a collection of {@link StrPair}s to a multimap.
   *
   * @param toConvert the collection of {@link StrPair}s to be converted.
   * @return a multimap converted by the given collection of {@link StrPair}s, or an empty multimap
   *     if the collection is null or empty
   */
  public static ListMultimap<String, String> convertCollectionToMultimap(
      Collection<StrPair> toConvert) {
    ListMultimap<String, String> converted = ArrayListMultimap.create();
    if (!isCollectionNullOrEmpty(toConvert)) {
      toConvert.forEach(strPair -> converted.put(strPair.getName(), strPair.getValue()));
    }
    return converted;
  }

  /**
   * Converts the {@link StrPair} collection to a readable string.
   *
   * @param toConvert the {@link StrPair} collection to be converted
   * @param keyValueDelimiter the delimiter between the {@link StrPair}'s name and value
   * @param pairDelimiter the delimiter between two {@link StrPair}s
   * @return a readable string of the given {@link StrPair} collection, or an empty string if the
   *     given list is null or empty
   */
  public static String convertCollectionToString(
      Collection<StrPair> toConvert, String keyValueDelimiter, String pairDelimiter) {
    if (isCollectionNullOrEmpty(toConvert)) {
      return "";
    }
    return toConvert.stream()
        .map(strPair -> strPair.getName() + keyValueDelimiter + strPair.getValue())
        .collect(joining(pairDelimiter));
  }

  /**
   * Merges two lists of strings to a list of {@link StrPair}.
   *
   * @param names the list of names of StrPair
   * @param values the list of values of StrPair
   * @return a list of {@link StrPair}s
   */
  public static List<StrPair> mergeNameValues(List<String> names, List<String> values) {
    List<StrPair> list = new ArrayList<>();
    if (names == null || values == null) {
      return list;
    }
    for (int i = 0; i < min(names.size(), values.size()); ++i) {
      list.add(StrPair.newBuilder().setName(names.get(i)).setValue(values.get(i)).build());
    }
    return list;
  }

  /** Sorts the given {@link StrPair} list. */
  public static void sort(List<StrPair> toSort) {
    toSort.sort(Comparator.comparing(StrPair::getName).thenComparing(StrPair::getValue));
  }

  /**
   * Converts a collection of {@link DeviceDimension}s to a list of {@link StrPair}s.
   *
   * @param deviceDimensions a collection of {@link DeviceDimension}s
   * @return a List of {@link StrPair}s
   */
  public static ImmutableList<StrPair> convertFromDeviceDimension(
      Collection<DeviceDimension> deviceDimensions) {
    return deviceDimensions.stream()
        .map(
            deviceDimension ->
                StrPair.newBuilder()
                    .setName(deviceDimension.getName())
                    .setValue(deviceDimension.getValue())
                    .build())
        .collect(toImmutableList());
  }

  /**
   * Converts a collection of {@link StrPair}s to a list of {@link DeviceDimension}s.
   *
   * @param strPairs a collection of {@link StrPair}s
   * @return a list of {@link DeviceDimension}s
   */
  public static ImmutableList<DeviceDimension> convertToDeviceDimension(
      Collection<StrPair> strPairs) {
    return strPairs.stream()
        .map(
            strPair ->
                DeviceDimension.newBuilder()
                    .setName(strPair.getName())
                    .setValue(strPair.getValue())
                    .build())
        .collect(toImmutableList());
  }

  private static <E> boolean isCollectionNullOrEmpty(Collection<E> collection) {
    return collection == null || collection.isEmpty();
  }

  private static <K, V> boolean isMapNullOrEmpty(Map<K, V> map) {
    return map == null || map.isEmpty();
  }

  /** Converts an entry of a map to a {@link StrPair}. */
  private static StrPair mapEntryToStrPair(Map.Entry<String, String> entry) {
    return StrPair.newBuilder().setName(entry.getKey()).setValue(entry.getValue()).build();
  }
}
