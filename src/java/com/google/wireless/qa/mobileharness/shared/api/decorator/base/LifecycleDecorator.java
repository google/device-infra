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

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.wireless.qa.mobileharness.shared.api.decorator.BaseDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A generic base decorator class that provides native, framework-level enforcement of the setup and
 * teardown lifecycle steps.
 *
 * <pre>{@code
 * // Conceptual execution flow:
 * try {
 *   setUp();                      // Phase 1: Setup
 *   getDecorated().run();         // Phase 2: Decorated driver
 * } finally {
 *   tearDown();                   // Phase 3: Guaranteed cleanup (suppresses teardown error if setup or driver failed)
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
   *   <li>If {@code setUp} completes successfully (does not throw an exception), execution proceeds
   *       to the decorated driver.
   *   <li>If {@code setUp} throws an exception (e.g., {@link MobileHarnessException} or {@link
   *       InterruptedException}), execution skips the decorated driver and proceeds directly to
   *       {@link #tearDown(TeardownContext)}. The exception thrown by {@code setUp} is preserved
   *       and rethrown after cleanup.
   *   <li>Regardless of whether {@code setUp} throws an exception or not, {@link
   *       #tearDown(TeardownContext)} will always run.
   *   <li>Therefore, if {@code setUp} needs to skip the decorated driver's execution, the
   *       recommended approach is to set the test result (e.g., calling {@code
   *       testInfo.resultWithCause().setNonPassing(...)}) and throw a {@link
   *       MobileHarnessException}.
   * </ul>
   *
   * @param context the context containing setup metadata
   * @throws MobileHarnessException if setup fails due to a MobileHarness error
   * @throws InterruptedException if setup is interrupted
   */
  protected abstract void setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException;

  /**
   * Invoked after the decorated driver or setup phase completes, regardless of success or failure.
   *
   * <p>Behavior and execution guarantees:
   *
   * <ul>
   *   <li><b>Guaranteed Cleanup Execution:</b> Always executed in a {@code finally} block, ensuring
   *       cleanup runs whether {@link #setUp(SetupContext)} succeeded, {@link #setUp(SetupContext)}
   *       failed, or the decorated driver failed.
   *   <li><b>Defensive Resource Cleanup:</b> Because {@code tearDown} is always called regardless
   *       of where {@link #setUp(SetupContext)} throws an exception, {@code tearDown} needs to
   *       check every single resource individually to determine whether it needs to be released and
   *       release each of them.
   *   <li><b>Error Context Inspection:</b> {@code tearDown} can inspect {@link
   *       TeardownContext#setupError()}, {@link TeardownContext#decoratedError()}, and {@link
   *       TeardownContext#setupOrDecoratedError()} to determine whether {@code setUp} or the
   *       decorated driver threw an exception.
   *   <li><b>Exception Suppression:</b> If an exception occurred during {@code setUp} or decorated
   *       driver execution, and {@code tearDown} also throws an exception, the teardown exception
   *       is automatically attached as a suppressed exception to the primary error. If both setup
   *       and driver run succeeded, any exception thrown by {@code tearDown} is thrown directly to
   *       the caller.
   * </ul>
   *
   * @param context the context containing runtime error states
   * @throws MobileHarnessException if teardown fails due to a MobileHarness error
   * @throws InterruptedException if teardown is interrupted
   */
  protected abstract void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException;

  @Override
  public final void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    boolean setUpSuccess = false;
    Throwable setUpException = null;
    Throwable decoratedException = null;
    try {
      SetupContext setupContext = SetupContext.create(testInfo);
      executePhase(testInfo, "setup", this::setUp, setupContext, /* primaryException= */ null);
      setUpSuccess = true;
      getDecorated().run(testInfo);
    } catch (Throwable e) {
      if (!setUpSuccess) {
        setUpException = e;
      } else {
        decoratedException = e;
      }
      throw e;
    } finally {
      TeardownContext teardownContext =
          TeardownContext.create(testInfo, setUpException, decoratedException);
      executePhase(
          testInfo,
          "teardown",
          this::tearDown,
          teardownContext,
          setUpException != null ? setUpException : decoratedException);
    }
  }

  @FunctionalInterface
  private interface LifecyclePhase<T> {
    void run(T context) throws MobileHarnessException, InterruptedException;
  }

  private <T> void executePhase(
      TestInfo testInfo,
      String phaseName,
      LifecyclePhase<T> phase,
      T context,
      Throwable primaryException)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Decorator [%s] %s starting.", classSimpleName, phaseName);
    Throwable phaseError = null;
    try {
      phase.run(context);
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

  /** Context containing metadata for the decorator setup phase. */
  @AutoValue
  public abstract static class SetupContext {
    public abstract TestInfo testInfo();

    public static SetupContext create(TestInfo testInfo) {
      return new AutoValue_LifecycleDecorator_SetupContext(testInfo);
    }
  }

  /** Context containing execution results and metadata for the decorator teardown phase. */
  @AutoValue
  public abstract static class TeardownContext {
    public abstract TestInfo testInfo();

    public abstract Optional<Throwable> setupError();

    public abstract Optional<Throwable> decoratedError();

    /**
     * Gets the unique error that occurred during setup or execution. Returns empty if successful.
     */
    public final Optional<Throwable> setupOrDecoratedError() {
      return setupError().or(() -> decoratedError());
    }

    public static TeardownContext create(
        TestInfo testInfo, @Nullable Throwable setupError, @Nullable Throwable decoratedError) {
      return new AutoValue_LifecycleDecorator_TeardownContext(
          testInfo, Optional.ofNullable(setupError), Optional.ofNullable(decoratedError));
    }
  }
}
