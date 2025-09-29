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

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import java.util.List;

/**
 * Version of a test suite.
 *
 * <p>The format of the version for a test suite is "<major>[.<minor>][.<patch>]_<revision>", e.g.
 * "16.1_r1", "16_r1", "8.0_r26", "4.0.3_r4".
 */
@AutoValue
public abstract class TestSuiteVersion implements Comparable<TestSuiteVersion> {

  public abstract int major();

  public abstract int minor();

  public abstract int patch();

  public abstract int revision();

  public static TestSuiteVersion create(String versionString) {
    List<String> parts =
        Splitter.on('_').trimResults().omitEmptyStrings().splitToList(versionString);
    int major = 0;
    int minor = 0;
    int patch = 0;
    int revision = 0;
    if (parts.size() != 2) {
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

    String revisionString = parts.get(1);
    if (revisionString.startsWith("r") || revisionString.startsWith("R")) {
      revision = Integer.parseInt(revisionString.substring(1));
    } else {
      throw new IllegalArgumentException(
          String.format("Invalid version string: %s", versionString));
    }
    return create(major, minor, patch, revision);
  }

  public static TestSuiteVersion create(int major, int minor, int patch, int revision) {
    return new AutoValue_TestSuiteVersion(major, minor, patch, revision);
  }

  @Override
  public int compareTo(TestSuiteVersion otherVersion) {
    return ComparisonChain.start()
        .compare(major(), otherVersion.major())
        .compare(minor(), otherVersion.minor())
        .compare(patch(), otherVersion.patch())
        .compare(revision(), otherVersion.revision())
        .result();
  }
}
