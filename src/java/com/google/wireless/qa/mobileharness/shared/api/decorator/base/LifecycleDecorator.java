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

package com.google.wireless.qa.mobileharness.shared.api.decorator.base;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.wireless.qa.mobileharness.shared.api.decorator.BaseDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/**
 * A generic base decorator class that provides native, framework-level enforcement of the setup and
 * teardown lifecycle steps.
 *
 * <pre>{@code
 * // Conceptual execution flow:
 * try {
 *   setUp(testInfo);              // Phase 1: Setup
 *   getDecorated().run(testInfo); // Phase 2: Decorated driver
 * } finally {
 *   tearDown(testInfo);           // Phase 3: Guaranteed cleanup (suppresses teardown error if setup or driver failed)
 * }
 * }</pre>
 */
public abstract class LifecycleDecorator extends BaseDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String classSimpleName = getClass().getSimpleName();

  protected LifecycleDecorator(Driver decorated, TestInfo testInfo) {
    super(decorated, testInfo);
  }

  /**
   * Invoked before the decorated driver executes.
   *
   * <p>Behavior and execution guarantees:
   *
   * <ul>
   *   <li>Executed as the first lifecycle phase when this decorator's {@link #run(TestInfo)} is
   *       invoked.
   *   <li>If {@code setUp} completes successfully, execution proceeds to the decorated driver.
   *   <li>If {@code setUp} throws an exception (e.g., {@link MobileHarnessException} or {@link
   *       InterruptedException}), execution skips the decorated driver and proceeds directly to
   *       {@link #tearDown(TestInfo)}. The exception thrown by {@code setUp} is preserved and
   *       rethrown after cleanup.
   * </ul>
   *
   * @param testInfo information and context of the running test
   * @throws MobileHarnessException if setup fails due to a MobileHarness error
   * @throws InterruptedException if setup is interrupted
   */
  protected abstract void setUp(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  /**
   * Invoked after the decorated driver or setup phase completes, regardless of success or failure.
   *
   * <p>Behavior and execution guarantees:
   *
   * <ul>
   *   <li><b>Guaranteed Cleanup Execution:</b> Always executed in a {@code finally} block, ensuring
   *       cleanup runs whether {@link #setUp(TestInfo)} succeeded, {@link #setUp(TestInfo)} failed,
   *       or the decorated driver failed.
   *   <li><b>Idempotent Teardown Contract:</b> Subclasses must implement this method defensibly and
   *       idempotently (e.g., checking for non-null resources before cleanup), as it may be invoked
   *       when {@code setUp} only partially completed.
   *   <li><b>Exception Suppression:</b> If an exception occurred during {@code setUp} or decorated
   *       driver execution, and {@code tearDown} also throws an exception, the teardown exception
   *       is automatically attached as a suppressed exception to the primary error. If both setup
   *       and driver run succeeded, any exception thrown by {@code tearDown} is thrown directly to
   *       the caller.
   * </ul>
   *
   * @param testInfo information and context of the running test
   * @throws MobileHarnessException if teardown fails due to a MobileHarness error
   * @throws InterruptedException if teardown is interrupted
   */
  protected abstract void tearDown(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  @Override
  public final void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    Throwable primaryException = null;
    try {
      executePhase(testInfo, "setup", this::setUp, /* primaryException= */ null);
      getDecorated().run(testInfo);
    } catch (Throwable e) {
      primaryException = e;
      throw e;
    } finally {
      executePhase(testInfo, "teardown", this::tearDown, primaryException);
    }
  }

  @FunctionalInterface
  private interface LifecyclePhase {
    void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException;
  }

  private void executePhase(
      TestInfo testInfo, String phaseName, LifecyclePhase phase, Throwable primaryException)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Decorator [%s] %s starting.", classSimpleName, phaseName);
    Throwable phaseError = null;
    try {
      phase.run(testInfo);
    } catch (Throwable e) {
      phaseError = e;
      if (primaryException != null) {
        if (MoreThrowables.isInterruption(e)) {
          Thread.currentThread().interrupt();
        }
        primaryException.addSuppressed(e);
      } else {
        throw e;
      }
    } finally {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Decorator [%s] %s finished%s.",
              classSimpleName, phaseName, getFailureSuffix(phaseError));
    }
  }

  private static String getFailureSuffix(Throwable error) {
    return error == null
        ? ""
        : String.format(" with failure [%s]", MoreThrowables.shortDebugString(error));
  }
}
