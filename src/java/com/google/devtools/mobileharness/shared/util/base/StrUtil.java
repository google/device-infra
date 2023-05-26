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

package com.google.devtools.mobileharness.shared.util.base;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.max;
import static java.lang.Math.min;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/** Utility methods for string operations. */
public final class StrUtil {

  /** Max length of the tail string return by {@link #tail(String)} method. */
  public static final int MAX_TAILING_LINES = 50;

  /** Delimiter between map/set entries */
  public static final String DEFAULT_ENTRY_DELIMITER = ",";

  /** Delimiter between the key and value. */
  public static final String DEFAULT_KEY_VALUE_DELIMITER = "=";

  /** Default map joiner. */
  public static final MapJoiner DEFAULT_MAP_JOINER =
      Joiner.on(DEFAULT_ENTRY_DELIMITER).withKeyValueSeparator(DEFAULT_KEY_VALUE_DELIMITER);

  /** Default line length for the {@link #addFrame(String, int)} method. */
  public static final int DEFAULT_LINE_LENGTH = 80;

  /** The error message for an invalid map entry. */
  private static final String INVALID_MAP_ENTRY_MSG_TEMPLATE = "Chunk [%s] is not a valid entry";

  /** The default delimiter to split a string. */
  private static final String DEFAULT_DELIMITER = ",";

  /**
   * Gets the tailing lines the given string, if the given string has more than {@link
   * #MAX_TAILING_LINES}.
   */
  @Nullable
  public static String tail(@Nullable String str) {
    if (str == null) {
      return null;
    }
    int idx = str.lastIndexOf('\n');
    int lineCount = 0;
    while (idx >= 0) {
      lineCount++;
      if (lineCount > MAX_TAILING_LINES) {
        break;
      }
      idx = str.lastIndexOf('\n', idx - 1);
    }
    if (idx < 0) {
      // Less than the line limit.
      return str;
    } else {
      return "(...only show last " + MAX_TAILING_LINES + " lines...)\n" + str.substring(idx + 1);
    }
  }

  /**
   * Splits the string into an unmodifiable map using default entry delimiter "," and default key
   * value delimiter "=". The key and value strings are trimmed. Empty entry is ignored. The
   * returned map preserves the order of the entries from the given string.
   *
   * @throws IllegalArgumentException if the specified string does not split into valid map entries,
   *     or if there are duplicate keys
   */
  public static Map<String, String> toMap(@Nullable String string) {
    return toMap(string, DEFAULT_ENTRY_DELIMITER, DEFAULT_KEY_VALUE_DELIMITER, false);
  }

  /**
   * Splits the string into an unmodifiable map using default entry delimiter "," and default key
   * value delimiter "=". The key and value strings are trimmed. Empty entry is ignored. The
   * returned map preserves the order of the entries from the given string.
   *
   * @param allowDelimiterInValue whether the key-value delimiter is allowed in the map value
   * @throws IllegalArgumentException if the specified string does not split into valid map entries,
   *     or if there are duplicate keys
   */
  public static Map<String, String> toMap(@Nullable String string, boolean allowDelimiterInValue) {
    return toMap(
        string, DEFAULT_ENTRY_DELIMITER, DEFAULT_KEY_VALUE_DELIMITER, allowDelimiterInValue);
  }

  /**
   * Splits the string into an unmodifiable map using default entry delimiter "," and the given key
   * value delimiter. The key and value strings are trimmed. Empty entry is ignored. The returned
   * map preserves the order of the entries from the given string.
   *
   * @throws IllegalArgumentException if the specified string does not split into valid map entries,
   *     or if there are duplicate keys
   */
  public static Map<String, String> toMap(@Nullable String string, String keyValueDelimiter) {
    return toMap(string, DEFAULT_ENTRY_DELIMITER, keyValueDelimiter, false);
  }

  /**
   * Splits the string into an unmodifiable map using default entry delimiter "," and the given key
   * value delimiter. The key and value strings are trimmed. Empty entry is ignored. The returned
   * map preserves the order of the entries from the given string.
   *
   * @param allowDelimiterInValue whether the key-value delimiter is allowed in the map value
   * @throws IllegalArgumentException if the specified string does not split into valid map entries,
   *     or if there are duplicate keys
   */
  public static Map<String, String> toMap(
      @Nullable String string, String keyValueDelimiter, boolean allowDelimiterInValue) {
    return toMap(string, DEFAULT_ENTRY_DELIMITER, keyValueDelimiter, allowDelimiterInValue);
  }

