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

import com.google.api.services.storage.model.StorageObject;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.annotations.GuiceAnnotations.GCSCredential;
import com.google.devtools.deviceaction.common.annotations.GuiceAnnotations.GenFileDirRoot;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.utils.GCSUtil.ListResult;
import com.google.devtools.deviceaction.framework.proto.FileSpec;
import com.google.devtools.deviceaction.framework.proto.GCSFile;
import com.google.devtools.deviceaction.framework.proto.ResourcePath;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** A {@link SimpleResolver} to resolve GCS objects. */
class GCSResolver extends SimpleResolver {

  private static final int MAX_RESULTS_UNLIMITED = -1;

  private final LocalFileUtil localFileUtil;
  private final File serviceAccountKey;
  private final File rootDir;

  private final LoadingCache<String, GCSUtil> storageCache;

  @Inject
  @SuppressWarnings("UnnecessarilyVisible")
  public GCSResolver(
      LocalFileUtil localFileUtil,
      @GCSCredential File serviceAccountKey,
      @GenFileDirRoot File rootDir) {
    this(
        localFileUtil,
        serviceAccountKey,
        rootDir,
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, GCSUtil>() {
                  @Override
                  public GCSUtil load(String project) throws DeviceActionException {
                    return new GCSUtil(project, serviceAccountKey);
                  }
                }));
  }

  @VisibleForTesting
  GCSResolver(
      LocalFileUtil localFileUtil,
      File serviceAccountKey,
      File rootDir,
      LoadingCache<String, GCSUtil> storageCache) {
    this.localFileUtil = localFileUtil;
    this.serviceAccountKey = serviceAccountKey;
    this.rootDir = rootDir;
    this.storageCache = storageCache;
  }

  /** Only applies to GCS file specs. */
  @Override
  boolean appliesTo(FileSpec fileSpec) {
    return fileSpec.hasGcsFile();
  }

  /** Resolves GCS items to local. */
  @Override
  File resolveFile(FileSpec fileSpec) throws DeviceActionException {
    GCSFile gcsFile = fileSpec.getGcsFile();
    GCSUtil gCSUtil;
    try {
      gCSUtil = storageCache.get(gcsFile.getProject());
    } catch (ExecutionException e) {
      throw new DeviceActionException(
          "STORAGE_CLIENT_ERROR",
          ErrorType.DEPENDENCY_ISSUE,
          "Failed to get the storage for client " + gcsFile.getProject(),
          e);
    }

    String[] components = getComponents(gcsFile.getGsUri());
    String bucketName = components[0];
    String objectName = components[1];
    ResourcePath resource =
        ResourcePath.newBuilder()
            .setPath(objectName)
            .setIsDirectory(gCSUtil.isDirectory(bucketName, objectName))
            .build();
    downloadToLocalRecursively(gCSUtil, bucketName, resource);
    return getResolvedPath(resource).toFile();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (other instanceof GCSResolver) {
      GCSResolver that = (GCSResolver) other;
      return Objects.equals(this.localFileUtil, that.localFileUtil)
          && Objects.equals(this.serviceAccountKey, that.serviceAccountKey)
          && Objects.equals(this.rootDir, that.rootDir);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(localFileUtil, serviceAccountKey, rootDir);
  }

  /**
   * Divides a GCS uri into components.
   *
   * <p>The first component is the bucket name, the second component is the relative path.
   *
   * @param uri starts with gs://
   * @return an array of components.
   */
  private static String[] getComponents(String uri) {
    return uri.substring(Constants.GS_PREFIX.length()).split(Constants.GS_DELIMITER, 2);
  }

  private void downloadToLocalRecursively(GCSUtil gCSUtil, String bucketName, ResourcePath resource)
      throws DeviceActionException {
    Path dest = getResolvedPath(resource);
    if (!resource.getIsDirectory()) {
      prepareFile(rootDir, resource.getPath());
      gCSUtil.copyFileItemToLocal(bucketName, resource.getPath(), dest);
      return;
    }
    try {
      localFileUtil.prepareDir(dest);
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to create dir %s", dest);
    }
    for (ResourcePath child : listChildren(gCSUtil, bucketName, resource)) {
      downloadToLocalRecursively(gCSUtil, bucketName, child);
    }
  }

  private Path getResolvedPath(ResourcePath resource) {
    // Assumes the local file system also uses '/' as the delimiter.
    return rootDir.toPath().resolve(resource.getPath());
  }

  private void prepareFile(File rootDir, String relativePath) throws DeviceActionException {
    try {
      localFileUtil.prepareParentDir(rootDir.toPath().resolve(relativePath));
    } catch (MobileHarnessException e) {
      throw new DeviceActionException(e, "Failed to create dir %s", relativePath);
    }
  }

  private ImmutableSet<ResourcePath> listChildren(
      GCSUtil gCSUtil, String bucketName, ResourcePath resource) throws DeviceActionException {
    ImmutableSet.Builder<ResourcePath> builder = ImmutableSet.builder();
    ListResult result =
        gCSUtil.listItemsAndPrefixes(
            bucketName,
            resource.getPath(),
            Constants.GS_DELIMITER,
            /* includeTrailingDelimiter= */ true,
            /* maxResults= */ MAX_RESULTS_UNLIMITED);
    for (String prefix : result.listPrefixes()) {
      builder.add(ResourcePath.newBuilder().setPath(prefix).setIsDirectory(true).build());
    }
    for (StorageObject storageObject : result.listItems()) {
      builder.add(
          ResourcePath.newBuilder()
              .setPath(storageObject.getName())
              .setIsDirectory(GCSUtil.isDirectoryPath(storageObject.getName()))
              .build());
    }
    return builder.build();
  }
}
