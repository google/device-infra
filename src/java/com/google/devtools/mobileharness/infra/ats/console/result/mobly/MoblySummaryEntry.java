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
import java.util.Optional;

/**
 * A summary of the test run stats, e.g., how many test requested, executed, passed, failed, etc.
 *
 * <p>This class should mirror the Mobly class at {@code records.py#TestResult}, the part regarding
 * the test result summary.
 */
@AutoValue
public abstract class MoblySummaryEntry implements MoblyYamlDocEntry {

  @Override
  public Type getType() {
    return Type.SUMMARY;
  }

  public abstract int requested();

  public abstract int executed();

  public abstract int skipped();

  public abstract int passed();

  public abstract int failed();

  public abstract int error();

  public static Builder builder() {
    return new com.google.devtools.mobileharness.infra.ats.console.result.mobly
        .AutoValue_MoblySummaryEntry.Builder();
  }

  /** MoblySummaryEntry Builder class. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setRequested(int requested);

    public abstract Builder setExecuted(int executed);

    public abstract Builder setSkipped(int skipped);

    public abstract Builder setPassed(int passed);

    public abstract Builder setFailed(int failed);

    public abstract Builder setError(int error);

    abstract Optional<Integer> requested();

    abstract Optional<Integer> executed();

    abstract Optional<Integer> skipped();

    abstract Optional<Integer> passed();

    abstract Optional<Integer> failed();

    abstract Optional<Integer> error();

    abstract MoblySummaryEntry autoBuild();

    public final MoblySummaryEntry build() {
      if (requested().isEmpty()) {
        setRequested(0);
      }
      if (executed().isEmpty()) {
        setExecuted(0);
      }
      if (skipped().isEmpty()) {
        setSkipped(0);
      }
      if (passed().isEmpty()) {
        setPassed(0);
      }
      if (failed().isEmpty()) {
        setFailed(0);
      }
      if (error().isEmpty()) {
        setError(0);
      }
      return autoBuild();
    }
  }
}
