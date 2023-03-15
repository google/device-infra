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

package com.google.wireless.qa.mobileharness.shared.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.collect.Lists;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/** Utility class for parsing mobile tests. */
public final class FlagUtil {
  /** Prefixes for dimension, file and param flags. */
  @VisibleForTesting static final String DIMENSION_PREFIX = "--dimension_";

  @VisibleForTesting static final String FILE_PREFIX = "--file_";
  @VisibleForTesting static final String PARAM_PREFIX = "--param_";

  /** For splitting the values of the same key from a multimap. */
  private static final Splitter FLAG_VALUE_SPLITTER =
      Splitter.on("#VALUE_SEPARATOR#").trimResults().omitEmptyStrings();

  /** For splitting the list/map entries in the flag value. */
  private static final Splitter ITEM_SPLITTER =
      Splitter.on("#ITEM_SEPARATOR#").trimResults().omitEmptyStrings();

  /** For parsing a map from the flag value. */
  private static final MapSplitter FLAG_MAP_SPLITTER =
      ITEM_SPLITTER.withKeyValueSeparator("#KV_SEPARATOR#");

  /**
   * Splits arguments into two lists, one is standard argument list. another in non-standard
   * argument list.
   */
  public static void splitArgs(
      String[] args, List<String> standardArgs, List<String> nonstandardFlags) {
    for (String arg : args) {
      if (arg.startsWith(DIMENSION_PREFIX)
          || arg.startsWith(PARAM_PREFIX)
          || arg.startsWith(FILE_PREFIX)) {
        nonstandardFlags.add(arg);
      } else {
        standardArgs.add(arg);
      }
    }
  }

  /**
   * Loads individual dimension, files, and parameters override information from the non-standard
   * argument list.
   */
  public static void loadOverridingInfo(
      List<String> nonstandardFlags,
      Map<String, String> overridingDimensions,
      Map<String, List<String>> overridingFiles,
      Map<String, String> overridingParams)
      throws MobileHarnessException {
    String ateShardCount = null;
    String ateShardIndex = null;
    for (String flag : nonstandardFlags) {
      Map.Entry<String, String> arg;
      if (flag.startsWith(DIMENSION_PREFIX)) {
        arg = splitArg(flag.substring(DIMENSION_PREFIX.length()));
        overridingDimensions.put(arg.getKey(), arg.getValue());
      } else if (flag.startsWith(FILE_PREFIX)) {
        arg = splitArg(flag.substring(FILE_PREFIX.length()));
        List<String> fileList = new ArrayList<>();
        for (String file : FLAG_VALUE_SPLITTER.split(arg.getValue())) {
          fileList.add(file);
        }
        overridingFiles.put(arg.getKey(), fileList);
      } else if (flag.startsWith(PARAM_PREFIX)) {
        arg = splitArg(flag.substring(PARAM_PREFIX.length()));
        if (arg.getKey().equals("ate_shard_count")) {
          ateShardCount = arg.getValue();
        } else if (arg.getKey().equals("ate_shard_index")) {
          ateShardIndex = arg.getValue();
        } else {
          overridingParams.put(arg.getKey(), arg.getValue());
        }
      }

      if (ateShardCount != null && ateShardIndex != null && !ateShardCount.equals("1")) {
        overridingParams.put("ate_shard_count", ateShardCount);
        overridingParams.put("ate_shard_index", ateShardIndex);
      }
    }
  }

  /** Splits {@code s} to a list with {@link #ITEM_SPLITTER}. Returns null if {@code s} is null. */
  @Nullable
  public static List<String> splitToList(@Nullable String s) {
    if (s == null) {
      return null;
    }
    List<String> result = new ArrayList<>();
    ITEM_SPLITTER.split(s).forEach(result::add);
    return result;
  }

  /**
   * Splits {@code s} to a map with {@link #FLAG_MAP_SPLITTER}. The order of entries in the map is
   * preserved. Returns null if {@code s} is null.
   */
  @Nullable
  public static Map<String, String> splitToMap(@Nullable String s) {
    return s == null ? null : FLAG_MAP_SPLITTER.split(s);
  }

  /**
   * Splits {@code s} to a multimap with {@link #FLAG_MAP_SPLITTER} and {@link
   * #FLAG_VALUE_SPLITTER}. The order of entries in map is preserved.
   */
  @Nullable
  public static Map<String, List<String>> splitToMultimap(String s) {
    if (s == null) {
      return null;
    }
    // Must use Linked Hashmap to keep the order of entries.
    // Implementation of the MapSplitter is also backed up by a LinkedHashMap, so the order of
    // file entries are preserved.
    Map<String, List<String>> map = new LinkedHashMap<>();
    for (Entry<String, String> entry : FlagUtil.FLAG_MAP_SPLITTER.split(s).entrySet()) {
      map.put(
          entry.getKey(), Lists.newArrayList(FlagUtil.FLAG_VALUE_SPLITTER.split(entry.getValue())));
    }
    return map;
  }

  /** Splits the key and the value to a pair from a nonstandard flag. */
  @VisibleForTesting
  static Map.Entry<String, String> splitArg(String flag) throws MobileHarnessException {
    String splitter = "=";
    // There may be several "=" in the flag. The first of the "=" character in the flag is splitter.
    int characterIndex = flag.indexOf("=");
    if (characterIndex < 1 || characterIndex >= flag.length() - splitter.length() + 1) {
      String errMsg =
          String.format(
              "Unrecognized flag: %s. The flag format should be '(%s|%s|%s)KEY=VALUE'.",
              flag, DIMENSION_PREFIX, PARAM_PREFIX, FILE_PREFIX);
      throw new MobileHarnessException(BasicErrorId.FLAG_FORMAT_ERROR, errMsg);
    }
    return Map.entry(
        flag.substring(0, characterIndex),
        flag.substring(characterIndex + splitter.length(), flag.length()));
  }

  /**
   * Splits each items of {@code list} to a list and returns the list of list. Returns {@code null}
   * if {@code list} is {@code null}.
   */
  @Nullable
  @VisibleForTesting
  static List<List<String>> splitEachToList(@Nullable List<String> list) {
    if (list == null) {
      return null;
    }
    List<List<String>> result = new ArrayList<>();
    list.forEach(s -> result.add(splitToList(s)));
    return result;
  }

  /**
   * Splits each items of {@code list} to a map and returns the map list. Returns {@code null} if
   * {@code list} is {@code null}.
   */
  @Nullable
  @VisibleForTesting
  static List<Map<String, String>> splitEachToMap(@Nullable List<String> list) {
    if (list == null) {
      return null;
    }
    List<Map<String, String>> result = new ArrayList<>();
    list.forEach(s -> result.add(splitToMap(s)));
    return result;
  }

  /**
   * Splits each items of {@code list} to a multimap and returns the map list. Returns {@code null}
   * if {@code list} is {@code null}.
   */
  @Nullable
  @VisibleForTesting
  static List<Map<String, List<String>>> splitEachToMultimap(@Nullable List<String> list) {
    if (list == null) {
      return null;
    }
    List<Map<String, List<String>>> result = new ArrayList<>();
    list.forEach(s -> result.add(splitToMultimap(s)));
    return result;
  }

  private FlagUtil() {}
}
