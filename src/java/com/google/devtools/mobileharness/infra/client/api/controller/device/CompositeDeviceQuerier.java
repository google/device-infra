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

package com.google.devtools.mobileharness.infra.client.api.controller.device;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier.LabQueryResult;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabInfo;
import com.google.wireless.qa.mobileharness.shared.proto.DeviceQuery.DeviceFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;

/** The composite device querier which can query devices from multiple sources. */
public class CompositeDeviceQuerier implements DeviceQuerier {

  private final List<DeviceQuerier> deviceQuerierList;
  private final Executor executor;

  public CompositeDeviceQuerier(List<DeviceQuerier> deviceQuerierList) {
    this(deviceQuerierList, MoreExecutors.directExecutor());
  }

  public CompositeDeviceQuerier(List<DeviceQuerier> deviceQuerierList, Executor executor) {
    this.deviceQuerierList = deviceQuerierList;
    this.executor = executor;
  }

  @Override
  public List<LabInfo> getDeviceInfos(@Nullable DeviceFilter deviceFilter)
      throws MobileHarnessException, InterruptedException {
    List<LabInfo> labInfos = new ArrayList<>();
    for (DeviceQuerier deviceQuerier : deviceQuerierList) {
      labInfos.addAll(deviceQuerier.getDeviceInfos(deviceFilter));
    }
    return labInfos;
  }

  @Override
  public DeviceQueryResult queryDevice(DeviceQueryFilter deviceQueryFilter)
      throws MobileHarnessException, InterruptedException {
    List<DeviceQueryResult> deviceQueryResults = new ArrayList<>();

    for (DeviceQuerier deviceQuerier : deviceQuerierList) {
      deviceQueryResults.add(deviceQuerier.queryDevice(deviceQueryFilter));
    }
    return mergeDeviceQueryResults(deviceQueryResults);
  }

  @Override
  public ListenableFuture<DeviceQueryResult> queryDeviceAsync(DeviceQueryFilter deviceQueryFilter)
      throws MobileHarnessException, InterruptedException {
    List<ListenableFuture<DeviceQueryResult>> deviceQueryResultListenableFutures =
        new ArrayList<>();
    for (DeviceQuerier deviceQuerier : deviceQuerierList) {
      deviceQueryResultListenableFutures.add(deviceQuerier.queryDeviceAsync(deviceQueryFilter));
    }
    return Futures.whenAllComplete(deviceQueryResultListenableFutures)
        .call(
            () -> {
              List<DeviceQueryResult> deviceQueryResults = new ArrayList<>();
              for (ListenableFuture<DeviceQueryResult> deviceQueryResultListenableFuture :
                  deviceQueryResultListenableFutures) {
                deviceQueryResults.add(deviceQueryResultListenableFuture.get());
              }
              return mergeDeviceQueryResults(deviceQueryResults);
            },
            executor);
  }

  @Override
  public List<LabQueryResult> queryDevicesByLab(DeviceQueryFilter deviceQueryFilter)
      throws MobileHarnessException, InterruptedException {
    List<LabQueryResult> results = new ArrayList<>();
    for (DeviceQuerier querier : deviceQuerierList) {
      results.addAll(querier.queryDevicesByLab(deviceQueryFilter));
    }
    return results;
  }

  private DeviceQueryResult mergeDeviceQueryResults(List<DeviceQueryResult> deviceQueryResults) {
    DeviceQueryResult.Builder mergedDeviceQueryResult = DeviceQueryResult.newBuilder();
    deviceQueryResults.forEach(
        deviceQueryResult ->
            mergedDeviceQueryResult.addAllDeviceInfo(deviceQueryResult.getDeviceInfoList()));
    return mergedDeviceQueryResult.build();
  }
}
