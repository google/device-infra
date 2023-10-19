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

package com.google.devtools.mobileharness.api.model.job.out;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StackSize;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.in.Params;
import com.google.devtools.mobileharness.api.model.proto.Error.ExceptionDetail;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.model.proto.Test.TestStatus;
import com.google.devtools.mobileharness.shared.util.error.ErrorModelConverter;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Result of a job/test. */
public class Result {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Job param to allow overriding PASS test to TIMEOUT. False by default. */
  @VisibleForTesting
  static final String PARAM_ALLOW_OVERRIDE_PASS_TO_TIMEOUT = "allow_override_pass_to_timeout";

  static final String PARAM_ALLOW_OVERRIDE_PASS_TO_ERROR = "allow_override_pass_to_error";

  /** Job param to print stack trace when setting the result as PASS. True by default. */
  static final String PARAM_PRINT_STACK_TRACE_FOR_PASS_TEST = "print_stack_trace_for_pass_test";

  /** Job/test result. Any result doesn't mean finished, while {@link TestStatus#DONE} does. */
  @GuardedBy("lock")
  private TestResult result = TestResult.UNKNOWN;

  /**
   * The cause of the result when it is not PASS/UNKNOWN.
   *
   * <p>We use a serialized proto rather than an exception object here because:
   *
   * <ol>
   *   <li>exception objects may bring unexpected memory usage.
   *   <li>serialized protos provides consistent results among different components.
   * </ol>
   */
  @GuardedBy("lock")
  @Nullable
  private ExceptionProto.ExceptionDetail cause = null;

  private final TouchableTiming timing;
  private final Params params;
  private final Object lock = new Object();

  /** Creates the result of a job/test. */
  public Result(TouchableTiming timing, Params params) {
    this.timing = timing;
    this.params = params;
  }

  /** Sets the test result to PASS, and cleans up the cause exception if any. */
  @CanIgnoreReturnValue
  public Result setPass() {
    return setPass(/* logChangedResult= */ true);
  }

  /** Sets the test result to PASS, and cleans up the cause exception if any. */
  @CanIgnoreReturnValue
  public Result setPass(boolean logChangedResult) {
    synchronized (lock) {
      if (this.result == TestResult.PASS) {
        return this;
      }
      if (this.result.equals(TestResult.TIMEOUT)) {
        // Timeout test may require rebooting the device: b/33743212.
        logger.atWarning().withStackTrace(StackSize.MEDIUM).log(
            "Prevent overriding %s result to PASS", this);
        return this;
      }

      if (logChangedResult) {
        if (params.getBool(PARAM_PRINT_STACK_TRACE_FOR_PASS_TEST, true)) {
          logger.atInfo().log(
              "Result %s -> PASS, caller=%s", this, MoreThrowables.shortDebugCurrentStackTrace(4));
        } else {
          logger.atInfo().log("Result %s -> PASS", this);
        }
      }

      this.result = TestResult.PASS;
      this.cause = null;
      timing.touch();
    }
    return this;
  }

  /**
   * Sets to non-PASSing result and records the cause.
   *
   * @param result can't be PASS/UNKNOWN
   * @see <a href="go/mh-test-result">MH Test Result Classification</a>
   */
  @CanIgnoreReturnValue
  public Result setNonPassing(TestResult result, MobileHarnessException cause) {
    return setNonPassing(
        result,
        com.google.devtools.common.metrics.stability.converter.ErrorModelConverter
            .toExceptionDetail(cause));
  }

