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

package com.google.wireless.qa.mobileharness.shared.model.job.out;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.proto.Job;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;

/**
 * Please use {@link com.google.devtools.mobileharness.api.model.job.out.Result} instead.
 *
 * <p>Result of a job/test.
 */
public class Result {
  private static class NewResult
      extends com.google.devtools.mobileharness.api.model.job.out.Result {
    /** Creates the api result of a job/test. */
    NewResult(
        TouchableTiming timing, com.google.devtools.mobileharness.api.model.job.in.Params params) {
      super(timing, params);
    }

    public void resetToUnknown() {
      setUnknown();
    }
  }

  private final NewResult newResult;

  /** Creates the result of a job/test. */
  public Result(Timing timing, Params params) {
    newResult = new NewResult(timing.toNewTiming(), params.toNewParams());
  }

  /** Returns the new data model which has the same backend of this object. */
  public com.google.devtools.mobileharness.api.model.job.out.Result toNewResult() {
    return newResult;
  }

  /** Updates the result with the given value. */
  @CanIgnoreReturnValue
  public Result set(TestResult result) {
    if (newResult.get().type().name().equals(result.name())) {
    } else if (result == TestResult.UNKNOWN) {
      newResult.resetToUnknown();
    } else if (result == TestResult.PASS) {
      newResult.setPass();
    } else {
      // See go/mh-test-result for mapping.
      Test.TestResult newType = upgradeTestResult(result);
      ErrorId errorId;
      String errorMsg = "";
      switch (result) {
        case FAIL:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_FAIL;
          break;
        case ALLOC_FAIL:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_ALLOC_FAIL;
          break;
        case INFRA_ERROR:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_INFRA_ERROR;
          break;
        case ALLOC_ERROR:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_ALLOC_ERROR;
          break;
        case ABORT:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_ABORT;
          break;
        case TIMEOUT:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_TIMEOUT;
          errorMsg = "Please try to increase job/test timeout (go/mh-timing) to fix timeout issues";
          break;
        case SKIP:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_SKIP;
          break;
        case ERROR:
        default:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_ERROR;
          break;
      }
      newResult.setNonPassing(
          newType,
          new MobileHarnessException(errorId, String.format("Legacy test result. %s", errorMsg)));
    }
    return this;
  }

  /** Gets the current result. */
  public TestResult get() {
    return downgradeTestResult(newResult.get().type());
  }

  @Override
  public String toString() {
    return newResult.toString();
  }

  /** Converts to the new TestResult type according to the mapping in go/mh-test-result. */
  public static Test.TestResult upgradeTestResult(Job.TestResult oldType) {
    switch (oldType) {
      case UNKNOWN:
        return Test.TestResult.UNKNOWN;
      case PASS:
        return Test.TestResult.PASS;
      case FAIL:
        return Test.TestResult.FAIL;
      case ERROR:
      default:
        return Test.TestResult.ERROR;
      case TIMEOUT:
        return Test.TestResult.TIMEOUT;
      case SKIP:
        return Test.TestResult.SKIP;

        // The following types will be merged into ERROR according to go/mh-test-result.
      case ALLOC_FAIL:
        return Test.TestResult.ALLOC_FAIL;
      case INFRA_ERROR:
        return Test.TestResult.INFRA_ERROR;
      case ALLOC_ERROR:
        return Test.TestResult.ALLOC_ERROR;
      case ABORT:
        return Test.TestResult.ABORT;
    }
  }

  /** Converts to the old TestResult type. */
  public static TestResult downgradeTestResult(Test.TestResult newType) {
    return TestResult.valueOf(newType.name());
  }
}
