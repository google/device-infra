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

package com.google.devtools.mobileharness.infra.controller.test;

import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;

/** Test runner which is the main entry for controlling a generic Mobile Harness test. */
public interface TestRunner {

  /**
   * @return the execution information of the test.
   */
  TestExecutionUnit getTestExecutionUnit();

  /**
   * @return the allocation information of the test.
   */
  Allocation getAllocation();

  /**
   * Starts the test.
   *
   * <p>This method should only be invoked once.
   */
  void start() throws MobileHarnessException;

  /**
   * Kills the test.
   *
   * @param timeout if the test is killed because it timeouts
   * @return how many times the current test has been killed
   */
  int kill(boolean timeout);

  /**
   * Note that this method should be invoked after {@link #start()}.
   *
   * @return whether the current test is running.
   */
  boolean isRunning();

  /**
   * Returns whether the test runner is closed and test manager can safely remove it.
   *
   * <p>For some test runner, after it stops ({@link #isRunning()} returns <tt>false</tt>), it is
   * still necessary to keep the test runner until it is explicitly closed (e.g., {@linkplain
   * com.google.devtools.mobileharness.infra.container.controller.ProxyTestRunner ProxyTestRunner}).
   */
  boolean isClosed();
}
