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

package com.google.devtools.mobileharness.platform.android.xts.suite;

/** Common constants for suite. */
public final class SuiteCommon {

  public static final String SUITE_NAME = "suite_name";

  public static final String SUITE_VARIANT = "suite_variant";

  public static final String SUITE_VERSION = "suite_version";

  public static final String SUITE_PLAN = "suite_plan";

  public static final String SUITE_BUILD = "suite_build";

  public static final String SUITE_REPORT_VERSION = "suite_report_version";

  public static final String TEST_RESULT_PB_FILE_NAME = "test_result.pb";

  public static final String TEST_RESULT_XML_FILE_NAME = "test_result.xml";

  public static final String TEST_REPORT_PROPERTIES_FILE_NAME = "test-report.properties";

  public static final String TEST_REPORT_PROPERTY_HAS_TF_MODULE = "has_tf_module";

  public static final String TEST_REPORT_PROPERTY_HAS_NON_TF_MODULE = "has_non_tf_module";

  private SuiteCommon() {}
}
