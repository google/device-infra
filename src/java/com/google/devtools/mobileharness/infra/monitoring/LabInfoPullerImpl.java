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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceProperties;
import com.google.devtools.mobileharness.api.model.proto.Lab.HostProperty;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQuery.Filter;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitoredRecordProto.Attribute;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitoredRecordProto.MonitoredEntry;
import com.google.devtools.mobileharness.infra.monitoring.proto.MonitoredRecordProto.MonitoredRecord;
import com.google.devtools.mobileharness.shared.labinfo.LabInfoProvider;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Pulls lab info from LabInfoProvider. */
public final class LabInfoPullerImpl implements DataPuller<MonitoredRecord> {

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
  public ImmutableList<MonitoredRecord> pull() throws MobileHarnessException {
    ImmutableList.Builder<MonitoredRecord> builder = ImmutableList.builder();
    for (LabData labData :
        labInfoProvider.getLabInfos(Filter.getDefaultInstance()).getLabDataList()) {
      MonitoredRecord.Builder record =
          MonitoredRecord.newBuilder().setTimestamp(TimeUtils.toProtoTimestamp(Instant.now()));
      List<HostProperty> hostPropertyList =
          labData.getLabInfo().getLabServerFeature().getHostProperties().getHostPropertyList();
      MonitoredEntry.Builder hostEntry =
          MonitoredEntry.newBuilder()
              .putIdentifier("host_name", labData.getLabInfo().getLabLocator().getHostName())
              .putIdentifier("host_ip", labData.getLabInfo().getLabLocator().getIp());
      addAttribute(
          hostEntry, "status", Optional.of(labData.getLabInfo().getLabStatus().toString()));
      addAttribute(hostEntry, "github_version", getProperty(hostPropertyList, "github_version"));
      addAttribute(hostEntry, "total_mem", getProperty(hostPropertyList, "total_mem"));
      record.setHostEntry(hostEntry.build());
      for (DeviceInfo deviceInfo : labData.getDeviceList().getDeviceInfoList()) {
        DeviceCompositeDimension compositeDimension =
            deviceInfo.getDeviceFeature().getCompositeDimension();
        ImmutableList<DeviceDimension> dimensionList =
            ImmutableList.<DeviceDimension>builder()
                .addAll(compositeDimension.getSupportedDimensionList())
                .addAll(compositeDimension.getRequiredDimensionList())
                .build();
        DeviceProperties deviceProperties = deviceInfo.getDeviceFeature().getProperties();
        MonitoredEntry.Builder deviceEntry =
            MonitoredEntry.newBuilder()
                .putIdentifier("device_id", deviceInfo.getDeviceLocator().getId());

        addAttribute(deviceEntry, "status", Optional.of(deviceInfo.getDeviceStatus().toString()));
        addAttribute(deviceEntry, "device_type", deviceInfo.getDeviceFeature().getTypeList());
        addAttribute(deviceEntry, "driver", deviceInfo.getDeviceFeature().getDriverList());
        addAttribute(deviceEntry, "decorator", deviceInfo.getDeviceFeature().getDecoratorList());
        addAttribute(
            deviceEntry,
            "version",
            Stream.of(
                    getDimension(dimensionList, "sdk_version"),
                    getDimension(dimensionList, "software_version"))
                .flatMap(Optional::stream)
                .findFirst());
        dimensionList.forEach(
            dimension ->
                addAttribute(deviceEntry, dimension.getName(), Optional.of(dimension.getValue())));
        deviceProperties
            .getPropertyList()
            .forEach(
                property ->
                    addAttribute(
                        deviceEntry, property.getName(), Optional.of(property.getValue())));

        record.addDeviceEntry(deviceEntry.build());
      }
      builder.add(record.build());
    }
    return builder.build();
  }

  private void addAttribute(
      MonitoredEntry.Builder builder, String attributeName, Optional<String> attributeValue) {
    if (attributeValue.isPresent() && !attributeValue.get().isEmpty()) {
      addAttribute(builder, attributeName, ImmutableList.of(attributeValue.get()));
    }
  }

  private void addAttribute(
      MonitoredEntry.Builder builder, String attributeName, List<String> attributeValues) {
    for (String attributeValue : attributeValues) {
      builder.addAttribute(Attribute.newBuilder().setName(attributeName).setValue(attributeValue));
    }
  }

  private Optional<String> getDimension(List<DeviceDimension> dimensions, String name) {
    return dimensions.stream()
        .filter(dimension -> dimension.getName().equals(name))
        .findAny()
        .map(DeviceDimension::getValue);
  }

  private Optional<String> getProperty(List<HostProperty> properties, String key) {
    return properties.stream()
        .filter(property -> Ascii.equalsIgnoreCase(property.getKey(), key))
        .findAny()
        .map(HostProperty::getValue);
  }
}
