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

package com.google.devtools.mobileharness.shared.util.dir;

import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.util.ArrayUtil;

/** Utility for calculating test dir paths. */
public class TestDirUtil {

  /**
   * Returns a test dir path.
   *
   * <p>For example, for a sub test "test1->test2->test3" (test1 is the top level test, test2 is
   * test1's child, test3 is test2's child) and job dir path "fake_job_gen_dir", this method will
   * return "fake_job_gen_dir/test_test1/test2/test3".
   *
   * @param jobDirPath the job dir path
   * @param topLevelTestId the top level test ID
   * @param subTestIds the sub test ID sequence
   */
  public static String getTestDirPath(
      String jobDirPath, String topLevelTestId, String... subTestIds) {
    return PathUtil.join(ArrayUtil.join(jobDirPath, "test_" + topLevelTestId, subTestIds));
  }

  private TestDirUtil() {}
}
