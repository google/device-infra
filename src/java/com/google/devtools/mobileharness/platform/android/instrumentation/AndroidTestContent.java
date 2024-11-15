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

package com.google.devtools.mobileharness.platform.android.instrumentation;

import static com.google.common.base.Preconditions.checkNotNull;

/** Class to enumerate constant data provided by Android X support library. */
public final class AndroidTestContent {
  /**
   * A copy of HostedFile.FileType in
   * third_party/android/androidx_test/services/storage/java/androidx/test/services/storage/file/HostedFile.java
   */
  public enum FileType {
    FILE("f"),
    DIRECTORY("d");

    private final String type;

    FileType(String type) {
      this.type = checkNotNull(type);
    }

    public String getType() {
      return type;
    }
  }
}
