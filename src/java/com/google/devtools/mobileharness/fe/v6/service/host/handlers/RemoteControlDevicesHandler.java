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

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.query.proto.FilterProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.fe.v6.service.host.builder.RemoteControlUrlBuilder;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDeviceConfig;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDevicesRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDevicesResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RemoteControlDevicesResponse.SessionResult;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the RemoteControlDevices RPC. */
@Singleton
public final class RemoteControlDevicesHandler {

  private final LabInfoProvider labInfoProvider;
  private final RemoteControlUrlBuilder remoteControlUrlBuilder;
  private final ListeningExecutorService executor;

  @Inject
  RemoteControlDevicesHandler(
      LabInfoProvider labInfoProvider,
      RemoteControlUrlBuilder remoteControlUrlBuilder,
      ListeningExecutorService executor) {
    this.labInfoProvider = labInfoProvider;
    this.remoteControlUrlBuilder = remoteControlUrlBuilder;
    this.executor = executor;
  }

  /** Starts remote control sessions for the requested devices. */
  public ListenableFuture<RemoteControlDevicesResponse> remoteControlDevices(
      RemoteControlDevicesRequest request, UniverseScope universe) {
    // Create a request to fetch device details from the lab for the given host.
    GetLabInfoRequest getLabInfoRequest = createGetLabInfoRequest(request.getHostName());

    // Asynchronously fetch lab info and then process it to generate session URLs.
    return Futures.transform(
        labInfoProvider.getLabInfoAsync(getLabInfoRequest, universe),
        response -> processLabInfoResponse(response, request),
        executor);
  }

  /** Processes the lab info response and generates remote control URLs for each device. */
  private RemoteControlDevicesResponse processLabInfoResponse(
      GetLabInfoResponse response, RemoteControlDevicesRequest request) {
    // Build a map of device ID to DeviceInfo for quick lookup.
    ImmutableMap<String, DeviceInfo> deviceInfoMap =
        response.getLabQueryResult().getLabView().getLabDataList().stream()
            .flatMap(labData -> labData.getDeviceList().getDeviceInfoList().stream())
            .collect(
                toImmutableMap(
                    deviceInfo -> deviceInfo.getDeviceLocator().getId(),
                    deviceInfo -> deviceInfo,
                    (v1, v2) -> v1));

    return RemoteControlDevicesResponse.newBuilder()
        .addAllSessions(
            request.getDeviceConfigsList().stream()
                .map(config -> generateSessionResult(config, deviceInfoMap, request))
                .collect(toImmutableList()))
        .build();
  }

  /** Helper method to generate a SessionResult for a single device configuration. */
  private SessionResult generateSessionResult(
      RemoteControlDeviceConfig config,
      ImmutableMap<String, DeviceInfo> deviceInfoMap,
      RemoteControlDevicesRequest request) {
    String deviceId = config.getDeviceId();
    DeviceInfo deviceInfo = deviceInfoMap.get(deviceId);

    // 1. Handle missing device info.
    if (deviceInfo == null) {
      return SessionResult.newBuilder()
          .setDeviceId(deviceId)
          .setErrorMessage("Device not found: " + deviceId)
          .build();
    }

    // 2. Delegate URL generation and map the resulting Optional to a SessionResult.
    return remoteControlUrlBuilder
        .generateRemoteControlUrl(deviceInfo, config, request)
        .map(url -> SessionResult.newBuilder().setDeviceId(deviceId).setSessionUrl(url).build())
        .orElseGet(
            () ->
                SessionResult.newBuilder()
                    .setDeviceId(deviceId)
                    .setErrorMessage("Remote control is not supported for device: " + deviceId)
                    .build());
  }

  private static GetLabInfoRequest createGetLabInfoRequest(String hostName) {
    return GetLabInfoRequest.newBuilder()
        .setLabQuery(LabQuery.newBuilder().setFilter(createLabQueryFilter(hostName)))
        .build();
  }

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
