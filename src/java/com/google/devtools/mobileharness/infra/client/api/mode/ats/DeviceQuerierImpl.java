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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.getUnchecked;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.base.Ascii;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.api.mode.local.DeviceInfoFilter;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class DeviceQuerierImpl implements DeviceQuerier {

  private final RemoteDeviceManager remoteDeviceManager;
  private final ListeningExecutorService threadPool;

  @Inject
  DeviceQuerierImpl(RemoteDeviceManager remoteDeviceManager, ListeningExecutorService threadPool) {
    this.remoteDeviceManager = remoteDeviceManager;
    this.threadPool = threadPool;
  }

  @Override
  public DeviceQueryResult queryDevice(DeviceQueryFilter deviceQueryFilter)
      throws InterruptedException {
    getUnchecked(remoteDeviceManager.getFirstDeviceOrTimeoutFuture());
    return DeviceQueryResult.newBuilder()
        .addAllDeviceInfo(
            remoteDeviceManager.getDeviceInfos().stream()
                .filter(new DeviceInfoFilter(deviceQueryFilter))
                .collect(toImmutableList()))
        .build();
  }

  @Override
  public ListenableFuture<DeviceQueryResult> queryDeviceAsync(DeviceQueryFilter deviceQueryFilter)
      throws MobileHarnessException, InterruptedException {
    return threadPool.submit(
        threadRenaming(() -> queryDevice(deviceQueryFilter), () -> "device-querier"));
  }

  @Override
  public List<LabQueryResult> queryDevicesByLab(DeviceQueryFilter deviceQueryFilter)
      throws MobileHarnessException, InterruptedException {
    return remoteDeviceManager.getDeviceInfos().stream()
        .filter(new DeviceInfoFilter(deviceQueryFilter))
        .collect(groupingBy(deviceInfo -> getDimensionValue(deviceInfo, Name.HOST_NAME).orElse("")))
        .entrySet()
        .stream()
        .map(
            entry ->
                LabQueryResult.create(
                    entry.getKey(),
                    entry.getValue().stream()
                        .flatMap(deviceInfo -> getDimensionValue(deviceInfo, Name.HOST_IP).stream())
                        .findFirst()
                        .orElse(""),
                    DeviceQueryResult.newBuilder().addAllDeviceInfo(entry.getValue()).build()))
        .collect(toImmutableList());
  }

  private static Optional<String> getDimensionValue(DeviceInfo deviceInfo, Name dimensionName) {
    return deviceInfo.getDimensionList().stream()
        .filter(dimension -> dimension.getName().equals(Ascii.toLowerCase(dimensionName.name())))
        .map(Dimension::getValue)
        .findFirst();
  }
}
