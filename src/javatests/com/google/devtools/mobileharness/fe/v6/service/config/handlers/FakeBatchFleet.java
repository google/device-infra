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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Device.DeviceConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Lab.LabConfig;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Lab.LabLocator;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Mask.DeviceInfoMask;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.IamPermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetAllDeviceDimensionsResponse;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Serves the batch reads of {@code BatchDeviceConfigHelper} (device lookup by UUID, batch device
 * config read and per-host lab config read) from an in-memory fleet on mocked providers.
 *
 * <p>Lab info queries without a device UUID filter (fleet-wide queries) return the response set by
 * {@link #setFleetQueryResponse}.
 */
public final class FakeBatchFleet {

  private final LabInfoProvider labInfoProvider;
  private final ConfigurationProvider configurationProvider;
  private final IamPermissionChecker iamPermissionChecker;
  private final UniverseScope universe;

  private record LabDeviceEntry(String deviceId, String hostName) {}

  private final List<LabDeviceEntry> labDevices = new ArrayList<>();
  private final Map<String, DeviceConfig> configByDevice = new HashMap<>();
  private final Map<String, LabConfig> labConfigByHost = new HashMap<>();
  private final Set<String> failingLabConfigHosts = new HashSet<>();
  private GetLabInfoResponse fleetQueryResponse = GetLabInfoResponse.getDefaultInstance();
  private GetAllDeviceDimensionsResponse allDeviceDimensionsResponse =
      GetAllDeviceDimensionsResponse.getDefaultInstance();
  private boolean labInfoFails;
  private boolean deviceConfigsFail;

  public FakeBatchFleet(
      LabInfoProvider labInfoProvider,
      ConfigurationProvider configurationProvider,
      IamPermissionChecker iamPermissionChecker,
      UniverseScope universe) {
    this.labInfoProvider = labInfoProvider;
    this.configurationProvider = configurationProvider;
    this.iamPermissionChecker = iamPermissionChecker;
    this.universe = universe;
  }

  /** Routes the provider calls of {@code BatchDeviceConfigHelper} to this fleet. */
  @CanIgnoreReturnValue
  public FakeBatchFleet install() {
    when(labInfoProvider.getLabInfoAsync(any(), eq(universe)))
        .thenAnswer(invocation -> labInfo(invocation.getArgument(0)));
    when(labInfoProvider.getAllDeviceDimensionsAsync(any(), eq(universe)))
        .thenAnswer(invocation -> allDeviceDimensions());
    when(configurationProvider.getDeviceConfigs(anyList(), eq(universe)))
        .thenAnswer(invocation -> deviceConfigs(invocation.getArgument(0)));
    when(configurationProvider.getLabConfig(anyString(), eq(universe)))
        .thenAnswer(invocation -> labConfig(invocation.getArgument(0)));
    return this;
  }

  /** Adds a device on {@code hostName}; an empty {@code config} means no stored DeviceConfig. */
  @CanIgnoreReturnValue
  public FakeBatchFleet addDevice(String deviceId, String hostName, Optional<DeviceConfig> config) {
    labDevices.add(new LabDeviceEntry(deviceId, hostName));
    config.ifPresent(c -> configByDevice.put(deviceId, c));
    return this;
  }

  @CanIgnoreReturnValue
  public FakeBatchFleet setLabConfig(String hostName, LabConfig labConfig) {
    labConfigByHost.put(hostName, labConfig);
    return this;
  }

  /** Sets the response of lab info queries that do not look devices up by UUID. */
  @CanIgnoreReturnValue
  public FakeBatchFleet setFleetQueryResponse(GetLabInfoResponse response) {
    fleetQueryResponse = response;
    return this;
  }

  /** Sets the response of {@code GetAllDeviceDimensions} queries. */
  @CanIgnoreReturnValue
  public FakeBatchFleet setAllDeviceDimensionsResponse(GetAllDeviceDimensionsResponse response) {
    allDeviceDimensionsResponse = response;
    return this;
  }

  @CanIgnoreReturnValue
  public FakeBatchFleet failLabConfig(String hostName) {
    failingLabConfigHosts.add(hostName);
    return this;
  }

  @CanIgnoreReturnValue
  public FakeBatchFleet failLabInfo() {
    labInfoFails = true;
    return this;
  }

  @CanIgnoreReturnValue
  public FakeBatchFleet failDeviceConfigs() {
    deviceConfigsFail = true;
    return this;
  }

  /** Stubs the Cloud IAM decision for {@code deviceId}. */
  @CanIgnoreReturnValue
  public FakeBatchFleet allowConfig(String deviceId, boolean allowed) {
    when(iamPermissionChecker.canConfigDevice(eq(deviceId), any(DeviceConfig.class), eq(universe)))
        .thenReturn(immediateFuture(allowed));
    return this;
  }

  @CanIgnoreReturnValue
  public FakeBatchFleet failPermissionCheck(String deviceId) {
    when(iamPermissionChecker.canConfigDevice(eq(deviceId), any(DeviceConfig.class), eq(universe)))
        .thenReturn(immediateFailedFuture(new IllegalStateException("IAM failed")));
    return this;
  }

  private ListenableFuture<GetAllDeviceDimensionsResponse> allDeviceDimensions() {
    if (labInfoFails) {
      return immediateFailedFuture(new IllegalStateException("GetAllDeviceDimensions failed"));
    }
    return immediateFuture(allDeviceDimensionsResponse);
  }

  private ListenableFuture<GetLabInfoResponse> labInfo(GetLabInfoRequest request) {
    if (labInfoFails) {
      return immediateFailedFuture(new IllegalStateException("GetLabInfo failed"));
    }
    if (request == null
        || request.getLabQuery().getFilter().getDeviceFilter().getDeviceMatchConditionCount()
            == 0) {
      return immediateFuture(fleetQueryResponse);
    }
    DeviceMatchCondition matchCondition =
        request.getLabQuery().getFilter().getDeviceFilter().getDeviceMatchCondition(0);
    if (matchCondition.hasDimensionMatchCondition()) {
      String dimName = matchCondition.getDimensionMatchCondition().getCondition().getKey();
      return immediateFuture(
          filterFleetQueryByDimension(
              dimName, request.getLabQuery().getMask().getDeviceInfoMask()));
    }
    List<String> requestedIds =
        matchCondition.getDeviceUuidMatchCondition().getCondition().getInclude().getExpectedList();
    Set<String> requestedSet = new HashSet<>(requestedIds);
    DeviceList.Builder devices = DeviceList.newBuilder();
    labDevices.stream()
        .filter(entry -> requestedSet.contains(entry.deviceId()))
        .forEach(
            entry ->
                devices.addDeviceInfo(
                    DeviceInfo.newBuilder()
                        .setDeviceLocator(
                            DeviceLocator.newBuilder()
                                .setId(entry.deviceId())
                                .setLabLocator(
                                    LabLocator.newBuilder().setHostName(entry.hostName())))));
    return immediateFuture(
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setDeviceView(
                        DeviceView.newBuilder()
                            .setGroupedDevices(GroupedDevices.newBuilder().setDeviceList(devices))))
            .build());
  }

  private GetLabInfoResponse filterFleetQueryByDimension(
      String dimName, DeviceInfoMask deviceInfoMask) {
    if (!fleetQueryResponse.getLabQueryResult().hasLabView()) {
      return fleetQueryResponse;
    }
    boolean maskPresent = deviceInfoMask.hasFieldMask();
    Set<String> maskPaths = new HashSet<>(deviceInfoMask.getFieldMask().getPathsList());
    LabView.Builder filteredLabView = LabView.newBuilder();
    for (LabData labData : fleetQueryResponse.getLabQueryResult().getLabView().getLabDataList()) {
      ImmutableList<DeviceInfo> matchingDevices =
          labData.getDeviceList().getDeviceInfoList().stream()
              .filter(device -> hasDimensionKey(device, dimName))
              .map(device -> applyDimensionListMask(device, maskPresent, maskPaths))
              .collect(toImmutableList());
      if (!matchingDevices.isEmpty()) {
        filteredLabView.addLabData(
            labData.toBuilder()
                .setDeviceList(DeviceList.newBuilder().addAllDeviceInfo(matchingDevices)));
      }
    }
    return GetLabInfoResponse.newBuilder()
        .setLabQueryResult(LabQueryResult.newBuilder().setLabView(filteredLabView))
        .build();
  }

  private static DeviceInfo applyDimensionListMask(
      DeviceInfo device, boolean maskPresent, Set<String> maskPaths) {
    if (!maskPresent || !device.hasDeviceFeature()) {
      return device;
    }
    DeviceCompositeDimension.Builder composite =
        device.getDeviceFeature().getCompositeDimension().toBuilder();
    if (!maskPaths.contains("device_feature.composite_dimension.supported_dimension")) {
      composite.clearSupportedDimension();
    }
    if (!maskPaths.contains("device_feature.composite_dimension.required_dimension")) {
      composite.clearRequiredDimension();
    }
    return device.toBuilder()
        .setDeviceFeature(device.getDeviceFeature().toBuilder().setCompositeDimension(composite))
        .build();
  }

  private static boolean hasDimensionKey(DeviceInfo deviceInfo, String dimName) {
    DeviceCompositeDimension composite = deviceInfo.getDeviceFeature().getCompositeDimension();
    return Stream.concat(
            composite.getSupportedDimensionList().stream(),
            composite.getRequiredDimensionList().stream())
        .anyMatch(
            dim -> Ascii.equalsIgnoreCase(dim.getName(), dimName) && !dim.getValue().isEmpty());
  }

  private ListenableFuture<List<DeviceConfig>> deviceConfigs(List<String> deviceIds) {
    if (deviceConfigsFail) {
      return immediateFailedFuture(new IllegalStateException("GetDeviceConfigs failed"));
    }
    if (deviceIds == null) {
      return immediateFuture(ImmutableList.of());
    }
    return immediateFuture(
        deviceIds.stream()
            .filter(configByDevice::containsKey)
            .map(configByDevice::get)
            .collect(toImmutableList()));
  }

  private ListenableFuture<ConfigResult<LabConfig>> labConfig(String hostName) {
    if (hostName != null && failingLabConfigHosts.contains(hostName)) {
      return immediateFailedFuture(new IllegalStateException("GetLabConfig failed"));
    }
    return immediateFuture(
        ConfigResult.available(Optional.ofNullable(labConfigByHost.get(hostName))));
  }
}
