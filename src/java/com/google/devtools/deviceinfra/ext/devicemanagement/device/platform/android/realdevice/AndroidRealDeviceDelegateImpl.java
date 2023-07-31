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

package com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.realdevice;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.AndroidDeviceDelegate;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.AndroidDeviceDelegateImpl;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.lab.LabLocator;
import com.google.devtools.mobileharness.infra.container.sandbox.device.DeviceSandboxController;
import com.google.devtools.mobileharness.infra.container.sandbox.device.NoOpDeviceSandboxController;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.DeviceDaemonHelper;
import com.google.devtools.mobileharness.platform.android.connectivity.AndroidConnectivityUtil;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.android.RuntimeChargingUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.controller.stat.DeviceStat;
import com.google.wireless.qa.mobileharness.shared.controller.stat.StatManager;
import java.time.Clock;
import javax.annotation.Nullable;

/** Default implementation of {@code AndroidRealDeviceDelegate}. */
public class AndroidRealDeviceDelegateImpl extends AndroidRealDeviceDelegate {

  private final String deviceId;

  private final AndroidAdbInternalUtil androidAdbInternalUtil;
  private final AndroidConnectivityUtil connectivityUtil;
  private final NoOpDeviceSandboxController noOpDeviceSandboxController;

  private static AndroidDeviceDelegate getAndroidDeviceDelegate(AndroidDevice device) {
    return new AndroidDeviceDelegateImpl(device);
  }

  public AndroidRealDeviceDelegateImpl(AndroidDevice device) {
    this(
        device,
        getAndroidDeviceDelegate(device),
        StatManager.getInstance()
            .getOrCreateLabStat(LabLocator.LOCALHOST.ip())
            .getDeviceStat(device.getDeviceId()),
        Clock.systemUTC(),
        new AndroidAdbInternalUtil(),
        new AndroidAdbUtil(),
        new AndroidProcessUtil(),
        new AndroidSystemSettingUtil(),
        new AndroidFileUtil(),
        new AndroidConnectivityUtil(),
        new AndroidSystemStateUtil(),
        new AndroidSystemSpecUtil(),
        new AndroidPackageManagerUtil(),
        new AndroidMediaUtil(),
        new AndroidUserUtil(),
        new RuntimeChargingUtil(),
        new NetworkUtil(),
        new ApkInstaller(),
        new SystemStateManager(),
        new DeviceDaemonHelper(),
        new Fastboot(),
        new LocalFileUtil());
  }

  @VisibleForTesting
  AndroidRealDeviceDelegateImpl(
      AndroidDevice device,
      AndroidDeviceDelegate androidDeviceDelegate,
      @Nullable DeviceStat deviceStat,
      Clock clock,
      AndroidAdbInternalUtil androidAdbInternalUtil,
      AndroidAdbUtil androidAdbUtil,
      AndroidProcessUtil androidProcessUtil,
      AndroidSystemSettingUtil systemSettingUtil,
      AndroidFileUtil androidFileUtil,
      AndroidConnectivityUtil connectivityUtil,
      AndroidSystemStateUtil systemStateUtil,
      AndroidSystemSpecUtil systemSpecUtil,
      AndroidPackageManagerUtil androidPkgManagerUtil,
      AndroidMediaUtil androidMediaUtil,
      AndroidUserUtil androidUserUtil,
      RuntimeChargingUtil chargingUtil,
      NetworkUtil networkUtil,
      ApkInstaller apkInstaller,
      SystemStateManager systemStateManager,
      DeviceDaemonHelper deviceDaemonHelper,
      Fastboot fastboot,
      LocalFileUtil localFileUtil) {
    super(
        device,
        androidDeviceDelegate,
        deviceStat,
        clock,
        androidAdbInternalUtil,
        androidAdbUtil,
        androidProcessUtil,
        systemSettingUtil,
        androidFileUtil,
        connectivityUtil,
        systemStateUtil,
        systemSpecUtil,
        androidPkgManagerUtil,
        androidMediaUtil,
        androidUserUtil,
        chargingUtil,
        networkUtil,
        apkInstaller,
        systemStateManager,
        deviceDaemonHelper,
        fastboot,
        localFileUtil);
    this.androidAdbInternalUtil = androidAdbInternalUtil;
    this.connectivityUtil = connectivityUtil;
    this.noOpDeviceSandboxController = new NoOpDeviceSandboxController(device);
    this.deviceId = device.getDeviceId();
  }