  /**
   * Splits the string into an unmodifiable map. The key and value strings are trimmed. Empty entry
   * is ignored. The returned map preserves the order of the entries from the given string.
   *
   * @throws IllegalArgumentException if the specified string does not split into valid map entries,
   *     or if there are duplicate keys
   */
  public static Map<String, String> toMap(
      @Nullable String string, String entryDelimiter, String keyValueDelimiter) {
    return toMap(string, entryDelimiter, keyValueDelimiter, false);
  }

  /**
   * Splits the string into an unmodifiable map. The key and value strings are trimmed. Empty entry
   * is ignored. The returned map preserves the order of the entries from the given string.
   *
   * @param allowDelimiterInValue whether the key-value delimiter is allowed in the map value
   * @throws IllegalArgumentException if the specified string does not split into valid map entries,
   *     or if there are duplicate keys
   */
  public static Map<String, String> toMap(
      @Nullable String string,
      String entryDelimiter,
      String keyValueDelimiter,
      boolean allowDelimiterInValue) {
    if (Strings.isNullOrEmpty(string)) {
      return ImmutableMap.<String, String>of();
    }
    Splitter keyValueSplitter = Splitter.on(keyValueDelimiter).trimResults();
    if (allowDelimiterInValue) {
      keyValueSplitter = keyValueSplitter.limit(2);
    }
    MapSplitter mapSplitter =
        Splitter.on(entryDelimiter)
            .trimResults()
            .omitEmptyStrings()
            .withKeyValueSeparator(keyValueSplitter);
    return mapSplitter.split(string);
  }

  /**
   * Splits the string into an unmodifiable map. The key and value strings are trimmed. Empty entry
   * is ignored. The returned map preserves the order of the entries from the given string.
   *
   * @param allowDelimiterInValue whether the key-value delimiter is allowed in the map value
   * @param isValueOptional whether the value could be optional. If true, null will be set as the
   *     value if the value is not present
   * @throws IllegalArgumentException if the specified string does not split into valid map entries,
   *     or if there are duplicate keys
   */
  public static Map<String, String> toMap(
      @Nullable String string,
      String entryDelimiter,
      String keyValueDelimiter,
      boolean allowDelimiterInValue,
      boolean isValueOptional) {
    return toMap(
        string, entryDelimiter, keyValueDelimiter, allowDelimiterInValue, isValueOptional, false);
  }

  /**
   * Splits the string into an unmodifiable map. The key and value strings are trimmed. Empty entry
   * is ignored. The returned map preserves the order of the entries from the given string.
   *
   * @param ignoreInvalidEntries whether invalid entries should be ignored. If true, any entries
   *     that do not contain the keyValueDelimiter will be ignored.
   * @throws IllegalArgumentException if the specified string does not split into valid map entries,
   *     or if there are duplicate keys
   */
  public static Map<String, String> toMap(
      @Nullable String string,
      String entryDelimiter,
      String keyValueDelimiter,
      boolean allowDelimiterInValue,
      boolean isValueOptional,
      boolean ignoreInvalidEntries) {
    if (Strings.isNullOrEmpty(string)) {
      return ImmutableMap.<String, String>of();
    }
    Splitter keyValueSplitter = Splitter.on(keyValueDelimiter).trimResults();
    if (allowDelimiterInValue) {
      keyValueSplitter = keyValueSplitter.limit(2);
    }
    Splitter entrySplitter = Splitter.on(entryDelimiter).trimResults().omitEmptyStrings();
    List<String> entries = entrySplitter.splitToList(string);
    Map<String, String> map = new LinkedHashMap<>();
    for (String entry : entries) {
      if (ignoreInvalidEntries && !entry.contains(keyValueDelimiter)) {
        continue;
      }
      Iterator<String> keyValue = keyValueSplitter.split(entry).iterator();
      checkArgument(keyValue.hasNext(), INVALID_MAP_ENTRY_MSG_TEMPLATE, entry);
      String key = keyValue.next();
      checkArgument(!map.containsKey(key), "Duplicate key [%s] found.", key);
      if (isValueOptional && !keyValue.hasNext()) {
        map.put(key, null);
      } else {
        checkArgument(keyValue.hasNext(), INVALID_MAP_ENTRY_MSG_TEMPLATE, entry);
        String value = keyValue.next();
        map.put(key, value);
      }
      checkArgument(!keyValue.hasNext(), INVALID_MAP_ENTRY_MSG_TEMPLATE, entry);
    }
    return Collections.unmodifiableMap(map);
  }

