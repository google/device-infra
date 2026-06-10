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
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.TestSuiteVersion;
import java.util.List;
import java.util.Optional;

/** A class that resolves build related metadata for test suite. */
@AutoValue
public abstract class TestSuiteInfo {

  private static final String BUILD_NUMBER = "build_number";
  private static final String TARGET_ARCH = "target_arch";
  private static final String NAME = "name";
  private static final String FULLNAME = "fullname";
  private static final String VERSION = "version";

  public abstract String xtsRootDir();

  public abstract String xtsType();

  public abstract ImmutableMap<String, String> testSuiteInfoProps();

  public abstract Optional<TestSuiteVersion> getTestSuiteVersion();

  public static TestSuiteInfo create(
      String xtsRootDir,
      String xtsType,
      ImmutableMap<String, String> testSuiteInfoProps,
      Optional<TestSuiteVersion> testSuiteVersion) {
    return new AutoValue_TestSuiteInfo(xtsRootDir, xtsType, testSuiteInfoProps, testSuiteVersion);
  }

  /** Gets the build number of the test suite. */
  public String getBuildNumber() {
    return testSuiteInfoProps().get(BUILD_NUMBER);
  }

  /** Gets the target archs supported by the test suite. */
  public List<String> getTargetArchs() {
    String testSuiteInfoArch = testSuiteInfoProps().get(TARGET_ARCH);
    return Splitter.on(",").trimResults().omitEmptyStrings().splitToList(testSuiteInfoArch);
  }

  /** Gets the short name of the test suite. */
  public String getName() {
    return testSuiteInfoProps().get(NAME);
  }

  /** Gets the full name of the test suite. */
  public String getFullName() {
    return testSuiteInfoProps().get(FULLNAME);
  }

  /** Gets the version name of the test suite. */
  public String getVersion() {
    return testSuiteInfoProps().get(VERSION);
  }

  /**
   * Retrieves test information keyed with the provided name. Or null if not property associated.
   */
  public String get(String name) {
    return testSuiteInfoProps().get(name);
  }
}