  @Override
  protected boolean shouldSetUpAsOnlineModeDevice()
      throws MobileHarnessException, InterruptedException {
    return androidAdbInternalUtil.getRealDeviceSerials(true).contains(deviceId);
  }

  @Override
  protected void extrasInSetUp() throws InterruptedException {
    // No extra setup by default
  }

  @Override
  protected Multimap<Dimension.Name, String> extraDimensionsForSetUpDevice() {
    // No extra dimensions by default
    return ImmutableListMultimap.of();
  }

  @Override
  protected boolean ifEnableDeviceFlashAndResetDecorators() {
    return false;
  }

  @Override
  protected void addExtraRealDeviceBasicSupportedDriversDecorators() throws InterruptedException {
    // Do nothing by default
  }

  @Override
  protected void checkExtraSupport() throws InterruptedException {
    // Do nothing by default
  }

  @Override
  protected boolean ifEnableFullStackFeatures() {
    return true;
  }

  @Override
  protected void addExtraRealDeviceFullStackSupportedDriversDecorators()
      throws InterruptedException {
    // Do nothing by default
  }

  @Override
  protected boolean ifScreenshotAble(Integer sdkVersion) {
    return sdkVersion != null && sdkVersion >= 10;
  }

  @Override
  protected void startActivityController() throws InterruptedException {
    // Do nothing by default
  }

  @Override
  protected void stopActivityController() throws InterruptedException {
    // Do nothing by default
  }

  @Override
  protected boolean ifSkipCheckAbnormalDevice() {
    return false;
  }

  @Override
  protected boolean alwaysCheckNetwork() {
    return false;
  }

  @Override
  protected boolean onlyCheckNetworkWhenCheckOnlineModeDevice() {
    return false;
  }

  @Override
  protected boolean extraChecksForOnlineModeDevice() throws InterruptedException {
    // Do nothing by default
    return false;
  }

  @Override
  protected DeviceSandboxController getSandboxControllerImpl() {
    return noOpDeviceSandboxController;
  }

  @Override
  protected void prependedRealDevicePreparationBeforeTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    // Do nothing by default
  }

  @Override
  protected boolean skipRealDeviceDefaultPreparationBeforeTest() {
    return false;
  }

  @Override
  protected void prependedRealDeviceAfterTestProcess(TestInfo testInfo)
      throws InterruptedException {
    // Do nothing by default
  }

  @Override
  protected boolean skipRealDeviceDefaultAfterTestProcess()
      throws MobileHarnessException, InterruptedException {
    return !androidAdbInternalUtil.getRealDeviceSerials(/* online= */ true).contains(deviceId);
  }

  @Override
  protected void deviceTearDown()
      throws InterruptedException,
          com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    // Do nothing by default
  }

  @Override
  protected boolean ifTrySetDevicePropertiesAndDisablePackages() {
    return true;
  }

  @Override
  protected boolean needToInstallWifiApk() {
    return true;
  }

  @Override
  protected boolean ifHasInternet() throws InterruptedException {
    return getFlagSkipCheckDeviceInternet() || connectivityUtil.pingSuccessfully(deviceId);
  }

  @Override
  protected boolean ifCheckDefaultWifi() {
    return true;
  }

  @Override
  protected boolean ifSkipRecoverDeviceNetwork() {
    return false;
  }

  @Override
  protected boolean ifSkipClearMultiUsers(int sdkVersion) {
    return !getFlagClearAndroidDeviceMultiUsers()
        || sdkVersion <= AndroidVersion.JELLY_BEAN.getStartSdkVersion();
  }

  @Override
  protected boolean notAllowSafeDischarge() {
    return false;
  }

  @Override
  protected boolean needExtraForceRootDevice() {
    return false;
  }

  @Override
  protected void validateDeviceOnceReady(String deviceId, String deviceClassName)
      throws MobileHarnessException, InterruptedException {
    // Do nothing by default
  }
}
