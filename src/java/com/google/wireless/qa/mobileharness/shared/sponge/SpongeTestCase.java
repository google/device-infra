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

package com.google.wireless.qa.mobileharness.shared.sponge;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;
import javax.annotation.Nullable;

/** The object to record test case in sponge. */
public class SpongeTestCase extends SpongeNode {

  /** The result of this test case. */
  public enum TestResult {
    PASS,
    FAIL,
    ERROR,
    SKIPPED
  }

  /** The test result of the test case. */
  private volatile TestResult testResult;

  /**
   * If the {{@link #testResult} is FAIL, the log is failure log. If the {{@link #testResult} is
   * ERROR, the log is error log. It should not be empty if the test is FAIL/ERROR.
   */
  private volatile String failureOrErrorLog;

  /**
   * If the {{@link #testResult} is FAIL, the stack trace is failure stack trace. If the {{@link
   * #testResult} is ERROR, the stack trace is error stack trace.
   */
  private volatile String failureOrErrorStackTrace;

  /**
   * If the {{@link #testResult} is FAIL, the type is the type of exception being thrown for the
   * failure. If the {{@link #testResult} is ERROR, the type is the type of exception being thrown
   * for the error.
   */
  private volatile String failureOrErrorType;

  private Optional<Integer> repeatNumber = Optional.empty();
  private Optional<Integer> retryNumber = Optional.empty();

  public SpongeTestCase(String name) {
    super(name);
    testResult = TestResult.PASS;
    /**
     * By default, a test result is PASS. From each given state, the possible state that it can be
     * changed to are: PASS > (FAIL | ERROR | SKIPPED) FAIL > PASS* ERROR > PASS* SKIPPED > (PASS* |
     * FAIL | ERROR) This prevents hiding any test failures or errors (setting a result to PASS can
     * only be done from within this class).
     */
    failureOrErrorLog = null;
    failureOrErrorStackTrace = null;
    failureOrErrorType = null;
  }

  /**
   * Returns the failure log of the test case. If the test is FAIL, it should not be empty or null.
   */
  @Nullable
  public String getFailureLog() {
    if (testResult == TestResult.FAIL) {
      return failureOrErrorLog;
    } else {
      return null;
    }
  }

  /** Returns the failure stack trace of the test case. */
  @Nullable
  public String getFailureStackTrace() {
    if (testResult == TestResult.FAIL) {
      return failureOrErrorStackTrace;
    } else {
      return null;
    }
  }

  /** Returns the failure type of the test case. */
  @Nullable
  public String getFailureType() {
    if (testResult == TestResult.FAIL) {
      return failureOrErrorType;
    } else {
      return null;
    }
  }

  /** Sets the failure log of the test case. The sponge result will be FAIL and show the log. */
  @CanIgnoreReturnValue
  public SpongeTestCase setFailure(String failureLog) {
    return setFailure(failureLog, /* failureStackTrace= */ "");
  }

  /**
   * Sets the failure log and stack trace of the test case. The sponge result will be FAIL and show
   * the log.
   */
  @CanIgnoreReturnValue
  public SpongeTestCase setFailure(String failureLog, String failureStackTrace) {
    return setFailure(failureLog, failureStackTrace, /* failureType= */ "");
  }

  /**
   * Sets the failure log, stack trace and the thrown exception type of the test case. The sponge
   * result will be FAIL and show the log.
   */
  @CanIgnoreReturnValue
  public SpongeTestCase setFailure(
      String failureLog, String failureStackTrace, String failureType) {
    Preconditions.checkState(
        testResult != TestResult.ERROR, "Can not set failure log in the ERROR sponge node.");
    if (Strings.isNullOrEmpty(failureLog)) {
      failureLog = " ";
    }
    failureOrErrorLog = failureLog;
    failureOrErrorStackTrace = failureStackTrace;
    failureOrErrorType = failureType;
    testResult = TestResult.FAIL;
    return this;
  }

  /**
   * Returns the error log of the test case. If the test is ERROR, it should not be empty or null.
   */
  @Nullable
  public String getErrorLog() {
    if (testResult == TestResult.ERROR) {
      return failureOrErrorLog;
    } else {
      return null;
    }
  }

  /** Returns the error stack trace of the test case. */
  @Nullable
  public String getErrorStackTrace() {
    if (testResult == TestResult.ERROR) {
      return failureOrErrorStackTrace;
    } else {
      return null;
    }
  }

  /** Returns the error type of the test case. */
  @Nullable
  public String getErrorType() {
    if (testResult == TestResult.ERROR) {
      return failureOrErrorType;
    } else {
      return null;
    }
  }

  /** Sets the failure log of the test case. The sponge result will be FAIL and show the log. */
  @CanIgnoreReturnValue
  public SpongeTestCase setError(String errorLog) {
    return setError(errorLog, /* errorStackTrace= */ "");
  }

  /** Sets the error log of the test case. The sponge result will be ERROR and show the log. */
  @CanIgnoreReturnValue
  public SpongeTestCase setError(String errorLog, String errorStackTrace) {
    return setError(errorLog, errorStackTrace, /* errorType= */ "");
  }

  /**
   * Sets the error log, stack trace and error type of the test case. The sponge result will be
   * ERROR and show the log.
   */
  @CanIgnoreReturnValue
  public SpongeTestCase setError(String errorLog, String errorStackTrace, String errorType) {
    Preconditions.checkState(
        testResult != TestResult.FAIL, "Can not set error log in the FAIL sponge node.");
    if (Strings.isNullOrEmpty(errorLog)) {
      errorLog = " ";
    }
    failureOrErrorLog = errorLog;
    failureOrErrorStackTrace = errorStackTrace;
    failureOrErrorType = errorType;
    testResult = TestResult.ERROR;
    return this;
  }

  /** Sets the test case to SKIPPED. The sponge result will be show that the test was skipped. */
  @CanIgnoreReturnValue
  public SpongeTestCase setSkipped() {
    Preconditions.checkState(
        testResult != TestResult.FAIL, "Can not change FAIL sponge node to SKIPPED.");
    Preconditions.checkState(
        testResult != TestResult.ERROR, "Can not change ERROR sponge node to SKIPPED.");
    testResult = TestResult.SKIPPED;
    failureOrErrorLog = null;
    failureOrErrorStackTrace = null;
    return this;
  }

  /** Returns whether the test was skipped. */
  public boolean isSkipped() {
    return testResult.equals(TestResult.SKIPPED);
  }

  /** Clears the failure or error log of the test case. The sponge result will be Pass. */
  @CanIgnoreReturnValue
  SpongeTestCase setPass() {
    testResult = TestResult.PASS;
    failureOrErrorLog = null;
    failureOrErrorStackTrace = null;
    return this;
  }

  public TestResult getTestResult() {
    return testResult;
  }

  @CanIgnoreReturnValue
  public SpongeTestCase setRepeatNumber(Integer repeatNumber) {
    this.repeatNumber = Optional.of(repeatNumber);
    return this;
  }

  @CanIgnoreReturnValue
  public SpongeTestCase setRetryNumber(Integer retryNumber) {
    this.retryNumber = Optional.of(retryNumber);
    return this;
  }

  public Optional<Integer> getRepeatNumber() {
    return repeatNumber;
  }

  public Optional<Integer> getRetryNumber() {
    return retryNumber;
  }
}
