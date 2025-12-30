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

package com.google.devtools.mobileharness.platform.android.shared;

import java.util.regex.Pattern;

/** Static utility methods for Android application IDs. */
public final class ApplicationIds {

  // Based on https://developer.android.com/build/configure-app-module#set-application-id
  private static final Pattern APPLICATION_ID_REGEX =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");

  /** Returns whether the given string is a valid application ID. */
  public static boolean isValid(String applicationId) {
    return APPLICATION_ID_REGEX.matcher(applicationId).matches();
  }

  private ApplicationIds() {}
}
