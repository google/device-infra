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

package com.google.devtools.mobileharness.infra.ats.console.result.mobly;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;

/**
 * A container class for a Mobly {@code TestClassInfo} YAML summary document.
 *
 * <p>This class mirrors {@code records.py#TestClassInfoRecord} in Mobly.
 */
@AutoValue
public abstract class MoblyTestClassInfoEntry implements MoblyYamlDocEntry {

  private static final ImmutableSet<String> CLASS_ABORT_SIGNAL_TYPES =
      ImmutableSet.of("TestAbortClass", "TestAbortAll");

  @Override
  public Type getType() {
    return Type.TEST_CLASS_INFO;
  }

  public abstract String getTestClass();

  /** Start time of the test class in milliseconds since epoch. */
  @SuppressWarnings({"GoodTime", "ApiWithNumericTimeUnit"})
  public abstract Optional<Long> getBeginTime();

  /** End time of the test class in milliseconds since epoch. */
  @SuppressWarnings({"GoodTime", "ApiWithNumericTimeUnit"})
  public abstract Optional<Long> getEndTime();

  /** Termination signal type if the test class was aborted or terminated by an unhandled error. */
  public abstract Optional<String> getTerminationSignalType();

  /** Details of the class termination signal. */
  public abstract Optional<String> getDetails();

  /** Stacktrace of the class termination signal. */
  public abstract Optional<String> getStacktrace();

  /** Additional info piped into an assertion or termination signal. */
  public abstract Optional<String> getExtras();

  /** Returns true if the test class was aborted via {@code abort_class} or {@code abort_all}. */
  public boolean isClassSkipped() {
    return getTerminationSignalType().isPresent()
        && CLASS_ABORT_SIGNAL_TYPES.contains(getTerminationSignalType().get());
  }

  /**
   * Returns true if this {@code TestClassInfo} entry indicates that the test class terminated due
   * to an unhandled error (rather than normal completion or {@code abort_class}/{@code abort_all}).
   */
  public boolean hasClassError() {
    return getTerminationSignalType().isPresent()
        && !CLASS_ABORT_SIGNAL_TYPES.contains(getTerminationSignalType().get());
  }

  public static Builder builder() {
    return new AutoValue_MoblyTestClassInfoEntry.Builder();
  }

  /** Builder for {@link MoblyTestClassInfoEntry}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTestClass(String value);

    public abstract Builder setBeginTime(long value);

    public abstract Builder setEndTime(long value);

    public abstract Builder setTerminationSignalType(String value);

    public abstract Builder setDetails(String value);

    public abstract Builder setStacktrace(String value);

    public abstract Builder setExtras(String value);

    public abstract MoblyTestClassInfoEntry build();
  }
}
