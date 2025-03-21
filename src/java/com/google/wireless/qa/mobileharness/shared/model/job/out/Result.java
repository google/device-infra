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
import com.google.devtools.mobileharness.service.moss.proto.Slg.ResultProto;
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

    /** Creates the api result of a job/test by the given {@link Test.TestResult}. */
    NewResult(
        TouchableTiming timing,
        com.google.devtools.mobileharness.api.model.job.in.Params params,
        ResultProto resultProto) {
      super(timing, params, resultProto);
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

  /**
   * Creates the api result of a job/test by the given {@link ResultProto}. Note: please don't make
   * this public at any time.
   */
  Result(Timing timing, Params params, ResultProto resultProto) {
    this.newResult = new NewResult(timing.toNewTiming(), params.toNewParams(), resultProto);
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
      Test.TestResult newType = upgradeTestResult(result);
      ErrorId errorId;
      String errorMsg = "";
      switch (result) {
        case FAIL:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_FAIL;
          break;
        case ABORT:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_ABORT;
          break;
        case TIMEOUT:
          errorId = BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_TIMEOUT;
          errorMsg = "Please try to increase job/test timeout " + "to fix timeout issues";
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

  /** Converts to the new TestResult type */
  public static Test.TestResult upgradeTestResult(Job.TestResult oldType) {
    switch (oldType) {
      case UNKNOWN:
        return Test.TestResult.UNKNOWN;
      case PASS:
        return Test.TestResult.PASS;
      case FAIL:
        return Test.TestResult.FAIL;
      case TIMEOUT:
        return Test.TestResult.TIMEOUT;
      case SKIP:
        return Test.TestResult.SKIP;
      case ABORT:
        return Test.TestResult.ABORT;
      case ERROR:
      default:
        return Test.TestResult.ERROR;
    }
  }

  /** Converts to the old TestResult type. */
  public static TestResult downgradeTestResult(Test.TestResult newType) {
    return TestResult.valueOf(newType.name());
  }
}