  /**
   * Splits the string into an unmodifiable multimap. The key and value strings are trimmed. Empty
   * entry is ignored. The returned map preserves the order of the entries from the given string. If
   * the key is not specified, use {@code defaultKey} instead.
   *
   * @throws IllegalArgumentException if the specified string does not split into valid map entries.
   */
  public static Map<String, List<String>> toMultimap(
      @Nullable String string,
      String entryDelimiter,
      String keyValueDelimiter,
      boolean allowDelimiterInValue,
      @Nullable String defaultKey) {
    if (Strings.isNullOrEmpty(string)) {
      return ImmutableMap.<String, List<String>>of();
    }
    Splitter keyValueSplitter = Splitter.on(keyValueDelimiter).trimResults();
    if (allowDelimiterInValue) {
      keyValueSplitter = keyValueSplitter.limit(2);
    }
    Splitter entrySplitter = Splitter.on(entryDelimiter).trimResults().omitEmptyStrings();
    List<String> entries = entrySplitter.splitToList(string);
    Map<String, List<String>> multiMap = new LinkedHashMap<>();
    for (String entry : entries) {
      Iterator<String> keyValue = keyValueSplitter.split(entry).iterator();
      checkArgument(keyValue.hasNext(), INVALID_MAP_ENTRY_MSG_TEMPLATE, entry);
      String key = defaultKey;
      String value = keyValue.next();
      if (keyValue.hasNext()) {
        key = value;
        value = keyValue.next();
      }
      if (!Strings.isNullOrEmpty(key)) {
        List<String> values = multiMap.computeIfAbsent(key, (String k) -> new ArrayList<>());
        values.add(value);
      } else {
        continue;
      }
      checkArgument(!keyValue.hasNext(), INVALID_MAP_ENTRY_MSG_TEMPLATE, entry);
    }
    return Collections.unmodifiableMap(multiMap);
  }

  /** Adds a frame to the message in order to get attention for this message. */
  public static String addFrame(String message) {
    return addFrame(message, DEFAULT_LINE_LENGTH);
  }

  /**
   * Adds a frame to the message in order to get attention for this message. If there is any line
   * longer than {@code maxWidth}, we wrap it to several lines.
   */
  public static String addFrame(String message, int maxWidth) {
    if (message.isEmpty()) {
      return message;
    }
    List<String> lines = Splitter.on('\n').splitToList(message);
    int maxLength = -1;
    for (String line : lines) {
      maxLength = max(maxLength, line.length());
    }
    // The margin size of each line, including "*".
    int margin = 3;
    maxLength = min(maxLength, maxWidth - margin * 2);

    List<String> res = new ArrayList<>();
    res.add(""); // Print in a new line.
    res.add("*".repeat(maxLength + margin * 2));
    res.add("*" + " ".repeat(maxLength + (margin - 1) * 2) + "*");
    for (String line : lines) {
      if (line.length() > maxLength) {
        for (String subLine : Splitter.on("\n").split(formatLineToFixedWidth(line, maxLength))) {
          res.add(String.format("*  %-" + maxLength + "s  *", subLine));
        }
      } else {
        res.add(String.format("*  %-" + maxLength + "s  *", line));
      }
    }
    res.add("*" + " ".repeat(maxLength + (margin - 1) * 2) + "*");
    res.add("*".repeat(maxLength + margin * 2));

    return Joiner.on('\n').join(res);
  }

  /**
   * Returns a new long initialized to the value represented by the string of a human readable size.
   *
   * <p>The 'G'/'g'/'M'/'m'/'K'/'k' in the end are allowed, such as "12.1G", "417.6m", etc.
   *
   * <p>The extra tailing 'B'/'b' are also allowed, such as "1.6kB", "34Mb", "54b", etc.
   *
   * @param s the string to be parsed.
   * @throws NumberFormatException if the string is null or it does not contain a parsable number.
   */
  public static long parseHumanReadableSize(String s) {
    int originalLength = 0;
    int newLength = 0;
    long multiple = 1L;
    if (s != null) {
      originalLength = s.length();
      newLength = originalLength;
      // Parses tailing 'B'/'b'.
      if (newLength > 0 && (s.charAt(newLength - 1) == 'B' || s.charAt(newLength - 1) == 'b')) {
        newLength--;
      }
      // Parses tailing 'G'/'g'/'M'/'m'/'K'/'k'.
      if (newLength > 0) {
        switch (s.charAt(newLength - 1)) {
          case 'G':
          case 'g':
            newLength--;
            multiple = 1L << 30;
            break;
          case 'M':
          case 'm':
            newLength--;
            multiple = 1L << 20;
            break;
          case 'K':
          case 'k':
            newLength--;
            multiple = 1L << 10;
            break;
          default:
            break;
        }
      }
    }
    if (newLength < originalLength) {
      s = s.substring(0, newLength);
    }
    return multiple == 1L ? Long.parseLong(s) : (long) (Double.parseDouble(s) * multiple);
  }

