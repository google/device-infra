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

package com.google.wireless.qa.mobileharness.shared.controller.event;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.Beta;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;

/**
 * Event that signals the beginning of a test, right after the device finishes the preparation work
 * for the test. These events can only be received on the same workstation where the devices are
 * physically connected.
 *
 * @deprecated Use {@link TestStartedEvent} instead.
 */
@Deprecated
public class TestStartEvent extends TestEvent implements DeviceCheckedTestEvent {

  /**
   * NOTE: Do NOT instantiate it in tests of your plugin and use mocked object instead.
   *
   * <p>TODO: Uses factory to prevent instantiating out of MH infrastructure.
   */
  @Beta
  public TestStartEvent(TestInfo testInfo, Allocation allocation, DeviceInfo deviceInfo) {
    super(testInfo, allocation, checkNotNull(deviceInfo));
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    return deviceInfo;
  }
}
