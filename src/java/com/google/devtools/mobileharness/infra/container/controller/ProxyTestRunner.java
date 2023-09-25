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

package com.google.devtools.mobileharness.infra.container.controller;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineLocator;
import com.google.devtools.mobileharness.infra.container.proto.TestEngine.TestEngineStatus;
import com.google.devtools.mobileharness.infra.controller.test.TestRunner;
import com.google.devtools.mobileharness.infra.controller.test.util.TestRunnerTiming;
import com.google.devtools.mobileharness.infra.lab.proto.File.JobOrTestFileUnit;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.util.Optional;

/**
 * Proxy test runner for proxying a test to a direct test runner in a container or the current lab
 * server process in a MH lab.
 *
 * @see <a href="http://go/mh-container-design">Mobile Harness Container Design</a>
 * @see <a href="http://go/mhv5-tr-design">Mobile Harness V5 Test Runner Design</a>
 */
public interface ProxyTestRunner extends TestRunner, TestRunnerTiming {

  /**
   * Notifies that MH lab has received a job/test file related to this test.
   *
   * <p><b>IMPORTANT</b>: Do <b>not</b> do any heavy-weight work and do <b>not</b> throw exceptions
   * in this method.
   */
  void notifyJobOrTestFile(JobOrTestFileUnit fileUnit);

  /**
   * Closes the test in lab side and indicates that MH client will not send more requests about the
   * test.
   *
   * <p><b>IMPORTANT</b>: Please do heavy-weight work asynchronously in this method.
   */
  void closeTest();

  /** Whether the test runs in container mode. */
  boolean isContainerMode();

  /** Whether the test runs in sandbox mode. */
  boolean isSandboxMode();

  Optional<TestEngineLocator> getTestEngineLocator();

  /**
   * {@link #getTestEngineLocator()} should be present if it is {@link TestEngineStatus#READY};
   * {@link #getTestEngineError()} should be present if it is {@link TestEngineStatus#FAILED}.
   */
  TestEngineStatus getTestEngineStatus();

  Optional<MobileHarnessException> getTestEngineError();

  /**
   * Waits if necessary for at most the given time for the test engine ready, and then retrieves its
   * result, if available.
   *
   * @param timeout the maximum time to wait
   * @return true if the test engine is ready, or false if timeout.
   */
  @CanIgnoreReturnValue
  boolean waitUntilTestEngineReady(Duration timeout) throws InterruptedException;

  void asyncStartTestEngine();

  Optional<Duration> getTestEngineSetupTime();
}
