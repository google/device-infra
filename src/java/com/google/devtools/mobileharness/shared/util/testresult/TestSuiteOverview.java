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
import java.time.Duration;

/** Data class representing basic test statistics for a test suite. */
@AutoValue
public abstract class TestSuiteOverview {
  public abstract String name();

  public abstract Duration elapsedTime();

  public abstract int totalCount();

  public abstract int errorCount();

  public abstract int failureCount();

  public abstract int skippedCount();

  public abstract int flakyCount();

  public static TestSuiteOverview create(
      String name,
      Duration elapsedTime,
      int totalCount,
      int errorCount,
      int failureCount,
      int skippedCount,
      int flakyCount) {
    return builder()
        .setName(name)
        .setElapsedTime(elapsedTime)
        .setTotalCount(totalCount)
        .setErrorCount(errorCount)
        .setFailureCount(failureCount)
        .setSkippedCount(skippedCount)
        .setFlakyCount(flakyCount)
        .build();
  }

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_TestSuiteOverview.Builder()
        .setTotalCount(0)
        .setErrorCount(0)
        .setFailureCount(0)
        .setSkippedCount(0)
        .setFlakyCount(0)
        .setElapsedTime(Duration.ZERO);
  }

  /** Builder for {@link TestSuiteOverview}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setElapsedTime(Duration elapsedTime);

    public abstract Builder setTotalCount(int totalCount);

    public abstract Builder setErrorCount(int errorCount);

    public abstract Builder setFailureCount(int failureCount);

    public abstract Builder setSkippedCount(int skippedCount);

    public abstract Builder setFlakyCount(int flakyCount);

    public abstract TestSuiteOverview build();
  }
}
