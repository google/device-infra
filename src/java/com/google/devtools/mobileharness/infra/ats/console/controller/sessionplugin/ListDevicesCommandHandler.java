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

package com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;

import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.realdevice.AndroidRealDeviceConstants;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.DeviceDescriptorProto.DeviceDescriptor;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.ListDevicesCommand;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.shared.util.base.TableFormatter;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Handler for "list devices" commands. */
class ListDevicesCommandHandler {

  private static final ImmutableList<String> HEADERS =
      ImmutableList.of("Serial", "State", "Allocation", "Product", "Variant", "Build", "Battery");
  private static final ImmutableList<String> HEADERS_ALL =
      ImmutableList.<String>builder().addAll(HEADERS).add("class", "TestDeviceState").build();
  private static final Comparator<DeviceDescriptor> DEVICE_DESCRIPTOR_COMPARATOR =
      Comparator.comparing(DeviceDescriptor::getAllocationState)
          .thenComparing(DeviceDescriptor::getSerial);

  private final DeviceQuerier deviceQuerier;

  @Inject
  ListDevicesCommandHandler(DeviceQuerier deviceQuerier) {
    this.deviceQuerier = deviceQuerier;
  }

  /**
   * Example output:
   *
   * <pre>{@code
   * Serial State Allocation Product Variant Build Battery
   * abc ONLINE Available bullhead bullhead MTC20K 100
   * }</pre>
   */
  AtsSessionPluginOutput handle(ListDevicesCommand command)
      throws MobileHarnessException, InterruptedException {
    boolean listAllDevices = command.getListAllDevices();
    ImmutableList<String> headers = listAllDevices ? HEADERS_ALL : HEADERS;
    ImmutableList<DeviceDescriptor> devices = listDevices();
    ImmutableList<ImmutableList<String>> table =
        Stream.concat(
                Stream.of(headers),
                devices.stream()
                    .sorted(DEVICE_DESCRIPTOR_COMPARATOR)
                    .map(device -> formatDeviceDescriptor(device, listAllDevices))
                    .flatMap(Optional::stream))
            .collect(toImmutableList());
    String result = TableFormatter.displayTable(table);
    return AtsSessionPluginOutput.newBuilder()
        .setSuccess(Success.newBuilder().setOutputMessage(result))
        .build();
  }

  private ImmutableList<DeviceDescriptor> listDevices()
      throws MobileHarnessException, InterruptedException {
    DeviceQueryResult deviceQueryResult = queryDevice();
    return deviceQueryResult.getDeviceInfoList().stream()
        .map(ListDevicesCommandHandler::convertDeviceInfo)
        .flatMap(Optional::stream)
        .collect(toImmutableList());
  }

  private DeviceQueryResult queryDevice() throws MobileHarnessException, InterruptedException {
    try {
      return deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_LIST_DEVICES_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }
  }

  private static Optional<DeviceDescriptor> convertDeviceInfo(DeviceInfo deviceInfo) {
    ImmutableListMultimap<String, String> dimensions =
        deviceInfo.getDimensionList().stream()
            .collect(
                toImmutableListMultimap(
                    DeviceQuery.Dimension::getName, DeviceQuery.Dimension::getValue));
    return Optional.of(
        DeviceDescriptor.newBuilder()
            .setSerial(deviceInfo.getId())
            .setDeviceState(getDeviceState(deviceInfo.getTypeList()))
            .setAllocationState(getAllocationState(deviceInfo.getStatus()))
            .setProduct("n/a")
            .setProductVariant("n/a")
            .setBuildId("n/a")
            .setBatteryLevel(
                getDimension(dimensions, Name.BATTERY_LEVEL.lowerCaseName()).orElse("n/a"))
            .setDeviceClass("n/a")
            .setTestDeviceState("n/a")
            .setIsStubDevice(false)
            .build());
  }

  private static String getDeviceState(List<String> deviceTypes) {
    boolean androidOnlineDevice = false;
    boolean androidFastbootDevice = false;
    boolean androidFlashableDevice = false;
    boolean androidOfflineDevice = false;
    boolean androidUnauthorizedDevice = false;
    for (String deviceType : deviceTypes) {
      switch (deviceType) {
        case AndroidRealDeviceConstants.ANDROID_ONLINE_DEVICE:
          androidOnlineDevice = true;
          break;
        case AndroidRealDeviceConstants.ANDROID_FASTBOOT_DEVICE:
          androidFastbootDevice = true;
          break;
        case AndroidRealDeviceConstants.ANDROID_FLASHABLE_DEVICE:
          androidFlashableDevice = true;
          break;
        case "AndroidOfflineDevice":
          androidOfflineDevice = true;
          break;
        case "AndroidUnauthorizedDevice":
          androidUnauthorizedDevice = true;
          break;
        default:
          break;
      }
    }
    if (androidOnlineDevice) {
      return "ONLINE";
    }
    if (androidOfflineDevice) {
      return "OFFLINE";
    }
    if (androidUnauthorizedDevice) {
      return "UNAUTHORIZED";
    }
    if (!androidFastbootDevice && androidFlashableDevice) {
      return "FASTBOOTD";
    }
    return "n/a";
  }

  private static String getAllocationState(String deviceStatus) {
    Optional<DeviceStatus> statusEnum =
        Enums.getIfPresent(DeviceStatus.class, Ascii.toUpperCase(deviceStatus)).toJavaUtil();
    if (statusEnum.isPresent()) {
      switch (statusEnum.get()) {
        case IDLE:
          return "Available";
        case BUSY:
          return "Allocated";
        case INIT:
        case PREPPING:
        case DYING:
          return "Checking_Availability";
        case LAMEDUCK:
          return "Unavailable";
        default:
      }
    }
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, deviceStatus);
  }

  private static Optional<String> getDimension(
      ImmutableListMultimap<String, String> dimensions, String name) {
    ImmutableList<String> values = dimensions.get(name);
    return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
  }

  /** See com.android.tradefed.device.DeviceManager. */
  private static Optional<ImmutableList<String>> formatDeviceDescriptor(
      DeviceDescriptor deviceDescriptor, boolean listAllDevices) {
    if (!listAllDevices
        && deviceDescriptor.getIsStubDevice()
        && !deviceDescriptor.getAllocationState().equals("Allocated")) {
      return Optional.empty();
    }
    ImmutableList.Builder<String> result = ImmutableList.builder();
    String serial =
        deviceDescriptor.getDisplaySerial().isEmpty()
            ? deviceDescriptor.getSerial()
            : deviceDescriptor.getDisplaySerial();
    result.add(
        serial,
        deviceDescriptor.getDeviceState(),
        deviceDescriptor.getAllocationState(),
        deviceDescriptor.getProduct(),
        deviceDescriptor.getProductVariant(),
        deviceDescriptor.getBuildId(),
        deviceDescriptor.getBatteryLevel());
    if (listAllDevices) {
      result.add(deviceDescriptor.getDeviceClass(), deviceDescriptor.getTestDeviceState());
    }
    return Optional.of(result.build());
  }
}
