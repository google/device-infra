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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionUnit;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.UsbDeviceLocator;
import java.util.Optional;

/**
 * Controller for passing through a device to MH sandbox.
 *
 * <p>The order to invoke its methods:
 *
 * <ol>
 *   <li>{@link #checkSandboxSupport}
 *   <li>{@link #preRunSandbox}
 *   <li>(MH sandbox runs)
 *   <li>{@link #postRunSandbox}
 * </ol>
 */
public interface DeviceSandboxController {

  /** Returns non-empty if the device does not support MH sandbox mode. */
  Optional<MobileHarnessException> checkSandboxSupport() throws InterruptedException;

  /** Returns the UsbDeviceLocator of the device. Return empty if the device is not usb device. */
  Optional<UsbDeviceLocator> getUsbDeviceLocator();

  /** Does preparation work before a MH sandbox starts for device pass-through. */
  void preRunSandbox(TestExecutionUnit testExecutionUnit)
      throws MobileHarnessException, InterruptedException;

  /** Does cleanup work after a MH sandbox ends for device pass-through. */
  void postRunSandbox() throws MobileHarnessException, InterruptedException;
}
