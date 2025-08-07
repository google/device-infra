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

package com.google.devtools.mobileharness.infra.controller.test.manager;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.TestRunner;
import java.util.List;

/**
 * Test manager which manages all the running tests. It can start and kill a test.
 *
 * @param <T> the type of test runner the manager manages
 */
public interface TestManager<T extends TestRunner> extends Runnable {
  /**
   * Starts a test runner to execute a test.
   *
   * @throws TestStartedException if the test has already started
   * @throws MobileHarnessException if the test fails to start
   */
  void startTest(T testRunner) throws MobileHarnessException;

  /** Kills a test if the test exists and is running and removes it from test manager. */
  void killAndRemoveTest(String testId);

  /** Kills all tests. */
  void killAllTests();

  /** Checks whether there is any {@link TestRunner} running. */
  boolean isAnyTestRunning();

  // TODO: Remove this method.
  /** Gets running test ids of the given job. */
  List<String> getRunningTestIds();

  /** Gets all tests of the given job. */
  ImmutableList<String> getAllTests(String jobId);

  /** Checks whether the test of this allocation is already running. */
  boolean isTestRunning(Allocation allocation) throws MobileHarnessException;
}
