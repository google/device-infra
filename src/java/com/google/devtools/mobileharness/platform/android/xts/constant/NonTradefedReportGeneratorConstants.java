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

import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** Constants for {@code NonTradefedReportGenerator}. */
public final class NonTradefedReportGeneratorConstants {

  @ParamAnnotation(
      required = false,
      help = "Whether to run certification test suite. Default to false.")
  public static final String PARAM_RUN_CERTIFICATION_TEST_SUITE = "run_certification_test_suite";

  @ParamAnnotation(
      required = false,
      help = "xTS suite info. Should be key value pairs like 'key1=value1,key2=value2'.")
  public static final String PARAM_XTS_SUITE_INFO = "xts_suite_info";

  private NonTradefedReportGeneratorConstants() {}
}
