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

package com.google.devtools.mobileharness.shared.util.testresult;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;

/** Data class representing a test case (test method) execution result. */
@AutoValue
public abstract class TestCase {
  public abstract TestCaseReference testCaseReference();

  public abstract TestStatus status();

  @Nullable
  public abstract Instant startTime();

  @Nullable
  public abstract Instant endTime();

  public abstract Duration elapsedTime();

  public abstract ImmutableList<StackTrace> stackTraces();

  public static TestCase create(
      TestCaseReference testCaseReference,
      TestStatus status,
      @Nullable Instant startTime,
      @Nullable Instant endTime,
      Duration elapsedTime,
      ImmutableList<StackTrace> stackTraces) {
    return builder()
        .setTestCaseReference(testCaseReference)
        .setStatus(status)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setElapsedTime(elapsedTime)
        .setStackTraces(stackTraces)
        .build();
  }

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_TestCase.Builder()
        .setStackTraces(ImmutableList.of())
        .setElapsedTime(Duration.ZERO);
  }

  /** Builder for {@link TestCase}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTestCaseReference(TestCaseReference testCaseReference);

    public abstract Builder setStatus(TestStatus status);

    public abstract Builder setStartTime(@Nullable Instant startTime);

    public abstract Builder setEndTime(@Nullable Instant endTime);

    public abstract Builder setElapsedTime(Duration elapsedTime);

    public abstract Builder setStackTraces(ImmutableList<StackTrace> stackTraces);

    abstract ImmutableList.Builder<StackTrace> stackTracesBuilder();

    @CanIgnoreReturnValue
    public Builder addStackTraces(StackTrace stackTrace) {
      stackTracesBuilder().add(stackTrace);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAllStackTraces(Iterable<StackTrace> stackTraces) {
      stackTracesBuilder().addAll(stackTraces);
      return this;
    }

    public abstract TestCase build();
  }
}
