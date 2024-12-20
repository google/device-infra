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

package com.google.devtools.mobileharness.infra.controller.test.event;

import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;

/** Event which signals that a test has just been executed. */
public class TestExecutionEndedEvent {
  private final Allocation allocation;
  private final TestResult testResult;
  private final boolean needReboot;

  public TestExecutionEndedEvent(Allocation allocation, TestResult testResult, boolean needReboot) {
    this.allocation = allocation;
    this.testResult = testResult;
    this.needReboot = needReboot;
  }

  public Allocation getAllocation() {
    return allocation;
  }

  public TestResult getTestResult() {
    return testResult;
  }

  public boolean needReboot() {
    return needReboot;
  }
}
