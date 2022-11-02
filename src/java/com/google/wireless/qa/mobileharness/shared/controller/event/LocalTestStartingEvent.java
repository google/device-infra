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

import com.google.common.annotations.Beta;
import com.google.devtools.mobileharness.shared.util.message.StrPairUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.EventInjectionScope;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.util.DeviceInfoUtil;

/**
 * Event that signals the beginning of a test, even before the device finishes the preparation work
 * for the test. These events can only be received on the same workstation where the devices are
 * physically connected.
 *
 * <p>Note unless you are sure you want to do something before the device is well prepared, use
 * {@link LocalTestStartedEvent} instead.
 *
 * @since Lab Server 4.2.75, Client 4.2.48
 */
public class LocalTestStartingEvent extends TestStartingEvent implements LocalTestEvent {

  private final Device localDevice;

  /**
   * NOTE: Do NOT instantiate it in tests of your plugin and use mocked object instead.
   *
   * <p>TODO: Uses factory to prevent instantiating out of MH infrastructure.
   */
  @Beta
  public LocalTestStartingEvent(
      TestInfo testInfo, Device localDevice, Allocation allocation, DeviceInfo deviceInfo) {
    super(testInfo, allocation, deviceInfo);
    this.localDevice = localDevice;
  }

  public LocalTestStartingEvent(TestInfo testInfo, Device localDevice) {
    super(
        testInfo,
        new Allocation(
            testInfo.locator(),
            new DeviceLocator(localDevice.getDeviceId()),
            StrPairUtil.convertCollectionToMultimap(localDevice.getDimensions())),
        DeviceInfoUtil.getDeviceInfoForCurrentTest(localDevice, testInfo));
    this.localDevice = localDevice;
  }

  @Override
  public Device getLocalDevice() {
    return localDevice;
  }

  @Override
  public void enter() {
    EventInjectionScope.instance.put(Device.class, getLocalDevice());

    super.enter();
  }
}
