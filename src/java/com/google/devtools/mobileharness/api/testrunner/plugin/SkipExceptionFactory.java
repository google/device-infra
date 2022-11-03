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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import javax.annotation.Nullable;

/**
 * Utility that creates a {@link SkipJobException} or a {@link SkipTestException}
 *
 * <p>For MH internal use only.
 */
public final class SkipExceptionFactory {

  // SkipExceptionFactory cannot be installed.
  private SkipExceptionFactory() {}

  /**
   * Creates a {@link SkipJobException} which can have any {@link TestResult} for MH internal use.
   *
   * @param jobResult the desired job result and top-level test results which should not be {@link
   *     TestResult#UNKNOWN}.
   * @param errorId the plugin defined errorId.
   */
  public static SkipJobException createSkipJobException(
      String reason, TestResult jobResult, ErrorId errorId, @Nullable Throwable cause) {
    checkArgument(
        jobResult != TestResult.UNKNOWN,
        "Desired job result of SkipJobException can not be UNKNOWN");
    return new SkipJobException(reason, jobResult, errorId, cause);
  }

  /**
   * Creates a {@link SkipTestException} which can have any {@link TestResult} for MH internal use.
   *
   * @deprecated Please use {@link #createSkipTestException(String, TestResult, ErrorId, Throwable)}
   *     instead.
   * @param testResult the desired test result which should not be {@link TestResult#UNKNOWN}.
   */
  @Deprecated
  public static SkipTestException createSkipTestException(
      String reason, TestResult testResult, @Nullable Throwable cause) {
    return createSkipTestException(reason, testResult, SkipTestException.DEFAULT_ERROR_ID, cause);
  }

  /**
   * Creates a {@link SkipTestException} which can have any {@link TestResult} for MH internal use.
   *
   * @param testResult the desired test result which should not be {@link TestResult#UNKNOWN}.
   * @param errorId the plugin defined errorId, used for tracing the errors in go/mh-data
   */
  public static SkipTestException createSkipTestException(
      String reason, TestResult testResult, ErrorId errorId, @Nullable Throwable cause) {
    checkArgument(
        testResult != TestResult.UNKNOWN,
        "Desired test result of SkipTestException can not be UNKNOWN");
    return new SkipTestException(reason, testResult, errorId, cause);
  }
}
