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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.ERROR;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.FAILURE;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.IGNORED;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.OTHER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import java.util.Collection;
import java.util.stream.Stream;

/** Record class to hold the packages/processes to monitor appropriately. */
public record MonitoringConfig(
    ImmutableSet<String> reportAsFailurePackages,
    ImmutableSet<String> errorOnCrashPackages,
    ImmutableSet<String> packagesToIgnore) {

  public MonitoringConfig {
    reportAsFailurePackages = ImmutableSet.copyOf(reportAsFailurePackages);
    errorOnCrashPackages = ImmutableSet.copyOf(errorOnCrashPackages);
    packagesToIgnore = ImmutableSet.copyOf(packagesToIgnore);
  }

  public MonitoringConfig(
      Collection<String> reportAsFailurePackages,
      Collection<String> errorOnCrashPackages,
      Collection<String> packagesToIgnore) {
    this(
        ImmutableSet.copyOf(reportAsFailurePackages),
        ImmutableSet.copyOf(errorOnCrashPackages),
        ImmutableSet.copyOf(packagesToIgnore));
  }

  /** Return {@link ProcessCategory} for the supplied {@code processName} */
  public ProcessCategory categorizeProcess(String processName) {
    if (reportAsFailurePackages.contains(processName)) {
      return FAILURE;
    } else if (errorOnCrashPackages.contains(processName)) {
      return ERROR;
    } else if (packagesToIgnore.contains(processName)) {
      return IGNORED;
    } else {
      return OTHER;
    }
  }

  /**
   * Returns packages whose crash/ANR dialogs should be ignored by {@link CrashDialogDetector}:
   * explicitly ignored packages and packages under test, excluding any package categorized as
   * {@link ProcessCategory#ERROR}.
   */
  public ImmutableList<String> dialogPackagesToIgnore() {
    return Stream.concat(packagesToIgnore.stream(), reportAsFailurePackages.stream())
        .distinct()
        .filter(pkg -> categorizeProcess(pkg) != ERROR)
        .collect(toImmutableList());
  }
}
