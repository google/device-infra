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

package com.google.devtools.mobileharness.infra.monitoring;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitorEntryProto.DeviceData;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitorEntryProto.HostData;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitorEntryProto.MonitorEntry;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Pulls lab info from LabInfoProvider. */
public final class LabInfoPullerImpl implements DataPuller<MonitorEntry> {

  private final LabInfoProvider labInfoProvider;

  @Inject
  LabInfoPullerImpl(LabInfoProvider labInfoProvider) {
    this.labInfoProvider = labInfoProvider;
  }

  @Override
  public void setUp() {}

  @Override
  public void tearDown() {}

  @Override
  public ImmutableList<MonitorEntry> pull() {
    ImmutableList.Builder<MonitorEntry> builder = ImmutableList.builder();
    for (LabData labData :
        labInfoProvider.getLabInfos(Filter.getDefaultInstance()).getLabDataList()) {
      MonitorEntry.Builder monitorEntry =
          MonitorEntry.newBuilder()
              .setTimestamp(TimeUtils.toProtoTimestamp(Instant.now()))
              .setHostData(
                  HostData.newBuilder()
                      .setHostName(labData.getLabInfo().getLabLocator().getHostName())
                      .setHostIp(labData.getLabInfo().getLabLocator().getIp()));
      for (DeviceInfo deviceInfo : labData.getDeviceList().getDeviceInfoList()) {
        DeviceCompositeDimension compositeDimension =
            deviceInfo.getDeviceFeature().getCompositeDimension();
        ImmutableList<DeviceDimension> dimensionList =
            ImmutableList.<DeviceDimension>builder()
                .addAll(compositeDimension.getSupportedDimensionList())
                .addAll(compositeDimension.getRequiredDimensionList())
                .build();

        DeviceData.Builder deviceData =
            DeviceData.newBuilder()
                .setId(deviceInfo.getDeviceLocator().getId())
                .setStatus(deviceInfo.getDeviceStatus().toString());

        getDimension(dimensionList, "model").ifPresent(deviceData::setModel);

        Stream.of(
                getDimension(dimensionList, "sdk_version"),
                getDimension(dimensionList, "software_version"))
            .flatMap(Optional::stream)
            .findFirst()
            .ifPresent(deviceData::setVersion);

        getDimension(dimensionList, "hardware").ifPresent(deviceData::setHardware);

        getDimension(dimensionList, "build_type").ifPresent(deviceData::setBuildType);
        monitorEntry.addDeviceData(deviceData.build());
      }
      builder.add(monitorEntry.build());
    }
    return builder.build();
  }

  private Optional<String> getDimension(List<DeviceDimension> dimensions, String name) {
    return dimensions.stream()
        .filter(dimension -> dimension.getName().equals(name))
        .findAny()
        .map(DeviceDimension::getValue);
  }
}
