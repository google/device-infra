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

package com.google.devtools.mobileharness.platform.android.xts.constant;

import java.util.regex.Pattern;

/** Constants for xTS tests. */
public class XtsConstants {

  public static final String TRADEFED_TESTS_PASSED = "tradefed_tests_passed";
  public static final String TRADEFED_TESTS_FAILED = "tradefed_tests_failed";
  public static final String TRADEFED_TESTS_DONE = "tradefed_tests_done";
  public static final String TRADEFED_TESTS_TOTAL = "tradefed_tests_total";
  public static final String TRADEFED_JOBS_PASSED = "tradefed_jobs_passed";
  public static final String TRADEFED_JOBS_HAS_RESULT_FILE = "tradefed_jobs_has_result_file";

  public static final String TRADEFED_OUTPUT_FILE_NAME = "xts_tf_output.log";

  /** A MH test property key of TF invocation dir name. */
  public static final String TRADEFED_INVOCATION_DIR_NAME = "tf_inv_dir_name";

  public static final String TRADEFED_INVOCATION_DIR_NAME_PREFIX = "inv_";

  /**
   * A MH test property key of TF runtime info file path.
   *
   * <p>Use {@linkplain
   * com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil
   * XtsTradefedRuntimeInfoFileUtil} to read its content.
   */
  public static final String TRADEFED_RUNTIME_INFO_FILE_PATH = "tf_runtime_info_file_path";

  public static final String TRADEFED_RUNTIME_INFO_FILE_NAME = "tf_runtime_info";

  public static final String INVOCATION_SUMMARY_FILE_NAME = "invocation_summary.txt";

  /** A MH job property key to indicate whether Mobly resultstore upload is enabled. */
  public static final String IS_MOBLY_RESULTSTORE_UPLOAD_ENABLED =
      "is_mobly_resultstore_upload_enabled";

  /** A MH job property key to indicate whether xTS dynamic download is enabled. */
  public static final String IS_XTS_DYNAMIC_DOWNLOAD_ENABLED = "is_xts_dynamic_download_enabled";

  /** The job type of dynamic download xTS jobs. */
  public static final String XTS_DYNAMIC_DOWNLOAD_JOB_NAME = "xts_dynamic_download_job_name";

  /** The job name of dynamic download xTS job. */
  public static final String DYNAMIC_MCTS_JOB_NAME = "MCTS";

  /** The job name of static xTS job. */
  public static final String STATIC_XTS_JOB_NAME = "STATIC_XTS";

  /**
   * MH test property key for the a tradefed test's filtered expanded module names (e.g. arm64-v8a
   * CtsBatteryHealthTestCases).
   */
  public static final String TRADEFED_FILTERED_EXPANDED_MODULES_FOR_TEST_PROPERTY_KEY =
      "tradefed_filtered_expanded_modules_for_test";

  /** MH test property keys of path relative to the xts dynamic download dir. */
  public static final String XTS_DYNAMIC_DOWNLOAD_PATH_TEST_PROPERTY_KEY =
      "xts_dynamic_download_path";

  /** MH test property keys of the path of the test list file. */
  public static final String XTS_DYNAMIC_DOWNLOAD_PATH_TEST_LIST_PROPERTY_KEY =
      "xts_dynamic_download_test_list_path";

  /** MH test property keys of the path of the downloaded JDK. */
  public static final String XTS_DYNAMIC_DOWNLOAD_PATH_JDK_PROPERTY_KEY =
      "xts_dynamic_download_jdk_path";

  /** MH test property keys of the preloaded mainline version. */
  public static final String PRELOAD_MAINLINE_VERSION_TEST_PROPERTY_KEY =
      "preload_mainline_version";

  public static final Pattern RESULT_ZIP_FILENAME_PATTERN =
      Pattern.compile("^\\d{4}\\.\\d{2}\\.\\d{2}_\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{3}_\\d{4}\\.zip$");

  /** A pattern that matches a test class entry in Mobly test listing. */
  public static final Pattern MOBLY_TEST_CLASS_PATTERN =
      Pattern.compile("==========> (\\w+) <==========");

  public static final String XTS_FINAL_TEST_LOG_DIR_PROPERTY_KEY = "xts_final_test_log_dir";

  /** A MH test property key for the AOSP version of the device allocated. */
  public static final String DEVICE_AOSP_VERSION_PROPERTY_KEY = "device_aosp_version";

  /** A MH test property key for the TVP (Train Version Package) version of the device allocated. */
  public static final String DEVICE_TVP_VERSION_PROPERTY_KEY = "device_tvp_version";

  /** A MH test property key for the ABI of the device allocated. */
  public static final String DEVICE_ABI_PROPERTY_KEY = "device_abi";

  /** A MH test property key for the MCTS modules info of the device allocated. */
  public static final String DEVICE_MCTS_MODULES_INFO_PROPERTY_KEY = "device_mcts_modules_info";

  private XtsConstants() {}
}
