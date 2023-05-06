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

package com.google.devtools.deviceinfra.infra.client.api.mode.local;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.controller.device.DeviceStatusInfo;
import com.google.devtools.mobileharness.infra.controller.device.LocalDeviceManager;
import com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.devtools.mobileharness.shared.util.network.localhost.LocalHost;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.LabLocator;
import com.google.wireless.qa.mobileharness.shared.proto.DeviceQuery.DeviceFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Device querier for retrieving the local device information. */
class LocalDeviceQuerier implements DeviceQuerier {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ListenableFuture<LocalDeviceManager> deviceManagerFuture;

  /** Creates a device querier to retrieve the local device information. */
  public LocalDeviceQuerier(ListenableFuture<LocalDeviceManager> deviceManagerFuture) {
    this.deviceManagerFuture = deviceManagerFuture;
  }

  @Override
  public List<LabInfo> getDeviceInfos(@Nullable DeviceFilter deviceFilter)
      throws MobileHarnessException, InterruptedException {
    LocalDeviceManager deviceManager = getDeviceManager();
    LabInfo labInfo = new LabInfo(LabLocator.LOCALHOST);
    for (Entry<Device, DeviceStatusInfo> entry :
        deviceManager.getAllDeviceStatus(false /* realtimeDetect */).entrySet()) {
      // TODO: Support device filter.
      Device device = entry.getKey();
      DeviceInfo deviceInfo =
          new DeviceInfo(
              new DeviceLocator(device.getDeviceId()),
              entry.getValue().getDeviceStatusWithTimestamp().getStatus());
      deviceInfo.owners().addAll(device.getOwners());
      deviceInfo.types().addAll(device.getDeviceTypes());
      deviceInfo.drivers().addAll(device.getDriverTypes());
      deviceInfo.decorators().addAll(device.getDecoratorTypes());
      deviceInfo.dimensions().supported().addAll(device.getDimensions());
      deviceInfo.dimensions().required().addAll(device.getRequiredDimensions());
      labInfo.devices().add(deviceInfo);
    }
    return ImmutableList.of(labInfo);
  }

