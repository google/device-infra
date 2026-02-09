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

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.model.StorageObject;
import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.protobuf.ByteString;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Utility class for operating with files using Google Cloud Storage. */
public interface GcsUtil {

  /** Credential type for GCS. */
  @AutoOneOf(CredentialType.Type.class)
  public abstract static class CredentialType {
    /** Type of credential. */
    public enum Type {
      NONE,
      CREDENTIAL_FILE,
      APP_DEFAULT,
    }

    public abstract Type getType();

    public abstract void none();

    public abstract String credentialFile();

    public abstract void appDefault();

    public static CredentialType ofNone() {
      return AutoOneOf_GcsUtil_CredentialType.none();
    }

    public static CredentialType ofCredentialFile(String credentialFile) {
      return AutoOneOf_GcsUtil_CredentialType.credentialFile(credentialFile);
    }

    public static CredentialType ofAppDefault() {
      return AutoOneOf_GcsUtil_CredentialType.appDefault();
    }
  }

  /** Parameters about the Google Cloud Storage the client used. */
  public static class GcsParams {
    /**
     * Default application name for using GCS.
     *
     * <p>The application name is to be sent in the User-Agent header of Google API requests.
     */
    private static final String DEFAULT_APPLICATION_NAME = "mobile-harness-labserver";

    /** access scope of the client to the bucket. */
    public enum Scope {
      READ_ONLY("https://www.googleapis.com/auth/devstorage.read_only"),
      READ_WRITE("https://www.googleapis.com/auth/devstorage.read_write"),
      FULL_CONTROL("https://www.googleapis.com/auth/devstorage.full_control"),
      CLOUDPLATFORM_READ_ONLY("https://www.googleapis.com/auth/cloud-platform.read-only"),
      CLOUDPLATFORM("https://www.googleapis.com/auth/cloud-platform");
      final String uri;

      Scope(String uri) {
        this.uri = uri;
      }
    }

    @VisibleForTesting final String applicationName;
    @VisibleForTesting final String bucketName;
    @VisibleForTesting final CredentialType credentialType;
    @VisibleForTesting final Scope scope;

    /**
     * Constructs CloudStorage Params given parameters about the storage.
     *
     * @param bucketName name of the bucket containing files
     * @param scope access scope of the service account
     * @param credentialType credential type of the service account
     */
    public GcsParams(String bucketName, Scope scope, CredentialType credentialType) {
      this(DEFAULT_APPLICATION_NAME, bucketName, scope, credentialType);
    }

    /**
     * Constructs CloudStorage Params given parameters about the storage.
     *
     * @param applicationName name of the Google Cloud application to send in the User-Agent header
     *     of Google API requests.
     * @param bucketName name of the bucket containing files
     * @param scope access scope of the service account
     * @param credentialType credential type of the service account
     */
    public GcsParams(
        String applicationName, String bucketName, Scope scope, CredentialType credentialType) {
      this.applicationName = applicationName;
      this.bucketName = bucketName;
      this.scope = scope;
      this.credentialType = credentialType;
    }
  }

  /**
   * Object used for making get, copy, and delete requests.
   *
   * <p>See https://cloud.google.com/storage/docs/json_api/v1/objects for details on how parameters
   * are used.
   */
  @AutoValue
  public abstract static class GcsApiObject {
    abstract Path path();

    abstract Optional<Long> generationNumber();

    public static GcsApiObject create(GcsUri gcsUri) {
      return create(gcsUri.objectPath());
    }

    public static GcsApiObject create(Path path) {
      return new AutoValue_GcsUtil_GcsApiObject(path, Optional.empty());
    }

    public static GcsApiObject create(Path path, Long generationNumber) {
      return new AutoValue_GcsUtil_GcsApiObject(path, Optional.of(generationNumber));
    }

    @Override
    public final String toString() {
      String toString = path().toString();
      if (generationNumber().isPresent()) {
        toString += "#" + generationNumber().get();
      }
      return toString;
    }
  }

  /**
   * Makes sure the directory or file exists.
   *
   * @throws MobileHarnessException if the file or directory does not exist
   */
  boolean fileOrDirExist(String fileOrDirPath) throws MobileHarnessException;

  /**
   * Copies the file or directory to local.
   *
   * @param gcsFileOrDir the file or directory to copy
   * @param localFileOrDir the local file or directory to copy to
   * @throws MobileHarnessException if failed to copy
   * @throws InterruptedException if interrupted
   */
  void copyFileOrDirectoryToLocal(String gcsFileOrDir, Path localFileOrDir)
      throws MobileHarnessException, InterruptedException;

  /**
   * Copy file from GCS to local, replace destination file with atomic move. Save md5hash as file
   * attribute.
   *
   * @param gcsFile gcs path to remote file.
   * @param localFile destination path to local file.
   * @throws MobileHarnessException when failed to download.
   * @throws InterruptedException when interrupted.
   */
  void copyFileToLocalIfNotExist(String gcsFile, Path localFile)
      throws MobileHarnessException, InterruptedException;

