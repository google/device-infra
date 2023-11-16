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

package com.google.devtools.mobileharness.infra.ats.console.result.xml;

/** Constants used in test_result.xml. */
public final class XmlConstants {

  // XML constants
  // <Result> element
  public static final String RESULT_TAG = "Result";
  public static final String START_DISPLAY_TIME_ATTR = "start_display";
  public static final String END_DISPLAY_TIME_ATTR = "end_display";
  public static final String DEVICES_ATTR = "devices";
  public static final String OS_NAME_ATTR = "os_name";
  public static final String OS_VERSION_ATTR = "os_version";
  public static final String OS_ARCH_ATTR = "os_arch";
  public static final String JAVA_VENDOR_ATTR = "java_vendor";
  public static final String JAVA_VERSION_ATTR = "java_version";
  public static final String SUITE_NAME_ATTR = "suite_name";
  public static final String SUITE_VARIANT_ATTR = "suite_variant";
  public static final String SUITE_VERSION_ATTR = "suite_version";
  public static final String SUITE_PLAN_ATTR = "suite_plan";
  public static final String SUITE_BUILD_ATTR = "suite_build_number";
  public static final String SUITE_REPORT_VERSION_ATTR = "report_version";
  public static final String SUITE_CTS_VERIFIER_MODE_ATTR = "suite_cts_verifier_mode";
  public static final String SUITE_NAME_CTS_VERIFIER_VALUE = "CTS_VERIFIER";
  public static final String SUITE_CTS_VERIFIER_AUTOMATED_MODE_VALUE = "automated";
  public static final String SUITE_CTS_VERIFIER_MERGED_MODE_VALUE = "merged";
  public static final String SUITE_PLAN_CTS_VERIFIER_VALUE = "verifier";

  // <Build> element
  public static final String BUILD_TAG = "Build";
  public static final String BUILD_FINGERPRINT_ATTR = "build_fingerprint";
  public static final String DEVICE_KERNEL_INFO_ATTR = "device_kernel_info";
  public static final String SYSTEM_IMG_INFO_ATTR = "system_img_info";
  public static final String VENDOR_IMG_INFO_ATTR = "vendor_img_info";

  // <RunHistory> element
  public static final String RUN_HISTORY_TAG = "RunHistory";

  // <Run> element
  public static final String RUN_TAG = "Run";

  // <Summary> element
  public static final String SUMMARY_TAG = "Summary";
  public static final String MODULES_DONE_ATTR = "modules_done";
  public static final String MODULES_TOTAL_ATTR = "modules_total";

  // <Module> element
  public static final String MODULE_TAG = "Module";
  public static final String ABI_ATTR = "abi";
  public static final String RUNTIME_ATTR = "runtime";
  public static final String DONE_ATTR = "done";
  public static final String TOTAL_TESTS_ATTR = "total_tests";

  // <Reason> element
  public static final String MODULES_NOT_DONE_REASON = "Reason";

  // <TestCase> element
  public static final String CASE_TAG = "TestCase";

  // <Test> element
  public static final String TEST_TAG = "Test";
  public static final String RESULT_ATTR = "result";
  public static final String SKIPPED_ATTR = "skipped";

  // <Failure> element
  public static final String FAILURE_TAG = "Failure";

  // <StackTrace> element
  public static final String STACKTRACE_TAG = "StackTrace";

  // <BugReport> element
  public static final String BUGREPORT_TAG = "BugReport";

  // <Logcat> element
  public static final String LOGCAT_TAG = "Logcat";

  // <Screenshot> element
  public static final String SCREENSHOT_TAG = "Screenshot";

  // <Metric> element
  public static final String METRIC_TAG = "Metric";
  public static final String METRIC_KEY = "key";

  // Shared across elements
  public static final String COMMAND_LINE_ARGS = "command_line_args";
  public static final String END_TIME_ATTR = "end";
  public static final String ERROR_CODE_ATTR = "error_code";
  public static final String ERROR_NAME_ATTR = "error_name";
  public static final String FAILED_ATTR = "failed";
  public static final String HOST_NAME_ATTR = "host_name";
  public static final String LOG_FILE_NAME_ATTR = "file_name";
  public static final String MESSAGE_ATTR = "message";
  public static final String NAME_ATTR = "name";
  public static final String PASS_ATTR = "pass";
  public static final String START_TIME_ATTR = "start";

  private XmlConstants() {}
}
