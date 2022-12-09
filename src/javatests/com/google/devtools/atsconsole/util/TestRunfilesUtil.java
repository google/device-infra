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

package com.google.devtools.atsconsole.util;

/** Util to handle test runfiles (like test data files) for unit tests. */
public final class TestRunfilesUtil {

  private static final String TEST_DATA_ROOT_PATH =
      "com_google_deviceinfra/src/javatests/com/google/devtools/atsconsole/";

  @SuppressWarnings("UnnecessarilyFullyQualified")
  public static String getRunfilesLocation(String suffix) {
    try {
      return com.google.devtools.build.runfiles.Runfiles.create()
          .rlocation(TEST_DATA_ROOT_PATH + suffix);
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }

  private TestRunfilesUtil() {}
}
