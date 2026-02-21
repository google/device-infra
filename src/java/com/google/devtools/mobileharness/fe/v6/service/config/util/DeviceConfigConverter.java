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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.BasicDeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.DetectorSpecs;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.DetectorSpecs.ManekiDetectorSpecs;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.PermissionInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigMode;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceDiscoverySettings;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostPermissions;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostProperty;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ManekiSpec;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.StabilitySettings;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.WifiConfig;
import com.google.protobuf.Int32Value;
import java.util.List;

/** Utility for converting between internal and FE configuration protos. */
public final class DeviceConfigConverter {

  private DeviceConfigConverter() {}

  /** Converts internal {@link BasicDeviceConfig} to FE {@link DeviceConfig}. */
  public static DeviceConfig toFeDeviceConfig(BasicDeviceConfig basicConfig) {
    DeviceConfig.Builder builder = DeviceConfig.newBuilder();

    // Permissions
    builder.setPermissions(
        PermissionInfo.newBuilder()
            .addAllOwners(basicConfig.getOwnerList())
            .addAllExecutors(basicConfig.getExecutorList())
            .build());

    // Wifi
    if (basicConfig.hasDefaultWifi()) {
      com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig internalWifi =
          basicConfig.getDefaultWifi();
      builder.setWifi(
          WifiConfig.newBuilder()
              .setSsid(internalWifi.getSsid())
              .setPsk(internalWifi.getPsk())
              .setScanSsid(internalWifi.getScanSsid())
              .setType(internalWifi.getSsid().isEmpty() ? "none" : "custom")
              .build());
    }

    // Dimensions
    if (basicConfig.hasCompositeDimension()) {
      builder.setDimensions(
          DeviceConfig.ConfigDimensions.newBuilder()
              .addAllSupported(
                  basicConfig.getCompositeDimension().getSupportedDimensionList().stream()
                      .map(
                          d ->
                              com.google.devtools.mobileharness.fe.v6.service.proto.common
                                  .DeviceDimension.newBuilder()
                                  .setName(d.getName())
                                  .setValue(d.getValue())
                                  .build())
                      .collect(toImmutableList()))
              .addAllRequired(
                  basicConfig.getCompositeDimension().getRequiredDimensionList().stream()
                      .map(
                          d ->
                              com.google.devtools.mobileharness.fe.v6.service.proto.common
                                  .DeviceDimension.newBuilder()
                                  .setName(d.getName())
                                  .setValue(d.getValue())
                                  .build())
                      .collect(toImmutableList()))
              .build());
    }

    // Stability Settings
    StabilitySettings.Builder settingsBuilder = StabilitySettings.newBuilder();
    if (basicConfig.hasMaxConsecutiveTest()) {
      settingsBuilder.setMaxConsecutiveTest(basicConfig.getMaxConsecutiveTest().getValue());
    }
    if (basicConfig.hasMaxConsecutiveFail()) {
      settingsBuilder.setMaxConsecutiveFail(basicConfig.getMaxConsecutiveFail().getValue());
    }
    return builder.setSettings(settingsBuilder.build()).build();
  }

  /** Converts FE {@link DeviceConfig} to internal {@link BasicDeviceConfig}. */
  public static BasicDeviceConfig toBasicDeviceConfig(DeviceConfig feConfig) {
    BasicDeviceConfig.Builder builder = BasicDeviceConfig.newBuilder();

    // Permissions
    if (feConfig.hasPermissions()) {
      builder
          .addAllOwner(feConfig.getPermissions().getOwnersList())
          .addAllExecutor(feConfig.getPermissions().getExecutorsList());
    }

    // Wifi
    if (feConfig.hasWifi()) {
      builder.setDefaultWifi(
          com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig.newBuilder()
              .setSsid(feConfig.getWifi().getSsid())
              .setPsk(feConfig.getWifi().getPsk())
              .setScanSsid(feConfig.getWifi().getScanSsid())
              .build());
    }

    // Dimensions
    if (feConfig.hasDimensions()) {
      DeviceCompositeDimension dimension =
          DeviceCompositeDimension.newBuilder()
              .addAllSupportedDimension(
                  feConfig.getDimensions().getSupportedList().stream()
                      .map(
                          d ->
                              DeviceDimension.newBuilder()
                                  .setName(d.getName())
                                  .setValue(d.getValue())
                                  .build())
                      .collect(toImmutableList()))
              .addAllRequiredDimension(
                  feConfig.getDimensions().getRequiredList().stream()
                      .map(
                          d ->
                              DeviceDimension.newBuilder()
                                  .setName(d.getName())
                                  .setValue(d.getValue())
                                  .build())
                      .collect(toImmutableList()))
              .build();
      builder.setCompositeDimension(dimension);
    }

    // Stability Settings
    if (feConfig.hasSettings()) {
      builder
          .setMaxConsecutiveTest(Int32Value.of(feConfig.getSettings().getMaxConsecutiveTest()))
          .setMaxConsecutiveFail(Int32Value.of(feConfig.getSettings().getMaxConsecutiveFail()));
    }

    return builder.build();
  }

