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

package com.google.devtools.mobileharness.shared.util.file.remote;

import com.google.auto.value.AutoValue;
import java.nio.file.Path;

/** A wrapper class of cache info. */
@AutoValue
public abstract class CacheInfo {
  abstract Path localCachePath();

  abstract boolean isCached();

  public static CacheInfo create(Path localCachePath, boolean isCached) {
    return new AutoValue_CacheInfo(localCachePath, isCached);
  }
}
