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

package com.google.devtools.mobileharness.platform.android.xts.common.util;

/** Constants for xTS tests. */
public class XtsConstants {

  public static final String TRADEFED_TESTS_PASSED = "tradefed_tests_passed";
  public static final String TRADEFED_TESTS_FAILED = "tradefed_tests_failed";
  public static final String TRADEFED_TESTS_DONE = "tradefed_tests_done";
  public static final String TRADEFED_TESTS_TOTAL = "tradefed_tests_total";
  public static final String TRADEFED_JOBS_PASSED = "tradefed_jobs_passed";

  public static final String TRADEFED_OUTPUT_FILE_NAME = "xts_tf_output.log";
  public static final String TRADEFED_INVOCATION_DIR_NAME = "tf_inv_dir_name";
  public static final String TRADEFED_INVOCATION_DIR_NAME_PREFIX = "inv_";

  /** A path relative to the test temp dir. */
  public static final String XTS_DYNAMIC_DOWNLOAD_PATH_TEST_PROPERTY_KEY =
      "xts_dynamic_download_path";

  private XtsConstants() {}
}
