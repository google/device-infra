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

package com.google.devtools.mobileharness.api.testrunner.plugin;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import javax.annotation.Nullable;

/**
 * Signals that the rest test execution (driver/decorators) should be skipped by the plugin and the
 * test result should be set to the given one.
 *
 * <p><b>NOTE</b> that it only works in a subscriber of MH test event before driver starts. These
 * events includes:
 *
 * <ul>
 *   <li>TestStartingEvent/LocalTestStartingEvent
 *   <li>TestStartedEvent/LocalTestStartedEvent
 *   <li>LocalDriverStartingEvent
 *   <li>LocalDecoratorPreForwardEvent
 * </ul>
 *
 * Throwing this exception in a MH test event which is after driver execution like
 * <b>TestEndingEvent</b> will <b>NOT</b> have any affect and the test result will <b>NOT</b> be
 * changed by the exception either. Please set the test result directly in your plugin in that case.
 *
 * @since MH lab server 4.39
 * @since MH client 4.28.2
 */
public class SkipTestException extends Exception {

  /** Desired test result when throwing a {@link SkipTestException}. */
  public enum DesiredTestResult {
    PASS(TestResult.PASS),
    SKIP(TestResult.SKIP),
    FAIL(TestResult.FAIL),
    ERROR(TestResult.ERROR);

    private final TestResult testResult;

    DesiredTestResult(TestResult testResult) {
      this.testResult = testResult;
    }

    public TestResult toProto() {
      return testResult;
    }
  }

  static final ErrorId DEFAULT_ERROR_ID = BasicErrorId.USER_PLUGIN_SKIP_TEST;

  private final TestResult testResult;

  private final ErrorId errorId;

  /**
   * Creates a {@code SkipTestException} object.
   *
   * @param desiredTestResult the desired test result
   * @param errorId When plugin throws a SkipTestException, please define an ErrorId in {@linkplain
   *     com.google.devtools.mobileharness.api.model.error.UserErrorId UserErrorId}, and pass it to
   *     {@code SkipTestException}. This ErrorId helps MH to better track the test error/failures
   *     (More details in go/mh-exception)
   * @since MH lab server 4.133.0
   */
  public static SkipTestException create(
      String reason,
      DesiredTestResult desiredTestResult,
      ErrorId errorId,
      @Nullable Throwable cause) {
    return new SkipTestException(reason, desiredTestResult.toProto(), errorId, cause);
  }

  /**
   * Creates a {@code SkipTestException} object.
   *
   * @param desiredTestResult the desired test result
   * @param errorId When plugin throws a SkipTestException, please define an ErrorId in {@linkplain
   *     com.google.devtools.mobileharness.api.model.error.UserErrorId UserErrorId}, and pass it to
   *     {@code SkipTestException}. This ErrorId helps MH to better track the test error/failures
   *     (More details in go/mh-exception)
   * @since MH lab server 4.133.0
   */
  public static SkipTestException create(
      String reason, DesiredTestResult desiredTestResult, ErrorId errorId) {
    return new SkipTestException(reason, desiredTestResult.toProto(), errorId, null);
  }

  /**
   * @param pass <tt>true</tt> if the test result should be set to <tt>PASS</tt> and <tt>false</tt>
   *     for FAIL
   * @deprecated Please use {@link #SkipTestException(String, DesiredTestResult)} instead.
   */
  @Deprecated
  public SkipTestException(String reason, boolean pass) {
    this(reason, pass, null /* cause */);
  }

  /**
   * @param pass <tt>true</tt> if the test result should be set to <tt>PASS</tt> and <tt>false</tt>
   *     for FAIL
   * @deprecated Please use {@link #SkipTestException(String, DesiredTestResult, Throwable)}
   *     instead.
   * @since MH lab server 4.40
   * @since MH client 4.28.2
   */
  @Deprecated
  public SkipTestException(String reason, boolean pass, @Nullable Throwable cause) {
    this(reason, pass ? DesiredTestResult.PASS : DesiredTestResult.FAIL, cause);
  }

  /**
   * @param desiredTestResult the desired test result
   * @deprecated Please use {@link #create(String, DesiredTestResult, ErrorId)} instead.
   * @since MH lab server 4.42
   * @since MH client 4.28.3
   */
  @Deprecated
  public SkipTestException(String reason, DesiredTestResult desiredTestResult) {
    this(reason, desiredTestResult, null /* cause */);
  }

  /**
   * @param desiredTestResult the desired test result
   * @deprecated Please use {@link #create(String, DesiredTestResult, ErrorId, Throwable)} instead.
   * @since MH lab server 4.42
   * @since MH client 4.28.3
   */
  @Deprecated
  public SkipTestException(
      String reason, DesiredTestResult desiredTestResult, @Nullable Throwable cause) {
    this(reason, desiredTestResult.toProto(), DEFAULT_ERROR_ID, cause);
  }

  /**
   * For MH internal use only. Don't make it public.
   *
   * @param testResult the desired test result which should not be {@link TestResult#UNKNOWN}.
   * @since MH lab server 4.42
   * @since MH client 4.28.3
   */
  SkipTestException(
      String reason, TestResult testResult, ErrorId errorId, @Nullable Throwable cause) {
    super(formatMessage(reason, checkNotNull(testResult), checkNotNull(errorId)), cause);
    this.testResult = testResult;
    this.errorId = errorId;
  }

  /** Test result which will not be {@link TestResult#UNKNOWN}. */
  public TestResult testResult() {
    return testResult;
  }

  public ErrorId errorId() {
    return errorId;
  }

  private static String formatMessage(String reason, TestResult testResult, ErrorId errorId) {
    return String.format("%s, test_result=%s, error_id=%s", reason, testResult, errorId);
  }
}
