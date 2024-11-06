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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.device.faileddevice.FailedDeviceTable;
import com.google.wireless.qa.mobileharness.shared.api.annotation.RetainDeviceInfoAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** Failed device that needs recovery jobs. */
@RetainDeviceInfoAnnotation
public class FailedDevice extends BaseDevice {
  /** Name of the driver type which takes no action for a test. */
  @VisibleForTesting static final String NO_OP_DRIVER = "NoOpDriver";

  public FailedDevice(String deviceId) {
    this(deviceId, ApiConfig.getInstance(), new ValidatorFactory());
  }

  @VisibleForTesting
  FailedDevice(String deviceId, ApiConfig config, ValidatorFactory validatorFactory) {
    super(deviceId, config, validatorFactory, /* managedDeviceInfo= */ true);
  }

  @Override
  public void setUp() throws MobileHarnessException, InterruptedException {
    super.setUp();

    // Allow no-op driver to run to facilitate custom job recovery.
    addSupportedDriver(NO_OP_DRIVER);

    // The FailedDevice also supports all the decorators/drivers of original device. The previous
    // properties/decorators/drivers will be inherited through the same DeviceInfo instance.

    // To present normal tests from allocating this FailedDevice, let us add a "Failed" prefix to
    // all the device type to differentiate.
    for (String type : getDeviceTypes()) {
      if (!type.startsWith("Failed")) {
        info().deviceTypes().remove(type);
        info().deviceTypes().add("Failed" + type);
      }
    }
  }

  @Override
  public boolean checkDevice() {
    return false;
  }

  @Override
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    FailedDeviceTable.getInstance().remove(getDeviceControlId());
    return super.postRunTest(testInfo);
  }
}
