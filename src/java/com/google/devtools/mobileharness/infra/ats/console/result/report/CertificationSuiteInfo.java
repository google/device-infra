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

package com.google.devtools.mobileharness.infra.ats.console.result.report;

import com.google.auto.value.AutoValue;

/** Data class for certification suite info. */
@AutoValue
public abstract class CertificationSuiteInfo {

  /** Name for the certification suite. */
  public abstract String suiteName();

  /** Variant for the certification suite. By default it's same as the suite name. */
  public abstract String suiteVariant();

  /** Version for the certification suite. Like "13.0_r3", "12.1_r5", "12.0_r7", etc. */
  public abstract String suiteVersion();

  /** Plan for the certification suite. */
  public abstract String suitePlan();

  /** Build number for the certification suite. */
  public abstract String suiteBuild();

  /** Suite report version. */
  public abstract String suiteReportVersion();

  /** Gets a builder for {@link CertificationSuiteInfo}. */
  public static Builder builder() {
    return new com.google.devtools.mobileharness.infra.ats.console.result.report
        .AutoValue_CertificationSuiteInfo.Builder();
  }

  /** Builder for {@link CertificationSuiteInfo}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSuiteName(String suiteName);

    public abstract Builder setSuiteVariant(String suiteVariant);

    public abstract Builder setSuiteVersion(String suiteVersion);

    public abstract Builder setSuitePlan(String suitePlan);

    public abstract Builder setSuiteBuild(String suiteBuild);

    public abstract Builder setSuiteReportVersion(String suiteReportVersion);

    public abstract CertificationSuiteInfo build();
  }
}
