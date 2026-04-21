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
import com.google.devtools.mobileharness.api.testrunner.event.test.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** Implementation for {@linkplain TestStartingEvent}. */
public record TestStartingEventImpl(
    DeviceFeature deviceFeature,
    ImmutableList<LabQueryProto.DeviceInfo> allDeviceInfos,
    TestInfo test,
    Allocation allocation)
    implements TestStartingEvent {

  @Override
  public DeviceFeature getDeviceFeature() {
    return deviceFeature;
  }

  @Override
  public ImmutableList<LabQueryProto.DeviceInfo> getAllDeviceInfos() {
    return allDeviceInfos;
  }

  @Override
  public TestInfo getTest() {
    return test;
  }

  @Override
  public Allocation getAllocation() {
    return allocation;
  }
}
