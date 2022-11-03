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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.exception.TestRunnerLauncherConnectedException;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionResult;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.concurrent.GuardedBy;

/**
 * Test runner launcher for specifying test launching strategy of an {@link AbstractTestRunner}.
 *
 * <p>A test launching strategy includes the following abstract methods a sub class of this class
 * needs to implement:
 *
 * <ol>
 *   <li>{@link #asyncLaunchTest()}: Launches the test asynchronously. <b>Launching a test</b> means
 *       invoking {@link #executeTest()} after {@link TestRunner#start()} invoked and all
 *       prerequisites are meet (e.g., a local device finishes periodical check).
 *   <li>{@link #killTest()}: Kills the test.
 *   <li>{@link #isTestRunning()}: Decides whether the test is running.
 * </ol>
 *
 * <p>This class also provides necessary methods which can help implement a launching strategy:
 *
 * <ul>
 *   <li>{@link #executeTest()}
 *   <li>{@link #isTestExecuting()}
 *   <li>{@link #finalizeTest(String)}
 *   <li>{@link #getTestRunner()}
 * </ul>
 *
 * <p>Note that one instance of this class can only be passed to <b>one</b> {@link
 * AbstractTestRunner}.
 *
 * <p>An example implementation of this class is {@linkplain
 * com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher
 * LocalDeviceTestRunnerLauncher}, which submits the test to a {@linkplain
 * com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunner LocalDeviceRunner}
 * and runs the test in the main thread of the device runner.
 *
 * @param <T> the type of test runner the launcher can connect to
 * @see AbstractTestRunner
 */
public abstract class TestRunnerLauncher<T extends TestRunner> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Object testRunnerLock = new Object();

  @GuardedBy("testRunnerLock")
  private T testRunner;

  @GuardedBy("testRunnerLock")
  private AbstractTestRunner<?> abstractTestRunner;

  private final AtomicBoolean isExecuting = new AtomicBoolean();

  /**
   * Launches the test asynchronously.
   *
   * <p><b>Launching a test</b> means invoking {@link #executeTest()} when all prerequisites are
   * meet (e.g., a local device finishes periodical check).
   *
   * <p>This method will not be invoked repeatedly by {@link AbstractTestRunner}.
   */
  protected abstract void asyncLaunchTest() throws MobileHarnessException;

  /** Kills the test. */
  protected abstract void killTest();

  /**
   * @return whether the test is running
   */
  protected abstract boolean isTestRunning();

  /**
   * Synchronously invokes {@link AbstractTestRunner#execute()}.
   *
   * <p>This method can not be invoked repeatedly.
   *
   * <p>Do <b>not</b> invoke this method before you pass this launcher to an {@link
   * AbstractTestRunner}.
   */
  protected final TestExecutionResult executeTest() throws InterruptedException {
    AbstractTestRunner<?> abstractTestRunner;
    synchronized (testRunnerLock) {
      abstractTestRunner = this.abstractTestRunner;
    }
    checkState(
        isExecuting.compareAndSet(false /* expect */, true /* update */),
        "Can not execute test twice");
    try {
      logger.atInfo().log(
          "Executing test [%s]", getTestRunner().getTestExecutionUnit().locator().id());
      return abstractTestRunner.doExecute();
    } finally {
      isExecuting.set(false);
    }
  }

  /**
   * Returns whether {@link AbstractTestRunner#execute()} is being invoked.
   *
   * <p>Do <b>not</b> invoke this method before you pass this launcher to an {@link
   * AbstractTestRunner}.
   */
  protected final boolean isTestExecuting() {
    return isExecuting.get();
  }

  /**
   * Invokes {@link AbstractTestRunner#finalizeTest(MobileHarnessException)}.
   *
   * <p>Do <b>not</b> invoke this method before you pass this launcher to an {@link
   * AbstractTestRunner}.
   *
   * @see AbstractTestRunner#finalizeTest(MobileHarnessException)
   */
  protected final void finalizeTest(MobileHarnessException error) {
    AbstractTestRunner<?> abstractTestRunner;
    synchronized (testRunnerLock) {
      abstractTestRunner = this.abstractTestRunner;
    }
    abstractTestRunner.finalizeTest(error);
  }

  /**
   * Gets the {@link TestRunner} to get the basic information of the test, such as {@link
   * TestRunner#getAllocation()} and {@link TestRunner#getTestExecutionUnit()}.
   *
   * <p>Do <b>not</b> invoke this method before you pass this launcher to an {@link
   * AbstractTestRunner}.
   */
  protected final T getTestRunner() {
    synchronized (testRunnerLock) {
      return testRunner;
    }
  }

  /**
   * @return whether the launcher has been connected to a test runner
   */
  protected final boolean isConnected() {
    synchronized (testRunnerLock) {
      return testRunner != null;
    }
  }

  /** Invoked by {@link AbstractTestRunner}. */
  final void setTestRunner(T testRunner, AbstractTestRunner<?> abstractTestRunner)
      throws TestRunnerLauncherConnectedException {
    checkNotNull(testRunner);
    checkNotNull(abstractTestRunner);
    synchronized (testRunnerLock) {
      if (this.testRunner != null) {
        throw new TestRunnerLauncherConnectedException(
            "Test runner launcher can not be used twice");
      }
      this.testRunner = testRunner;
      this.abstractTestRunner = abstractTestRunner;
    }
  }
}
