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

package com.google.devtools.mobileharness.platform.android.instrumentation.parser;

/**
 * Receives events during instrumentation runs.
 *
 * <p>The order of calls is defined below. Calls in square brackets are optional.
 *
 * <ul>
 *   <li>`instrumentationStarted`
 *   <li>Zero or more of:
 *       <ul>
 *         <li>`testStarted`
 *         <li>`testEnded`
 *       </ul>
 *   <li>`[instrumentationFailed]`
 *   <li>`instrumentationEnded`
 * </ul>
 */
public interface AmInstrumentationListener {

  /**
   * Reports the start of an instrumentation.
   *
   * @param testCount Total number of tests in the instrumentation. For custom, non-test
   *     instrumentations the count is 0.
   */
  void instrumentationStarted(int testCount);

  /**
   * Reports the execution start of a test case.
   *
   * @param testIdentifier Identifies the started test case.
   */
  void testStarted(TestIdentifier testIdentifier);

  /**
   * Reports the execution end of a test case.
   *
   * @param testResult End result of the test case.
   */
  void testEnded(TestResult testResult);

  /**
   * Reports an instrumentation failed to complete due to a fatal error.
   *
   * @param errorMessage Describes the reason for the fatal error.
   */
  void instrumentationFailed(String errorMessage);

  /**
   * Reports the end of an instrumentation.
   *
   * @param instrumentationResult [InstrumentationResult] reported at the end of an instrumentation
   *     run.
   */
  void instrumentationEnded(InstrumentationResult instrumentationResult);
}