  /**
   * Sets to non-PASSing result and records the cause.
   *
   * @param result can't be PASS/UNKNOWN
   * @see <a href="go/mh-test-result">MH Test Result Classification</a>
   */
  @CanIgnoreReturnValue
  public Result setNonPassing(TestResult result, ExceptionProto.ExceptionDetail cause) {
    Preconditions.checkArgument(result != TestResult.PASS && result != TestResult.UNKNOWN);
    Preconditions.checkNotNull(cause);

    synchronized (lock) {
      if (this.result == result && Objects.equals(this.cause, cause)) {
        return this;
      }
      if (this.result.equals(TestResult.TIMEOUT)) {
        // Timeout test may require rebooting the device: b/33743212.
        logger.atWarning().withStackTrace(StackSize.FULL).log(
            "Prevent overriding %s result to %s", this, formatResultWithCause(result, cause));
        this.cause =
            this.cause == null ? null : this.cause.toBuilder().addSuppressed(cause).build();
        return this;
      }

      if ((this.result.equals(TestResult.PASS)
          && ((!params.isTrue(PARAM_ALLOW_OVERRIDE_PASS_TO_ERROR)
                  && (result.equals(TestResult.ERROR) || result.equals(TestResult.INFRA_ERROR)))
              || (!params.isTrue(PARAM_ALLOW_OVERRIDE_PASS_TO_TIMEOUT)
                  && result.equals(TestResult.TIMEOUT))))) {
        logger.atWarning().withStackTrace(StackSize.FULL).log(
            "Prevent overriding %s result to %s", this, formatResultWithCause(result, cause));
        return this;
      }

      if ((this.result.equals(TestResult.FAIL) || this.result.equals(TestResult.SKIP))
          && (result.equals(TestResult.ERROR)
              || result.equals(TestResult.INFRA_ERROR)
              || result.equals(TestResult.TIMEOUT))) {
        logger.atWarning().withStackTrace(StackSize.FULL).log(
            "Prevent overriding %s result to %s", this, formatResultWithCause(result, cause));
        this.cause =
            this.cause == null ? null : this.cause.toBuilder().addSuppressed(cause).build();
        return this;
      }

      logger.atInfo().withStackTrace(StackSize.FULL).log(
          "Result %s -> %s", this, formatResultWithDetailedCause(result, cause));
      this.result = result;
      if (this.cause == null) {
        this.cause = cause;
      } else {
        this.cause = cause.toBuilder().addSuppressed(this.cause).build();
      }
      timing.touch();
    }
    return this;
  }

  /** Test result type and its cause. */
  @AutoValue
  public abstract static class ResultTypeWithCause {

    /**
     * @param cause NON-NULL when not PASS and not UNKNOWN and NULL when PASS or UNKNOWN
     * @throws IllegalArgumentException if the parameters are illegal
     */
    private static ResultTypeWithCause create(
        TestResult type, @Nullable ExceptionProto.ExceptionDetail cause) {
      return createInternal(type, /* useProtoBackend= */ true, cause, /* exceptionBackend= */ null);
    }

    /**
     * Creates a {link ResultTypeWithCause}.
     *
     * @param type if it is PASS, the cause must be NULL; if it is not PASS, the cause must be
     *     NON-NULL; it can not be UNKNOWN
     * @param cause if it is NULL, the type must be PASS; if it is NON-NULL, the type must not be
     *     PASS or UNKNOWN
     * @throws IllegalArgumentException if the parameters are illegal
     */
    public static ResultTypeWithCause create(
        TestResult type, @Nullable MobileHarnessException cause) {
      Preconditions.checkNotNull(type);
      Preconditions.checkArgument(type != TestResult.UNKNOWN);
      return createInternal(type, /* useProtoBackend= */ false, /* protoBackend= */ null, cause);
    }

    /** Gets the current result type. */
    public abstract TestResult type();

    /**
     * The cause of the result. Not empty if and only if the result is not PASS and not UNKNOWN.
     *
     * @see <a href="go/mh-test-result">MH Test Result Classification</a>
     * @deprecated Please use {@link #causeProto()}
     */
    @Memoized
    @Deprecated
    public Optional<ExceptionDetail> cause() {
      return causeProto().map(ErrorModelConverter::toExceptionDetailWithoutNamespace);
    }

    /**
     * The cause of the result.
     *
     * @throws NullPointerException if the result is PASS or UNKNOWN
     * @see <a href="go/mh-test-result">MH Test Result Classification</a>
     * @deprecated Please use {@link #causeProtoNonEmpty()}
     */
    @Memoized
    @Deprecated
    public ExceptionDetail causeNonEmpty() {
      return cause().orElseThrow(NullPointerException::new);
    }

    /**
     * The cause of the result. Not empty if and only if the result is not PASS and not UNKNOWN.
     *
     * @see <a href="go/mh-test-result">MH Test Result Classification</a>
     */
    @Memoized
    public Optional<ExceptionProto.ExceptionDetail> causeProto() {
      return useProtoBackend()
          ? protoBackend()
          : exceptionBackend()
              .map(
                  com.google.devtools.common.metrics.stability.converter.ErrorModelConverter
                      ::toExceptionDetail);
    }

