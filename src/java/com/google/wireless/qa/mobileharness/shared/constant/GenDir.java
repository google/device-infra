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

package com.google.wireless.qa.mobileharness.shared.constant;

/** Constants about MH gen file dirs. */
public class GenDir {

  /**
   * The Android test arg name, or iOS/Java/Python ENV VAR name for the relative path of the test
   * gen file dir.
   */
  public static final String KEY_NAME_OF_TEST_GEN_FILE_DIR_RELATIVE_PATH =
      "mh_gen_file_dir_relative_path";

  private GenDir() {}
}