  /** Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. */
  void copyFileToLocal(GcsApiObject gcsFile, Path localFile)
      throws MobileHarnessException, InterruptedException;

  /** Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. */
  void copyFileToLocal(String gcsFile, String localFile)
      throws MobileHarnessException, InterruptedException;

  /** Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. */
  void copyFileToLocal(Path gcsFile, Path localFile)
      throws MobileHarnessException, InterruptedException;

  /**
   * Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. file {@code
   * gcsFile} will be split into shards with size {@code shardSize} and copied in parallel.
   */
  void copyFileToLocalInParallel(Path gcsFile, Path localFile, long shardSize)
      throws MobileHarnessException, InterruptedException;

  /**
   * Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. file {@code
   * gcsFile} will be split into shards with size {@code shardSize} and copied in parallel.
   */
  void copyFileToLocalInParallel(GcsApiObject gcsFile, Path localFile, long shardSize)
      throws MobileHarnessException, InterruptedException;

  /** Gets the size of the GCS file {@code gcsFile}. */
  long getGcsFileSize(Path gcsFile) throws MobileHarnessException, InterruptedException;

  /** Gets name of bucket this util is connecting to. */
  String getBucketName();

  /** Checks if the GCS file {@code gcsFile} is compressed. */
  boolean isCompressed(Path gcsFile) throws MobileHarnessException, InterruptedException;

  /**
   * Gets MD5 Hash of GCS file {@code gscFile}. Go to
   * https://cloud.google.com/storage/docs/composite-objects for more details.
   *
   * @return md5 of file {@code file}; or empty if {@code file} doesn't exist
   */
  Optional<String> getMd5Hash(Path gcsFile) throws MobileHarnessException, InterruptedException;

