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

package com.google.wireless.qa.mobileharness.shared.api.spec;

import com.google.common.collect.ImmutableSet;
import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/**
 * The parameters/files of Android Instrumentation driver. AndroidInstrumentation driver need to
 * inherit this interface in order to make the following parameters/file associate to the drivers in
 * the wizard tool of MH FE.
 */
public interface AndroidInstrumentationDriverSpec {
  @FileAnnotation(required = true, help = "The test apk, which contains your test code.")
  String TAG_TEST_APK = "test_apk";

  @FileAnnotation(required = false, help = "The build apk, which contains your app under test.")
  String TAG_BUILD_APK = "build_apk";

  @FileAnnotation(
      required = false,
      help =
          "The basic_services.apk. If set, install this one instead of the one from the resource "
              + " of lab server.")
  String TAG_BASIC_SERVICES_APK = "basic_services_apk";

  @FileAnnotation(
      required = false,
      help =
          "The test_services.apk. If set, install this one instead of the one from the resource "
              + " of lab server.")
  String TAG_TEST_SERVICES_APK = "test_services_apk";

  @FileAnnotation(
      required = false,
      help =
          "The test data. It should be rooted at Google3. You can access it by "
              + "AndroidTestUtil.getTestDataInputStream(). See https://sites.google.com/a/"
              + "google.com/mobile-ninjas/mobile-ninjas-for-android/android-apps-getting-started/"
              + "3-testing-an-app/7-accessing-test-data")
  String TAG_TEST_DATA = "test_data";

