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

package com.google.devtools.mobileharness.shared.util.file.checksum;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;

/** Utility class for generating checksum in order to verify data. */
public class ChecksumUtil {

  /** File information as the cache key. */
  @AutoValue
  abstract static class FileNameWithTime {
    public static FileNameWithTime create(String fileName, FileTime fileTime) {
      return new AutoValue_ChecksumUtil_FileNameWithTime(fileName, fileTime);
    }

    /** The file name. */
    abstract String fileName();

    /** The file time. */
    abstract FileTime fileTime();
  }

  private static final Duration CACHE_EXPIRE_TIME = Duration.ofHours(1);

  private final LoadingCache<FileNameWithTime, HashCode> lastFingerprints;

  private final HashFunction hashFunction;

  public ChecksumUtil() {
    this(Hashing.crc32());
  }

  /**
   * Passes in hash function and avoid MD5 if possible, while MD5 is probably neither fast nor
   * secure.
   */
  public ChecksumUtil(HashFunction hashFunction) {
    this.hashFunction = hashFunction;
    lastFingerprints =
        CacheBuilder.newBuilder()
            .expireAfterAccess(CACHE_EXPIRE_TIME)
            .build(
                new CacheLoader<FileNameWithTime, HashCode>() {
                  @Override
                  public HashCode load(FileNameWithTime fileNameWithTime)
                      throws MobileHarnessException {
                    try {
                      int bufSize = 16 * 1024;
                      Hasher hasher = hashFunction.newHasher();
                      byte[] buf = new byte[bufSize];

                      try (java.io.BufferedInputStream in =
                          new java.io.BufferedInputStream(
                              new java.io.FileInputStream(fileNameWithTime.fileName()))) {
                        int n;
                        while ((n = in.read(buf, 0, bufSize)) > 0) {
                          hasher.putBytes(buf, 0, n);
                        }
                      }
                      return hasher.hash();
                    } catch (IOException e) {
                      throw new MobileHarnessException(
                          BasicErrorId.FILE_CHECKSUM_ERROR, "Failed to get the file checksum.", e);
                    }
                  }
                });
  }

  /** Returns the fingerprint in hash code for the contents of a file or directory. */
  public HashCode fingerprintHashCode(String fileOrDirPath) throws MobileHarnessException {
    File file = new File(fileOrDirPath);
    if (file.isDirectory()) {
      return hashDir(file, file.getPath().length() + 1);
    } else {
      return hashFile(file);
    }
  }

  /** Returns the fingerprint in hash code of a file or dir. */
  public HashCode fingerprintHashCode(File file) throws MobileHarnessException {
    return fingerprintHashCode(file.toPath().toAbsolutePath().toString());
  }

  /** Returns the fingerprint hash code of {@code Path}. */
  public HashCode fingerprintHashCode(Path fileOrDirPath) throws MobileHarnessException {
    return fingerprintHashCode(fileOrDirPath.toAbsolutePath().toString());
  }

  /** Returns the fingerprint in string of a file or directory. */
  public String fingerprint(File fileOrDir) throws MobileHarnessException {
    return fingerprint(fileOrDir.toPath().toAbsolutePath().toString());
  }

  /** Returns the fingerprint in string of a file or directory. */
  public String fingerprint(Path fileOrDirPath) throws MobileHarnessException {
    return fingerprint(fileOrDirPath.toAbsolutePath().toString());
  }

  /** Returns the fingerprint in string of a file or directory. */
  public String fingerprint(String fileOrDirPath) throws MobileHarnessException {
    return fingerprintHashCode(fileOrDirPath).toString();
  }

  /** Returns fingerprint in string of a string. */
  public String fingerprintStr(String string) {
    return hashFunction.hashString(string, UTF_8).toString();
  }

  /** Returns fingerprint hash code of a byte array. */
  public HashCode fingerprintBytesHashCode(byte[] bytes) {
    return hashFunction.hashBytes(bytes);
  }

  /**
   * Gets the checksum of contents of a file.
   *
   * @return the hashcode of the file
   * @throws MobileHarnessException if anything wrong in the file operation
   */
  @VisibleForTesting
  HashCode hashFile(File file) throws MobileHarnessException {
    try {
      return lastFingerprints.get(
          FileNameWithTime.create(file.getPath(), FileTime.fromMillis(file.lastModified())));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof MobileHarnessException) {
        throw (MobileHarnessException) e.getCause();
      }
      throw new MobileHarnessException(
          BasicErrorId.FILE_CHECKSUM_ERROR, "Failed to get the file checksum.", e.getCause());
    }
  }

  /**
   * Gets the checksum of contents of a directory. The typical use is "hashDir(rootDirectory,
   * rootDirectory.getPath().length() + 1)".
   *
   * @param prefixLen the number of leading characters in the path of the root directory
   * @return the hashcode of the directory
   * @throws MobileHarnessException if anything wrong in the file operation
   */
  @VisibleForTesting
  HashCode hashDir(File dir, int prefixLen) throws MobileHarnessException {
    Hasher hasher = hashFunction.newHasher();
    File[] contents = dir.listFiles();
    if (contents != null && contents.length > 0) {
      Arrays.sort(contents, Comparator.comparing(File::getName));
      for (File f : contents) {
        hasher.putString(f.getPath().substring(prefixLen), UTF_8);
        if (f.isDirectory()) {
          hasher.putString(hashDir(f, prefixLen).toString(), UTF_8);
        } else {
          hasher.putString(hashFile(f).toString(), UTF_8);
        }
      }
    }
    return hasher.hash();
  }
}
