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

import static com.google.devtools.mobileharness.shared.util.cache.persistent.ChecksumHelper.encode;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.Metadata;
import com.google.errorprone.annotations.Immutable;
import java.nio.file.Path;

/** The data class for the cache result. */
@Immutable
@AutoValue
public abstract class CacheResult {

  public abstract Path symlinkPath();

  public abstract Metadata metadata();

  /** The checksum of the file encoded in base64. */
  @Memoized
  public String getEncodedChecksum() {
    return encode(metadata().getChecksum().getData());
  }

  public static CacheResult create(Path symlinkPath, Metadata metadata) {
    return new AutoValue_CacheResult(symlinkPath, metadata);
  }
}
