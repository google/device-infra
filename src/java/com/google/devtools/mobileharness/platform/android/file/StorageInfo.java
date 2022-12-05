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

package com.google.devtools.mobileharness.platform.android.file;

import com.google.auto.value.AutoValue;

/** Disk information of an Android device. */
@AutoValue
public abstract class StorageInfo {
  public static StorageInfo create(long totalBytes, long freeBytes) {
    return new AutoValue_StorageInfo(totalBytes >> 10, freeBytes >> 10);
  }

  /** Total disk space (KB). */
  public abstract long totalKB();

  /** Free disk space (KB). */
  public abstract long freeKB();
}
