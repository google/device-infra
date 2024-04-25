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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.common.base.Ascii;

/** Helper class for session handler utils. */
public class SessionHandlerHelper {

  public static final String XTS_TF_JOB_PROP = "xts-tradefed-job";
  public static final String XTS_NON_TF_JOB_PROP = "xts-non-tradefed-job";
  public static final String XTS_MODULE_NAME_PROP = "xts-module-name";
  public static final String XTS_MODULE_ABI_PROP = "xts-module-abi";
  public static final String XTS_MODULE_PARAMETER_PROP = "xts-module-parameter";

  public static final String TEST_RESULT_XML_FILE_NAME = "test_result.xml";

  /** Checks if the test plan is retry. */
  public static boolean isRunRetry(String testPlan) {
    return Ascii.equalsIgnoreCase(testPlan, "retry");
  }

  private SessionHandlerHelper() {}
}
