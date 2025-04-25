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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import java.util.Optional;

/** Util class for Android adb command output. */
public final class AndroidAdbOutputUtil {

  /**
   * Converts a string property value to a boolean value if possible.
   *
   * <p>See the definition of boolean type in <a
   * href="https://source.android.com/docs/core/architecture/configuration/add-system-properties#type">
   * Android doc</a>
   */
  public static Optional<Boolean> convertPropertyValueToBoolean(String stringPropertyValue) {
    if (stringPropertyValue.equals("true") || stringPropertyValue.equals("1")) {
      return Optional.of(true);
    }
    if (stringPropertyValue.equals("false") || stringPropertyValue.equals("0")) {
      return Optional.of(false);
    }
    return Optional.empty();
  }

  private AndroidAdbOutputUtil() {}
}
