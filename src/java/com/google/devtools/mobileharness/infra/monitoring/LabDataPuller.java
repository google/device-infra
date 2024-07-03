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
import java.util.List;
import java.util.Optional;

final class LabDataPuller implements DataPuller<MonitorEntry> {

  private final LabInfoProvider labInfoProvider;

  public static LabDataPuller create(LabInfoProvider labInfoProvider) {
    return new LabDataPuller(labInfoProvider);
  }

  LabDataPuller(LabInfoProvider labInfoProvider) {
    this.labInfoProvider = labInfoProvider;
  }

  @Override
  public void setUp() {}

  @Override
  public ImmutableList<MonitorEntry> pull() {
    ImmutableList.Builder<MonitorEntry> builder = ImmutableList.builder();
    for (LabData labData :
        labInfoProvider.getLabInfos(Filter.getDefaultInstance()).getLabDataList()) {
      MonitorEntry.Builder monitorEntry =
          MonitorEntry.newBuilder()
              .setHostData(
                  HostData.newBuilder()
                      .setHostName(labData.getLabInfo().getLabLocator().getHostName())
                      .setHostIp(labData.getLabInfo().getLabLocator().getIp()));
      for (DeviceInfo deviceInfo : labData.getDeviceList().getDeviceInfoList()) {
        DeviceCompositeDimension compositeDimension =
            deviceInfo.getDeviceFeature().getCompositeDimension();
        List<DeviceDimension> dimensionList =
            ImmutableList.<DeviceDimension>builder()
                .addAll(compositeDimension.getSupportedDimensionList())
                .addAll(compositeDimension.getRequiredDimensionList())
                .build();

        DeviceData.Builder deviceData =
            DeviceData.newBuilder()
                .setId(deviceInfo.getDeviceLocator().getId())
                .setStatus(deviceInfo.getDeviceStatus().toString());

        Optional<String> model = getDimension(dimensionList, "model");
        if (!model.isEmpty()) {
          deviceData.setModel(model.get());
        }

        Optional<String> sdkVersion = getDimension(dimensionList, "sdk_version");
        Optional<String> softwareVersion = getDimension(dimensionList, "software_version");
        if (!sdkVersion.isEmpty()) {
          deviceData.setVersion(sdkVersion.get());
        } else if (!softwareVersion.isEmpty()) {
          deviceData.setVersion(softwareVersion.get());
        }

        Optional<String> hardware = getDimension(dimensionList, "hardware");
        if (!hardware.isEmpty()) {
          deviceData.setHardware(hardware.get());
        }

        Optional<String> buildType = getDimension(dimensionList, "build_type");
        if (!buildType.isEmpty()) {
          deviceData.setBuildType(buildType.get());
        }
        monitorEntry.addDeviceData(deviceData.build());
      }
      builder.add(monitorEntry.build());
    }
    return builder.build();
  }

  @Override
  public void tearDown() {}

  private Optional<String> getDimension(List<DeviceDimension> dimensions, String name) {
    return dimensions.stream()
        .filter(dimension -> dimension.getName().equals(name))
        .findAny()
        .map(dimension -> dimension.getValue());
  }
}