    /**
     * The cause of the result.
     *
     * @throws NullPointerException if the result is PASS or UNKNOWN
     * @see <a href="go/mh-test-result">MH Test Result Classification</a>
     */
    @Memoized
    public ExceptionProto.ExceptionDetail causeProtoNonEmpty() {
      return causeProto().orElseThrow(NullPointerException::new);
    }

    /**
     * The exception representation of the cause of the result. Not empty if and only if the result
     * is not PASS and not UNKNOWN.
     *
     * @see <a href="go/mh-test-result">MH Test Result Classification</a>
     */
    @Memoized
    public Optional<MobileHarnessException> causeException() {
      return useProtoBackend()
          ? protoBackend()
              .map(ErrorModelConverter::toExceptionDetailWithoutNamespace)
              .map(ErrorModelConverter::toMobileHarnessException)
          : exceptionBackend();
    }

    /**
     * The exception representation of the cause of the result.
     *
     * @throws NullPointerException if the result is PASS or UNKNOWN
     * @see <a href="go/mh-test-result">MH Test Result Classification</a>
     */
    @Memoized
    public MobileHarnessException causeExceptionNonEmpty() {
      return causeException().orElseThrow(NullPointerException::new);
    }

    @Memoized
    @Override
    public String toString() {
      return formatResultWithCause(type(), causeProto().orElse(null));
    }

    /** Example: FAIL[cause=java.lang.IOException...] */
    @Memoized
    public String toStringWithDetail() {
      return formatResultWithDetailedCause(type(), causeProto().orElse(null));
    }

    /** Do NOT call this method directly out of this class. */
    abstract boolean useProtoBackend();

    /** Do NOT call this method directly out of this class. */
    abstract Optional<ExceptionProto.ExceptionDetail> protoBackend();

    /** Do NOT call this method directly out of this class. */
    abstract Optional<MobileHarnessException> exceptionBackend();

    /** Do NOT call this method directly out of this class. */
    private static ResultTypeWithCause createInternal(
        TestResult type,
        boolean useProtoBackend,
        @Nullable ExceptionProto.ExceptionDetail protoBackend,
        @Nullable MobileHarnessException exceptionBackend) {
      boolean passOrUnknown = (type == TestResult.PASS || type == TestResult.UNKNOWN);
      if (useProtoBackend) {
        exceptionBackend = null;
      } else {
        protoBackend = null;
      }
      boolean hasCause = (protoBackend != null || exceptionBackend != null);
      Preconditions.checkArgument(passOrUnknown != hasCause);
      return new AutoValue_Result_ResultTypeWithCause(
          type,
          useProtoBackend,
          Optional.ofNullable(protoBackend),
          Optional.ofNullable(exceptionBackend));
    }
  }

  public ResultTypeWithCause get() {
    synchronized (lock) {
      return ResultTypeWithCause.create(result, cause);
    }
  }

  @Override
  public String toString() {
    synchronized (lock) {
      return formatResultWithCause(result, cause);
    }
  }

  /**
   * Sets to UNKNOWN from other result types. Only be used to support the users of the legacy {@link
   * com.google.wireless.qa.mobileharness.shared.model.job.out.Result#set}.
   *
   * <p>DO NOT public this method. Will remove this method after all legacy users are migrated.
   */
  @CanIgnoreReturnValue
  protected Result setUnknown() {
    synchronized (lock) {
      if (result.equals(TestResult.TIMEOUT)) {
        // Timeout test may require rebooting the device: b/33743212.
        logger.atWarning().log("Prevent overriding %s result to UNKNOWN", this);
      } else if (result != TestResult.UNKNOWN) {
        logger.atInfo().log("Result %s -> UNKNOWN", this);
        result = TestResult.UNKNOWN;
        cause = null;
        timing.touch();
      }
    }
    return this;
  }

  private static String formatResultWithCause(
      TestResult result, @Nullable ExceptionProto.ExceptionDetail cause) {
    if (cause == null) {
      return result.name();
    } else {
      return result.name() + "[" + cause.getSummary().getErrorId().getName() + "]";
    }
  }

  private static String formatResultWithDetailedCause(
      TestResult result, @Nullable ExceptionProto.ExceptionDetail cause) {
    if (cause == null) {
      return result.name();
    } else {
      return result.name()
          + "[cause="
          + Throwables.getStackTraceAsString(
              com.google.devtools.common.metrics.stability.converter.ErrorModelConverter
                  .toDeserializedException(cause))
          + "]";
    }
  }
}
