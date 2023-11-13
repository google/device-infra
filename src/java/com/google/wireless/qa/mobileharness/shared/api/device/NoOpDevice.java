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

package com.google.wireless.qa.mobileharness.shared.api.device;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.devtools.mobileharness.api.model.lab.DeviceInfo;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.infra.container.sandbox.device.DeviceSandboxController;
import com.google.devtools.mobileharness.infra.container.sandbox.device.NoOpDeviceSandboxController;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import javax.annotation.Nullable;

/**
 * A virtual devices which don't map to any physical device or emulator/simulator.
 *
 * <p>This is for integration test of Mobile Harness itself. Only for debug/test purpose. If
 * --no_op_device_num is set, all other devices will be disabled.
 */
public class NoOpDevice extends BaseDevice {

  private final DeviceSandboxController sandboxController = new NoOpDeviceSandboxController(this);

  public NoOpDevice(String deviceId) {
    super(deviceId);
  }

  @VisibleForTesting
  public NoOpDevice(
      @Nullable ApiConfig apiConfig, ValidatorFactory validatorFactory, DeviceInfo deviceInfo) {
    super(apiConfig, validatorFactory, deviceInfo);
  }

  @Override
  public void setUp() throws MobileHarnessException, InterruptedException {
    super.setUp();
    addSupportedDriver("NoOpDriver");
    addSupportedDriver("AndroidCommsActsTest");
    addSupportedDriver("AndroidTradefedTest");

    addSupportedDecorator("NoOpDecorator");

    addDimension("label", "noop");

    String supportedDeviceType = Flags.instance().noOpDeviceType.getNonNull();
    if (!Strings.isNullOrEmpty(supportedDeviceType)) {
      addSupportedDeviceType(supportedDeviceType);
      // To make j/c/g/devtools/mobileharness/infra/master/scheduler/DeviceUtil.java happy.
      if (supportedDeviceType.equals("AndroidRealDevice")) {
        addDimension("model", "pixel");
        addDimension("sdk_version", "24");
      } else if (supportedDeviceType.equals("IosRealDevice")) {
        addDimension("model", "iPhone6");
        addDimension("software_version", "5.0.1");
      }
    }
  }

  @Override
  public DeviceSandboxController getSandboxController() {
    return sandboxController;
  }

  @Override
  public boolean supportsContainer() {
    return true;
  }

  @Override
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    testInfo.setProperty(Test.NO_OP_DEVICE_PRE_RUN_TEST_CALLED.name(), "true");
    super.preRunTest(testInfo);
  }

  @Override
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    testInfo.setProperty(Test.NO_OP_DEVICE_POST_RUN_TEST_CALLED.name(), "true");
    return super.postRunTest(testInfo);
  }
}
