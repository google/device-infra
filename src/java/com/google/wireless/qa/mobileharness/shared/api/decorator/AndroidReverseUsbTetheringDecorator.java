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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.connectivity.UsbReverseTetheringUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupContext;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.SetupResult;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator.TeardownContext;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidReverseUsbTetheringDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Duration;
import javax.inject.Inject;

/**
 * Driver decorator for enabling USB reverse tethering (Reverse NCM) on a USB-connected Android
 * device before running the test, persisting tethering across device reboots, and resetting USB
 * functions after the test completes.
 */
@DecoratorAnnotation(
    help =
        "For enabling USB reverse tethering on a USB-connected Android device before the test is"
            + " run and resetting USB functions after the test.")
public class AndroidReverseUsbTetheringDecorator extends LifecycleDecorator
    implements SpecConfigable<AndroidReverseUsbTetheringDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final UsbReverseTetheringUtil usbReverseTetheringUtil;

  private boolean tetheringAttempted = false;

  @Inject
  AndroidReverseUsbTetheringDecorator(
      Driver decoratedDriver, TestInfo testInfo, UsbReverseTetheringUtil usbReverseTetheringUtil) {
    super(decoratedDriver, testInfo);
    this.usbReverseTetheringUtil = usbReverseTetheringUtil;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    Device device = getDevice();
    String deviceId = device.getDeviceId();
    AndroidReverseUsbTetheringDecoratorSpec spec = testInfo.jobInfo().combinedSpec(this, deviceId);

    if (DeviceUtil.isOverTcpDevice(deviceId)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_OVER_TCP_NOT_SUPPORTED,
          String.format(
              "Device %s is connected over TCP; AndroidReverseUsbTetheringDecorator requires a"
                  + " physical USB connection.",
              deviceId));
    }
    if (!usbReverseTetheringUtil.isHostSupported()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_HOST_NOT_SUPPORTED,
          String.format("Host does not support USB reverse tethering for device %s.", deviceId));
    }
    if (!usbReverseTetheringUtil.isDeviceSupported(deviceId)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_DEVICE_NOT_SUPPORTED,
          String.format(
              "Device %s build does not support USB reverse tethering (Reverse NCM).", deviceId));
    }
    long timeoutSec = spec.getConnectivityTimeoutSec();
    if (timeoutSec <= 0) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REVERSE_USB_TETHERING_ENABLE_ERROR,
          String.format("connectivity_timeout_sec must be positive, got %d.", timeoutSec));
    }

    this.tetheringAttempted = true;
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Setting up USB reverse tethering on device %s.", deviceId);
    usbReverseTetheringUtil.enableUsbReverseTethering(
        device,
        spec.getPersistAcrossReboot(),
        Duration.ofSeconds(timeoutSec),
        spec.hasConnectivityCheckHost() ? spec.getConnectivityCheckHost() : null,
        testInfo.log());
    return SetupResult.continueDecorated();
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    if (tetheringAttempted) {
      usbReverseTetheringUtil.disableUsbReverseTethering(getDevice(), context.testInfo().log());
    }
  }
}
