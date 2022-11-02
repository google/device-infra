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

import com.google.common.collect.Multimap;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.EventInjectionScope;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.InjectionEvent;
import com.google.wireless.qa.mobileharness.shared.model.allocation.Allocation;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceLocator;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import java.util.Optional;
import javax.annotation.Nullable;

/** Event that signals the process of a test. */
public class TestEvent implements ControllerEvent, InjectionEvent {
  protected final DeviceInfo deviceInfo;
  private final TestInfo testInfo;
  private final Allocation allocation;
  private final EventInjectionScope injectionScope;

  public TestEvent(TestInfo testInfo, Allocation allocation, @Nullable DeviceInfo deviceInfo) {
    this.testInfo = testInfo;
    this.allocation = allocation;
    this.deviceInfo = deviceInfo;
    this.injectionScope = EventInjectionScope.instance;
  }

  public TestInfo getTest() {
    return testInfo;
  }

  /** This method is going to be DEPRECATED. Use {@link #getTest()} instead. */
  public com.google.wireless.qa.mobileharness.shared.api.job.TestInfo getTestInfo() {
    return new com.google.wireless.qa.mobileharness.shared.api.job.TestInfo(testInfo);
  }

  public Allocation getAllocation() {
    return allocation;
  }

  public DeviceLocator getDeviceLocator() {
    return allocation.getDevice();
  }

  /**
   * Gets the device info if the device is checked successfully.
   *
   * @since Lab Server 4.24, Client 4.24
   */
  public Optional<DeviceInfo> getDeviceInfoIfChecked() {
    return Optional.ofNullable(deviceInfo);
  }

  /**
   * Gets the dimensions of the allocated device. If you are using this method in a Lab Plugin, make
   * sure it is running on Lab Server >= 4.2.63.
   */
  public Multimap<String, String> getDeviceDimensions() {
    return allocation.getDimensions();
  }

  @Override
  public void enter() {
    injectionScope.put(TestInfo.class, getTest());
    injectionScope.put(
        com.google.wireless.qa.mobileharness.shared.api.job.TestInfo.class, getTestInfo());

    injectionScope.put(DeviceLocator.class, getDeviceLocator());
    getDeviceInfoIfChecked().ifPresent(di -> injectionScope.put(DeviceInfo.class, di));

    injectionScope.put(JobInfo.class, getTest().jobInfo());
    injectionScope.put(
        com.google.wireless.qa.mobileharness.shared.api.job.JobInfo.class,
        getTestInfo().getJobInfo());

    injectionScope.enter();
  }

  @Override
  public void leave() {
    injectionScope.exit();
  }
}