  Optional<String> getMd5Hash(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException;

  /**
   * Gets Crc32c of GCS file {@code gscFile}. Go to
   * https://cloud.google.com/storage/docs/composite-objects for more details.
   *
   * @return crc32c of file {@code file}; or empty if {@code file} doesn't exist
   */
  Optional<String> getCrc32c(Path gcsFile) throws MobileHarnessException, InterruptedException;

  /**
   * Gets Crc32c of GCS file {@code gscFile}. Go to
   * https://cloud.google.com/storage/docs/composite-objects for more details.
   *
   * @return crc32c of file {@code file}; or empty if {@code file} doesn't exist
   */
  Optional<String> getCrc32c(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException;

  /**
   * Gets Crc32c of GCS file {@code gscFile} in bytes. Go to
   * https://cloud.google.com/storage/docs/composite-objects for more details.
   *
   * @return crc32c of file {@code file}; or empty if {@code file} doesn't exist
   */
  Optional<ByteString> getCrc32cBytes(Path gcsFile)
      throws MobileHarnessException, InterruptedException;

  /**
   * Gets Decoded crc32c of GCS file {@code gscFile}. Default crc32c of a GCS file is encoded by
   * base64 in big-endian byte order. Go to https://cloud.google.com/storage/docs/hashes-etags for
   * more details.
   *
   * @return crc32c of file {@code file}; or empty if {@code file} doesn't exist
   */
  Optional<String> getDecodedCrc32c(Path gcsFile)
      throws MobileHarnessException, InterruptedException;

  /**
   * Decodes GCS {@code crc32c} into a standard one. Default crc32c of a GCS file is encoded by
   * base64 in big-endian byte order. Go to https://cloud.google.com/storage/docs/hashes-etags for
   * more details.
   */
  String decodeCrc32c(String crc32c);

  /** Encodes a standard {@code crc32c} into GCS compatible one. */
  String encodeCrc32c(String crc32c);

  /**
   * Calculates GCS compatible crc32c of {@code file}. GCS is using base64 encoded crc32c bytes in
   * big-endian order.,
   */
  String calculateCrc32c(Path localFile) throws MobileHarnessException;

  /**
   * Calculates GCS compatible crc32c of {@code bytes}. GCS is using base64 encoded crc32c bytes in
   * big-endian order.,
   */
  String calculateCrc32cOfBytes(byte[] bytes);

  /**
   * Calculates the checksum of {@code localFile}.
   *
   * <p>The checksum algorithm is murmur3_128 now.
   */
  String calculateChecksum(Path localFile) throws MobileHarnessException;

  /** Return all the objects names beginning with this prefix. */
  List<String> listFiles(String prefix) throws MobileHarnessException;

  /**
   * Return the objects names beginning with this prefix in directory-like mode. Items will contain
   * only objects whose names, aside from the prefix, do not contain delimiter. Objects whose names,
   * aside from the prefix, contain delimiter will have their name, truncated after the delimiter,
   * returned in prefixes. Duplicate prefixes are omitted.
   *
   * @param prefix name of the objects' prefix
   * @param delimiter name of the objects' delimiter
   */
  List<String> listFiles(String prefix, String delimiter) throws MobileHarnessException;

  /**
   * Return the objects names beginning with this prefix in directory-like mode. Items will contain
   * only objects whose names, aside from the prefix, do not contain delimiter. Objects whose names,
   * aside from the prefix, contain delimiter will have their name, truncated after the delimiter,
   * returned in prefixes. Duplicate prefixes are omitted.
   *
   * @param prefix name of the objects' prefix
   * @param delimiter name of the objects' delimiter
   * @param recursively whether to recursively list files
   */
  List<String> listFiles(String prefix, String delimiter, boolean recursively)
      throws MobileHarnessException;

  /**
   * Copies {@code localFile} to Google Cloud Storage file {@code gcsFile}.
   *
   * @param localFile path of source file
   * @param gcsFile name of the object in the cloud
   * @param contentType content type of the object in the cloud
   * @throws MobileHarnessException if failed to copy file
   */
  void copyFileToCloud(String localFile, String gcsFile, String contentType)
      throws MobileHarnessException, InterruptedException;

  /**
   * Copies {@code localFile} to Google Cloud Storage file {@code gcsFile}.
   *
   * @param localFile path of source file
   * @param gcsFile name of the object in the cloud
   * @param contentType content type of the object in the cloud
   * @throws MobileHarnessException if failed to copy file
   */
  void copyFileToCloud(Path localFile, Path gcsFile, String contentType)
      throws MobileHarnessException, InterruptedException;

  /**
   * Copies {@code [from, from + size)} in {@code localFile} to {@code gcsFile}.
   *
   * @param localFile local file to copy
   * @param from byte offset to start
   * @param size byte size of copy
   * @param gcsFile GCS file to copy to
   */
  void partialCopyFileToCloud(Path localFile, long from, long size, Path gcsFile)
      throws MobileHarnessException, InterruptedException;

  // TODO: Make it private.
  /**
   * Copies content from {@code contentStream} to cloud as an object with default ACL.
   *
   * @param contentStream the content to upload
   * @param objectMetadata metaData of the object in the cloud, including the name of object
   * @throws MobileHarnessException if fails to copy file.
   */
  void copyContentStreamToCloud(InputStreamContent contentStream, StorageObject objectMetadata)
      throws MobileHarnessException;

  /** Composes gcs files in list {@code srcGcsFiles} to {@code dstGcsFile}. */
  void compose(Path dstGcsFile, boolean removeSources, List<Path> srcGcsFiles, String contentType)
      throws MobileHarnessException, InterruptedException;

  /**
   * Copies {@code localFile} to Google Cloud Storage {@code gcsFile} in shards with size {@code
   * shardSize} in parallel.
   *
   * @param localFile path of source file
   * @param gcsFile name of the object in the cloud
   * @throws MobileHarnessException if failed to copy file
   */
  void copyFileToCloudInParallel(Path localFile, Path gcsFile, long shardSize, String contentType)
      throws MobileHarnessException, InterruptedException;

  /**
   * Copies file {@code localFile} to Google Cloud Storage as {@code gcsFile} if {@code gcsFile}
   * doesn't exist or is dying (age >= ttl).
   *
   * @return whether copy is executed
   */
  @SuppressWarnings("GoodTime") // TODO: fix GoodTime violation
  boolean copyFileToCloudIfNonExistingOrDead(
      Path localFile, Path gcsFile, Duration ttl, String contentType)
      throws MobileHarnessException, InterruptedException;

  /**
   * Copies file {@code localFile} to Google Cloud Storage as {@code gcsFile} if {@code gcsFile}
   * doesn't exist or is dying (age >= ttl).
   *
   * @return whether copy is executed
   */
  boolean copyFileToCloudInParallelIfNonExistingOrDead(
      Path localFile, Path gcsFile, Duration ttl, long shardSize, String contentType)
      throws MobileHarnessException, InterruptedException;

  /**
   * Gets ages of {@code gcsFile}.
   *
   * @return age of file {@code file}; or empty if {@code file} doesn't exist
   */
  Optional<Duration> getAge(Path gcsFile) throws MobileHarnessException, InterruptedException;

  /**
   * Deletes a file on the cloud.
   *
   * @param targetFilePath the name of GCS object to delete
   * @throws MobileHarnessException if fails to delete the file
   */
  void deleteCloudFile(String targetFilePath) throws MobileHarnessException;

  /**
   * Checks if an object named {@code gcsFile} exists. This method is faster than fileOrDirExist(),
   * especially when there is a large amount of objects in the bucket. Because Google Cloud Storage
   * doesn't have native directory support, it won't have the performance of a native filesystem
   * listing deeply nested sub-directories.
   */
  boolean fileExist(Path gcsFile) throws MobileHarnessException, InterruptedException;

  /**
   * Checks if an object named {@code gcsFile} exists. This method is faster than fileOrDirExist(),
   * especially when there is a large amount of objects in the bucket. Because Google Cloud Storage
   * doesn't have native directory support, it won't have the performance of a native filesystem
   * listing deeply nested sub-directories.
   */
  boolean fileExist(GcsApiObject gcsFile) throws MobileHarnessException, InterruptedException;

  /** Gets the size of the local file {@code localFile}. */
  long getFileSize(Path localFile) throws MobileHarnessException;
}
