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

/**
 * Version of a test suite.
 *
 * <p>The format of the version for a test suite is "<major>[.<minor>][.<patch>]_<revision>", e.g.
 * "16.1_r1", "16_r1", "8.0_r26", "4.0.3_r4", "15_sts-r46".
 */
@AutoValue
public abstract class TestSuiteVersion {

  public abstract int major();

  public abstract int minor();

  public abstract int patch();

  public abstract int revision();

  public static TestSuiteVersion create(int major, int minor, int patch, int revision) {
    return new AutoValue_TestSuiteVersion(major, minor, patch, revision);
  }
}
