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
import static com.google.common.primitives.Bytes.reverse;

import com.google.common.flogger.FluentLogger;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.devtools.mobileharness.shared.util.file.checksum.proto.ChecksumProto.Algorithm;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/** Helper class to compute and compare checksums. */
final class ChecksumHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final BaseEncoding HEX_ENCODING = BaseEncoding.base16().lowerCase();

  static ByteString decode(String encoded) {
    return ByteString.copyFrom(HEX_ENCODING.decode(encoded));
  }

  static String encode(ByteString checksum) {
    return HEX_ENCODING.encode(checksum.toByteArray());
  }

  static boolean isChecksumValid(Path dataPath, Algorithm checksumAlgorithm, ByteString expected) {
    return computeChecksum(dataPath, checksumAlgorithm)
        .map(computed -> computed.equals(expected))
        .orElse(false);
  }

  private static Optional<ByteString> computeChecksum(Path dataPath, Algorithm checksumAlgorithm) {
    return switch (checksumAlgorithm) {
      case GCS_CRC32C, GCS_MD5, ATS_FILE_SERVER_SHA256 ->
          getHashFunction(checksumAlgorithm)
              .map(hashFunction -> compute(dataPath, hashFunction, checksumAlgorithm));
      case ALGORITHM_UNSPECIFIED, UNRECOGNIZED -> {
        logger.atWarning().log("Unknown checksum algorithm %s.", checksumAlgorithm);
        yield Optional.empty();
      }
    };
  }

  @Nullable
  private static ByteString compute(
      Path dataPath, HashFunction hashFunction, Algorithm checksumAlgorithm) {
    try {
      byte[] bytes = asByteSource(dataPath).hash(hashFunction).asBytes();
      switch (checksumAlgorithm) {
        case GCS_CRC32C -> {
          // GCS stores the checksum in big endian while the CRC32C is computed in little endian.
          // See https://cloud.google.com/storage/docs/metadata#crc32c
          reverse(bytes);
        }
        case GCS_MD5, ATS_FILE_SERVER_SHA256, ALGORITHM_UNSPECIFIED, UNRECOGNIZED -> {
          // Do nothing.
        }
      }
      return ByteString.copyFrom(bytes);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to compute checksum for %s.", dataPath);
      return null;
    }
  }

  @SuppressWarnings("deprecation") // MD5 is still used in GCS.
  private static Optional<HashFunction> getHashFunction(Algorithm checksumAlgorithm) {
    return switch (checksumAlgorithm) {
      case GCS_CRC32C -> Optional.of(Hashing.crc32c());
      case GCS_MD5 -> Optional.of(Hashing.md5());
      case ATS_FILE_SERVER_SHA256 -> Optional.of(Hashing.sha256());
      case ALGORITHM_UNSPECIFIED, UNRECOGNIZED -> {
        logger.atWarning().log("Unknown checksum algorithm %s.", checksumAlgorithm);
        yield Optional.empty();
      }
    };
  }

  private ChecksumHelper() {}
}
