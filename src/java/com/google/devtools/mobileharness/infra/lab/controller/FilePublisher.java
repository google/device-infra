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

package com.google.devtools.mobileharness.infra.lab.controller;

import com.google.common.annotations.VisibleForTesting;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.constant.DirCommon;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import java.net.URI;
import java.net.URISyntaxException;

/** Utility for providing metadata of test generated data for clients to download. */
public class FilePublisher {

  private final NetUtil netUtil;

  public FilePublisher() {
    this(new NetUtil());
  }

  @VisibleForTesting
  FilePublisher(NetUtil netUtil) {
    this.netUtil = netUtil;
  }

  /**
   * Gets the downloading URL for a specific file or dir.
   *
   * @param filePath The absolute path of file or dir to download.
   * @return the downloading URL
   */
  public String getFileUrl(String filePath) throws MobileHarnessException {
    if (!filePath.startsWith(DirCommon.getPublicDirRoot())) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_FILE_PUBLISHER_INVALID_FILE_PATH,
          "Can not create URI for " + filePath + ". It is not under " + "public directory.");
    }
    try {
      // Lab IP can change. Needs to detect real-time IP.
      String ip = netUtil.getLocalHostIp();
      String relativePath = "/" + PathUtil.makeRelative(DirCommon.getPublicDirRoot(), filePath);
      int port = 80;
      URI uri = new URI("http", null, ip, port, relativePath, null, null);
      return uri.toString();
    } catch (MobileHarnessException | URISyntaxException | NumberFormatException e) {
      // Thrown out when error occurs during constructing URI.
      throw new MobileHarnessException(
          InfraErrorId.LAB_FILE_PUBLISHER_CREATING_URI_ERROR,
          "Get error when creating URI for the gen files",
          e);
    }
  }

  /**
   * Encodes a file path to quote illegal URI characters in it.
   *
   * <p>For example, it returns "some_dir/foo%23foo.txt" for "some_dir/foo#foo.txt".
   *
   * @param filePath the file path to encode
   * @return the encoded file path, or an empty string if the file path is {@code null}.
   * @see <a href="https://docs.oracle.com/javase/8/docs/api/java/net/URI.html">java.net.URI</a>
   */
  public String encodeFilePath(String filePath) throws MobileHarnessException {
    try {
      // Hack to make file paths like 127.0.0.1:22/test work. The problem is that if URI parser meet
      // ":" before "/", it will assume that everything before ":" is the scheme, which is not true.
      // This hack adds "/" in front of the string in such case and removes it after the encoding.
      int colonIndex = filePath.indexOf(':');
      boolean hackNeeded = colonIndex != -1 && colonIndex < filePath.indexOf('/');
      if (hackNeeded) {
        filePath = "/" + filePath;
      }
      String escapedPath = new URI(/* scheme= */ null, filePath, /* fragment= */ null).toString();
      if (hackNeeded) {
        escapedPath = escapedPath.substring(1);
      }
      return escapedPath;
    } catch (URISyntaxException e) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_FILE_PUBLISHER_ENCODING_ERROR,
          String.format("Failed to encode file path %s", filePath),
          e);
    }
  }
}
