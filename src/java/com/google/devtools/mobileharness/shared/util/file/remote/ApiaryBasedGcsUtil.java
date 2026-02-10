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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.shared.util.file.checksum.ChecksumUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

/**
 * Implementation of {@link GcsUtil} that uses the Google Cloud Storage JSON API (Apiary).
 *
 * <p>This implementation relies on the standard Google API Client Library for Java to communicate
 * with GCS via HTTP/JSON. This is the default implementation for general cloud access and does not
 * require an internal Envelope (Trusted Infrastructure) environment.
 */
public class ApiaryBasedGcsUtil extends GcsUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

  private final Storage client;

  /**
   * Constructs a ApiaryBasedGcsUtil given the related parameters about the storage.
   *
   * @throws MobileHarnessException if failed to set up the client
   */
  public ApiaryBasedGcsUtil(GcsParams storageParams) throws MobileHarnessException {
    super(storageParams);
    this.client = getClient(storageParams);
  }

  @VisibleForTesting
  ApiaryBasedGcsUtil(
      GcsParams storageParams,
      Storage client,
      ChecksumUtil checksumUtil,
      ChecksumUtil crc32cChecksumUtil,
      LocalFileUtil localFileUtil) {
    super(storageParams, checksumUtil, crc32cChecksumUtil, localFileUtil);
    this.client = client;
  }

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

  @Override
  protected void copyFileToLocal(GcsApiObject gcsFile, Path localFile, long from, long size)
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

  @Override
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

  @Override
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

  @Override
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

  @Override
  protected Optional<StorageObject> getMetadata(GcsApiObject gcsFile)
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

  @Override
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

  @Override
  protected boolean isQuotaIssue(MobileHarnessException e) {
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

  @Override
  protected boolean isNetworkIssue(MobileHarnessException e) {
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

  private static boolean isObjectNoFound(IOException e) {
    Optional<Integer> errorCode = getGcsServerErrorCode(e);
    return errorCode.isPresent() && errorCode.get().equals(HttpStatusCodes.STATUS_CODE_NOT_FOUND);
  }

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

  /** For mocking in unit test, because the wrapped method is `final`. */
  @VisibleForTesting
  MediaHttpDownloader getMediaHttpDownloader(Storage.Objects.Get get) {
    return get.getMediaHttpDownloader();
  }
}
