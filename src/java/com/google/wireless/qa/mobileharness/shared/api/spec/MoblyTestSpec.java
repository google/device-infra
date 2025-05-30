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

import com.google.wireless.qa.mobileharness.shared.api.annotation.FileAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;

/** The parameters/files for MoblyTest. */
@SuppressWarnings("InterfaceWithOnlyStatics")
public interface MoblyTestSpec {

  @FileAnnotation(required = true, help = "The .par file of your Mobly testcases.")
  public static final String FILE_TEST_LIB_PAR = "test_lib_par";

  @FileAnnotation(required = false, help = "A custom Mobly YAML config file")
  public static final String FILE_MOBLY_CONFIG = "mobly_config";

  @ParamAnnotation(
      required = false,
      help =
          "The number of seconds to allow for cleanup. Must be a positive integer. Uses the default"
              + " value if not set.")
  public static final String CLEANUP_TIMEOUT_KEY = "cleanup_timeout_sec";

  @ParamAnnotation(
      required = false,
      help =
          "By default, all testcases in a Mobly test class are executed. To only execute a subset"
              + " of tests, supply the test names in this param as: \"test_a test_b ...\"")
  public static final String TEST_SELECTOR_KEY = "test_case_selector";

  @ParamAnnotation(required = false, help = "Whether to use none for the secure wrapper user.")
  public static final String SECURE_WRAPPER_USER_NONE = "secure_wrapper_user_none";
}
