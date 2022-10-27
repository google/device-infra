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

package com.google.wireless.qa.mobileharness.shared.controller.stat;

import com.google.auto.value.AutoValue;
import com.google.wireless.qa.mobileharness.shared.proto.Job.ResultCounter;

/** Test history of a device. */
@AutoValue
public abstract class TestStat {
  public static TestStat create(int totalTestCount, ResultCounter testResults) {
    return new AutoValue_TestStat(totalTestCount, testResults);
  }

  /** Returns the total number of the tests ever run with this device. */
  public abstract int totalTestCount();

  /** Test results of this device. */
  public abstract ResultCounter testResults();
}
