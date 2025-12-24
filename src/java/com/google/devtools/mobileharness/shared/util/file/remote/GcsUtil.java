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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.ComposeRequest;
import com.google.api.services.storage.model.ComposeRequest.SourceObjects;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.auto.value.AutoOneOf;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.file.checksum.ChecksumUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.ShutdownHookManager;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.ByteString;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

/** Utility class for operating with files using Google Cloud Storage. */
public class GcsUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Maximum numbers of shards. It's a limitation of Google Cloud Storage. {@code
   * https://cloud.google.com/storage/docs/gsutil/commands/compose}.
   */
  private static final int MAX_SHARD_COUNT = 32;

  /** Maximum attempts while meet network issue. */
  private static final int MAX_ATTEMPTS = 5;

  /** Output signal when lab server has no space left. */
  private static final String OUTPUT_NO_SPACE = "No space left on device";

  /**
   * Timeout of connecting to GCS service. Default value is 20s, see {@link
   * com.google.api.client.http.HttpRequest#connectTimeout}.
   */
  private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofMinutes(1);

  /**
   * Timeout of reading from GCS service. Default value is 20s, see {@link
   * com.google.api.client.http.HttpRequest#readTimeout}.
   */
  private static final Duration HTTP_READ_TIMEOUT = Duration.ofMinutes(1);

  /** Thread pool for uploading/downloading GSC file in parellel. */
  private static final class Holder {
    private static final ListeningExecutorService threadpool =
        ThreadPools.createStandardThreadPoolWithMaxSize(
            "gcs-util", Flags.instance().gcsUtilThreads.getNonNull());

    static {
      ShutdownHookManager.getInstance()
          .addShutdownHook(
              () -> {
                logger.atInfo().log("Shutting down GcsUtil thread pool.");
                threadpool.shutdownNow();
              },
              "gcs-util-shutdown");
    }
  }

  /** Random number generator for creating temporary file. */
  private static final Random random = new Random();

  /** File attribute view to save file attribute to. */
  private static final String ATTR_VIEW = "user";

  /** File attribute name to save file md5hash to. */
  private static final String MD5_ATTR_NAME = "md5hash";

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
      private final String uri;

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

  private final Storage client;

  private final GcsParams storageParams;

  private final ChecksumUtil checksumUtil;

  private final ChecksumUtil crc32cChecksumUtil;

  private final LocalFileUtil localFileUtil;

  private static Storage getClient(GcsParams storageParams) throws MobileHarnessException {
    // Get credential
    HttpRequestInitializer credential = getCredential(storageParams);
    return new Storage.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
        .setHttpRequestInitializer(
            request -> {
              // Timeout should be large enough to fit bad network conditions. b/79751793,
              // b/80438442, b/80443729.
              credential.initialize(request);
              request.setConnectTimeout((int) HTTP_CONNECT_TIMEOUT.toMillis());
              request.setReadTimeout((int) HTTP_READ_TIMEOUT.toMillis());
            })
        .setApplicationName(storageParams.applicationName)
        .build();
  }

  private static HttpRequestInitializer getCredential(GcsParams storageParams)
      throws MobileHarnessException {
    switch (storageParams.credentialType.getType()) {
      case APP_DEFAULT:
        return appDefaultCredential(storageParams.scope);
      case CREDENTIAL_FILE:
        return credentialFromJsonFile(
            storageParams.credentialType.credentialFile(), storageParams.scope);
      case NONE:
    }
    throw new MobileHarnessException(
        BasicErrorId.GCS_INVALID_PARAMS,
        String.format(
            "Invalid GcsParams %s. Please provide a valid credential type.", storageParams));
  }

  private static HttpRequestInitializer credentialFromJsonFile(
      String filePath, GcsParams.Scope scope) throws MobileHarnessException {
    try {
      return GoogleCredential.fromStream(
              new FileInputStream(filePath),
              GoogleNetHttpTransport.newTrustedTransport(),
              GsonFactory.getDefaultInstance())
          .createScoped(ImmutableSet.of(scope.uri));
    } catch (GeneralSecurityException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_SECURITY_ERROR, "General Security Exception when connecting to GCS", e);
    } catch (FileNotFoundException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND, "Credential file not found " + filePath, e);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_NETWORK_ERROR, "Failed to create credential", e);
    }
  }

  private static HttpRequestInitializer appDefaultCredential(GcsParams.Scope scope)
      throws MobileHarnessException {
    try {
      return GoogleCredential.getApplicationDefault().createScoped(ImmutableSet.of(scope.uri));
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_SECURITY_ERROR, "Cannot create application default credential!", e);
    }
  }

  /**
   * Constructs a CloudStorageUtil given the related parameters about the storage.
   *
   * @throws MobileHarnessException if failed to set up the client
   */
  public GcsUtil(GcsParams storageParams) throws MobileHarnessException {
    this(
        storageParams,
        getClient(storageParams),
        new ChecksumUtil(Hashing.murmur3_128()),
        new ChecksumUtil(Hashing.crc32c()),
        new LocalFileUtil());
  }

  @VisibleForTesting
  GcsUtil(
      GcsParams storageParams,
      Storage client,
      ChecksumUtil checksumUtil,
      ChecksumUtil crc32cChecksumUtil,
      LocalFileUtil localFileUtil) {
    this.storageParams = storageParams;
    this.client = client;
    this.checksumUtil = checksumUtil;
    this.crc32cChecksumUtil = crc32cChecksumUtil;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Makes sure the directory or file exists.
   *
   * @throws MobileHarnessException if the file or directory does not exist
   */
  public boolean fileOrDirExist(String fileOrDirPath) throws MobileHarnessException {
    return listFiles(fileOrDirPath).contains(fileOrDirPath);
  }

  /**
   * Copies the file or directory to local.
   *
   * @param gcsFileOrDir the file or directory to copy
   * @param localFileOrDir the local file or directory to copy to
   * @throws MobileHarnessException if failed to copy
   * @throws InterruptedException if interrupted
   */
  public void copyFileOrDirectoryToLocal(String gcsFileOrDir, Path localFileOrDir)
      throws MobileHarnessException, InterruptedException {
    Path gcsPath = GcsUri.parseUri(gcsFileOrDir).objectPath();
    if (isFile(gcsPath.toString())) {
      copyFileToLocal(gcsPath, localFileOrDir);
    } else {
      copyDirectoryToLocal(gcsPath, localFileOrDir);
    }
  }

  /** Returns true if the file is a file, false if it is a directory or not exist. */
  private boolean isFile(String fileOrDirPath) throws MobileHarnessException {
    List<String> files = listFiles(fileOrDirPath);
    return files.size() == 1 && files.get(0).equals(fileOrDirPath);
  }

  /**
   * Copies the directory to local.
   *
   * @param gcsDirectory the directory to copy
   * @param localDirectory the local directory to copy to
   * @throws MobileHarnessException if failed to copy
   * @throws InterruptedException if interrupted
   */
  private void copyDirectoryToLocal(Path gcsDirectory, Path localDirectory)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Copying directory from %s to %s", gcsDirectory, localDirectory);
    localFileUtil.prepareDir(localDirectory);
    localFileUtil.grantFileOrDirFullAccess(localDirectory);
    List<String> files = listFiles(gcsDirectory + "/");
    for (String file : files) {
      if (!file.endsWith("/")) { // Skip directories
        copyFileToLocal(
            Path.of(file), localDirectory.resolve(gcsDirectory.relativize(Path.of(file))));
      }
    }
  }

  /**
   * Copy file from GCS to local, replace destination file with atomic move. Save md5hash as file
   * attribute.
   *
   * @param gcsFile gcs path to remote file.
   * @param localFile destination path to local file.
   * @throws MobileHarnessException when failed to download.
   * @throws InterruptedException when interrupted.
   */
  public void copyFileToLocalIfNotExist(String gcsFile, Path localFile)
      throws MobileHarnessException, InterruptedException {
    if (localFileUtil.isFileExist(localFile)) {
      logger.atInfo().log("Skip copy from %s to %s, file exist!", gcsFile, localFile);
    }
    try {
      logger.atInfo().log("Copying from %s to %s", gcsFile, localFile);
      localFileUtil.prepareDir(localFile.getParent());
      localFileUtil.grantFileOrDirFullAccess(localFile.getParent());
      Path tempLocalPath =
          Files.createTempFile(localFile.getParent(), localFile.getFileName().toString(), null);
      Path gcsPath = GcsUri.parseUri(gcsFile).objectPath();
      this.copyFileToLocal(gcsPath, tempLocalPath);
      localFileUtil.moveFileOrDir(tempLocalPath, localFile);
      localFileUtil.grantFileOrDirFullAccess(localFile);

      String md5 = this.getMd5Hash(gcsPath).orElse("");
      if (!md5.isEmpty()) {
        Files.setAttribute(localFile, ATTR_VIEW + ":" + MD5_ATTR_NAME, UTF_8.encode(md5));
      }
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_DOWNLOAD_FILE_ERROR,
          String.format("Fail to download file from %s to %s", gcsFile, localFile),
          e);
    }
  }

  /** Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. */
  public void copyFileToLocal(GcsApiObject gcsFile, Path localFile)
      throws MobileHarnessException, InterruptedException {
    copyFileToLocal(gcsFile, localFile, 0, -1);
  }

  /** Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. */
  public void copyFileToLocal(String gcsFile, String localFile)
      throws MobileHarnessException, InterruptedException {
    copyFileToLocal(GcsApiObject.create(Path.of(gcsFile)), Path.of(localFile));
  }

  /** Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. */
  public void copyFileToLocal(Path gcsFile, Path localFile)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Copying GCS file %s to local %s", gcsFile, localFile);
    copyFileToLocal(GcsApiObject.create(gcsFile), localFile);
  }

  /**
   * Copies bytes [from, from + size) from {@code gcsFile} to {@code localFile}.
   *
   * @param gcsFile file to download from GCS
   * @param localFile file to save in local
   * @param from byte offset to start; it should large or equal to 0
   * @param size size to download; -1 means no limitation;
   */
  private void copyFileToLocal(GcsApiObject gcsFile, Path localFile, long from, long size)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessExceptions.check(
        from >= 0,
        BasicErrorId.GCS_ILLEGAL_OFFSET,
        () -> "file offset should be a non negative position, but get " + from);
    localFileUtil.prepareParentDir(localFile);

    String fileInfo =
        String.format(
            "file gs://%s/%s [%s, %s) to %s",
            storageParams.bucketName, gcsFile, from, from + size, localFile);

    retryIfMeetQuotaOrNetworkIssue(
        () -> {
          try (BufferedOutputStream bufferedOutputStream = getOutputStream(localFile)) {
            Storage.Objects.Get get =
                client.objects().get(storageParams.bucketName, gcsFile.path().toString());

            if (gcsFile.generationNumber().isPresent()) {
              get.setGeneration(gcsFile.generationNumber().get());
            }

            MediaHttpDownloader downloader = getMediaHttpDownloader(get);
            if (downloader == null) {
              throw new MobileHarnessException(
                  BasicErrorId.GCS_NO_DOWNLOADER,
                  String.format(
                      "Downloader of GCS file gs://%s/%s is not initialized.",
                      storageParams.bucketName, gcsFile));
            }

            downloader.setDirectDownloadEnabled(true);
            if (size > 0) {
              downloader.setContentRange(from, from + size - 1);
            } else if (from > 0) {
              downloader.setBytesDownloaded(from);
            }
            get.executeMediaAndDownloadTo(bufferedOutputStream);
          } catch (IOException e) {
            if (e.getMessage().contains(OUTPUT_NO_SPACE)) {
              throw new MobileHarnessException(
                  BasicErrorId.GCS_DOWNLOAD_FILE_NO_SPACE_ERROR,
                  "Please clean the lab machine to make space for GCS file downloading",
                  e);
            }
            throw new MobileHarnessException(
                BasicErrorId.GCS_DOWNLOAD_FILE_ERROR,
                String.format("Fail to copy %s: %s", fileInfo, e.getMessage()),
                e);
          }
          return null;
        },
        "copy " + fileInfo);

    logger.atInfo().log(
        "Copied file gs://%s/%s [%s, %s) to %s",
        storageParams.bucketName, gcsFile, from, from + size, localFile);
  }

  /** For mocking in unit test, because the wrapped method is `final`. */
  @VisibleForTesting
  MediaHttpDownloader getMediaHttpDownloader(Storage.Objects.Get get) {
    return get.getMediaHttpDownloader();
  }

  /**
   * Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. file {@code
   * gcsFile} will be split into shards with size {@code shardSize} and copied in parallel.
   */
  public void copyFileToLocalInParallel(Path gcsFile, Path localFile, long shardSize)
      throws MobileHarnessException, InterruptedException {
    copyFileToLocalInParallel(GcsApiObject.create(gcsFile), localFile, shardSize);
  }

  /**
   * Copies {@code gcsFile} from Google Cloud Storage to local {@code localFile}. file {@code
   * gcsFile} will be split into shards with size {@code shardSize} and copied in parallel.
   */
  public void copyFileToLocalInParallel(GcsApiObject gcsFile, Path localFile, long shardSize)
      throws MobileHarnessException, InterruptedException {
    long fileSize = getGcsFileSize(gcsFile.path());
    if (shardSize <= 0) {
      logger.atWarning().log(
          "The shard size %s should not be a negative value. So copy with only one shard",
          shardSize);
      copyFileToLocal(gcsFile, localFile, 0, -1);
      return;
    }

    if (fileSize <= shardSize) {
      copyFileToLocal(gcsFile, localFile, 0, -1);
      return;
    }

    int shardCount = (int) Math.ceil(fileSize * 1.0 / shardSize);
    logger.atInfo().log(
        "Copying GCS file gs://%s/%s(size %d) to local %s in %s shards,",
        storageParams.bucketName, gcsFile.path(), fileSize, localFile, shardCount);

    String shardNamePrefix =
        String.format(".%s.%s", localFile.getFileName(), Integer.toUnsignedLong(random.nextInt()));
    List<ListenableFuture<?>> results = new ArrayList<>();
    List<Path> shards = new ArrayList<>();
    for (int i = 0; i < shardCount; i++) {
      Path localFileShard = localFile.resolveSibling(String.format("%s.%s", shardNamePrefix, i));
      long from = (long) i * shardSize;
      long size = i < shardCount - 1 ? shardSize : -1;
      shards.add(localFileShard);

      results.add(
          Holder.threadpool.submit(
              () -> {
                copyFileToLocal(gcsFile, localFileShard, from, size);
                return null;
              }));
    }

    ListenableFuture<?> future = Futures.whenAllSucceed(results).call(() -> null, directExecutor());
    try {
      future.get();
      logger.atInfo().log(
          "Downloaded the gcs file %s with %s shards to %s", gcsFile, shards.size(), localFile);

      for (int i = 1; i < shards.size(); i++) {
        localFileUtil.appendToFile(shards.get(i), shards.get(0));
      }
      logger.atInfo().log("Merged the split gcs file %s to one", gcsFile);

      if (localFileUtil.isFileOrDirExist(localFile)) {
        localFileUtil.removeFileOrDir(localFile);
      }
      localFileUtil.moveFileOrDir(shards.get(0), localFile);
      logger.atInfo().log("Moved the merged gcs file %s to %s", gcsFile, localFile);
    } catch (InterruptedException e) {
      throw e;
    } catch (Throwable e) {
      tryRemoveFile(localFile);
      throw new MobileHarnessException(
          BasicErrorId.GCS_DOWNLOAD_FILE_ERROR,
          String.format(
              "Failed to download GCS file gs://%s/%s to %s in parallel",
              storageParams.bucketName, gcsFile, localFile),
          e);
    } finally {
      // Remove all temporary files at last
      for (Path shard : shards) {
        tryRemoveFile(shard);
      }
    }
  }

  /** Gets the size of the GCS file {@code gcsFile}. */
  public long getGcsFileSize(Path gcsFile) throws MobileHarnessException, InterruptedException {
    StorageObject metadata =
        getMetadata(GcsApiObject.create(gcsFile))
            .orElseThrow(
                () ->
                    new MobileHarnessException(
                        BasicErrorId.GCS_NO_METADATA,
                        String.format(
                            "GCS file gs://%s/%s doesn't exist.",
                            storageParams.bucketName, gcsFile)));
    return metadata.getSize().longValue();
  }

  /** Remove {@code file} without throwing an exception. */
  private void tryRemoveFile(Path file) throws InterruptedException {
    try {
      if (localFileUtil.isFileOrDirExist(file)) {
        localFileUtil.removeFileOrDir(file);
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to remove temporary file: %s", file);
    }
  }

  /** Gets name of bucket this util is connecting to. */
  public String getBucketName() {
    return storageParams.bucketName;
  }

  /** Checks if the GCS file {@code gcsFile} is compressed. */
  public boolean isCompressed(Path gcsFile) throws MobileHarnessException, InterruptedException {
    return getMetadata(GcsApiObject.create(gcsFile))
        .map(StorageObject::getContentType)
        .map(contentType -> contentType.equals("application/zip"))
        .orElse(false);
  }

  /**
   * Gets MD5 Hash of GCS file {@code gscFile}. Go to
   * https://cloud.google.com/storage/docs/composite-objects for more details.
   *
   * @return md5 of file {@code file}; or empty if {@code file} doesn't exist
   */
  public Optional<String> getMd5Hash(Path gcsFile)
      throws MobileHarnessException, InterruptedException {
    return getMd5Hash(GcsApiObject.create(gcsFile));
  }

  public Optional<String> getMd5Hash(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException {
    return getMetadata(gcsFile).map(StorageObject::getMd5Hash);
  }

  /**
   * Gets Crc32c of GCS file {@code gscFile}. Go to
   * https://cloud.google.com/storage/docs/composite-objects for more details.
   *
   * @return crc32c of file {@code file}; or empty if {@code file} doesn't exist
   */
  public Optional<String> getCrc32c(Path gcsFile)
      throws MobileHarnessException, InterruptedException {
    return getCrc32c(GcsApiObject.create(gcsFile));
  }

  /**
   * Gets Crc32c of GCS file {@code gscFile}. Go to
   * https://cloud.google.com/storage/docs/composite-objects for more details.
   *
   * @return crc32c of file {@code file}; or empty if {@code file} doesn't exist
   */
  public Optional<String> getCrc32c(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException {
    return getMetadata(gcsFile).map(StorageObject::getCrc32c);
  }

  /**
   * Gets Crc32c of GCS file {@code gscFile} in bytes. Go to
   * https://cloud.google.com/storage/docs/composite-objects for more details.
   *
   * @return crc32c of file {@code file}; or empty if {@code file} doesn't exist
   */
  public Optional<ByteString> getCrc32cBytes(Path gcsFile)
      throws MobileHarnessException, InterruptedException {
    return getCrc32c(gcsFile).map(s -> ByteString.copyFrom(BaseEncoding.base64().decode(s)));
  }

  /**
   * Gets Decoded crc32c of GCS file {@code gscFile}. Default crc32c of a GCS file is encoded by
   * base64 in big-endian byte order. Go to https://cloud.google.com/storage/docs/hashes-etags for
   * more details.
   *
   * @return crc32c of file {@code file}; or empty if {@code file} doesn't exist
   */
  public Optional<String> getDecodedCrc32c(Path gcsFile)
      throws MobileHarnessException, InterruptedException {
    return getCrc32c(gcsFile).map(this::decodeCrc32c);
  }

  /**
   * Decodes GCS {@code crc32c} into a standard one. Default crc32c of a GCS file is encoded by
   * base64 in big-endian byte order. Go to https://cloud.google.com/storage/docs/hashes-etags for
   * more details.
   */
  public String decodeCrc32c(String crc32c) {
    byte[] bytes = BaseEncoding.base64().decode(crc32c);
    Bytes.reverse(bytes);
    return HashCode.fromBytes(bytes).toString();
  }

  /** Encodes a standard {@code crc32c} into GCS compatible one. */
  public String encodeCrc32c(String crc32c) {
    byte[] bytes = HashCode.fromString(crc32c).asBytes();
    Bytes.reverse(bytes);
    return BaseEncoding.base64().encode(bytes);
  }

  /**
   * Calculates GCS compatible crc32c of {@code file}. GCS is using base64 encoded crc32c bytes in
   * big-endian order.,
   */
  public String calculateCrc32c(Path localFile) throws MobileHarnessException {
    byte[] bytes = crc32cChecksumUtil.fingerprintHashCode(localFile).asBytes();
    Bytes.reverse(bytes);
    return BaseEncoding.base64().encode(bytes);
  }

  /**
   * Calculates GCS compatible crc32c of {@code bytes}. GCS is using base64 encoded crc32c bytes in
   * big-endian order.,
   */
  public String calculateCrc32cOfBytes(byte[] bytes) {
    byte[] checksumBytes = crc32cChecksumUtil.fingerprintBytesHashCode(bytes).asBytes();
    Bytes.reverse(checksumBytes);
    return BaseEncoding.base64().encode(checksumBytes);
  }

  /**
   * Calculates the checksum of {@code localFile}.
   *
   * <p>The checksum algorithm is murmur3_128 now.
   */
  public String calculateChecksum(Path localFile) throws MobileHarnessException {
    return checksumUtil.fingerprint(localFile);
  }

  /** Return all the objects names beginning with this prefix. */
  public List<String> listFiles(String prefix) throws MobileHarnessException {
    return listFiles(prefix, "");
  }

  /**
   * Return the objects names beginning with this prefix in directory-like mode. Items will contain
   * only objects whose names, aside from the prefix, do not contain delimiter. Objects whose names,
   * aside from the prefix, contain delimiter will have their name, truncated after the delimiter,
   * returned in prefixes. Duplicate prefixes are omitted.
   *
   * @param prefix name of the objects' prefix
   * @param delimiter name of the objects' delimiter
   */
  public List<String> listFiles(String prefix, String delimiter) throws MobileHarnessException {
    return listFiles(prefix, delimiter, false);
  }

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
  public List<String> listFiles(String prefix, String delimiter, boolean recursively)
      throws MobileHarnessException {
    List<String> files = new ArrayList<>();
    logger.atInfo().log(
        "List files with prefix(%s), delimiter(%s), recursively(%s)",
        prefix, delimiter, recursively);
    try {
      Storage.Objects.List listObjects = client.objects().list(storageParams.bucketName);
      if (!Strings.isNullOrEmpty(prefix)) {
        listObjects.setPrefix(prefix);
      }
      if (!Strings.isNullOrEmpty(delimiter)) {
        listObjects.setDelimiter(delimiter);
      }
      Objects objects;
      do {
        objects = listObjects.execute();
        List<String> prefixes = objects.getPrefixes();
        if (prefixes != null) {
          for (String subPrefix : prefixes) {
            if (recursively) {
              files.addAll(listFiles(subPrefix, delimiter, recursively));
            } else {
              files.add(subPrefix);
            }
          }
        }
        List<StorageObject> items = objects.getItems();
        if (items != null) {
          for (StorageObject object : items) {
            files.add(object.getName());
          }
        }
        listObjects.setPageToken(objects.getNextPageToken());
      } while (objects.getNextPageToken() != null);
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_LIST_FILES_ERROR, String.format("Failed to list file %s", prefix), e);
    }
    return files;
  }

  /**
   * Copies {@code localFile} to Google Cloud Storage file {@code gcsFile}.
   *
   * @param localFile path of source file
   * @param gcsFile name of the object in the cloud
   * @param contentType content type of the object in the cloud
   * @throws MobileHarnessException if failed to copy file
   */
  public void copyFileToCloud(String localFile, String gcsFile, String contentType)
      throws MobileHarnessException, InterruptedException {
    copyFileToCloud(Path.of(localFile), Path.of(gcsFile), contentType);
  }

  /**
   * Copies {@code localFile} to Google Cloud Storage file {@code gcsFile}.
   *
   * @param localFile path of source file
   * @param gcsFile name of the object in the cloud
   * @param contentType content type of the object in the cloud
   * @throws MobileHarnessException if failed to copy file
   */
  public void copyFileToCloud(Path localFile, Path gcsFile, String contentType)
      throws MobileHarnessException, InterruptedException {
    String fileInfo =
        String.format("local %s to gs://%s/%s", localFile, storageParams.bucketName, gcsFile);
    logger.atInfo().log("Uploading %s", fileInfo);
    retryIfMeetQuotaOrNetworkIssue(
        () -> {
          StorageObject metadata =
              new StorageObject().setName(gcsFile.toString()).setContentType(contentType);

          InputStreamContent contentStream =
              new InputStreamContent(contentType, getInputStream(localFile));
          contentStream.setLength(getFileSize(localFile));
          copyContentStreamToCloud(contentStream, metadata);
          return null;
        },
        "copy " + fileInfo);
    logger.atInfo().log("Uploaded %s", fileInfo);
  }

  /**
   * Copies {@code [from, from + size)} in {@code localFile} to {@code gcsFile}.
   *
   * @param localFile local file to copy
   * @param from byte offset to start
   * @param size byte size of copy
   * @param gcsFile GCS file to copy to
   */
  public void partialCopyFileToCloud(Path localFile, long from, long size, Path gcsFile)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessExceptions.check(
        from >= 0, BasicErrorId.GCS_ILLEGAL_OFFSET, () -> "from %s must not be negative.");
    MobileHarnessExceptions.check(
        size > 0, BasicErrorId.GCS_ILLEGAL_SIZE, () -> "size %s must be positive.");

    String fileInfo =
        String.format(
            "%s [%s, %s) to gs://%s/%s",
            localFile, from, from + size, storageParams.bucketName, gcsFile);
    logger.atInfo().log("Uploading %s", fileInfo);
    retryIfMeetQuotaOrNetworkIssue(
        () -> {
          StorageObject metadata = new StorageObject();
          metadata.setName(gcsFile.toString());

          try (InputStream in = getInputStream(localFile)) {
            if (from > 0) {
              long skipped = in.skip(from);
              if (skipped != from) {
                throw new MobileHarnessException(
                    BasicErrorId.GCS_UPLOAD_FILE_ERROR,
                    String.format(
                        "Failed to upload %s : Expected skip %s, but skipped %s",
                        fileInfo, from, skipped));
              }
            }

            InputStreamContent contentStream = new InputStreamContent("text/plain", in);
            // There is a regression if not specify the length or use -1 (its default value), the
            // entire upload speed is slow down dramatically.
            contentStream.setLength(size);
            copyContentStreamToCloud(contentStream, metadata);
            return null;
          } catch (IOException e) {
            throw new MobileHarnessException(
                BasicErrorId.GCS_UPLOAD_FILE_ERROR, "Failed to upload " + fileInfo, e);
          }
        },
        "upload " + fileInfo);
    logger.atInfo().log("Uploaded %s", fileInfo);
  }

  // TODO: Make it private.
  /**
   * Copies content from {@code contentStream} to cloud as an object with default ACL.
   *
   * @param contentStream the content to upload
   * @param objectMetadata metaData of the object in the cloud, including the name of object
   * @throws MobileHarnessException if fails to copy file.
   */
  public void copyContentStreamToCloud(
      InputStreamContent contentStream, StorageObject objectMetadata)
      throws MobileHarnessException {
    try {
      client.objects().insert(storageParams.bucketName, objectMetadata, contentStream).execute();
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_UPLOAD_FILE_ERROR,
          "Cannot upload the content stream to Google Cloud Storage object "
              + objectMetadata.getName(),
          e);
    }
  }

  /** Composes gcs files in list {@code srcGcsFiles} to {@code dstGcsFile}. */
  public void compose(
      Path dstGcsFile, boolean removeSources, List<Path> srcGcsFiles, String contentType)
      throws MobileHarnessException, InterruptedException {
    try {
      String actionInfo =
          String.format("Composes gcs file %s from source files: %s", dstGcsFile, srcGcsFiles);
      retryIfMeetQuotaOrNetworkIssue(
          () -> {
            try {
              StorageObject metadata =
                  new StorageObject().setName(dstGcsFile.toString()).setContentType(contentType);

              ComposeRequest request =
                  new ComposeRequest()
                      .setDestination(metadata)
                      .setSourceObjects(
                          srcGcsFiles.stream()
                              .map(s -> new SourceObjects().setName(s.toString()))
                              .collect(toImmutableList()));
              client
                  .objects()
                  .compose(storageParams.bucketName, dstGcsFile.toString(), request)
                  .execute();
              return null;
            } catch (IOException e) {
              throw new MobileHarnessException(
                  BasicErrorId.GCS_UPLOAD_FILE_ERROR,
                  String.format(
                      "Failed to compose GCS file %s to gs://%s/%s",
                      srcGcsFiles, storageParams.bucketName, dstGcsFile),
                  e);
            }
          },
          actionInfo);
    } finally {
      if (removeSources) {
        for (Path src : srcGcsFiles) {
          try {
            deleteCloudFile(src.toString());
          } catch (MobileHarnessException e) {
            logger.atWarning().withCause(e).log("Failed to remove compose file: %s", src);
          }
        }
      }
    }
  }

  /**
   * Copies {@code localFile} to Google Cloud Storage {@code gcsFile} in shards with size {@code
   * shardSize} in parallel.
   *
   * @param localFile path of source file
   * @param gcsFile name of the object in the cloud
   * @throws MobileHarnessException if failed to copy file
   */
  public void copyFileToCloudInParallel(
      Path localFile, Path gcsFile, long shardSize, String contentType)
      throws MobileHarnessException, InterruptedException {
    long fileSize = getFileSize(localFile);
    if (fileSize < shardSize) {
      copyFileToCloud(localFile, gcsFile, contentType);
      return;
    }

    int shardCount = (int) Math.ceil(fileSize * 1.0 / shardSize);
    if (shardCount > MAX_SHARD_COUNT) {
      if (fileSize / MAX_SHARD_COUNT < (2000 << 20)) {
        // If the file size is smaller than 2000MB * 32, will automatically change the shard size to
        // reduce the failure rate.
        shardSize = (int) Math.ceil(fileSize * 1.0 / MAX_SHARD_COUNT);
        logger.atWarning().log(
            "Shard count %s is larger than requirements, try to increase the shard size to %s",
            shardCount, shardSize);
        shardCount = (int) Math.ceil(fileSize * 1.0 / shardSize);
      } else {
        throw new MobileHarnessException(
            BasicErrorId.GCS_UPLOAD_FILE_ERROR,
            String.format(
                "File size %s it too large, please try to reduce the file size", fileSize));
      }
    }
    logger.atInfo().log(
        "Uploading local %s to gs://%s/%s in %s shards,",
        localFile, storageParams.bucketName, gcsFile, shardCount);
    String gcsShardNamePrefix =
        String.format(".%s.%s", gcsFile.getFileName(), Long.toUnsignedString(random.nextLong()));

    List<ListenableFuture<?>> results = new ArrayList<>();
    List<Path> gcsShards = new ArrayList<>();
    for (int i = 0; i < shardCount; i++) {
      long from = (long) i * shardSize;
      long size = i < shardCount - 1 ? shardSize : fileSize - from;
      Path gcsShard = gcsFile.resolveSibling(String.format("%s.%s", gcsShardNamePrefix, i));

      gcsShards.add(gcsShard);

      results.add(
          Holder.threadpool.submit(
              () -> {
                partialCopyFileToCloud(localFile, from, size, gcsShard);
                return null;
              }));
    }

    ListenableFuture<?> future =
        Futures.whenAllSucceed(results).call(() -> null, Holder.threadpool);
    try {
      future.get();

      compose(gcsFile, true, gcsShards, contentType);
      logger.atInfo().log(
          "Uploaded local %s to gs://%s/%s in %s shards,",
          localFile, storageParams.bucketName, gcsFile, shardCount);
    } catch (InterruptedException e) {
      throw e;
    } catch (Throwable e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_UPLOAD_FILE_ERROR,
          String.format(
              "Failed to upload local %s to GCS file gs://%s/%s in parallel",
              localFile, storageParams.bucketName, gcsFile),
          e);
    }
  }

  /**
   * Copies file {@code localFile} to Google Cloud Storage as {@code gcsFile} if {@code gcsFile}
   * doesn't exist or is dying (age >= ttl).
   *
   * @return whether copy is executed
   */
  @SuppressWarnings("GoodTime") // TODO: fix GoodTime violation
  public boolean copyFileToCloudIfNonExistingOrDead(
      Path localFile, Path gcsFile, Duration ttl, String contentType)
      throws MobileHarnessException, InterruptedException {
    Optional<Duration> age = getAge(gcsFile);
    if (age.isPresent() && age.get().compareTo(ttl) < 0) {
      logger.atInfo().log(
          "Skip copying file %s to Google Cloud Storage %s, because the gcs file "
              + "age [%s] is shorter than ttl [%s]",
          localFile, gcsFile, age.get(), ttl);
      return false;
    }
    copyFileToCloud(localFile, gcsFile, contentType);
    return true;
  }

  /**
   * Copies file {@code localFile} to Google Cloud Storage as {@code gcsFile} if {@code gcsFile}
   * doesn't exist or is dying (age >= ttl).
   *
   * @return whether copy is executed
   */
  public boolean copyFileToCloudInParallelIfNonExistingOrDead(
      Path localFile, Path gcsFile, Duration ttl, long shardSize, String contentType)
      throws MobileHarnessException, InterruptedException {
    Optional<Duration> age = getAge(gcsFile);
    if (age.isPresent() && age.get().compareTo(ttl) < 0) {
      logger.atInfo().log(
          "Skip copying file %s to Google Cloud Storage %s, because the gcs file "
              + "age [%s] is shorter than ttl [%s]",
          localFile, gcsFile, age.get(), ttl);
      return false;
    }
    copyFileToCloudInParallel(localFile, gcsFile, shardSize, contentType);
    return true;
  }

  /**
   * Gets ages of {@code gcsFile}.
   *
   * @return age of file {@code file}; or empty if {@code file} doesn't exist
   */
  public Optional<Duration> getAge(Path gcsFile)
      throws MobileHarnessException, InterruptedException {
    return getMetadata(GcsApiObject.create(gcsFile))
        .map(
            m ->
                Duration.between(
                    Instant.ofEpochMilli(m.getTimeCreated().getValue()), Instant.now()));
  }

  /**
   * Gets metadata of {@code gcsFile}.
   *
   * @return metadata of file {@code gcsFile}; or empty if {@code file} doesn't exit
   */
  private Optional<StorageObject> getMetadata(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException {
    String actionInfo = "get metadata of Google Cloud Storage File: " + gcsFile;
    return retryIfMeetQuotaOrNetworkIssue(
        () -> {
          try {
            Storage.Objects.Get getRequest =
                client.objects().get(storageParams.bucketName, gcsFile.path().toString());

            if (gcsFile.generationNumber().isPresent()) {
              getRequest.setGeneration(gcsFile.generationNumber().get());
            }

            return Optional.ofNullable(getRequest.execute());
          } catch (IOException e) {
            if (isObjectNoFound(e)) {
              return Optional.empty();
            }
            throw new MobileHarnessException(
                BasicErrorId.GCS_GET_METADATA_ERROR, "Failed to " + actionInfo, e);
          }
        },
        actionInfo);
  }

  /**
   * Runs {@code func} with retry if it failed on Quota or Network issue.
   *
   * <p>{@code https://cloud.google.com/storage/docs/request-rate#ramp-up}
   *
   * <p>{@code https://cloud.google.com/storage/docs/exponential-backoff}
   *
   * @param m method to run
   * @param actionInfo action information for logging
   * @return attempts that is taken to run {@code func}
   */
  @CanIgnoreReturnValue
  private <R> R retryIfMeetQuotaOrNetworkIssue(GcsMethod<R> m, String actionInfo)
      throws MobileHarnessException, InterruptedException {
    return retryIfMeetQuotaOrNetworkIssue(m, actionInfo, Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  <R> R retryIfMeetQuotaOrNetworkIssue(GcsMethod<R> m, String actionInfo, Sleeper sleeper)
      throws MobileHarnessException, InterruptedException {
    ArrayList<String> exceptions = new ArrayList<>();
    int sleepSecond = 1;
    MobileHarnessException lastException = null;
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      try {
        R response = m.call();
        if (i > 0) {
          logger.atInfo().log("Finish to %s in %d attempts", actionInfo, i + 1);
        }
        return response;
      } catch (MobileHarnessException e) {
        if (causedByQuotaIssue(e)) {
          logger.atWarning().log("Failed on quota issue, will retry: %s", actionInfo);
          lastException = e;
          exceptions.add(
              String.format(
                  "attempt #%s [%s]: %s",
                  i + 1, currentTime(), Throwables.getStackTraceAsString(e)));
        } else if (causedByNetworkIssue(e)) {
          logger.atWarning().log("Failed on network issue, will retry: %s", actionInfo);
          lastException = e;
          exceptions.add(
              String.format(
                  "attempt #%s [%s]: %s",
                  i + 1, currentTime(), Throwables.getStackTraceAsString(e)));
        } else {
          throw e;
        }
      }

      // https://cloud.google.com/storage/docs/exponential-backoff
      sleeper.sleep(Duration.ofMillis(sleepSecond * 1000 + random.nextInt(10)));
      sleepSecond *= 2;
    }
    if (lastException != null && causedByQuotaIssue(lastException)) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_MEET_QUOTA_ISSUE,
          String.format(
              "Failed in %s attempts. Exceptions from all tries:\n%s",
              MAX_ATTEMPTS, String.join(",", exceptions)));
    } else {
      throw new MobileHarnessException(
          BasicErrorId.GCS_MEET_NETWORK_ISSUE,
          String.format(
              "Failed in %s attempts. Exceptions from all tries:\n%s",
              MAX_ATTEMPTS, String.join(",", exceptions)));
    }
  }

  @VisibleForTesting
  interface GcsMethod<R> {
    R call() throws MobileHarnessException;
  }

  /** Returns true if {@code e} is failed because of object is no found. */
  private static boolean isObjectNoFound(IOException e) {
    Optional<Integer> errorCode = getGcsServerErrorCode(e);
    return errorCode.isPresent() && errorCode.get().equals(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
  }

  /**
   * Returns true if {@code e} is caused by GCS quota issue.
   *
   * <p>{@code https://cloud.google.com/storage/docs/request-rate#ramp-up}
   */
  private static boolean causedByQuotaIssue(MobileHarnessException e) {
    if (!(e.getCause() instanceof IOException)) {
      return false;
    }
    IOException cause = (IOException) e.getCause();
    Optional<Integer> errorCode = getGcsServerErrorCode(cause);
    if (errorCode.isPresent()) {
      if ((errorCode.get() / 100) == 5 || errorCode.get() == 429) {
        return true;
      }
    }
    return false;
  }

  /** Returns true if {@code e} is caused by GCS network issue. */
  private static boolean causedByNetworkIssue(MobileHarnessException e) {
    if (!(e.getCause() instanceof IOException)) {
      return false;
    }
    IOException cause = (IOException) e.getCause();
    String errorMessage = Strings.nullToEmpty(cause.getMessage());
    return (cause instanceof SocketTimeoutException)
        || (cause instanceof UnknownHostException)
        || (cause instanceof SocketException) /* b/140559689 */
        || (cause instanceof SSLException) /* b/113663972 */
        || errorMessage.contains("Remote host closed connection during handshake") /* b/111562372 */
        || errorMessage.contains("Error writing request body to server") /* b/111561615 */
        || errorMessage.contains("Connection closed prematurely") /* b/123259718 */;
  }

  /** Gets error code if {@code e} is a valid GCS server exception; otherwise returns empty. */
  private static Optional<Integer> getGcsServerErrorCode(IOException e) {
    if (!(e instanceof GoogleJsonResponseException)) {
      if (e instanceof HttpResponseException) {
        return Optional.of(((HttpResponseException) e).getStatusCode());
      }
      return Optional.empty();
    }
    @Nullable GoogleJsonError details = ((GoogleJsonResponseException) e).getDetails();
    if (details == null) {
      // For the http error code like 5XX, the details maybe null, use status code instead.
      return Optional.of(((GoogleJsonResponseException) e).getStatusCode());
    }
    return Optional.of(details.getCode());
  }

  /**
   * Deletes a file on the cloud.
   *
   * @param targetFilePath the name of GCS object to delete
   * @throws MobileHarnessException if fails to delete the file
   */
  public void deleteCloudFile(String targetFilePath) throws MobileHarnessException {
    try {
      client.objects().delete(storageParams.bucketName, targetFilePath).execute();
    } catch (IOException e) {
      throw new MobileHarnessException(
          BasicErrorId.GCS_DELETE_FILE_ERROR,
          String.format(
              "Failed to delete the file %s (bucket: %s) on GCS.",
              targetFilePath, storageParams.bucketName),
          e);
    }
  }

  /**
   * Checks if an object named {@code gcsFile} exists. This method is faster than fileOrDirExist(),
   * especially when there is a large amount of objects in the bucket. Because Google Cloud Storage
   * doesn't have native directory support, it won't have the performance of a native filesystem
   * listing deeply nested sub-directories.
   */
  public boolean fileExist(Path gcsFile) throws MobileHarnessException, InterruptedException {
    return fileExist(GcsApiObject.create(gcsFile));
  }

  /**
   * Checks if an object named {@code gcsFile} exists. This method is faster than fileOrDirExist(),
   * especially when there is a large amount of objects in the bucket. Because Google Cloud Storage
   * doesn't have native directory support, it won't have the performance of a native filesystem
   * listing deeply nested sub-directories.
   */
  public boolean fileExist(GcsApiObject gcsFile)
      throws MobileHarnessException, InterruptedException {
    return getMetadata(gcsFile).isPresent();
  }

  private static BufferedOutputStream getOutputStream(Path localFile) throws IOException {
    return getOutputStreamFromLocalFile(localFile);
  }

  private static BufferedOutputStream getOutputStreamFromLocalFile(Path localFile)
      throws IOException {
    return new BufferedOutputStream(new FileOutputStream(localFile.toFile()));
  }

  private InputStream getInputStream(Path localFile) throws MobileHarnessException {
    return localFileUtil.newInputStream(localFile);
  }

  long getFileSize(Path localFile) throws MobileHarnessException {
    return localFileUtil.getFileSize(localFile);
  }

  private Instant currentTime() {
    return currentInstant();
  }

  private Instant currentInstant() {
    return Clock.systemUTC().instant();
  }
}