  /** Converts internal {@link LabConfig} to FE {@link HostConfig}. */
  public static HostConfig toFeHostConfig(LabConfig labConfig) {
    HostConfig.Builder builder = HostConfig.newBuilder();

    // Permissions
    builder.setPermissions(
        HostPermissions.newBuilder()
            .addAllHostAdmins(labConfig.getDefaultDeviceConfig().getOwnerList())
            .build());

    // Device Config Mode
    DeviceConfigMode mode =
        labConfig.getHostProperties().getHostPropertyList().stream()
                .anyMatch(
                    p -> p.getKey().equals("device_config_mode") && p.getValue().equals("host"))
            ? DeviceConfigMode.SHARED
            : DeviceConfigMode.PER_DEVICE;
    builder.setDeviceConfigMode(mode);

    // Device Config
    if (labConfig.hasDefaultDeviceConfig()) {
      builder.setDeviceConfig(toFeDeviceConfig(labConfig.getDefaultDeviceConfig()));
    }

    // Host Properties
    builder.addAllHostProperties(
        labConfig.getHostProperties().getHostPropertyList().stream()
            .filter(p -> !p.getKey().equals("device_config_mode"))
            .map(p -> HostProperty.newBuilder().setKey(p.getKey()).setValue(p.getValue()).build())
            .collect(toImmutableList()));

    // Device Discovery
    DeviceDiscoverySettings.Builder discoveryBuilder =
        DeviceDiscoverySettings.newBuilder()
            .addAllMonitoredDeviceUuids(labConfig.getMonitoredDeviceUuidList())
            .addAllTestbedUuids(labConfig.getTestbedUuidList())
            .addAllMiscDeviceUuids(labConfig.getMiscDeviceUuidList())
            .addAllOverTcpIps(labConfig.getOverTcpIpList())
            .addAllOverSshDevices(
                labConfig.getOverSshList().stream()
                    .map(
                        d ->
                            DeviceDiscoverySettings.OverSshDevice.newBuilder()
                                .setIpAddress(d.getIpAddress())
                                .setUsername(d.getUsername())
                                .setPassword(d.getPassword())
                                .setSshDeviceType(d.getSshDeviceType())
                                .build())
                    .collect(toImmutableList()));
    if (labConfig.hasDetectorSpecs()) {
      discoveryBuilder.addAllManekiSpecs(toFeManekiSpecs(labConfig.getDetectorSpecs()));
    }
    return builder.setDeviceDiscovery(discoveryBuilder.build()).build();
  }