  @ParamAnnotation(
      required = false,
      help =
          "The class name of the instrumented test runner you are using. It is the value of the "
              + "android:name attribute of the instrumentation element in the test APK's manifest "
              + "file. If not provided, will use the first instrumetation test runner of the test "
              + "apk.")
  String PARAM_RUNNER = "runner";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to async run instrument. By default, this is disabled. Used for battery tests "
              + "when device can be disconnected programmatically. "
              + "For pre-L devices(api level < 21), only the process on the host machine of the "
              + "adb shell am instrument command run in background. "
              + "For L/L+ devices(api level >= 21), it also adds the nohup option to the adb shell "
              + "command so the process on device is also running in background, which is required "
              + "by uiautomator tests but optional for other instrumentation tests.")
  String PARAM_ASYNC = "async";

  @ParamAnnotation(
      required = false,
      help =
          "Extra options. Should be key value pairs like: 'key1=value1,key2=value2'. Use \\ to "
              + "escapes comma in key and value.")
  String PARAM_OPTIONS = "options";

  @ParamAnnotation(
      required = false,
      help =
          "Whether print the raw test results and parse them to method level. "
              + "By default, this is false.")
  String PARAM_SPLIT_METHODS = "split_methods";

  @ParamAnnotation(
      required = false,
      help =
          "Max execution time of the 'adb shell am instrument ...' command only. "
              + "No effect if large than test timeout setting.")
  String PARAM_INSTRUMENT_TIMEOUT_SEC = "instrument_timeout_sec";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to use android instrumentation parsing or gtest XML parsing. If omitted, this"
              + "defaults to true to use instrumentation parsing.")
  String PARAM_USE_ANDROIDINSTRUMENTATION_PARSER = "use_androidinstrumentation_parser";

  @ParamAnnotation(
      required = false,
      help =
          "The path to the gtest XML file on the device. This should be set if"
              + " use_androidinstrumentation_parser is set to false. e.g."
              + " /sdcard/Download/test_results.xml")
  String PARAM_GTEST_XML_FILE_ON_DEVICE = "gtest_xml_file_on_device";

  /**
   * adb shell command before each repeat, within each option map. The parameter name should be
   * followed by the option map index.
   */
  String PARAM_ITER_ADB_SHELL_BEFORE_INSTRUMENTATION = "iter_adb_shell_before_instrumentation";

  /**
   * adb shell command after each repeat, within each option map. The parameter name should be
   * followed by the option map index.
   */
  String PARAM_ITER_ADB_SHELL_AFTER_INSTRUMENTATION = "iter_adb_shell_after_instrumentation";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to skip the test APK method infos extraction from the test APK. Reading method"
              + " infos from test APK is both CPU and memory consuming, and having many concurrent"
              + " test runs on a single host can exhaust the host resources and slow down or fail"
              + " tests. Method infos are needed to ensure that only #testMethod[0] will run for"
              + " EACH iteration when using AndroidRepeatedTestRunner, so this parameter should"
              + " never be set to true when using AndroidRepeatedTestRunner, and otherwise can"
              + " safely be set to true to reduce CPU and memory usage.")
  String PARAM_SKIP_TEST_APK_METHOD_INFOS = "skip_test_apk_method_infos";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to cleanup the cache of the apps under test between the iterations of the "
              + "instrumentation, if an option map list or the repeat number is provided as a "
              + "batch run. By default, this is false.")
  String PARAM_ITER_CLEAR_BUILD = "iter_clear_build";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to cleanup the cache of the test package between the iterations of the "
              + "instrumentation, if an option map list or the repeat number is provided as a "
              + "batch run. By default, this is false.")
  String PARAM_ITER_CLEAR_TEST = "iter_clear_test";

  @ParamAnnotation(
      required = false,
      help =
          "Customized test args which can be retrieved by AndroidTestUtil.getTestArgs() in the "
              + "instrumentation code. Should be key value pairs like: 'key1=value1,key2=value2'. "
              + "These test args can also be passed to android_test target with --test_args.")
  String PARAM_TEST_ARGS = "test_args";

  @ParamAnnotation(
      required = false,
      help =
          "Whether force to install basic services and test services apk. By default, this is "
              + "true.")
  String PARAM_FORCE_REINSTALL_BASIC_SERVICE_APK = "force_reinstall_basic_service_apk";

  @ParamAnnotation(
      required = false,
      help =
          "Enable this flag if test APK will call executeShellCommand in AndroidTestUtil."
              + "By default, this is false.")
  String PARAM_PREFIX_ANDROID_TEST = "prefix_android_test";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to broadcast message when starting and finishing installing the app. "
              + "Default value is false. Specify this as true when you need to register message "
              + "listener.")
  String PARAM_BROADCAST_INSTALL_MESSAGE = "broadcast_install_message";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to disable isolated-storage feature for package in Android Q. "
              + "Default value is true. Specify this as false when you would like to verify sandbox"
              + "feature in test apk.")
  String PARAM_DISABLE_ISOLATED_STORAGE_FOR_APK = "disable_isolated_storage_for_apk";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to disable isolated-storage feature for instrumentation test in Android Q. "
              + "Default value is false. Specify this as true when you need extra support other "
              + "than "
              + PARAM_DISABLE_ISOLATED_STORAGE_FOR_APK
              + ".")
  String PARAM_DISABLE_ISOLATED_STORAGE_FOR_INSTRUMENTATION =
      "disable_isolated_storage_for_instrumentation";

  /** Default delay after installing APKs for performance tests in M&M. */
  long DEFAULT_POST_INSTALL_DELAY_SEC = 30;

  @ParamAnnotation(
      required = false,
      help =
          "Delay in seconds after installing APKs before the test starts. This delay is only "
              + "applied for M&M performance tests"
              + ".If not set, test will start "
              + DEFAULT_POST_INSTALL_DELAY_SEC
              + " seconds after installation.")
  String PARAM_POST_INSTALL_DELAY_SEC = "post_install_delay_sec";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to filter repeated tests generated by the test runner (e.g.,"
              + " AndroidRepeatedTestRunner) and only run one of generated repeated tests. Note:"
              + " ONLY set this param when the test class RunWith a test runner supporting"
              + " repeated tests. By default, this is false.")
  String PARAM_FILTER_INSTRUMENTATION_REPEATED_TESTS = "filter_instrumentation_repeated_tests";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to enable coverage generation for the instrumentation command. By default, this"
              + " is false.")
  String PARAM_ENABLE_COVERAGE = "enable_coverage";

  @ParamAnnotation(
      required = false,
      help = "Whether to use orchestrator to run the instrumentation. By default, this is false.")
  String PARAM_USE_ORCHESTRATOR = "use_orchestrator";

  @ParamAnnotation(
      required = false,
      help =
          "Set test result to FAIL if no tests ran. For example, user specified test methods do not"
              + " exist, or using Parameterized runner with ShardLevel.METHOD. By default, this is"
              + " false.")
  String PARAM_FAIL_IF_NO_TESTS_RAN = "fail_if_no_tests_ran";

  @ParamAnnotation(
      required = false,
      help = "Whether to skip clear media provider for multi user case. By default, this is false.")
  String PARAM_SKIP_CLEAR_MEDIA_PROVIDER_FOR_MULTI_USER_CASE =
      "skip_clear_media_provider_for_multi_user_case";

  /** Resource path of the Android basic_services.apk which is needed for reading test_args. */
  String BASIC_SERVICE_APK_PATH =
      "/com/google/android/apps/common/testing/services/basic_services.apk";

  /**
   * Resource path of the Android test_services.apk which is needed for executing shell commands.
   */
  String TEST_SERVICES_APK_PATH = "/third_party/android/androidx_test/services/test_services.apk";

  /** Value of an entry in the sequence option maps, to express comma character. */
  String COMMA_ESCAPED = "\\,";

  /** Value of an entry in the sequence option maps, to transit comma character. */
  String COMMA_ENCODED = "#$COMMA$#";

  /** The delimiter to split the test method names. */
  String METHODS_DELIMITER = "#,#";

  /** Value of an entry in the sequence option maps, to skip the run of the option entry. */
  String SKIPPED_OPTION_STR = "SKIP";

  /** Key name in option to skip some test methods in the test runner. */
  String NOT_ANNOTATION = "notAnnotation";

  /** Test generated property to show the package name of the test apk. */
  String PROPERTY_PACKAGE = "instrument_package";

  /** Test generated property to show the actual instrumented test runner you are using. */
  String PROPERTY_RUNNER = "instrument_runner";

  /** Test generated property to show the actual instrument options you are using. */
  String PROPERTY_OPTIONS = "instrument_options";

  /** Test generated property to show test results when an sequence of option map is provided. */
  String PROPERTY_RESULT = "instrument_result";

  /** Test generated property to show number of the iterations if batch run is enabled. */
  String PROPERTY_ITER_COUNT = "iter_count";

  /** Test name which means to run all tests in the APK. */
  String TEST_NAME_ALL = "all";

  /** Test name which means to run all small tests in the APK. */
  String TEST_NAME_SMALL = "small";

  /** Test name which means to run all medium tests in the APK. */
  String TEST_NAME_MEDIUM = "medium";

  /** Test name which means to run all large tests in the APK. */
  String TEST_NAME_LARGE = "large";

  /**
   * Repeated test runners whose generated repeated tests should be filtered out so only one of the
   * repeated tests will be run.
   */
  ImmutableSet<String> SUPPORTED_REPEATED_TEST_RUNNERS_FOR_FILTER =
      ImmutableSet.of(
          "com.google.devtools.mobileharness.platform.android.testrunner.AndroidRepeatedTestRunner");
}
