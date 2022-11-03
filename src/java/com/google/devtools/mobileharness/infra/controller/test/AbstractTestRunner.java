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
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.infra.controller.test.exception.TestRunnerLauncherConnectedException;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionResult;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.infra.controller.test.util.TestRunnerTiming;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract test runner which uses a specific {@link TestRunnerLauncher} to decide {@linkplain
 * TestRunnerLauncher#asyncLaunchTest() test launching strategy} and exposes several abstract
 * template methods
 *
 * <ul>
 *   <li>{@link #execute()}
 *   <li>{@link #preExecute()}
 *   <li>{@link #postKill(boolean, int)}
 *   <li>{@link #finalizeTest(MobileHarnessException)}
 * </ul>
 *
 * so its sub classes can implement test execution logic.
 *
 * @param <T> the type of the test runner
 * @see TestRunnerLauncher
 */
public abstract class AbstractTestRunner<T extends AbstractTestRunner<T>>
    implements TestRunner, TestRunnerTiming {

  private final TestRunnerLauncher<? super T> launcher;
  private final TestExecutionUnit testExecutionUnit;
  private final Allocation allocation;
  private final AtomicBoolean hasStarted = new AtomicBoolean();
  private final AtomicInteger killCount = new AtomicInteger();
  private volatile Instant startInstant;
  private volatile Instant executeInstant;

  private static final Clock CLOCK = Clock.systemUTC();

  protected AbstractTestRunner(
      TestRunnerLauncher<? super T> launcher,
      TestExecutionUnit testExecutionUnit,
      Allocation allocation)
      throws TestRunnerLauncherConnectedException {
    this.launcher = launcher;
    this.testExecutionUnit = testExecutionUnit;
    this.allocation = allocation;
    launcher.setTestRunner(self(), this);
  }

  @Override
  public final TestExecutionUnit getTestExecutionUnit() {
    return testExecutionUnit;
  }

  @Override
  public final Allocation getAllocation() {
    return allocation;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Firstly this method will invoke {@link #preExecute()} and then it will invoke {@link
   * TestRunnerLauncher#asyncLaunchTest()} to launch the test asynchronously.
   *
   * @throws MobileHarnessException if the test has already started or the launcher fails to launch
   *     the test
   */
  @Override
  public final void start() throws MobileHarnessException {
    MobileHarnessExceptions.check(
        hasStarted.compareAndSet(false /* expect */, true /* update */),
        InfraErrorId.TM_TEST_RUNNER_STARTED_TWICE,
        () -> String.format("Test %s has already started", getTestExecutionUnit().locator().id()));
    startInstant = CLOCK.instant();
    preExecute();
    launcher.asyncLaunchTest();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Firstly this method will invoke {@link TestRunnerLauncher#killTest()} to kill the test and
   * then it will invoke {@link #postKill(boolean, int)}.
   */
  @Override
  public final int kill(boolean timeout) {
    launcher.killTest();
    int killCount = this.killCount.incrementAndGet();
    postKill(timeout, killCount);
    return killCount;
  }

  /**
   * Sets the execute instant to now and then conducts the real execute logic. This method can only
   * be invoked by {@link TestRunnerLauncher}
   */
  final TestExecutionResult doExecute() throws InterruptedException {
    executeInstant = CLOCK.instant();
    return execute();
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method will invoke {@link TestRunnerLauncher#isTestRunning()} to decide whether the
   * test is running.
   */
  @Override
  public final boolean isRunning() {
    return launcher.isTestRunning();
  }

  @Override
  public final Optional<Instant> getTestRunnerStartInstant() {
    return Optional.ofNullable(startInstant);
  }

  @Override
  public final Optional<Instant> getTestRunnerExecuteInstant() {
    return Optional.ofNullable(executeInstant);
  }

  /**
   * Does pre-execution work of the test.
   *
   * <p>This method will be invoked right after {@link #start()} is invoked synchronously and before
   * {@link TestRunnerLauncher#asyncLaunchTest()} is invoked.
   *
   * <p>Note that this is intended only for very <b>lightweight</b> work, such as timing statistics
   * and initializing fields. If you need to do any heavier work, please move them to {@link
   * #execute()}.
   *
   * <p>In most cases, you can just leave this method empty.
   */
  protected abstract void preExecute();

  /**
   * Actually executes the test.
   *
   * <p>This method will be invoked by {@link TestRunnerLauncher} when it successfully launches the
   * test (e.g., the local device becomes ready in {@linkplain
   * com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher
   * LocalDeviceTestRunnerLauncher}).
   */
  protected abstract TestExecutionResult execute() throws InterruptedException;

  /** Does post-kill work after the test is killed by {@link TestRunnerLauncher#killTest()}. */
  protected abstract void postKill(boolean timeout, int killCount);

  /**
   * Does finalization work of the test.
   *
   * <p>This method will be invoked if {@link #start()} has been invoked but {@link #execute()} has
   * not been invoked and the {@link TestRunnerLauncher} knows that it will not invoke {@link
   * #execute()} anymore in the future because of some errors (e.g., the device disconnects when
   * {@linkplain com.google.devtools.mobileharness.infra.controller.device.LocalDeviceRunner
   * LocalDeviceRunner} is checking the device after {@linkplain
   * com.google.devtools.mobileharness.infra.controller.test.launcher.LocalDeviceTestRunnerLauncher
   * LocalDeviceTestRunnerLauncher} has reserved the device).
   *
   * <p>Note that MH infra will try best to invoke this method when an error happens but whether it
   * will be invoked and how many times it will be invoked are <b>not</b> ensured.
   *
   * <p>In most cases, you can just leave this method empty.
   *
   * @param error the detailed error which causes {@link TestRunnerLauncher} will not invoke {@link
   *     #execute()} anymore
   */
  protected abstract void finalizeTest(MobileHarnessException error);

  /**
   * @return self
   */
  protected abstract T self();
}
