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

package com.google.devtools.mobileharness.platform.android.app;

import com.google.auto.value.AutoValue;

/** Android app version info. */
@AutoValue
public abstract class AndroidAppVersion {
  public static AndroidAppVersion create(int code, String name) {
    return new AutoValue_AndroidAppVersion(code, name);
  }

  /** Version code. */
  public abstract int code();

  /** Version name. */
  public abstract String name();
}
