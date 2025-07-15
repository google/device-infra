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

import static com.google.common.base.Ascii.toLowerCase;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.ChecksumAlgorithm;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.StorageApi;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.ImmutableTypeParameter;
import java.nio.file.Path;

/** Key for persistent cache. */
@Immutable
@AutoValue
public abstract class CacheKey<@ImmutableTypeParameter K> {

  public abstract K originalKey();

  public abstract String team();

  public abstract StorageApi storageApi();

  public abstract ChecksumAlgorithm checksumAlgorithm();

  public abstract String checksum();

  public static <@ImmutableTypeParameter K> CacheKey<K> create(
      K originalKey,
      String team,
      StorageApi storageApi,
      ChecksumAlgorithm checksumAlgorithm,
      String checksum) {
    return new AutoValue_CacheKey<>(originalKey, team, storageApi, checksumAlgorithm, checksum);
  }

  Path getRelativePath() {
    return Path.of(
        team(),
        toLowerCase(storageApi().name()),
        toLowerCase(checksumAlgorithm().name()),
        checksum());
  }
}
