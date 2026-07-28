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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
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
 *   SetupResult result = setUp(); // Phase 1: Setup
 *   if (result.action() == CONTINUE_DECORATED) {
 *     getDecorated().run();       // Phase 2: Decorated driver
 *   }
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
   *   <li>If {@code setUp} completes successfully returning {@link
   *       SetupResult#continueDecorated()}, execution proceeds to the decorated driver.
   *   <li>If {@code setUp} completes returning a skip result (such as {@link
   *       SetupResult#skipDecoratedWithoutResult()}, {@link SetupResult#skipDecoratedWithPass()},
   *       or {@link SetupResult#skipDecoratedWithNonPassing(TestResult, MobileHarnessException)}),
   *       execution skips the decorated driver and proceeds directly to {@link
   *       #tearDown(TeardownContext)}.
   *   <li>If {@code setUp} throws an exception (e.g., {@link MobileHarnessException} or {@link
   *       InterruptedException}), execution skips the decorated driver and proceeds directly to
   *       {@link #tearDown(TeardownContext)}. The exception thrown by {@code setUp} is preserved
   *       and rethrown after cleanup.
   *   <li>Regardless of whether {@code setUp} returns continue, skip, or throws an exception,
   *       {@link #tearDown(TeardownContext)} will always run.
   * </ul>
   *
   * @param context the context containing setup metadata
   * @return the setup result indicating whether to continue to the decorated driver or skip it
   * @throws MobileHarnessException if setup fails due to a MobileHarness error
   * @throws InterruptedException if setup is interrupted
   */
  @CanIgnoreReturnValue
  protected abstract SetupResult setUp(SetupContext context)
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
      SetupResult setupResult = executeSetupPhase(testInfo, setupContext);
      setUpSuccess = true;
      if (setupResult.action() == SetupResult.Action.CONTINUE_DECORATED) {
        getDecorated().run(testInfo);
      }
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
      executeTeardownPhase(
          testInfo, teardownContext, setUpException != null ? setUpException : decoratedException);
    }
  }

  private SetupResult executeSetupPhase(TestInfo testInfo, SetupContext context)
      throws MobileHarnessException, InterruptedException {
    testInfo.log().atInfo().alsoTo(logger).log("Decorator [%s] setup starting.", classSimpleName);
    SetupResult setupResult = null;
    Throwable phaseError = null;
    try {
      setupResult = setUp(context);
      applySetupResult(testInfo, setupResult);
      return setupResult;
    } catch (Throwable e) {
      phaseError = e;
      throw e;
    } finally {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Decorator [%s] setup finished%s.",
              classSimpleName,
              phaseError != null
                  ? getFailureSuffix(phaseError)
                  : String.format(" with result [%s]", setupResult));
    }
  }

  private static void applySetupResult(TestInfo testInfo, SetupResult setupResult) {
    if (setupResult.action() == SetupResult.Action.SKIP_DECORATED
        && setupResult.testResult().isPresent()) {
      TestResult result = setupResult.testResult().get();
      if (result == TestResult.PASS) {
        testInfo.resultWithCause().setPass();
      } else {
        testInfo.resultWithCause().setNonPassing(result, setupResult.cause().orElseThrow());
      }
    }
  }

  private void executeTeardownPhase(
      TestInfo testInfo, TeardownContext context, Throwable primaryException)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Decorator [%s] teardown starting.", classSimpleName);
    Throwable phaseError = null;
    try {
      tearDown(context);
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
              "Decorator [%s] teardown finished%s.", classSimpleName, getFailureSuffix(phaseError));
    }
  }

  private static String getFailureSuffix(Throwable error) {
    return error == null
        ? ""
        : String.format(" with failure [%s]", MoreThrowables.shortDebugString(error));
  }

  /**
   * Result of the {@link #setUp(SetupContext)} phase, controlling whether to proceed to the
   * decorated driver or skip it.
   */
  @AutoValue
  public abstract static class SetupResult {

    /** Action to take after {@link #setUp(SetupContext)} completes. */
    public enum Action {
      /** Proceed to execute the decorated driver's {@code run()} method. */
      CONTINUE_DECORATED,

      /**
       * Skip the decorated driver's {@code run()} method and proceed directly to {@code
       * tearDown()}.
       */
      SKIP_DECORATED,
    }

    public abstract Action action();

    public abstract Optional<TestResult> testResult();

    public abstract Optional<MobileHarnessException> cause();

    /** Continues execution to the decorated driver. */
    public static SetupResult continueDecorated() {
      return new AutoValue_LifecycleDecorator_SetupResult(
          Action.CONTINUE_DECORATED, Optional.empty(), Optional.empty());
    }

    /** Skips the decorated driver, leaving the test result as currently set on {@code testInfo}. */
    public static SetupResult skipDecoratedWithoutResult() {
      return new AutoValue_LifecycleDecorator_SetupResult(
          Action.SKIP_DECORATED, Optional.empty(), Optional.empty());
    }

    /** Skips the decorated driver and sets the test result to PASS. */
    public static SetupResult skipDecoratedWithPass() {
      return new AutoValue_LifecycleDecorator_SetupResult(
          Action.SKIP_DECORATED, Optional.of(TestResult.PASS), Optional.empty());
    }

    /**
     * Skips the decorated driver and sets the specified non-passing test result with cause.
     *
     * @param testResult non-passing test result (e.g., FAIL, ERROR, SKIP, TIMEOUT)
     * @param cause non-null exception explaining why the test is non-passing
     */
    public static SetupResult skipDecoratedWithNonPassing(
        TestResult testResult, MobileHarnessException cause) {
      checkNotNull(testResult, "testResult must not be null");
      checkNotNull(cause, "cause must not be null for non-passing result [%s]", testResult);
      checkArgument(
          testResult != TestResult.PASS,
          "Use skipDecoratedWithPass() for PASS result instead of skipDecoratedWithNonPassing().");
      return new AutoValue_LifecycleDecorator_SetupResult(
          Action.SKIP_DECORATED, Optional.of(testResult), Optional.of(cause));
    }

    @Override
    public final String toString() {
      StringBuilder sb = new StringBuilder(action().name());
      if (testResult().isPresent() || cause().isPresent()) {
        sb.append(" (");
        boolean needsComma = false;
        if (testResult().isPresent()) {
          sb.append("test_result=").append(testResult().get());
          needsComma = true;
        }
        if (cause().isPresent()) {
          if (needsComma) {
            sb.append(", ");
          }
          sb.append("cause=").append(MoreThrowables.shortDebugString(cause().get()));
        }
        sb.append(")");
      }
      return sb.toString();
    }
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
