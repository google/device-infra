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

package com.google.devtools.mobileharness.infra.controller.test.event;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.allocation.Allocation;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto;
import com.google.devtools.mobileharness.api.testrunner.event.test.LocalDriverEndedEvent;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Optional;
import javax.annotation.Nullable;

/** Implementation of {@link LocalDriverEndedEvent}. */
public record LocalDriverEndedEventImpl(
    String driverName,
    DeviceFeature deviceFeature,
    ImmutableList<LabQueryProto.DeviceInfo> allDeviceInfos,
    Device device,
    @Nullable Throwable error,
    TestInfo testInfo,
    Allocation allocation)
    implements LocalDriverEndedEvent {

  @Override
  public String getDriverName() {
    return driverName;
  }

  @Override
  public DeviceFeature getDeviceFeature() {
    return deviceFeature;
  }

  @Override
  public ImmutableList<LabQueryProto.DeviceInfo> getAllDeviceInfos() {
    return allDeviceInfos;
  }

  @Override
  public Device getDevice() {
    return device;
  }

  @Override
  public Optional<Throwable> getExecutionError() {
    return Optional.ofNullable(error);
  }

  @Override
  public TestInfo getTest() {
    return testInfo;
  }

  @Override
  public Allocation getAllocation() {
    return allocation;
  }
}
