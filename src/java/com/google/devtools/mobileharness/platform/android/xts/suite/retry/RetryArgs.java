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

package com.google.devtools.mobileharness.platform.android.xts.suite.retry;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteTestFilter;
import java.nio.file.Path;
import java.util.Optional;

/** Args for the retry. */
@AutoValue
public abstract class RetryArgs {

  /** The path to the "results" directory. */
  public abstract Path resultsDir();

  /** ID to the previous session. */
  public abstract int previousSessionId();

  /**
   * Used to retry tests of a certain status. Possible values include {@link RetryType.FAILED},
   * {@link RetryType.NOT_EXECUTED}.
   */
  public abstract Optional<RetryType> retryType();

  /** Include filters passed in by the command. */
  public abstract ImmutableSet<SuiteTestFilter> passedInIncludeFilters();

  /** Exclude filters passed in by the command. */
  public abstract ImmutableSet<SuiteTestFilter> passedInExcludeFilters();

  public static Builder builder() {
    return new AutoValue_RetryArgs.Builder()
        .setPassedInIncludeFilters(ImmutableSet.of())
        .setPassedInExcludeFilters(ImmutableSet.of());
  }

  /** Builder for {@link RetryArgs}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setResultsDir(Path resultsDir);

    public abstract Builder setPreviousSessionId(int previousSessionId);

    public abstract Builder setRetryType(RetryType retryType);

    public abstract Builder setPassedInIncludeFilters(
        ImmutableSet<SuiteTestFilter> passedInIncludeFilters);

    public abstract Builder setPassedInExcludeFilters(
        ImmutableSet<SuiteTestFilter> passedInExcludeFilters);

    public abstract RetryArgs build();
  }
}
