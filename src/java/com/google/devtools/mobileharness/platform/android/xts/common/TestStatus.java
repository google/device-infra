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

package com.google.devtools.mobileharness.platform.android.xts.common;

import com.google.devtools.mobileharness.infra.ats.console.result.proto.ResultProto.MoblyResult;

/** Representation possible statuses for test methods for Tradefed and Non-Tradefed tests. */
public enum TestStatus {
  /** Test failed. */
  FAILURE,
  /** Test passed. */
  PASSED,
  /** Test started but not ended. */
  INCOMPLETE,
  /** Test assumption failure. */
  ASSUMPTION_FAILURE,
  /** Test ignored. */
  IGNORED,
  /** Test skipped, did not run for a reason. */
  SKIPPED,
  /** Test failed but being marked as warning. */
  WARNING;

  /** Converts our test status to a format compatible with CTS backend. */
  public static String convertToTestStatusCompatibilityString(TestStatus status) {
    return switch (status) {
      case PASSED -> "pass";
      case FAILURE -> "fail";
      case WARNING -> "warning";
      default -> status.toString();
    };
  }

  public static TestStatus convertFromTestStatusCompatibilityString(String status) {
    return switch (status) {
      case "pass" -> TestStatus.PASSED;
      case "fail" -> TestStatus.FAILURE;
      case "warning" -> TestStatus.WARNING;
      default -> TestStatus.valueOf(status);
    };
  }

  public static String convertMoblyResultToTestStatusCompatibilityString(MoblyResult moblyResult) {
    return switch (moblyResult) {
      case PASS -> "pass";
      case FAIL, ERROR -> "fail";
      case SKIP ->
          // Skipped Mobly test is considered as ignored in the report
          TestStatus.IGNORED.toString();
      case NULL -> TestStatus.INCOMPLETE.toString();
      default -> moblyResult.toString();
    };
  }
}
