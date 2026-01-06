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

/** Specs for MoblyAospTest. */
@SuppressWarnings("InterfaceWithOnlyStatics") // This interface is implemented by some decorators
public interface MoblyAospTestSpec {
  @FileAnnotation(
      required = true,
      help =
          "The package containing your Mobly testcases and data files. It can be generated via "
              + "a `python_test_host` rule in your test project's `Android.bp` file.")
  public static final String FILE_MOBLY_PKG = "mobly_pkg";

  @ParamAnnotation(
      required = false,
      help =
          "Relative path of Mobly test/suite. It can be a .py file within the test package or an"
              + "installed python executable relative to the venv/bin directory.")
  public static final String PARAM_TEST_PATH = "test_path";

  @ParamAnnotation(
      required = false,
      help =
          "Specifies version of python you wish to use for your test. Note that only 3.4+ is"
              + " supported. The expected format is ^(python)?3(\\.[4-9])?$. Note that the version"
              + " supplied here must match the executable name.")
  public static final String PARAM_PYTHON_VERSION = "python_version";

  @ParamAnnotation(required = false, help = "Base URL of Python Package Index.")
  public static final String PARAM_PY_PKG_INDEX_URL = "python_pkg_index_url";

  @ParamAnnotation(
      required = false,
      help =
          "The main command to run the mobly test with extra runner and params, for example, use"
              + " the mobly android partner runner with command 'mobly_runner mobly_test_suite -i'."
              + " If not set, the mobly test bin will be used.")
  public static final String PARAM_TEST_EXECUTION_COMMAND = "test_execution_command";

  @ParamAnnotation(
      required = false,
      help = "Path to the venv. If not set, a venv will be created in a temporary directory.")
  public static final String PARAM_VENV_PATH = "venv_path";
}
