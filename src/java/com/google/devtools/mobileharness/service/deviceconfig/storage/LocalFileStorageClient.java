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

package com.google.devtools.mobileharness.service.deviceconfig.storage;

import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.protobuf.TextFormat;
import java.util.Optional;
import javax.inject.Inject;

/** A {@link StorageClient} that stores configs in local files. */
final class LocalFileStorageClient implements StorageClient {

  // TODO: b/460296020 - change this to a flag.
  private static final String ROOT_DIR = "/tmp/ats/config";

  private final LocalFileUtil localFileUtil;

  @Inject
  LocalFileStorageClient(LocalFileUtil localFileUtil) {
    this.localFileUtil = localFileUtil;
  }

  @Override
  public Optional<DeviceConfig> getDeviceConfig(String deviceUuid) throws MobileHarnessException {
    String path = getDeviceConfigPath(deviceUuid);
    if (!localFileUtil.isFileExist(path)) {
      return Optional.empty();
    }
    try {
      return Optional.of(TextFormat.parse(localFileUtil.readFile(path), DeviceConfig.class));
    } catch (TextFormat.ParseException e) {
      throw new MobileHarnessException(
          InfraErrorId.CONFIG_TEXT_PARSE_ERROR,
          String.format("Failed to parse device config from file %s", path),
          e);
    }
  }

  @Override
  public Optional<LabConfig> getLabConfig(String hostName) throws MobileHarnessException {
    String path = getLabConfigPath(hostName);
    if (!localFileUtil.isFileExist(path)) {
      return Optional.empty();
    }
    try {
      return Optional.of(TextFormat.parse(localFileUtil.readFile(path), LabConfig.class));
    } catch (TextFormat.ParseException e) {
      throw new MobileHarnessException(
          InfraErrorId.CONFIG_TEXT_PARSE_ERROR,
          String.format("Failed to parse lab config from file %s", path),
          e);
    }
  }

  @Override
  public void upsertDeviceConfig(DeviceConfig deviceConfig) throws MobileHarnessException {
    String path = getDeviceConfigPath(deviceConfig.getUuid());
    localFileUtil.writeToFile(path, deviceConfig.toString());
  }

  @Override
  public void upsertLabConfig(LabConfig labConfig) throws MobileHarnessException {
    String path = getLabConfigPath(labConfig.getHostName());
    localFileUtil.writeToFile(path, labConfig.toString());
  }

  @Override
  public void deleteDeviceConfig(String deviceUuid) throws MobileHarnessException {
    String path = getDeviceConfigPath(deviceUuid);
    try {
      localFileUtil.removeFileOrDir(path);
    } catch (InterruptedException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_REMOVE_ERROR,
          String.format("Failed to remove file %s", path),
          e);
    }
  }

  @Override
  public void deleteLabConfig(String hostName) throws MobileHarnessException {
    String path = getLabConfigPath(hostName);
    try {
      localFileUtil.removeFileOrDir(path);
    } catch (InterruptedException e) {
      throw new MobileHarnessException(
          BasicErrorId.LOCAL_FILE_OR_DIR_REMOVE_ERROR,
          String.format("Failed to remove file %s", path),
          e);
    }
  }

  private String getDeviceConfigPath(String deviceUuid) {
    return PathUtil.join(ROOT_DIR, "device", deviceUuid, ".textproto");
  }

  private String getLabConfigPath(String hostName) {
    return PathUtil.join(ROOT_DIR, "lab", hostName, ".textproto");
  }
}
