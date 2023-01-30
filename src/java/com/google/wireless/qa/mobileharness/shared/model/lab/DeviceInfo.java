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

package com.google.wireless.qa.mobileharness.shared.model.lab;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceError;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.model.lab.out.Status;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfoOrBuilder;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.Dimension;
import java.util.List;
import java.util.Optional;

/** A thread-safe data model containing all information of a single device. */
public class DeviceInfo extends DeviceScheduleUnit {
  /** Device status. */
  private final Status status;

  /** Device errors collected from the lab server. */
  private final ImmutableList<DeviceError> deviceErrors;

  /** Create a device data model with the give locator and status. */
  public DeviceInfo(DeviceLocator deviceLocator, DeviceStatus status) {
    super(deviceLocator);
    this.status = new Status(status);
    this.deviceErrors = ImmutableList.of();
  }

  /** Create a device data model with the give locator and status. */
  private DeviceInfo(
      DeviceLocator deviceLocator, DeviceStatus status, List<DeviceError> deviceErrors) {
    super(deviceLocator);
    this.status = new Status(status);
    this.deviceErrors = ImmutableList.copyOf(deviceErrors);
  }

  /**
   * Creates a device data model with the given device info proto.
   *
   * <p>The model does not have port/proxy information in its lab locator.
   *
   * @throws IllegalArgumentException if the proto has an invalid status or does not include a host
   *     IP or a host name in its dimensions
   */
  public DeviceInfo(DeviceInfoOrBuilder deviceInfo) {
    this(
        new DeviceLocator(
            deviceInfo.getId(),
            new LabLocator(
                getDimensionValue(deviceInfo, Name.HOST_IP)
                    .orElseThrow(
                        () -> new IllegalArgumentException("Device info does not include host IP")),
                getDimensionValue(deviceInfo, Name.HOST_NAME)
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "Device info does not include host name")))),
        DeviceStatus.valueOf(Ascii.toUpperCase(deviceInfo.getStatus())),
        deviceInfo.getDeviceErrorFromLabList());
    types().addAll(deviceInfo.getTypeList());
    drivers().addAll(deviceInfo.getDriverList());
    decorators().addAll(deviceInfo.getDecoratorList());
    owners().addAll(deviceInfo.getOwnerList());
    deviceInfo
        .getDimensionList()
        .forEach(
            dimension ->
                (dimension.getRequired() ? dimensions().required() : dimensions().supported())
                    .add(dimension.getName(), dimension.getValue()));
  }

  /** Device status. */
  public Status status() {
    return status;
  }

  /** Device id. */
  public String deviceId() {
    return this.locator().getSerial();
  }

  /** Device errors. */
  public ImmutableList<DeviceError> deviceErrors() {
    return deviceErrors;
  }

  private static Optional<String> getDimensionValue(
      DeviceInfoOrBuilder deviceInfo, Name dimensionName) {
    return deviceInfo.getDimensionList().stream()
        .filter(dimension -> dimension.getName().equals(dimensionName.name().toLowerCase()))
        .findFirst()
        .map(Dimension::getValue);
  }
}
