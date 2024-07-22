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

package com.google.devtools.mobileharness.infra.ats.dda.stub;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.DeviceViewRequest;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStubModule;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import io.grpc.ManagedChannel;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Stub for query lab and device information. */
public class AtsLabInfoStub {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableList<String> REQUIRED_DIMENSIONS =
      ImmutableList.of(
          "id", "brand", "model", "hardware", "sdk_version", "screen_size", "screen_density");

  private static final GetLabInfoRequest GET_ALL_DEVICES_REQ =
      GetLabInfoRequest.newBuilder()
          .setLabQuery(
              LabQuery.newBuilder().setDeviceViewRequest(DeviceViewRequest.getDefaultInstance()))
          .build();

  private final LabInfoGrpcStub labInfoStub;

  public AtsLabInfoStub(ManagedChannel olcServerChannel) {
    this.labInfoStub =
        Guice.createInjector(new LabInfoGrpcStubModule(olcServerChannel))
            .getInstance(LabInfoGrpcStub.class);
  }

  /** Queries devices from the lab grouped by metadata specified in {@link DeviceGroupMetadata}. */
  public Map<DeviceGroupMetadata, List<DeviceInfo>> queryDeviceGroups()
      throws GrpcExceptionWithErrorId {
    GetLabInfoResponse response = labInfoStub.getLabInfo(GET_ALL_DEVICES_REQ);
    DeviceList deviceList =
        response.getLabQueryResult().getDeviceView().getGroupedDevices().getDeviceList();
    return deviceList.getDeviceInfoList().stream()
        .filter(device -> this.checkContainsDimensions(device, REQUIRED_DIMENSIONS))
        .collect(Collectors.groupingBy(this::genMetadataFromDeviceInfo));
  }

  private DeviceGroupMetadata genMetadataFromDeviceInfo(DeviceInfo device) {
    List<DeviceDimension> supportedDimensions =
        device.getDeviceFeature().getCompositeDimension().getSupportedDimensionList();
    String screenSize = findDimensionValue(supportedDimensions, "screen_size");
    List<String> screenSizeParts = Splitter.on('x').splitToList(screenSize);
    int screenX = 0;
    int screenY = 0;
    if (screenSizeParts.size() == 2) {
      screenX = Integer.parseInt(screenSizeParts.get(0));
      screenY = Integer.parseInt(screenSizeParts.get(1));
    }
    // Always set screenY as the larger one.
    if (screenY < screenX) {
      int tmp = screenX;
      screenX = screenY;
      screenY = tmp;
    }

    return DeviceGroupMetadata.builder()
        .setBrand(findDimensionValue(supportedDimensions, "brand"))
        .setModel(findDimensionValue(supportedDimensions, "model"))
        .setHardware(findDimensionValue(supportedDimensions, "hardware"))
        .setSdkVersion(Integer.parseInt(findDimensionValue(supportedDimensions, "sdk_version")))
        .setScreenX(screenX)
        .setScreenY(screenY)
        .setScreenDensity(
            Integer.parseInt(findDimensionValue(supportedDimensions, "screen_density")))
        .build();
  }

  private boolean checkContainsDimensions(DeviceInfo device, List<String> requiredDimensions) {
    List<DeviceDimension> dimensions =
        device.getDeviceFeature().getCompositeDimension().getSupportedDimensionList();
    String deviceId = findDimensionValue(dimensions, "id");
    for (String dimension : requiredDimensions) {
      if (Strings.isNullOrEmpty(findDimensionValue(dimensions, dimension))) {
        logger.atWarning().log(
            "Device %s doesn't contain dimension: %s. Skip this device.", deviceId, dimension);
        return false;
      }
    }
    return true;
  }

  private String findDimensionValue(List<DeviceDimension> dimensions, String name) {
    return dimensions.stream()
        .filter(dimension -> dimension.getName().equals(name))
        .findFirst()
        .orElse(DeviceDimension.getDefaultInstance())
        .getValue();
  }

  /** Metadata of a device to used for grouping devices. */
  @AutoValue
  public abstract static class DeviceGroupMetadata {
    public abstract String brand();

    public abstract String model();

    public abstract String hardware();

    public abstract int sdkVersion();

    public abstract int screenX();

    public abstract int screenY();

    public abstract int screenDensity();

    static Builder builder() {
      return new AutoValue_AtsLabInfoStub_DeviceGroupMetadata.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setBrand(String brand);

      abstract Builder setModel(String model);

      abstract Builder setHardware(String hardware);

      abstract Builder setSdkVersion(int sdkVersion);

      abstract Builder setScreenX(int screenX);

      abstract Builder setScreenY(int screenY);

      abstract Builder setScreenDensity(int screenDensity);

      abstract DeviceGroupMetadata build();
    }
  }
}
