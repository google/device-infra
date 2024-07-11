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
  SKIPPED;

  /** Converts our test status to a format compatible with CTS backend. */
  public static String convertToTestStatusCompatibilityString(TestStatus status) {
    switch (status) {
      case PASSED:
        return "pass";
      case FAILURE:
        return "fail";
      default:
        return status.toString();
    }
  }

  public static TestStatus convertFromTestStatusCompatibilityString(String status) {
    switch (status) {
      case "pass":
        return TestStatus.PASSED;
      case "fail":
        return TestStatus.FAILURE;
      default:
        return TestStatus.valueOf(status);
    }
  }

  public static String convertMoblyResultToTestStatusCompatibilityString(MoblyResult moblyResult) {
    switch (moblyResult) {
      case PASS:
        return "pass";
      case FAIL:
      case ERROR:
        return "fail";
      case SKIP:
        // Skipped Mobly test is considered as ignored in the report
        return TestStatus.IGNORED.toString();
      case NULL:
        return TestStatus.INCOMPLETE.toString();
      default:
        return moblyResult.toString();
    }
  }
}
