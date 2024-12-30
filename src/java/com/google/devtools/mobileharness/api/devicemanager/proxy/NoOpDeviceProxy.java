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

package com.google.devtools.mobileharness.api.devicemanager.proxy;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.TestLocator;
import com.google.devtools.mobileharness.api.model.lab.DeviceId;
import com.google.devtools.mobileharness.api.model.lab.LiteDeviceInfoFactory;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceRequirement;
import com.google.devtools.mobileharness.infra.controller.device.util.DeviceIdUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import java.util.UUID;
import javax.inject.Inject;

/**
 * {@link DeviceProxy} for {@code NoOpDevice}.
 *
 * <p>If {@link Name#ID} is specified in the dimensions, it will be used as the device control ID,
 * otherwise a random device control ID will be used.
 */
public class NoOpDeviceProxy implements DeviceProxy {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ProxyDeviceRequirement deviceRequirement;
  private final TestLocator testLocator;

  @Inject
  NoOpDeviceProxy(ProxyDeviceRequirement deviceRequirement, TestLocator testLocator) {
    this.deviceRequirement = deviceRequirement;
    this.testLocator = testLocator;
  }

  @Override
  public Device leaseDevice() throws MobileHarnessException, InterruptedException {
    // Generates DeviceId.
    SubDeviceSpec subDeviceSpec =
        deviceRequirement.subDeviceSpecs().getSubDevice(deviceRequirement.subDeviceIndex());
    String requiredDeviceControlId = subDeviceSpec.dimensions().get(Name.ID);
    String deviceControlId =
        requiredDeviceControlId == null
            ? "no-op-device-" + UUID.randomUUID()
            : requiredDeviceControlId;
    String deviceUuid = String.format("%s-test-%s", deviceControlId, testLocator.id());
    DeviceId deviceId = DeviceId.of(deviceControlId, deviceUuid);

    // Creates the Device instance.
    NoOpDevice device =
        new NoOpDevice(
            /* apiConfig= */ null, new ValidatorFactory(), LiteDeviceInfoFactory.create(deviceId));
    DeviceIdUtil.addDeviceIdAndClassNameToDimension(deviceId, device);

    // Sets up the Device.
    logger.atInfo().log("Setting up device %s", device);
    device.setUp();
    logger.atInfo().log("Set up device %s", device);
    return device;
  }

  @Override
  public void releaseDevice() {
    // Does nothing.
  }
}