  /**
   * Gets the human readable size given the total bytes.
   *
   * @param size size in bytes.
   * @return the human readable size.
   */
  public static String getHumanReadableSize(long size) {
    if (size < 1024) {
      return size + "B";
    }

    double tempSize = size / 1024.0;
    if (tempSize < 1024) {
      return getRound(tempSize) + "KB";
    }

    tempSize /= 1024.0;
    if (tempSize < 1024) {
      return getRound(tempSize) + "MB";
    }
    return getRound(tempSize / 1024.0) + "GB";
  }

  /**
   * Gets the size properly rounded.
   *
   * @param size the package size.
   * @return the rounded size.
   */
  private static String getRound(double size) {
    long roundedSize = Math.round(size);
    if (size < 100.0) {
      if (size == roundedSize) {
        // e.g. "2MB"
        return Long.toString(roundedSize);
      }
      // e.g. "2.34KB" or "22.34KB"
      return Double.toString(Math.round(size * 100) / 100.0);
    }
    // e.g. "234KB"
    return Long.toString(roundedSize);
  }

  /** Check whether the string can be converted to integer. */
  public static boolean isInteger(String value) {
    try {
      Integer.parseInt(value);
      return true;
    } catch (NumberFormatException e) {
      // Do nothing.
    }
    return false;
  }

  /** Splits the string into a characters set. */
  public static Set<Character> toCharSet(String s) {
    Set<Character> charSet = new HashSet<>();
    for (int i = 0; i < s.length(); i++) {
      char character = s.charAt(i);
      charSet.add(character);
    }
    return charSet;
  }

  /** Splits string with default delimiter, and trims results. */
  public static List<String> toList(@Nullable String str) {
    return toList(str, DEFAULT_DELIMITER);
  }

  /** Splits string with given delimiter, and trims results. */
  public static List<String> toList(@Nullable String str, String delimiter) {
    if (Strings.isNullOrEmpty(str)) {
      return ImmutableList.of();
    }
    return Splitter.on(delimiter).trimResults().omitEmptyStrings().splitToList(str);
  }

  /**
   * Returns {@code true} if the given string is null, empty, or comprises only whitespace
   * characters, as defined by {@link CharMatcher#whitespace()}.
   *
   * @param string the string reference to check
   * @return {@code true} if {@code string} is null, empty, or consists of whitespace characters
   *     only
   */
  public static boolean isEmptyOrWhitespace(@CheckForNull String string) {
    return string == null || CharMatcher.whitespace().matchesAllOf(string);
  }

  /**
   * Give me a string and a potential prefix, and I return the string following the prefix if the
   * prefix matches, else null. Analogous to the c++ functions strprefix and var_strprefix.
   *
   * @param str the string to strip
   * @param prefix the expected prefix
   * @return the stripped string or <code>null</code> if the string does not start with the prefix
   */
  @CheckForNull
  public static String stripPrefix(String str, String prefix) {
    return str.startsWith(prefix) ? str.substring(prefix.length()) : null;
  }

  private static final Splitter TO_WORDS =
      Splitter.on(CharMatcher.breakingWhitespace()).omitEmptyStrings();

  /**
   * Reformats the given array of lines to a fixed width by inserting newlines and trimming
   * unnecessary whitespace. This uses simple whitespace-based splitting, not sophisticated
   * internationalized line breaking. Newlines within a line are treated like any other whitespace.
   * Lines which are already short enough will be passed through unmodified.
   *
   * <p>Only breaking whitespace characters (those which match {@link
   * CharMatcher#breakingWhitespace()}) are treated as whitespace by this method. Non-breaking
   * whitespace characters will be considered as ordinary characters which are connected to any
   * other adjacent non-whitespace characters, and will therefore appear in the returned string in
   * their original context.
   *
   * @param line line to format
   * @param width the fixed width (in characters)
   */
  private static String formatLineToFixedWidth(String line, int width) {
    if (line.length() <= width) {
      return line;
    }

    StringBuilder builder = new StringBuilder();
    int col = 0;

    for (String word : TO_WORDS.split(line)) {
      if (col == 0) {
        col = word.length();
      } else {
        int newCol = col + word.length() + 1; // +1 for the space

        if (newCol <= width) {
          builder.append(' ');
          col = newCol;
        } else {
          builder.append('\n');
          col = word.length();
        }
      }

      builder.append(word);
    }

    return builder.toString();
  }

  private StrUtil() {}
}