  private static ImmutableList<ManekiSpec> toFeManekiSpecs(DetectorSpecs detectorSpecs) {
    ImmutableList.Builder<ManekiSpec> feSpecs = ImmutableList.builder();
    if (detectorSpecs.hasManekiDetectorSpecs()) {
      ManekiDetectorSpecs maneki = detectorSpecs.getManekiDetectorSpecs();
      maneki
          .getManekiAndroidDeviceDiscoverySpecList()
          .forEach(
              s ->
                  feSpecs.add(
                      ManekiSpec.newBuilder()
                          .setType("android")
                          .setMacAddress(s.getMacAddress())
                          .build()));
      maneki
          .getManekiRokuDeviceDiscoverySpecList()
          .forEach(
              s ->
                  feSpecs.add(
                      ManekiSpec.newBuilder()
                          .setType("roku")
                          .setMacAddress(s.getMacAddress())
                          .build()));
      maneki
          .getManekiRdkDeviceDiscoverySpecList()
          .forEach(
              s ->
                  feSpecs.add(
                      ManekiSpec.newBuilder()
                          .setType("rdk")
                          .setMacAddress(s.getMacAddress())
                          .build()));
      maneki
          .getManekiRaspberryPiDeviceDiscoverySpecList()
          .forEach(
              s ->
                  feSpecs.add(
                      ManekiSpec.newBuilder()
                          .setType("raspberry_pi")
                          .setMacAddress(s.getMacAddress())
                          .build()));
      maneki
          .getManekiPs4DeviceDiscoverySpecList()
          .forEach(
              s ->
                  feSpecs.add(
                      ManekiSpec.newBuilder()
                          .setType("ps4")
                          .setMacAddress(s.getWinMacAddress())
                          .build()));
      maneki
          .getManekiPs5DeviceDiscoverySpecList()
          .forEach(
              s ->
                  feSpecs.add(
                      ManekiSpec.newBuilder()
                          .setType("ps5")
                          .setMacAddress(s.getWinMacAddress())
                          .build()));
      maneki
          .getManekiGenericDeviceDiscoverySpecList()
          .forEach(
              s ->
                  feSpecs.add(
                      ManekiSpec.newBuilder()
                          .setType("generic")
                          .setMacAddress(s.getMacAddress())
                          .build()));
    }
    return feSpecs.build();
  }

  public static DetectorSpecs toInternalDetectorSpecs(List<ManekiSpec> feSpecs) {
    ManekiDetectorSpecs.Builder manekiBuilder = ManekiDetectorSpecs.newBuilder();
    for (ManekiSpec spec : feSpecs) {
      switch (spec.getType()) {
        case "android" ->
            manekiBuilder.addManekiAndroidDeviceDiscoverySpec(
                DetectorSpecs.ManekiDetectorSpecs.ManekiAndroidDeviceDiscoverySpec.newBuilder()
                    .setMacAddress(spec.getMacAddress())
                    .build());
        case "roku" ->
            manekiBuilder.addManekiRokuDeviceDiscoverySpec(
                DetectorSpecs.ManekiDetectorSpecs.ManekiRokuDeviceDiscoverySpec.newBuilder()
                    .setMacAddress(spec.getMacAddress())
                    .build());
        case "rdk" ->
            manekiBuilder.addManekiRdkDeviceDiscoverySpec(
                DetectorSpecs.ManekiDetectorSpecs.ManekiRdkDeviceDiscoverySpec.newBuilder()
                    .setMacAddress(spec.getMacAddress())
                    .build());
        case "raspberry_pi" ->
            manekiBuilder.addManekiRaspberryPiDeviceDiscoverySpec(
                DetectorSpecs.ManekiDetectorSpecs.ManekiRaspberryPiDeviceDiscoverySpec.newBuilder()
                    .setMacAddress(spec.getMacAddress())
                    .build());
        case "ps4" ->
            manekiBuilder.addManekiPs4DeviceDiscoverySpec(
                DetectorSpecs.ManekiDetectorSpecs.ManekiPs4DeviceDiscoverySpec.newBuilder()
                    .setWinMacAddress(spec.getMacAddress())
                    .build());
        case "ps5" ->
            manekiBuilder.addManekiPs5DeviceDiscoverySpec(
                DetectorSpecs.ManekiDetectorSpecs.ManekiPs5DeviceDiscoverySpec.newBuilder()
                    .setWinMacAddress(spec.getMacAddress())
                    .build());
        case "generic" ->
            manekiBuilder.addManekiGenericDeviceDiscoverySpec(
                DetectorSpecs.ManekiDetectorSpecs.ManekiGenericDeviceDiscoverySpec.newBuilder()
                    .setMacAddress(spec.getMacAddress())
                    .build());
        default -> {
          // Ignore unknown types
        }
      }
    }
    return DetectorSpecs.newBuilder().setManekiDetectorSpecs(manekiBuilder.build()).build();
  }
}
