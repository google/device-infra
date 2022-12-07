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

package com.google.devtools.mobileharness.platform.android.lightning.shared;

/** Utility for parsing ADB output. */
public final class AdbOutputParsingUtil {

  /**
   * Whether the given ADB output indicates an installation failure caused by insufficient storage.
   */
  public static boolean isAdbOutputOfInstallationInsufficientStorage(String adbOutput) {
    return adbOutput.contains("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE")
        || adbOutput.contains("not enough space")
        || adbOutput.contains("Not enough space");
  }

  private AdbOutputParsingUtil() {}
}
