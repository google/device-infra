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

package com.google.wireless.qa.mobileharness.shared.model.job;

import java.util.List;

/**
 * Utility class to help add a list of tests to a given {@link TestInfos} of a {@link JobInfo} or
 * {@link TestInfo}.
 */
public final class JobHelper {

  private JobHelper() {}

  /** Adds a list of tests to the given {@link TestInfos}. */
  public static TestInfos addTests(TestInfos testInfos, List<TestInfo> testInfoList) {
    return testInfos.addAll(testInfoList);
  }
}
