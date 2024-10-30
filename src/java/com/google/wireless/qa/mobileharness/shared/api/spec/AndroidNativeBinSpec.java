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

/** Specs for AndroidNativeBin. */
public interface AndroidNativeBinSpec {

  /** The default tmp dir for native binary test. */
  public static final String DEFAULT_RUN_DIR = "/data/local/tmp/";

  /** The default tmp dir name for unzipped test directories. */
  public static final String DEFAULT_UNZIP_DIR_NAME = "native_bin_unzip_dir";

  @FileAnnotation(
      required = true,
      help = "The binary file. The binary will be executed during the test.")
  public static final String TAG_BIN = "bin";

  @FileAnnotation(
      required = false,
      help = "The zip file containing tests. Unzipped contents are pushed to device before test.")
  public static final String TAG_BIN_ZIP = "bin_zip";

  @ParamAnnotation(required = false, help = "The flag set AndroidNativeBin binary execution time.")
  public static final String PARAM_ANDROID_BIN_TIMEOUT_SEC = "android_bin_timeout_sec";

  @ParamAnnotation(required = false, help = "Other optional flags for running binary file.")
  public static final String PARAM_OPTIONS = "options";

  @ParamAnnotation(required = false, help = "User to run the tests as.")
  public static final String PARAM_TEST_USER = "test_user";

  @ParamAnnotation(
      required = false,
      help =
          "The temporary folder for placing and running the binary.(default "
              + DEFAULT_RUN_DIR
              + ")")
  public static final String PARAM_RUN_DIR = "run_dir";

  @ParamAnnotation(
      required = false,
      help =
          "Launch the binary by taskset with a given CPU affinity."
              + "\"taskset\" and this flag is only available for device with API > 22."
              + "The CPU affinity is represented as a bitmask in hexadecimal:"
              + "1 is processor #0"
              + "3 is processors #0 and #1"
              + "F0 is processors #4 ~ #7")
  public static final String PARAM_CPU_AFFINITY = "cpu_affinity";

  @ParamAnnotation(
      required = false,
      help =
          "Launch the binary with the environmental variables provided here. "
              + "This parameter value should normally be a space-separated list of environment "
              + "variable settings of the form var='value'. (For example, this may be used to set "
              + "the LD_LIBRARY_PATH by passing: run_env: LD_LIBRARY_PATH='/some/path/to/lib'). "
              + "This parameter value will be prefixed to the shell command line that invokes the "
              + "binary. As such, it will be subject to the shell's usual expansions "
              + "(i.e. brace expansion, tilde expansion, shell parameter and variable expansion, "
              + "command substitution, arithmetic expansion, word splitting, and pathname "
              + "expansion). ")
  public static final String PARAM_RUN_ENVIRONMENT = "run_env";

  @ParamAnnotation(
      required = false,
      help =
          "The keyword in the native bin execution log to indicate the result is PASS. If"
              + " signal not found, will mark the test result as FAIL.")
  public static final String PARAM_PASS_SIGNAL = "pass_signal";
}
