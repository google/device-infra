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
import static com.google.devtools.mobileharness.shared.util.cache.persistent.ChecksumHelper.encode;

import com.google.auto.value.AutoValue;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Algorithm;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Checksum;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.ImmutableTypeParameter;
import java.nio.file.Path;

/** Key for persistent cache. */
@Immutable
@AutoValue
public abstract class CacheKey<@ImmutableTypeParameter K> {

  public abstract K originalKey();

  public abstract String team();

  public abstract Algorithm checksumAlgorithm();

  // Base64 encoded checksum.
  public abstract String checksum();

  public static <@ImmutableTypeParameter K> CacheKey<K> create(
      K originalKey, String team, Algorithm checksumAlgorithm, String checksum) {
    return new AutoValue_CacheKey<>(originalKey, team, checksumAlgorithm, checksum);
  }

  public static <@ImmutableTypeParameter K> CacheKey<K> create(
      K originalKey, String team, Checksum checksum) {
    return create(originalKey, team, checksum.getAlgorithm(), encode(checksum.getData()));
  }

  Path getRelativePath() {
    return Path.of(team(), toLowerCase(checksumAlgorithm().name()), checksum());
  }
}
