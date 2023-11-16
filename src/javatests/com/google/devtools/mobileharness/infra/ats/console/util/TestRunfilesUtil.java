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

package com.google.devtools.mobileharness.infra.ats.console.util;

import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;

/** Util to handle test runfiles (like test data files) for unit tests. */
public final class TestRunfilesUtil {

  public static String getRunfilesLocation(String suffix) {
    return RunfilesUtil.getRunfilesLocation(
        "javatests/com/google/devtools/mobileharness/infra/ats/console/" + suffix);
  }

  private TestRunfilesUtil() {}
}
