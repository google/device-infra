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

package com.google.devtools.mobileharness.shared.util.cache.persistent;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.errorprone.annotations.Immutable;
import java.nio.file.Path;

/** Paths data for a single cache entry. */
@AutoValue
@Immutable
public abstract class CachePaths {

  public abstract Path cacheDirPath();

  @Memoized
  public Path dataPath() {
    return cacheDirPath().resolve(".data");
  }

  @Memoized
  public Path lockPath() {
    return cacheDirPath().resolve(".lock");
  }

  @Memoized
  public Path metadataPath() {
    return cacheDirPath().resolve(".metadata");
  }

  public static CachePaths create(Path cacheDirPath) {
    return new AutoValue_CachePaths(cacheDirPath);
  }
}