  @Override
  public DeviceQueryResult queryDevice(DeviceQueryFilter deviceQueryFilter)
      throws MobileHarnessException, InterruptedException {
    LocalDeviceManager deviceManager = getDeviceManager();
    return DeviceQueryResult.newBuilder()
        .addAllDeviceInfo(
            deviceManager.getAllDeviceStatus(false /* realtimeDetect */).entrySet().stream()
                .map(
                    deviceEntry -> {
                      DeviceQuery.DeviceInfo.Builder builder =
                          DeviceQuery.DeviceInfo.newBuilder()
                              .setId(deviceEntry.getKey().getDeviceId())
                              .setStatus(
                                  Ascii.toLowerCase(
                                      deviceEntry
                                          .getValue()
                                          .getDeviceStatusWithTimestamp()
                                          .getStatus()
                                          .name()))
                              .addAllOwner(deviceEntry.getKey().getOwners())
                              .addAllType(deviceEntry.getKey().getDeviceTypes())
                              .addAllDriver(deviceEntry.getKey().getDriverTypes())
                              .addAllDecorator(deviceEntry.getKey().getDecoratorTypes())
                              .addAllDimension(
                                  StrPairUtil.convertCollectionToMultimap(
                                          deviceEntry.getKey().getDimensions())
                                      .entries()
                                      .stream()
                                      .map(
                                          dimension ->
                                              Dimension.newBuilder()
                                                  .setName(dimension.getKey())
                                                  .setValue(dimension.getValue())
                                                  .setRequired(false)
                                                  .build())
                                      .collect(Collectors.toList()))
                              .addAllDimension(
                                  StrPairUtil.convertCollectionToMultimap(
                                          deviceEntry.getKey().getRequiredDimensions())
                                      .entries()
                                      .stream()
                                      .map(
                                          dimension ->
                                              Dimension.newBuilder()
                                                  .setName(dimension.getKey())
                                                  .setValue(dimension.getValue())
                                                  .setRequired(true)
                                                  .build())
                                      .collect(Collectors.toList()))
                              .addDimension(
                                  Dimension.newBuilder()
                                      .setName(Ascii.toLowerCase(Name.HOST_NAME.name()))
                                      .setValue(LocalHost.getHostName())
                                      .build());
                      try {
                        String ipAddress = LocalHost.getAddress().getHostAddress();
                        builder.addDimension(
                            Dimension.newBuilder()
                                .setName(Ascii.toLowerCase(Name.HOST_IP.name()))
                                .setValue(ipAddress)
                                .build());
                      } catch (UnknownHostException e) {
                        // This should not stop us from returning device results
                      }
                      return builder.build();
                    })
                .filter(
                    deviceInfo ->
                        deviceQueryFilter.getOwnerRegexList().stream()
                                .allMatch(
                                    ownerRegex ->
                                        matchAttribute(
                                            ownerRegex,
                                            deviceInfo.getOwnerList().isEmpty()
                                                ? Stream.of("public")
                                                : deviceInfo.getOwnerList().stream()))
                            && deviceQueryFilter.getTypeRegexList().stream()
                                .allMatch(
                                    typeRegex ->
                                        matchAttribute(
                                            typeRegex, deviceInfo.getTypeList().stream()))
                            && deviceQueryFilter.getDriverRegexList().stream()
                                .allMatch(
                                    driverRegex ->
                                        matchAttribute(
                                            driverRegex, deviceInfo.getDriverList().stream()))
                            && deviceQueryFilter.getDecoratorRegexList().stream()
                                .allMatch(
                                    decoratorRegex ->
                                        matchAttribute(
                                            decoratorRegex, deviceInfo.getDecoratorList().stream()))
                            && (deviceQueryFilter.getStatusList().isEmpty()
                                || deviceQueryFilter.getStatusList().stream()
                                    .anyMatch(
                                        status ->
                                            Ascii.equalsIgnoreCase(status, deviceInfo.getStatus())))
                            && deviceQueryFilter.getDimensionFilterList().stream()
                                .allMatch(
                                    dimensionFilter ->
                                        matchAttribute(
                                            dimensionFilter.getValueRegex(),
                                            deviceInfo.getDimensionList().stream()
                                                .filter(
                                                    deviceDimension ->
                                                        deviceDimension
                                                            .getName()
                                                            .equals(dimensionFilter.getName()))
                                                .map(Dimension::getValue))))
                .collect(Collectors.toList()))
        .build();
    // TODO: Supports job/test info.
    // For consistency, each device API should only be invoked once. So if we use stream.flatMap()
    // and short-circuit logic to implement "invoke API 1 -> match condition 1 -> invoke API 2 ->
    // match condition 2 -> ... -> conversion device info with previous data", the code will be
    // very ugly. So let's just keep the current code until we meet performance problems.
  }

  @Override
  public List<LabQueryResult> queryDevicesByLab(DeviceQueryFilter filter)
      throws MobileHarnessException, InterruptedException {
    String ipAddress = "";
    try {
      ipAddress = LocalHost.getAddress().getHostAddress();
    } catch (UnknownHostException e) {
      logger.atWarning().withCause(e).log("Unable to determine host IP address");
    }
    return ImmutableList.of(
        LabQueryResult.create(LocalHost.getCanonicalHostName(), ipAddress, queryDevice(filter)));
  }

  @Override
  public ListenableFuture<DeviceQueryResult> queryDeviceAsync(DeviceQueryFilter deviceQueryFilter)
      throws MobileHarnessException, InterruptedException {
    return Futures.immediateFuture(queryDevice(deviceQueryFilter));
  }

  private boolean matchAttribute(String attributeRegex, Stream<String> attributes) {
    Pattern attributePattern = Pattern.compile(attributeRegex);
    return attributes.anyMatch(attribute -> attributePattern.matcher(attribute).matches());
  }

  private LocalDeviceManager getDeviceManager()
      throws MobileHarnessException, InterruptedException {
    return MoreFutures.get(
        deviceManagerFuture, InfraErrorId.DM_LOCAL_DEVICE_QUERIER_DEVICE_MANAGER_INIT_ERROR);
  }
}
