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

package com.google.devtools.mobileharness.platform.android.xts.suite;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import java.util.List;
import java.util.Locale;

/** Utility class for parsing test suite version strings. */
public final class TestSuiteVersionUtil {

  private TestSuiteVersionUtil() {}

  /**
   * Parses the version of a test suite from the given version string.
   *
   * <p>The format of the version for a test suite is "<major>[.<minor>][.<patch>]_r<revision>",
   * e.g., "16.1_r1", "16_r1", "8.0_r26", "4.0.3_r4", "15_sts-r46".
   */
  public static TestSuiteVersion parse(String versionString) {
    checkArgument(versionString != null, "versionString cannot be null");
    List<String> parts =
        Splitter.on('_').trimResults().omitEmptyStrings().splitToList(versionString);
    int major = 0;
    int minor = 0;
    int patch = 0;
    int revision = 0;
    // android-mts doesn't have revision in the version string, e.g., "6.0"
    boolean hasRevision = parts.size() > 1;
    if (parts.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Invalid version string: %s", versionString));
    }

    List<String> majorMinorPatch =
        Splitter.on('.').trimResults().omitEmptyStrings().splitToList(parts.get(0));
    if (majorMinorPatch.size() > 3) {
      throw new IllegalArgumentException(
          String.format("Invalid version string: %s", versionString));
    }
    if (majorMinorPatch.size() == 3) {
      patch = Integer.parseInt(majorMinorPatch.get(2));
    }
    if (majorMinorPatch.size() >= 2) {
      minor = Integer.parseInt(majorMinorPatch.get(1));
    }
    if (majorMinorPatch.size() >= 1) {
      major = Integer.parseInt(majorMinorPatch.get(0));
    }

    if (hasRevision) {
      String revisionString = parts.get(1);
      // The revision string can be like "r1" or "sts-r46", so we parse from the last 'r'.
      int rIndex = revisionString.toLowerCase(Locale.ROOT).lastIndexOf('r');
      if (rIndex != -1) {
        revision = Integer.parseInt(revisionString.substring(rIndex + 1));
      } else {
        throw new IllegalArgumentException(
            String.format("Invalid revision in version string: %s", versionString));
      }
    }
    return TestSuiteVersion.create(major, minor, patch, revision);
  }

  /**
   * Compares two test suite versions.
   *
   * @return a negative integer, zero, or a positive integer as the first version is less than,
   *     equal to, or greater than the second.
   */
  public static int compare(TestSuiteVersion v1, TestSuiteVersion v2) {
    checkArgument(v1 != null, "first version cannot be null");
    checkArgument(v2 != null, "second version cannot be null");
    return ComparisonChain.start()
        .compare(v1.major(), v2.major())
        .compare(v1.minor(), v2.minor())
        .compare(v1.patch(), v2.patch())
        .compare(v1.revision(), v2.revision())
        .result();
  }
}
