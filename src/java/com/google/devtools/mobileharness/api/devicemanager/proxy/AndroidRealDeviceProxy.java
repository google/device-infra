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
import com.google.devtools.mobileharness.infra.controller.device.provider.deviceapi.LeasedDeviceConnection;
import com.google.devtools.mobileharness.infra.controller.device.provider.deviceapi.RemoteDeviceConnector;
import com.google.devtools.mobileharness.infra.controller.device.proxy.ProxyDeviceRequirement;
import com.google.devtools.mobileharness.infra.controller.device.util.DeviceIdUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.omnilab.device.api.DeviceSpecification;
import com.google.devtools.omnilab.device.api.Dimension;
import com.google.devtools.omnilab.device.api.Dimensions;
import com.google.devtools.omnilab.device.api.UnknownDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidRealDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import java.time.Duration;
import javax.inject.Inject;

/** Device proxy for Android real devices. */
public class AndroidRealDeviceProxy implements DeviceProxy {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ProxyDeviceRequirement deviceRequirement;
  private final TestLocator testLocator;
  private final RemoteDeviceConnector remoteDeviceConnector;
  private final AndroidSystemStateUtil androidSystemStateUtil;
  private final JobSetting jobSetting;
  private LeasedDeviceConnection leasedDeviceConnection;

  @Inject
  AndroidRealDeviceProxy(
      ProxyDeviceRequirement deviceRequirement,
      TestLocator testLocator,
      RemoteDeviceConnector remoteDeviceConnector,
      AndroidSystemStateUtil androidSystemStateUtil,
      JobSetting jobSetting) {
    this.deviceRequirement = deviceRequirement;
    this.testLocator = testLocator;
    this.remoteDeviceConnector = remoteDeviceConnector;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.jobSetting = jobSetting;
  }

  @Override
  public Device leaseDevice() throws MobileHarnessException, InterruptedException {
    DeviceSpecification deviceSpec = createDeviceSpecification();
    this.leasedDeviceConnection =
        remoteDeviceConnector.connect(
            deviceSpec,
            Duration.ofMillis(jobSetting.getTimeout().getJobTimeoutMs()),
            Duration.ofMillis(jobSetting.getTimeout().getTestTimeoutMs()));

    // Creates the Device instance.
    DeviceId deviceId =
        DeviceId.of(
            leasedDeviceConnection.getLocalAdbSerial(), leasedDeviceConnection.getRemoteDeviceId());
    AndroidRealDevice device =
        new AndroidRealDevice(new ValidatorFactory(), LiteDeviceInfoFactory.create(deviceId));
    device.addDimension(Name.HOST_NAME, leasedDeviceConnection.getHostname());
    DeviceIdUtil.addDeviceIdAndClassNameToDimension(deviceId, device);
    leasedDeviceConnection.getAllocatedDeviceProperties().forEach(device::addDimension);

    // Wait for the device to be ready.
    androidSystemStateUtil.waitUntilReady(leasedDeviceConnection.getLocalAdbSerial());
    logger.atInfo().log(
        "Leased device for test %s: LocalAdbSerial=%s, Hostname=%s, RemoteDeviceId=%s",
        testLocator.id(),
        leasedDeviceConnection.getLocalAdbSerial(),
        leasedDeviceConnection.getHostname(),
        leasedDeviceConnection.getRemoteDeviceId());
    return device;
  }

  @Override
  public void releaseDevice() {
    if (leasedDeviceConnection != null) {
      leasedDeviceConnection.close();
    }
  }

  private DeviceSpecification createDeviceSpecification() {
    SubDeviceSpec subDeviceSpec =
        deviceRequirement.subDeviceSpecs().getSubDevice(deviceRequirement.subDeviceIndex());
    Dimensions.Builder dimensions =
        Dimensions.newBuilder()
            .addDimensions(
                Dimension.newBuilder().setName("devicetype").setValue("AndroidRealDevice"));
    // TODO: Add driver
    subDeviceSpec
        .decorators()
        .getAll()
        .forEach(
            decorator ->
                dimensions.addDimensions(
                    Dimension.newBuilder().setName("decorator").setValue(decorator).build()));
    subDeviceSpec
        .dimensions()
        .getAll()
        .forEach(
            (key, value) ->
                dimensions.addDimensions(
                    Dimension.newBuilder().setName(key).setValue(value).build()));
    return DeviceSpecification.newBuilder()
        .setUnknownDevice(UnknownDevice.getDefaultInstance())
        .setLegacyDimensions(dimensions)
        .build();
  }
}
