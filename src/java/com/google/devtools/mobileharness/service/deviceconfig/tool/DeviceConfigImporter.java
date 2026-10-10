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

package com.google.devtools.mobileharness.service.deviceconfig.tool;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceLocatorConfigPair;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.LabDevice.LabDeviceConfig;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.Annotation.DeviceConfigGrpcStub;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.grpc.DeviceConfigGrpcStubModule;
import com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.flags.core.FlagsManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tool to import device configs from a textproto file to the config service. */
public final class DeviceConfigImporter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static void main(String[] args) throws Exception {
    FlagsManager.parse(args);

    String filePath = Flags.labDeviceConfigFile.getNonNull();
    if (filePath.isEmpty()) {
      System.err.println("Error: --lab_device_config is required.");
      System.exit(1);
    }

    String target = Flags.configServiceGrpcTarget.getNonNull();

    logger.atInfo().log("Reading config file: %s", filePath);
    String content = Files.readString(Path.of(filePath));

    logger.atInfo().log("Parsing config file...");
    LabDeviceConfig labDeviceConfig = ProtoTextFormat.parse(content, LabDeviceConfig.class);

    logger.atInfo().log("Connecting to Config Service at: %s", target);
    Injector injector = Guice.createInjector(new DeviceConfigGrpcStubModule());
    DeviceConfigStub stub =
        injector.getInstance(Key.get(DeviceConfigStub.class, DeviceConfigGrpcStub.class));

    new DeviceConfigImporter().importConfig(stub, labDeviceConfig);
  }

  @VisibleForTesting
  void importConfig(DeviceConfigStub stub, LabDeviceConfig labDeviceConfig) throws Exception {
    if (labDeviceConfig.hasLabConfig()) {
      var labConfig = labDeviceConfig.getLabConfig();
      logger.atInfo().log("Found LabConfig for host: %s", labConfig.getHostName());
      logger.atInfo().log("Updating LabConfig...");
      stub.updateLabConfig(
          UpdateLabConfigRequest.newBuilder()
              .setLabConfig(labConfig)
              .setClient("importer")
              .build());
      logger.atInfo().log("LabConfig updated.");
    }

    var deviceConfigs = labDeviceConfig.getDeviceConfigList();
    if (!deviceConfigs.isEmpty()) {
      logger.atInfo().log("Found %d DeviceConfigs.", deviceConfigs.size());
      ImmutableList<DeviceLocatorConfigPair> pairs =
          deviceConfigs.stream()
              .map(
                  c ->
                      DeviceLocatorConfigPair.newBuilder()
                          .setDeviceLocator(
                              DeviceLocator.newBuilder().setDeviceUuid(c.getUuid()).build())
                          .setDeviceConfig(c)
                          .build())
              .collect(toImmutableList());

      logger.atInfo().log("Updating DeviceConfigs...");
      stub.updateDeviceConfigs(
          UpdateDeviceConfigsRequest.newBuilder()
              .setClient("importer")
              .addAllDeviceLocatorConfig(pairs)
              .build());
      logger.atInfo().log("DeviceConfigs updated.");
    }

    logger.atInfo().log("Done.");
  }

  @VisibleForTesting
  DeviceConfigImporter() {}
}
