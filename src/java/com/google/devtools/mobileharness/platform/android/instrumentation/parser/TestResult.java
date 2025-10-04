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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus;
import java.time.Instant;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * The end result of an individual test case.
 *
 * <p>A test result can be in one of the following final states:
 *
 * <ul>
 *   <li>`status = PASSED` - The test completed successfully.
 *   <li>`status = FAILED` - The test completed with a failure. `stackTrace` contains the stack
 *       trace where the failure occurred. E.g. this covers assertion failures or tests throwing
 *       exceptions.
 *   <li>`status = IGNORED` - Two possible sub states:
 *       <ul>
 *         <li>`stackTrace` is set - The test completed with an assumption failure. E.g. JUnit
 *             `assume*()` methods.
 *         <li>`stackTrace` is not set - The test was ignored. E.g. JUnit `@Ignore` was present on
 *             the test method.
 *       </ul>
 *   <li>`status = ERROR` - A fatal error occurred. E.g. the test case did not complete because the
 *       instrumentation crashed.
 * </ul>
 */
@AutoValue
public abstract class TestResult {

  /** Test case this end result is for. */
  public abstract TestIdentifier testIdentifier();

  /** Final status of the test. One of `PASSED`, `FAILED`, `IGNORED`, or `ERROR`. */
  public abstract TestStatus status();

  /** Start time of the test execution. Recorded by the parser. */
  public abstract Instant startTime();

  /** End time of the test execution. Recorded by the parser. */
  public abstract Instant endTime();

  /** In case of failures (assertions, exceptions, assumptions) the stack trace of the failure. */
  public abstract @Nullable String stackTrace();

  /** Additional status information written by the instrumentation. */
  public abstract ImmutableMap<String, String> statusBundle();

  public static Builder builder() {
    return new AutoValue_TestResult.Builder().setStatusBundle(ImmutableMap.of());
  }

  /** Builder for {@link TestResult}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTestIdentifier(TestIdentifier testIdentifier);

    public abstract Builder setStatus(TestStatus status);

    public abstract Builder setStartTime(Instant startTime);

    public abstract Builder setEndTime(Instant endTime);

    public abstract Builder setStackTrace(String stackTrace);

    public abstract Builder setStatusBundle(ImmutableMap<String, String> statusBundle);

    public abstract TestResult build();
  }
}
