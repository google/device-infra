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

import static java.lang.Math.max;

import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import java.util.List;

/** The util to parse and compare adb versions from the output of "adb version". */
final class AndroidAdbVersionUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public AndroidAdbVersionUtil() {}

  /**
   * Extacts the version number from the output of "adb version".
   *
   * @param adbVersion The output of "adb version".
   * @return The version number in the format of "1.2.3".
   */
  public static String getAdbVersionNumber(String adbVersion) {
    List<String> rows = Splitters.LINE_SPLITTER.splitToList(adbVersion);
    try {
      String versionRow = rows.get(1);
      return Splitter.onPattern("\\s+|-").splitToList(versionRow).get(1);
    } catch (IndexOutOfBoundsException e) {
      logger.atSevere().withCause(e).log("Failed to get adb version number from %s.", adbVersion);
      return "0.0.0";
    }
  }

  /**
   * Compares two adb version numbers. The adb version number is in the format of 1.2.3
   *
   * @param first The first adb version number to be compared.
   * @param second The second adb version number to be compared.
   * @return 1 if first is greater than second, 0 if first is equal to second, -1 if first is less
   *     than second.
   * @throws IllegalArgumentException if the adb version is customized by users, or either {@code
   *     first} or {@code second} is null.
   */
  static int compareAdbVersionNumber(String first, String second) {
    if (first == null || second == null) {
      throw new IllegalArgumentException(
          String.format("Version %s and %s can not be null", first, second));
    }
    String versionFormat = "[0-9]+(\\.[0-9]+)*";
    if (!first.matches(versionFormat) || !second.matches(versionFormat)) {
      throw new IllegalArgumentException(
          String.format("Invalid version format: %s, %s.", first, second));
    }
    List<String> thisParts = Splitter.on('.').splitToList(first);
    List<String> thatParts = Splitter.on('.').splitToList(second);
    int length = max(thisParts.size(), thatParts.size());
    for (int i = 0; i < length; i++) {
      int thisPart = i < thisParts.size() ? Integer.parseInt(thisParts.get(i)) : 0;
      int thatPart = i < thatParts.size() ? Integer.parseInt(thatParts.get(i)) : 0;
      if (thisPart < thatPart) {
        return -1;
      }
      if (thisPart > thatPart) {
        return 1;
      }
    }
    return 0;
  }
}
