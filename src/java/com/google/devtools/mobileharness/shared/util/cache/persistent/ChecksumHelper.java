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

import static com.google.common.io.MoreFiles.asByteSource;

import com.google.common.flogger.FluentLogger;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Bytes;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.ChecksumAlgorithm;
import com.google.devtools.mobileharness.shared.util.cache.persistent.proto.MetadataProto.StorageApi;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/** Helper class to compute and compare checksums. */
final class ChecksumHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static boolean isChecksumValid(
      Path dataPath, StorageApi storageApi, ChecksumAlgorithm checksumAlgorithm, String checksum) {
    return computeChecksum(dataPath, storageApi, checksumAlgorithm)
        .map(computedChecksum -> compareChecksum(computedChecksum, checksum))
        .orElse(false);
  }

  private static boolean compareChecksum(String computedChecksum, String expectedChecksum) {
    logger.atInfo().log(
        "Compare computed checksum %s with expected checksum %s.",
        computedChecksum, expectedChecksum);
    return computedChecksum.equals(expectedChecksum);
  }

  private static Optional<String> computeChecksum(
      Path dataPath, StorageApi storageApi, ChecksumAlgorithm checksumAlgorithm) {
    return switch (storageApi) {
      case GCS ->
          getHashFunction(checksumAlgorithm)
              .map(hashFunction -> computeGcsChecksum(dataPath, hashFunction, checksumAlgorithm));
      case API_UNSPECIFIED, UNRECOGNIZED -> {
        logger.atWarning().log("Unknown storage API %s.", storageApi);
        yield Optional.empty();
      }
    };
  }

  @Nullable
  private static String computeGcsChecksum(
      Path dataPath, HashFunction hashFunction, ChecksumAlgorithm checksumAlgorithm) {
    try {
      byte[] bytes = asByteSource(dataPath).hash(hashFunction).asBytes();
      switch (checksumAlgorithm) {
        case CRC32C -> {
          // GCS stores the checksum in big endian while the CRC32C is computed in little endian.
          // See https://cloud.google.com/storage/docs/metadata#crc32c
          Bytes.reverse(bytes);
        }
        case MD5, CHECKSUM_ALGORITHM_UNSPECIFIED, UNRECOGNIZED -> {
          // Do nothing.
        }
      }
      return BaseEncoding.base64().encode(bytes);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to compute checksum for %s.", dataPath);
      return null;
    }
  }

  @SuppressWarnings("deprecation") // MD5 is still used in GCS.
  private static Optional<HashFunction> getHashFunction(ChecksumAlgorithm checksumAlgorithm) {
    return switch (checksumAlgorithm) {
      case CRC32C -> Optional.of(Hashing.crc32c());
      case MD5 -> Optional.of(Hashing.md5());
      case CHECKSUM_ALGORITHM_UNSPECIFIED, UNRECOGNIZED -> {
        logger.atWarning().log("Unknown checksum algorithm %s.", checksumAlgorithm);
        yield Optional.empty();
      }
    };
  }

  private ChecksumHelper() {}
}
