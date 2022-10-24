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

package com.google.devtools.mobileharness.infra.controller.test.model;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;

/** Result of the execution of a test. */
@AutoValue
public abstract class TestExecutionResult {

  public static TestExecutionResult create(
      TestResult testResult, PostTestDeviceOp postTestDeviceOp) {
    return new AutoValue_TestExecutionResult(testResult, postTestDeviceOp);
  }

  public abstract TestResult testResult();

  public abstract PostTestDeviceOp postTestDeviceOp();
}
