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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.DeviceFilter.DeviceMatchCondition.DeviceUuidMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.FilterProto.StringMatchCondition;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.concurrent.Executor;

/** Helper for looking up device information. */
public final class DeviceInfoLookupHelper {

  private DeviceInfoLookupHelper() {}

  /**
   * Looks up the {@link DeviceInfo} for a single device ID.
   *
   * @return a future that resolves to the DeviceInfo
   * @throws RuntimeException if the device is not found during future transformation
   */
  public static ListenableFuture<DeviceInfo> lookUpDeviceInfoAsync(
      LabInfoProvider labInfoProvider, String deviceId, UniverseScope universe, Executor executor) {
    GetLabInfoRequest labInfoRequest = createSingleDeviceLabInfoRequest(deviceId);
    return Futures.transform(
        labInfoProvider.getLabInfoAsync(labInfoRequest, universe),
        response -> getDeviceInfoFromResponse(response, deviceId, universe),
        executor);
  }

  private static GetLabInfoRequest createSingleDeviceLabInfoRequest(String deviceId) {
    return GetLabInfoRequest.newBuilder()
        .setLabQuery(
            LabQuery.newBuilder()
                .setFilter(
                    LabQuery.Filter.newBuilder()
                        .setDeviceFilter(
                            DeviceFilter.newBuilder()
                                .addDeviceMatchCondition(
                                    DeviceMatchCondition.newBuilder()
                                        .setDeviceUuidMatchCondition(
                                            DeviceUuidMatchCondition.newBuilder()
                                                .setCondition(
                                                    StringMatchCondition.newBuilder()
                                                        .setInclude(
                                                            StringMatchCondition.Include
                                                                .newBuilder()
                                                                .addExpected(deviceId)))))))
                .setDeviceViewRequest(LabQuery.DeviceViewRequest.getDefaultInstance()))
        .build();
  }

  private static DeviceInfo getDeviceInfoFromResponse(
      GetLabInfoResponse response, String deviceId, UniverseScope universe) {
    return response
        .getLabQueryResult()
        .getDeviceView()
        .getGroupedDevices()
        .getDeviceList()
        .getDeviceInfoList()
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Device not found: "
                        + deviceId
                        + " in universe: "
                        // TODO: Add a toString() method to UniverseScope to avoid this
                        // pattern.
                        + (universe instanceof UniverseScope.RoutedUniverse routed
                            ? routed.atsControllerId()
                            : "self")));
  }
}
