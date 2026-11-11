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

package com.google.devtools.mobileharness.platform.android.logcat;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MonitoringConfigTest {

  private MonitoringConfig monitoringConfig;

  @Before
  public void setUp() {
    monitoringConfig =
        new MonitoringConfig(
            /* reportAsFailurePackages= */ ImmutableList.of("com.app.target"),
            /* errorOnCrashPackages= */ ImmutableList.of("com.app.system"),
            /* packagesToIgnore= */ ImmutableList.of("com.app.ignored"));
  }

  @Test
  public void categorizeProcess_returnsExpectedCategories() {
    assertThat(monitoringConfig.categorizeProcess("com.app.target"))
        .isEqualTo(ProcessCategory.FAILURE);
    assertThat(monitoringConfig.categorizeProcess("com.app.system"))
        .isEqualTo(ProcessCategory.ERROR);
    assertThat(monitoringConfig.categorizeProcess("com.app.ignored"))
        .isEqualTo(ProcessCategory.IGNORED);
    assertThat(monitoringConfig.categorizeProcess("com.app.other"))
        .isEqualTo(ProcessCategory.OTHER);
  }

  @Test
  public void dialogPackagesToIgnore_deduplicatesUnionAndExcludesErrorPackages() {
    MonitoringConfig config =
        new MonitoringConfig(
            /* reportAsFailurePackages= */ ImmutableList.of(
                "com.app.target",
                "com.app.target",
                "com.app.shared_failure_ignore",
                "com.app.failure_and_error"),
            /* errorOnCrashPackages= */ ImmutableList.of(
                "com.app.error_only", "com.app.error_and_ignored", "com.app.failure_and_error"),
            /* packagesToIgnore= */ ImmutableList.of(
                "com.app.ignored",
                "com.app.ignored",
                "com.app.shared_failure_ignore",
                "com.app.error_and_ignored"));

    ImmutableList<String> dialogPackagesToIgnore = config.dialogPackagesToIgnore();

    // `com.app.error_and_ignored` categorizes as ERROR and must NOT have its crash dialog ignored,
    // whereas `com.app.failure_and_error` categorizes as FAILURE (app under test) and is ignored.
    assertThat(dialogPackagesToIgnore)
        .containsExactly(
            "com.app.ignored",
            "com.app.shared_failure_ignore",
            "com.app.target",
            "com.app.failure_and_error")
        .inOrder();
  }
}
