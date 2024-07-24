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

package com.google.devtools.mobileharness.infra.controller.device.config;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.LabDevice.LabDeviceConfig;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.service.deviceconfig.util.generator.DeviceConfigGenerator;
import com.google.devtools.mobileharness.service.deviceconfig.util.generator.LabConfigGenerator;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.proto.Config;
import com.google.wireless.qa.mobileharness.shared.util.NetUtil;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Processes api.config file. */
public class ApiConfigFileProcessor {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Util for reading/writing config from/to protobuf text format files. */
  protected final LocalFileUtil fileUtil;

  private final NetUtil netUtil;

  public ApiConfigFileProcessor() {
    this(new LocalFileUtil(), new NetUtil());
  }

  @VisibleForTesting
  ApiConfigFileProcessor(LocalFileUtil fileUtil, NetUtil netUtil) {
    this.fileUtil = fileUtil;
    this.netUtil = netUtil;
  }

  public Optional<LabDeviceConfig> readConfigFile() throws MobileHarnessException {
    String apiConfigPath = Flags.instance().apiConfigFile.getNonNull();
    String labDeviceConfigPath = Flags.instance().labDeviceConfigFile.getNonNull();
    if (!labDeviceConfigPath.isEmpty()) {
      return readConfigFileInternal(labDeviceConfigPath, /* isLabDeviceConfig= */ true);
    } else if (!apiConfigPath.isEmpty()) {
      return readConfigFileInternal(apiConfigPath, /* isLabDeviceConfig= */ false);
    } else {
      logger.atInfo().atMostEvery(10, MINUTES).log(
          "Not load api config file because both api_config and lab_device_config flag are empty.");
      return Optional.empty();
    }
  }

  @VisibleForTesting
  Optional<LabDeviceConfig> readConfigFileInternal(String path, boolean isLabDeviceConfig)
      throws MobileHarnessException {
    Optional<String> content = readConfigFileContent(path);
    if (content.isEmpty()) {
      return Optional.empty();
    }
    // Parses proto.
    LabDeviceConfig labDeviceConfig;
    if (isLabDeviceConfig) {
      labDeviceConfig = ApiConfigProtoUtil.fromLabDeviceConfigText(content.get());
    } else {
      Config.ApiConfig apiConfig = ApiConfigProtoUtil.fromApiConfigText(content.get());
      labDeviceConfig = convertApiConfigToLabDeviceConfig(apiConfig);
    }
    List<String> errors = ApiConfigProtoUtil.verifyConfig(labDeviceConfig);
    if (!errors.isEmpty()) {
      if (errors.size() == 1) {
        throw new MobileHarnessException(BasicErrorId.API_CONFIG_FILE_READ_ERROR, errors.get(0));
      } else {
        throw new MobileHarnessException(
            BasicErrorId.API_CONFIG_FILE_READ_ERROR,
            errors.size() + " errors in the config file: \n" + Joiner.on(" - ").join(errors));
      }
    }

    return Optional.of(labDeviceConfig);
  }

  private Optional<String> readConfigFileContent(String path) throws MobileHarnessException {
    // Checks file.
    try {
      fileUtil.checkFile(path);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Skip loading config from file %s because it does not exist", path);
      return Optional.empty();
    }

    // Reads file.
    logger.atInfo().log("Loading config from file: %s", path);
    String text = fileUtil.readFile(path).trim();
    if (text.isEmpty()) {
      logger.atWarning().log("Config from file %s is empty", path);
      return Optional.empty();
    }
    logger.atInfo().log("Config file content:\n%s", text);
    return Optional.of(text);
  }

  private LabDeviceConfig convertApiConfigToLabDeviceConfig(Config.ApiConfig apiConfig)
      throws MobileHarnessException {
    String hostName = netUtil.getLocalHostName();
    LabConfig labConfig = LabConfigGenerator.fromApiConfig(hostName, apiConfig);
    logger.atFine().log("New LabConfig: %s", labConfig);
    ImmutableList<Device.DeviceConfig> deviceConfigs =
        apiConfig.getDeviceConfigList().stream()
            .map(oldDeviceConfig -> convertOldDeviceConfig(labConfig, oldDeviceConfig))
            .collect(toImmutableList());
    logger.atFine().log(
        "New DeviceConfigs: %s",
        deviceConfigs.stream()
            .map(deviceConfig -> String.format("%s: %s", deviceConfig.getUuid(), deviceConfig))
            .collect(Collectors.joining(", ")));
    return LabDeviceConfig.newBuilder()
        .setLabConfig(labConfig)
        .addAllDeviceConfig(deviceConfigs)
        .build();
  }

  private static Device.DeviceConfig convertOldDeviceConfig(
      LabConfig labConfig, Config.DeviceConfig oldDeviceConfig) {
    BasicDeviceConfig defaultDeviceConfig = labConfig.getDefaultDeviceConfig();
    return DeviceConfigGenerator.fromOldDeviceConfig(
        oldDeviceConfig,
        oldDeviceConfig.getId(),
        defaultDeviceConfig.hasMaxConsecutiveTest()
            ? defaultDeviceConfig.getMaxConsecutiveTest().getValue()
            : null,
        defaultDeviceConfig.hasMaxConsecutiveFail()
            ? defaultDeviceConfig.getMaxConsecutiveFail().getValue()
            : null);
  }
}
