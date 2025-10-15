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

import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.ERROR;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.FAILURE;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.IGNORED;
import static com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory.OTHER;

import com.google.devtools.mobileharness.platform.android.logcat.LogcatEvent.ProcessCategory;
import java.util.List;

/** Record class to hold the packages/processes to monitor appropriately. */
public record MonitoringConfig(
    List<String> reportAsFailurePackages,
    List<String> errorOnCrashPackages,
    List<String> packagesToIgnore) {

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
}
