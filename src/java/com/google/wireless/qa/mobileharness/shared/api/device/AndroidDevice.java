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
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.AndroidDeviceDelegate;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import java.util.List;
import javax.annotation.Nullable;

/** Android device operator, for controlling a specific Android real device or emulator. */
public abstract class AndroidDevice extends BaseDevice {

  /** Name of the CPU dimension. */
  static final String DIMENSION_NAME_ROOTED = "rooted";

  /** Name of the dimension which means whether the device can access Internet. */
  static final String DIMENSION_NAME_INTERNET = "internet";

  private static final ImmutableSet<String> UNROOTED_DEVICE_WHITELIST = ImmutableSet.of("nokia 1");

  @VisibleForTesting
  static final ImmutableSet<AndroidProperty> PROP_NOT_SET_AS_DIMENSION =
      ImmutableSet.of(
          AndroidProperty.FLAVOR, AndroidProperty.KAIOS_RUNTIME_TOKEN, AndroidProperty.PRODUCT);

  private AndroidDeviceDelegate delegate;

  /** Constructor for test only. */
  protected AndroidDevice(
      String deviceId, @Nullable ApiConfig apiConfig, @Nullable ValidatorFactory validatorFactory) {
    super(deviceId, apiConfig, validatorFactory);
    this.delegate = getAndroidDeviceDelegate();
  }

  @Override
  public void setUp() throws MobileHarnessException, InterruptedException {
    delegate.ensureDeviceReady();

    ImmutableListMultimap<Dimension.Name, String> extraDimensions = null;
    if (supportsContainer()) {
      extraDimensions =
          ImmutableListMultimap.of(Dimension.Name.DEVICE_SUPPORTS_CONTAINER, Dimension.Value.TRUE);
    }

    delegate.setUp(isRooted(), extraDimensions);
  }

  @CanIgnoreReturnValue
  @Override
  public boolean checkDevice() throws MobileHarnessException, InterruptedException {
    return delegate.checkDevice();
  }

  @Override
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    delegate.preRunTest(testInfo.toNewTestInfo(), isRooted());
  }

  /**
   * Cleans up when the device becomes undetectable/disconnected. This method will release the port
   * saved in device properties by drivers. Not thread safe.
   */
  @Override
  public void tearDown() throws MobileHarnessException, InterruptedException {}

  /** Returns the ABI of this device. */
  @Nullable
  public String getAbi() {
    return delegate.getCachedAbi().orElse(null);
  }

  /** Returns the sdk version of this device. */
  @Nullable
  public Integer getSdkVersion() {
    return delegate.getCachedSdkVersion().orElse(null);
  }

  /** Returns the screen density of this device. */
  @Nullable
  public Integer getScreenDensity() {
    return delegate.getCachedScreenDensity().orElse(null);
  }

  /** Updates the sdk version of this device. */
  public void updateSdkVersion() throws MobileHarnessException, InterruptedException {
    delegate.updateCachedSdkVersion();
  }

  /**
   * Updates the dimensions "gservices_android_id".
   *
   * @return true iff its value has changed
   */
  @CanIgnoreReturnValue
  public boolean updateGServicesAndroidID(String deviceId) throws InterruptedException {
    return delegate.updateGServicesAndroidID(deviceId);
  }

  /**
   * Returns whether the device is rooted.
   *
   * <p>Ensures device is booted up and ready to respond before calling this, especially in the
   * device setup process, because it's likely the implementation of this method will issue some adb
   * commands to the device.
   */
  public abstract boolean isRooted() throws MobileHarnessException, InterruptedException;

  /** Gets property value. */
  protected String getPropertyValue(String deviceId, AndroidProperty key)
      throws MobileHarnessException, InterruptedException {
    return delegate.getPropertyValue(deviceId, key);
  }

  /** List of decorators/drivers that should be supported by root/non-root devices. */
  protected void basicAndroidDeviceConfiguration()
      throws MobileHarnessException, InterruptedException {
    delegate.basicAndroidDeviceConfiguration(isRooted());
  }

  /** List of decorators that should be supported by root/non-root devices. */
  protected void basicAndroidDecoratorConfiguration() throws InterruptedException {
    delegate.basicAndroidDecoratorConfiguration();
  }

  public boolean isInSupportedWhitelistForPerformanceLockDecorator() {
    List<String> model = getDimension(Ascii.toLowerCase(Dimension.Name.MODEL.name()));
    if (model.size() == 1) {
      return UNROOTED_DEVICE_WHITELIST.contains(model.get(0));
    }
    return false;
  }

  private AndroidDeviceDelegate getAndroidDeviceDelegate() {
    return new com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android
        .AndroidDeviceDelegateImpl(this);
  }

  /** Used for unit test only. */
  protected void setAndroidDeviceDelegate(AndroidDeviceDelegate fakeDelegate) {
    this.delegate = fakeDelegate;
  }
}
