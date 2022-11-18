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

package com.google.devtools.mobileharness.infra.container.sandbox.device;

import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.UsbDeviceLocator;
import java.util.Optional;

/** Default {@link DeviceSandboxController} for all devices which do not support MH sandbox. */
public class UnsupportedDeviceSandboxController implements DeviceSandboxController {

  private final String deviceId;
  private final String deviceType;

  public UnsupportedDeviceSandboxController(String deviceId, String deviceType) {
    this.deviceId = deviceId;
    this.deviceType = deviceType;
  }

  @Override
  public Optional<MobileHarnessException> checkSandboxSupport() {
    return Optional.of(
        new MobileHarnessException(
            InfraErrorId.SANDBOX_DEVICE_TYPE_NOT_SUPPORTED,
            String.format(
                "Device [%s] does not support MH sandbox mode because its type is %s.",
                deviceId, deviceType)));
  }

  @Override
  public Optional<UsbDeviceLocator> getUsbDeviceLocator() {
    return Optional.empty();
  }

  @Override
  public void preRunSandbox(TestExecutionUnit testExecutionUnit) {
    // Does nothing.
  }

  @Override
  public void postRunSandbox() {
    // Does nothing.
  }
}
