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

package com.google.devtools.mobileharness.infra.ats.server.util;

import com.google.common.base.Ascii;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.shared.util.file.remote.constant.RemoteFileType;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Utility class for Ats Server Session. */
public class AtsServerSessionUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Matches xTS zip names like "android-cts.zip", "android-sts.zip_" and
  // "android-chunked-cts.zip". Names containing digits or dots before ".zip" (e.g.
  // "android-cts-media-1.5.zip") are intentionally excluded since they are not xTS packages.
  public static final Pattern ANDROID_XTS_ZIP_FILENAME_REGEX =
      Pattern.compile("android-[a-z-]+\\.zip_?");

  private static final String ACLOUD_FILENAME = "acloud_prebuilt";
  private static final String ATS_GOOGLE_CLOUD_STORAGE_PREFIX = "mtt:///google_cloud_storage/";

  private final DeviceQuerier deviceQuerier;
  private final NetworkUtil networkUtil;
  private final LocalFileUtil localFileUtil;

  @Inject
  AtsServerSessionUtil(
      DeviceQuerier deviceQuerier, NetworkUtil networkUtil, LocalFileUtil localFileUtil) {
    this.deviceQuerier = deviceQuerier;
    this.networkUtil = networkUtil;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Checks if the current OLCS instance is running on the same host as the lab.
   *
   * @return true if the current OLCS instance is running on the same host as the lab.
   */
  public boolean isLocalMode() throws InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to query device");
      return false;
    }
    String olcsHostName = "";
    try {
      olcsHostName = networkUtil.getLocalHostName();
    } catch (MobileHarnessException ignored) {
      return false;
    }
    if (olcsHostName.isEmpty()) {
      return false;
    }
    for (DeviceInfo deviceInfo : queryResult.getDeviceInfoList()) {
      String labHostName =
          deviceInfo.getDimensionList().stream()
              .filter(
                  dimension ->
                      dimension
                          .getName()
                          .equals(Ascii.toLowerCase(Dimension.Name.HOST_NAME.name())))
              .findFirst()
              .map(DeviceQuery.Dimension::getValue)
              .orElse("");
      if (!labHostName.equals(olcsHostName)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Converts a local or MTT-prefixed resource path into a remote-runner-accessible URI (e.g. {@code
   * ats-file-server::} or {@code gs://}), staging {@code acloud_prebuilt} into the ATS storage path
   * if needed.
   */
  public String replacePathForRemoteRunner(String path)
      throws MobileHarnessException, InterruptedException {
    if (path.startsWith(RemoteFileType.ATS_FILE_SERVER.prefix())) {
      return path;
    }
    if (path.startsWith(ATS_GOOGLE_CLOUD_STORAGE_PREFIX)) {
      return path.replace(ATS_GOOGLE_CLOUD_STORAGE_PREFIX, RemoteFileType.GCS.prefix());
    }
    if (PathUtil.basename(path).equals(ACLOUD_FILENAME)
        && !path.startsWith(Flags.atsStoragePath.getNonNull())) {
      String acloudPath =
          PathUtil.join(Flags.atsStoragePath.getNonNull(), "mh_resources", ACLOUD_FILENAME);
      if (!localFileUtil.isFileOrDirExist(acloudPath)) {
        localFileUtil.prepareDir(Path.of(acloudPath).getParent().toString());
        localFileUtil.copyFileOrDir(path, acloudPath);
      }
      path = acloudPath;
    }
    return PathUtil.join(
        RemoteFileType.ATS_FILE_SERVER.prefix(),
        PathUtil.makeRelative(Flags.atsStoragePath.getNonNull(), path));
  }

  /** Parses and validates the {@link URL} of the given {@link TestResource}. */
  public static URL getTestResourceUrl(TestResource testResource) throws MobileHarnessException {
    try {
      return URI.create(testResource.getUrl()).toURL();
    } catch (IllegalArgumentException | MalformedURLException e) {
      throw MobileHarnessExceptionFactory.createUserFacingException(
          InfraErrorId.ATS_SERVER_INVALID_TEST_RESOURCE,
          String.format("Failed to parse url from : %s", testResource.getUrl()),
          e);
    }
  }

  /**
   * Attaches additional file-based test resources from {@code testResources} (excluding xTS root
   * zip packages) to the given {@link JobInfo}, rewriting their paths for remote runner resolution.
   */
  public void insertAdditionalTestResource(JobInfo jobInfo, List<TestResource> testResources)
      throws MobileHarnessException, InterruptedException {
    for (TestResource testResource : testResources) {
      URL testResourceUrl = getTestResourceUrl(testResource);
      if (testResourceUrl.getProtocol().equals("file")
          && !ANDROID_XTS_ZIP_FILENAME_REGEX.matcher(testResource.getName()).matches()) {
        logger.atInfo().log(
            "Adding additional test resource: %s %s",
            testResource.getPath(), testResourceUrl.getPath());
        jobInfo
            .files()
            .add(testResource.getName(), replacePathForRemoteRunner(testResourceUrl.getPath()));
      }
    }
  }
}
