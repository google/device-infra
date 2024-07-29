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

package com.google.devtools.mobileharness.platform.android.packagemanager;

/** Filter of status for the list package command. */
public enum StatusFilter {
  NONE(""), // No filter.
  DISABLED("-d"), // Filter to only show disabled packages.
  ENABLED("-e"); // Filter to only show enabled packages.

  private final String option;

  private StatusFilter(String option) {
    this.option = option;
  }

  /** Returns the filter option for the adb shell command. */
  public String getOption() {
    return option;
  }
}
