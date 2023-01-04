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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.realdevice.AndroidRealDeviceDelegate;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLogType;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.infra.container.sandbox.device.DeviceSandboxController;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import java.time.Duration;
import javax.annotation.Nullable;

/**
 * Android real device operator, for controlling a specific Android real device.
 *
 * <p>There are three modes supported in Android real device.
 *
 * <ul>
 *   <li>Online mode: AndroidDevice AndroidRealDevice AndroidFlashableDevice
 *   <li>Fastboot mode: AndroidFastbootDevice AndroidFlashableDevice
 * </ul>
 */
public class AndroidRealDevice extends AndroidDevice {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private AndroidRealDeviceDelegate delegate;

  /**
   * Constructor with the given device ID.
   *
   * @param deviceId serial number of the Android device
   */
  public AndroidRealDevice(String deviceId) {
    this(deviceId, ApiConfig.getInstance(), new ValidatorFactory());
  }

  protected AndroidRealDevice(
      String deviceId, @Nullable ApiConfig apiConfig, @Nullable ValidatorFactory validatorFactory) {
    super(deviceId, apiConfig, validatorFactory);
    this.delegate = getAndroidRealDeviceDelegate();
  }

  private AndroidRealDeviceDelegate getAndroidRealDeviceDelegate() {
    return new com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android
        .realdevice.AndroidRealDeviceDelegateImpl(this);
  }

  @Override
  public void setUp() throws MobileHarnessException, InterruptedException {
    delegate.setUp();
  }

  @Override
  public boolean checkDevice() throws MobileHarnessException, InterruptedException {
    return delegate.checkDevice();
  }

  @Override
  public DeviceSandboxController getSandboxController() {
    return delegate.getSandboxController();
  }

  @Override
  public boolean supportsContainer() {
    return true;
  }

  @Override
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    delegate.preRunTest(testInfo);
  }

  @CanIgnoreReturnValue
  @Override
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    return delegate.postRunTest(testInfo);
  }

  @Override
  public boolean canReboot() {
    return true;
  }

  @Override
  public void reboot() throws MobileHarnessException, InterruptedException {
    if (canReboot()) {
      delegate.reboot();
    } else {
      logger.atSevere().log(
          "Unexpected attempt to reboot a non-rebootable device %s", getDeviceId());
    }
  }

  @Override
  public String takeScreenshot() throws MobileHarnessException, InterruptedException {
    return delegate.takeScreenshot();
  }

  @Override
  public String getDeviceLog(DeviceLogType deviceLogType)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    return delegate.getDeviceLog(deviceLogType);
  }

  @Override
  public boolean isRooted() throws MobileHarnessException, InterruptedException {
    return delegate.isRooted();
  }

  @Override
  public Duration getSetupTimeout() throws MobileHarnessException, InterruptedException {
    return delegate.getSetupTimeout();
  }

  @Override
  public void tearDown()
      throws InterruptedException,
          com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    delegate.tearDown();
  }

  /** Used for unit test only. */
  protected void setAndroidRealDeviceDelegate(AndroidRealDeviceDelegate fakeDelegate) {
    this.delegate = fakeDelegate;
  }
}
