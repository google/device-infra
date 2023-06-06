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

package com.google.devtools.deviceaction.common.utils;

import static com.google.devtools.deviceaction.common.utils.Constants.GS_DELIMITER;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.error.ErrorUtils;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A utility class to handle the low-level Google Cloud Storage API.
 *
 * <p>TODO: b/281809849 - Add unit tests for the class.
 */
class GCSUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> SCOPES =
      ImmutableSet.of("https://www.googleapis.com/auth/devstorage.read_only");
  private static final String OUTPUT_NO_SPACE = "No space left on device";

  /**
   * Timeout of connecting to GCS service. Default value is 1 min. See {@link
   * HttpRequest#getConnectTimeout()}.
   */
  private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofMinutes(1);

  /**
   * Timeout of reading from GCS service. Default value is 1 min. See {@link
   * HttpRequest#getReadTimeout()}.
   */
  private static final Duration HTTP_READ_TIMEOUT = Duration.ofMinutes(1);

  /** A value class to store list results. */
  public static class ListResult {
    private long maxRemainingResults;

    private final String itemNamePrefix;
    private final ArrayList<StorageObject> itemsCollector = new ArrayList<>();
    private final ArrayList<String> prefixesCollector = new ArrayList<>();
    // Record the names of added items. We might have duplicate prefixes and items if
    // 'includeTrailingDelimiter' set to be true. We don't add it to the prefix if it is already in
    // addedItemNames.
    private final LinkedHashSet<String> addedItemNames = new LinkedHashSet<>();

    public ListResult(long maxResults, String itemNamePrefix) {
      this.maxRemainingResults = maxResults;
      this.itemNamePrefix = itemNamePrefix;
    }

    /**
     * Lists all storage items.
     *
     * <p>A GCS item might be a file or an explicitly created folder. On the other hand, if an item
     * is uploaded to an uri with delimiter /, the corresponding parent folders are not considered
     * as items.
     */
    public ImmutableList<StorageObject> listItems() {
      return ImmutableList.copyOf(itemsCollector);
    }

    /**
     * Lists all prefixes of items.
     *
     * <p>A prefix is not listed if it represents an item so there is no duplication with {@link
     * ListResult#listItems()}.
     */
    public ImmutableList<String> listPrefixes() {
      return ImmutableList.copyOf(prefixesCollector);
    }

    void onExecute(Objects items) {
      // Add GCS items (if any).
      List<StorageObject> objects = items.getItems();
      logger.atFine().log("The storage objects %s", objects);
      if (objects != null) {
        logger.atInfo().log("listed %s objects", objects.size());
        Iterator<StorageObject> iterator = objects.iterator();
        while (iterator.hasNext() && getMaxRemainingResults() > 0) {
          StorageObject object = iterator.next();
          String objectName = object.getName();
          if (!identicalToPrefix(objectName, itemNamePrefix)) {
            addItem(object);
          }
        }
      }

      // Add prefixes (if any).
      List<String> pagePrefixes = items.getPrefixes();
      logger.atFine().log("The prefixes listed for %s are %s", itemNamePrefix, pagePrefixes);
      if (pagePrefixes != null) {
        logger.atInfo().log("listed %s prefixes", pagePrefixes.size());
        Iterator<String> iterator = pagePrefixes.iterator();
        while (iterator.hasNext() && getMaxRemainingResults() > 0) {
          String prefix = iterator.next();
          if (!addedItemNames.contains(prefix)) {
            addPrefix(prefix);
          }
        }
      }
    }

    long getMaxRemainingResults() {
      if (maxRemainingResults < 0) {
        return Long.MAX_VALUE;
      }
      return maxRemainingResults;
    }

    private void addPrefix(String prefix) {
      prefixesCollector.add(prefix);
      updateMaxRemainingResults();
    }

    private void addItem(StorageObject object) {
      itemsCollector.add(object);
      addedItemNames.add(object.getName());
      updateMaxRemainingResults();
    }

    private void updateMaxRemainingResults() {
      if (maxRemainingResults >= 1) {
        maxRemainingResults--;
      }
    }
  }

  private final Storage storage;

  @VisibleForTesting
  GCSUtil(Storage storage) {
    this.storage = storage;
  }

  public GCSUtil(String project, File serviceAccountKey) throws DeviceActionException {
    this(getStorage(project, serviceAccountKey));
  }

  /**
   * Lists both storage items and prefixes under a given item name prefix.
   *
   * <p>It executes the actual API calls to get paginated lists, accumulating the {@code
   * StorageObject} items and String prefixes into the {@code ListResult}.
   *
   * <p>Note: For GCS, if an item under a bucket has uri d1/d2/o, then both d1/ and d1/d2/ are
   * prefixes. An item might be a file or a folder explicitly created from UI. On the other hand, if
   * an uploaded file is copied to a GCS uri with delimiter /, the parent folders will just be
   * prefixes not items.
   *
   * <p>Although GCS does not implement a file system, it treats items that contain a delimiter as
   * different from other items when listing items. This will be clearer with an example: Consider a
   * bucket with items: o1, d1/, d1/o1, d1/o2, d2/o3 With prefix == null and delimiter == /, we get
   * prefixes d1/, d2/ and storage items o1, d1/. With prefix == null and delimiter == null, we get
   * prefixes d1/, d2/ and items o1, d1/, d1/o1, d1/o2 and d2/o3.
   *
   * <p>Thus, when delimiter is null, the entire key name is considered an opaque string, otherwise
   * only the part up to the first delimiter is considered.
   *
   * @param bucketName bucket name
   * @param itemNamePrefix object name prefix or null if all objects in the bucket are desired
   * @param delimiter delimiter to use (typically "/"), otherwise null
   * @param includeTrailingDelimiter whether to include prefix objects into the {@code ListResult}.
   *     For example, with prefix == d1, we get prefix d1/ if this is true and empty if this is
   *     false.
   * @param maxResults maximum number of results to return (total of both {@code listedObjects} and
   *     {@code listedPrefixes}), unlimited if negative or zero
   */
  public ListResult listItemsAndPrefixes(
      String bucketName,
      @Nullable String itemNamePrefix,
      @Nullable String delimiter,
      boolean includeTrailingDelimiter,
      long maxResults)
      throws DeviceActionException {
    logger.atInfo().log(
        "listItemsAndPrefixes(%s, %s, %s, %s, %d)",
        bucketName, itemNamePrefix, delimiter, includeTrailingDelimiter, maxResults);
    Storage.Objects.List listObject;
    try {
      listObject = storage.objects().list(bucketName);
    } catch (IOException e) {
      if (checkIfFileNotFound(e)) {
        throw new DeviceActionException(
            "BUCKET_NOT_FOUND", ErrorType.CUSTOMER_ISSUE, "Check the bucket name " + bucketName, e);
      } else {
        throw new DeviceActionException(
            "IO_ERROR", ErrorType.DEPENDENCY_ISSUE, "Failed to execute object list.", e);
      }
    }
    // Set delimiter if supplied.
    if (delimiter != null) {
      listObject.setDelimiter(delimiter);
      listObject.setIncludeTrailingDelimiter(includeTrailingDelimiter);
    }
    // Set number of items to retrieve per call.
    if (maxResults > 0) {
      // We add one in case we filter out itemNamePrefix.
      listObject.setMaxResults(maxResults + 1);
    }
    // Set prefix if supplied.
    if (!Strings.isNullOrEmpty(itemNamePrefix)) {
      listObject.setPrefix(itemNamePrefix);
    }

    ListResult result = new ListResult(maxResults, itemNamePrefix);
    String pageToken = null;
    do {
      if (pageToken != null) {
        logger.atInfo().log("listItemsAndPrefixes: next page %s", pageToken);
        listObject.setPageToken(pageToken);
      }
      logger.atInfo().log("listItemsAndPrefixesPage(%s, %d)", listObject, maxResults);

      Objects items;
      try {
        items = listObject.execute();
      } catch (IOException e) {
        if (checkIfFileNotFound(e)) {
          break;
        } else {
          throw new DeviceActionException(
              "IO_ERROR", ErrorType.DEPENDENCY_ISSUE, "Failed to execute object list.", e);
        }
      }
      result.onExecute(items);
      pageToken = items.getNextPageToken();
    } while (pageToken != null && result.getMaxRemainingResults() > 0);
    return result;
  }

  public boolean isDirectory(String bucketName, String itemName) throws DeviceActionException {
    // Make sure item name ends with "/"
    itemName = sanitizeDirectoryPath(itemName);
    Objects objects;
    try {
      objects =
          storage
              .objects()
              .list(bucketName)
              .setPrefix(itemName)
              .setDelimiter(GS_DELIMITER)
              .setMaxResults(1L)
              .execute();
    } catch (IOException e) {
      if (checkIfFileNotFound(e)) {
        return false;
      } else {
        throw new DeviceActionException(
            "IO_ERROR", ErrorType.DEPENDENCY_ISSUE, "Failed to execute object list.", e);
      }
    }
    if (objects.getItems() != null && !objects.getItems().isEmpty()) {
      // Since the item name ends with "/", it is a folder explicitly created from UI.
      return true;
    }
    // This will happen when the folder only contains folders but no objects.
    // objects.getItems() will be empty, but objects.getPrefixes will list
    // sub-folders.
    return objects.getPrefixes() != null && !objects.getPrefixes().isEmpty();
  }

  /** Copies file item to local path {@code localDest}. */
  public void copyFileItemToLocal(String bucketName, String itemName, Path localDest)
      throws DeviceActionException {
    try (BufferedOutputStream bufferedOutputStream =
        new BufferedOutputStream(new FileOutputStream(localDest.toFile()))) {
      Storage.Objects.Get getObject = storage.objects().get(bucketName, itemName);
      getObject.getMediaHttpDownloader().setDirectDownloadEnabled(true);
      getObject.executeMediaAndDownloadTo(bufferedOutputStream);
    } catch (IOException e) {
      if (e.getMessage().contains(OUTPUT_NO_SPACE)) {
        throw new DeviceActionException(
            "OUT_OF_SPACE",
            ErrorType.DEPENDENCY_ISSUE,
            "Please clean the lab machine to make space for GCS file downloading",
            e);
      }
      throw new DeviceActionException(
          "IO_ERROR",
          ErrorType.DEPENDENCY_ISSUE,
          String.format("Fail to copy file gs://%s/%s to %s", bucketName, itemName, localDest),
          e);
    }
  }

  /** Checks if the item name prefix represents a directory. */
  public static boolean isDirectoryPath(@Nullable String itemNamePrefix) {
    return !Strings.isNullOrEmpty(itemNamePrefix) && itemNamePrefix.endsWith(GS_DELIMITER);
  }

  /** Check if the {@code itemName} is actually the original prefix. */
  private static boolean identicalToPrefix(String itemName, @Nullable String itemNamePrefix) {
    return isDirectoryPath(itemNamePrefix) && itemName.equals(itemNamePrefix);
  }

  /** Corrects the item name format so that it represents a directory. */
  private static String sanitizeDirectoryPath(String itemName) {
    // A directory name should end with "/"
    if (!itemName.endsWith(GS_DELIMITER)) {
      itemName += GS_DELIMITER;
    }
    return itemName;
  }

  private boolean checkIfFileNotFound(IOException e) {
    logger.atWarning().withCause(e).log("Got IOException.");
    return ErrorUtils.hasStatusCode(e, HttpStatusCodes.STATUS_CODE_NOT_FOUND);
  }

  private static Storage getStorage(String project, File serviceAccountKey)
      throws DeviceActionException {
    GoogleCredential credential = getCredential(serviceAccountKey);
    try {
      return new Storage.Builder(
              GoogleNetHttpTransport.newTrustedTransport(),
              GsonFactory.getDefaultInstance(),
              credential)
          .setHttpRequestInitializer(
              request -> {
                credential.initialize(request);
                request.setConnectTimeout((int) HTTP_CONNECT_TIMEOUT.toMillis());
                request.setReadTimeout((int) HTTP_READ_TIMEOUT.toMillis());
              })
          .setApplicationName(project)
          .build();
    } catch (GeneralSecurityException e) {
      throw new DeviceActionException(
          "SECURITY_ERROR", ErrorType.DEPENDENCY_ISSUE, "Failed to get credential", e);
    } catch (IOException e) {
      throw new DeviceActionException(
          "IO_ERROR", ErrorType.DEPENDENCY_ISSUE, "Failed to get credential", e);
    }
  }

  private static GoogleCredential getCredential(File serviceAccountKey)
      throws DeviceActionException {
    try {
      return GoogleCredential.fromStream(
              new FileInputStream(serviceAccountKey),
              GoogleNetHttpTransport.newTrustedTransport(),
              GsonFactory.getDefaultInstance())
          .createScoped(SCOPES);
    } catch (GeneralSecurityException e) {
      throw new DeviceActionException(
          "SECURITY_ERROR",
          ErrorType.DEPENDENCY_ISSUE,
          "Invalid credential " + serviceAccountKey,
          e);
    } catch (FileNotFoundException e) {
      throw new DeviceActionException(
          "FILE_NOT_FOUND",
          ErrorType.DEPENDENCY_ISSUE,
          "Credential file not found " + serviceAccountKey,
          e);
    } catch (IOException e) {
      throw new DeviceActionException(
          "IO_ERROR", ErrorType.DEPENDENCY_ISSUE, "Failed to get credential", e);
    }
  }
}
