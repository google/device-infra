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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Pure parser for Android system property outputs such as {@code adb shell getprop}.
 *
 * <p>Decoupling parsing logic from ADB execution allows robust handling of formatting edge cases
 * (such as multi-line property values, empty values, daemon startup logs, nested brackets, and CRLF
 * line breaks) and efficient in-memory resolution of {@link AndroidProperty} fallback keys.
 */
public final class AndroidPropertyParser {

  /** Output of adb command may contain daemon lines before or after the starter line. */
  private static final String OUTPUT_ADB_DAEMON = "* daemon";

  /** Separator between property key and property value in getprop stdout. */
  private static final String KEY_VALUE_SEPARATOR = "]: [";

  private AndroidPropertyParser() {}

  /**
   * Parses the raw stdout output of {@code adb shell getprop} into a key-value property map.
   *
   * <p>The parser uses a state machine to handle:
   *
   * <ul>
   *   <li>Single-line properties: {@code [ro.build.id]: [TP1A.220624.014]}
   *   <li>Empty property values: {@code [persist.sys.test_harness]: []}
   *   <li>Multi-line properties:
   *       <pre>
   *       [persist.sys.timezone]: [America/
   *       Los_Angeles]
   *       </pre>
   *   <li>Nested brackets within property values: {@code [custom.prop]: [val[1]]}
   *   <li>CRLF and LF line separators.
   *   <li>ADB daemon startup messages (e.g. {@code * daemon not running...}) occurring anywhere.
   * </ul>
   *
   * @param rawProps the raw stdout string from {@code adb shell getprop}
   * @return an immutable map of property keys to property values
   */
  public static ImmutableMap<String, String> parse(@Nullable String rawProps) {
    if (Strings.isNullOrEmpty(rawProps)) {
      return ImmutableMap.of();
    }

    Map<String, String> properties = new HashMap<>();
    boolean inMultiLine = false;
    String currentKey = null;
    StringBuilder currentValueBuilder = null;

    try (BufferedReader reader = new BufferedReader(new StringReader(rawProps))) {
      String line;
      while ((line = reader.readLine()) != null) {
        // Filter out ADB daemon startup / restart lines.
        String trimmed = line.trim();
        if (trimmed.startsWith(OUTPUT_ADB_DAEMON)) {
          continue;
        }

        if (inMultiLine) {
          // Check if this line is actually starting a new property (recovery from unclosed
          // multi-line).
          if (line.startsWith("[") && line.contains(KEY_VALUE_SEPARATOR)) {
            // Save the accumulated multi-line value and reset state to process this line as a new
            // property.
            properties.put(currentKey, currentValueBuilder.toString());
            inMultiLine = false;
            currentKey = null;
            currentValueBuilder = null;
            // Fall through to parse this line as a new property.
          } else if (line.endsWith("]")) {
            // End of multi-line property value.
            currentValueBuilder.append("\n").append(line, 0, line.length() - 1);
            properties.put(currentKey, currentValueBuilder.toString());
            inMultiLine = false;
            currentKey = null;
            currentValueBuilder = null;
            continue;
          } else {
            // Continuation line of multi-line property value.
            currentValueBuilder.append("\n").append(line);
            continue;
          }
        }

        // Processing a new property line.
        if (line.startsWith("[")) {
          int sepIndex = line.indexOf(KEY_VALUE_SEPARATOR);
          if (sepIndex > 1) {
            String key = line.substring(1, sepIndex);
            String valuePart = line.substring(sepIndex + KEY_VALUE_SEPARATOR.length());

            if (valuePart.endsWith("]")) {
              // Single-line property (including empty value `[]` -> valuePart is `]` -> substring
              // `""`).
              String value = valuePart.substring(0, valuePart.length() - 1);
              properties.put(key, value);
            } else {
              // Beginning of a multi-line property value.
              currentKey = key;
              currentValueBuilder = new StringBuilder(valuePart);
              inMultiLine = true;
            }
          }
        }
      }

      // If EOF is reached while still in a multi-line value, save what was accumulated.
      if (inMultiLine && currentKey != null && currentValueBuilder != null) {
        properties.put(currentKey, currentValueBuilder.toString());
      }
    } catch (IOException e) {
      // StringReader does not throw IOException in practice.
      throw new AssertionError("Unexpected IOException while parsing properties in memory", e);
    }

    return ImmutableMap.copyOf(properties);
  }

  /**
   * Retrieves the value of the given {@link AndroidProperty} from the parsed properties map using
   * in-memory fallback lookup over {@link AndroidProperty#getPropertyKeys()}.
   *
   * <p>Design points:
   *
   * <ul>
   *   <li><b>Zero ADB roundtrips:</b> Fallback resolution occurs completely in-memory without
   *       executing extra ADB shell commands.
   *   <li><b>Fallback priority:</b> Property keys defined in {@link
   *       AndroidProperty#getPropertyKeys()} are evaluated in order. The first key present with a
   *       non-empty value is returned.
   *   <li><b>Missing / Empty handling:</b> If a key is absent or maps to an empty string {@code
   *       ""}, the parser continues to the next fallback key in sequence. If all fallback keys are
   *       exhausted, an empty string {@code ""} is returned.
   * </ul>
   *
   * @param properties the parsed properties map (e.g. from {@link #parse(String)})
   * @param property the {@link AndroidProperty} enum to look up
   * @return the property value, or empty string {@code ""} if not found
   */
  public static String getPropertyValue(
      @Nullable Map<String, String> properties, @Nullable AndroidProperty property) {
    if (properties == null || property == null) {
      return "";
    }
    for (String key : property.getPropertyKeys()) {
      String value = properties.get(key);
      if (!Strings.isNullOrEmpty(value)) {
        return value;
      }
    }
    return "";
  }
}
