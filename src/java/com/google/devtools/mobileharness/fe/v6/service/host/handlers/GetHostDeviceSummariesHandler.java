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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.FilterProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.HealthAndActivityBuilder;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.HealthAndActivityInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceHealthState;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceSummary;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDeviceSummariesResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetHostDeviceSummaries RPC. */
@Singleton
public final class GetHostDeviceSummariesHandler {

  private static final String DIMENSION_LABEL = "label";
  private static final String DIMENSION_MODEL = "model";
  private static final String DIMENSION_SOFTWARE_VERSION = "software_version";
  private static final String DIMENSION_SDK_VERSION = "sdk_version";

  private final LabInfoProvider labInfoProvider;
  private final HealthAndActivityBuilder healthAndActivityBuilder;
  private final SubDeviceInfoListFactory subDeviceInfoListFactory;
  private final ListeningExecutorService executor;

  @Inject
  GetHostDeviceSummariesHandler(
      LabInfoProvider labInfoProvider,
      HealthAndActivityBuilder healthAndActivityBuilder,
      SubDeviceInfoListFactory subDeviceInfoListFactory,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.healthAndActivityBuilder = healthAndActivityBuilder;
    this.subDeviceInfoListFactory = subDeviceInfoListFactory;
    this.executor = executor;
  }

  /** Gets host device summaries. */
  public ListenableFuture<GetHostDeviceSummariesResponse> getHostDeviceSummaries(
      GetHostDeviceSummariesRequest request) {
    GetLabInfoRequest getLabInfoRequest = createGetLabInfoRequest(request.getHostName());
    return Futures.transform(
        labInfoProvider.getLabInfoAsync(getLabInfoRequest, ""),
        this::processLabInfoResponse,
        executor);
  }

  private GetHostDeviceSummariesResponse processLabInfoResponse(GetLabInfoResponse response) {
    ImmutableList<DeviceSummary> deviceSummaries =
        response.getLabQueryResult().getLabView().getLabDataList().stream()
            .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
            .map(this::createDeviceSummary)
            .collect(toImmutableList());
    return GetHostDeviceSummariesResponse.newBuilder()
        .addAllDeviceSummaries(deviceSummaries)
        .build();
  }

  private DeviceSummary createDeviceSummary(DeviceInfo deviceInfo) {
    HealthAndActivityInfo healthAndActivityInfo =
        healthAndActivityBuilder.buildHealthAndActivityInfo(deviceInfo);

    ImmutableMap<String, String> dimensions =
        Stream.concat(
                deviceInfo
                    .getDeviceFeature()
                    .getCompositeDimension()
                    .getSupportedDimensionList()
                    .stream(),
                deviceInfo
                    .getDeviceFeature()
                    .getCompositeDimension()
                    .getRequiredDimensionList()
                    .stream())
            .collect(
                toImmutableMap(
                    DeviceDimension::getName, DeviceDimension::getValue, (v1, v2) -> v1));

    String requiredDims =
        deviceInfo.getDeviceFeature().getCompositeDimension().getRequiredDimensionList().stream()
            .map(d -> d.getName() + ":" + d.getValue())
            .collect(joining(", "));

    DeviceSummary.Builder deviceSummaryBuilder =
        DeviceSummary.newBuilder()
            .setId(deviceInfo.getDeviceLocator().getId())
            .setHealthState(
                DeviceHealthState.newBuilder()
                    .setHealth(healthAndActivityInfo.getState())
                    .setTitle(healthAndActivityInfo.getTitle())
                    .setTooltip(healthAndActivityInfo.getSubtitle()))
            .addAllTypes(healthAndActivityInfo.getDeviceTypesList())
            .setDeviceStatus(healthAndActivityInfo.getDeviceStatus())
            .setLabel(dimensions.getOrDefault(DIMENSION_LABEL, ""))
            .setRequiredDims(requiredDims)
            .setModel(dimensions.getOrDefault(DIMENSION_MODEL, ""))
            .setVersion(
                dimensions.getOrDefault(
                    DIMENSION_SOFTWARE_VERSION,
                    dimensions.getOrDefault(DIMENSION_SDK_VERSION, "")));

    // If it is a testbed device, decode sub-device information.
    boolean isTestbed = deviceInfo.getDeviceFeature().getTypeList().contains("TestbedDevice");
    if (isTestbed) { // Only attempt to decode sub-devices for testbeds
      deviceSummaryBuilder.addAllSubDevices(subDeviceInfoListFactory.create(dimensions));
    }

    return deviceSummaryBuilder.build();
  }

  /** Creates a {@link GetLabInfoRequest} to filter labs by host name. */
  private static GetLabInfoRequest createGetLabInfoRequest(String hostName) {
    return GetLabInfoRequest.newBuilder()
        .setLabQuery(LabQuery.newBuilder().setFilter(createLabQueryFilter(hostName)))
        .build();
  }

  /** Creates a {@link LabQuery.Filter} to filter labs by host name. */
  private static LabQuery.Filter createLabQueryFilter(String hostName) {
    return LabQuery.Filter.newBuilder()
        .setLabFilter(
            FilterProto.LabFilter.newBuilder()
                .addLabMatchCondition(
                    FilterProto.LabFilter.LabMatchCondition.newBuilder()
                        .setLabHostNameMatchCondition(
                            FilterProto.LabFilter.LabMatchCondition.LabHostNameMatchCondition
                                .newBuilder()
                                .setCondition(
                                    FilterProto.StringMatchCondition.newBuilder()
                                        .setInclude(
                                            FilterProto.StringMatchCondition.Include.newBuilder()
                                                .addExpected(hostName))))))
        .build();
  }
}
