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
 * Signals that the rest job execution should be skipped by the plugin and the job result and all
 * its top-level test results should be set to the given one.
 *
 * <p><b>NOTE</b> that it only works in a subscriber of {@linkplain
 * com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent JobStartEvent} in a MH client
 * plugin. Throwing this exception in <b>JobEndEvent</b> will <b>NOT</b> have any affect and the job
 * result will <b>NOT</b> be changed by the exception either. Please set the job result directly in
 * your plugin in that case.
 *
 * @since MH client 4.28.2
 */
public class SkipJobException extends Exception {

  /** Desired job result when throwing a {@link SkipJobException}. */
  public enum DesiredJobResult {
    PASS(TestResult.PASS),
    SKIP(TestResult.SKIP),
    FAIL(TestResult.FAIL);

    private final TestResult jobResult;

    DesiredJobResult(TestResult jobResult) {
      this.jobResult = jobResult;
    }

    public TestResult toProto() {
      return jobResult;
    }
  }

  private static final ErrorId DEFAULT_ERROR_ID = BasicErrorId.USER_PLUGIN_SKIP_JOB;

  private final TestResult jobResult;
  private final ErrorId errorId;

  /**
   * Creates a {@code SkipJobException} object.
   *
   * @param desiredJobResult the desired job result and top-level test results
   * @param errorId When plugin throws a {@code SkipJobException}, please define an ErrorId in
   *     {@linkplain com.google.devtools.mobileharness.api.model.error.UserErrorId UserErrorId}, and
   *     pass it to {@code SkipJobException}. This ErrorId helps MH to better track the test
   *     error/failures (More details in go/mh-exception)
   * @since MH lab server 4.133.0
   */
  public static SkipJobException create(
      String reason,
      DesiredJobResult desiredJobResult,
      ErrorId errorId,
      @Nullable Throwable cause) {
    return new SkipJobException(reason, desiredJobResult.toProto(), errorId, cause);
  }

  /**
   * Creates a {@code SkipJobException} object.
   *
   * @param desiredJobResult the desired job result and top-level test results
   * @param errorId When plugin throws a SkipJobException, please define an ErrorId in {@linkplain
   *     com.google.devtools.mobileharness.api.model.error.UserErrorId UserErrorId}, and pass it to
   *     {@code SkipJobException}. This ErrorId helps MH to better track the test error/failures
   *     (More details in go/mh-exception)
   * @since MH lab server 4.133.0
   */
  public static SkipJobException create(
      String reason, DesiredJobResult desiredJobResult, ErrorId errorId) {
    return new SkipJobException(reason, desiredJobResult.toProto(), errorId, null /* cause */);
  }

  /**
   * @param pass <tt>true</tt> if the job result and all its top-level test results should be set to
   *     <tt>PASS</tt> and <tt>false</tt> for FAIL
   * @deprecated Please use {@link #SkipJobException(String, DesiredJobResult)} instead.
   */
  @Deprecated
  public SkipJobException(String reason, boolean pass) {
    this(reason, pass, null /* cause */);
  }

  /**
   * @param pass <tt>true</tt> if the job result and all its top-level test results should be set to
   *     <tt>PASS</tt> and <tt>false</tt> for FAIL
   * @deprecated Please use {@link #create(String, DesiredJobResult, ErrorId, Throwable)} instead.
   */
  @Deprecated
  public SkipJobException(String reason, boolean pass, Throwable cause) {
    this(reason, pass ? DesiredJobResult.PASS : DesiredJobResult.FAIL, cause);
  }

  /**
   * @param desiredJobResult the desired job result and top-level test results
   * @deprecated Please use {@link #create(String, DesiredJobResult, ErrorId, Throwable)} instead.
   */
  @Deprecated
  public SkipJobException(String reason, DesiredJobResult desiredJobResult) {
    this(reason, desiredJobResult, null /* cause */);
  }

  /**
   * @param desiredJobResult the desired job result and top-level test results
   * @deprecated Please use {@link #create(String, DesiredJobResult, ErrorId, Throwable)} instead.
   */
  @Deprecated
  public SkipJobException(
      String reason, DesiredJobResult desiredJobResult, @Nullable Throwable cause) {
    this(reason, desiredJobResult.toProto(), DEFAULT_ERROR_ID, cause);
  }

  /**
   * For MH internal use only. Don't make it public
   *
   * @param jobResult the desired job result and top-level test results
   * @since MH client 4.28.3
   */
  SkipJobException(
      String reason, TestResult jobResult, ErrorId errorId, @Nullable Throwable cause) {
    super(formatMessage(reason, checkNotNull(jobResult), checkNotNull(errorId)), cause);
    this.jobResult = jobResult;
    this.errorId = errorId;
  }

  public TestResult jobResult() {
    return jobResult;
  }

  public ErrorId errorId() {
    return errorId;
  }

  private static String formatMessage(String reason, TestResult jobResult, ErrorId errorId) {
    return String.format("%s, job_result=%s, error_id=%s", reason, jobResult, errorId);
  }
}
