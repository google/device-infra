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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.util.ErrorIdComparator;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.BaseDeviceHelper;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.AndroidDeviceDelegate;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.AndroidDeviceDelegateHelper;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Constants;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootProperty;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.deviceconfig.proto.ConditionedDeviceConfigProto.ConditionedDeviceConfigs;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLogType;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.device.util.ConditionedDeviceConfigUtil;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.DeviceDaemonApkInfoProvider;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.DeviceDaemonHelper;
import com.google.devtools.mobileharness.platform.android.app.mtaastools.MtaasToolsInstantiator;
import com.google.devtools.mobileharness.platform.android.app.telephony.TelephonyHelper;
import com.google.devtools.mobileharness.platform.android.connectivity.AndroidConnectivityUtil;
import com.google.devtools.mobileharness.platform.android.connectivity.ConnectToWifiArgs;
import com.google.devtools.mobileharness.platform.android.device.AndroidDeviceHelper;
import com.google.devtools.mobileharness.platform.android.deviceadmin.DeviceAdminUtil;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.file.StorageInfo;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.AndroidFrpUtil;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.IntentArgs;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.RebootMode;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.UsbDeviceLocator;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.shared.constant.PackageConstants;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.PostSettingDeviceOp;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.network.NetworkUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.android.RuntimeChargingUtil;
import com.google.wireless.qa.mobileharness.shared.android.WifiUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidRealDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidSetWifiDecorator;
import com.google.wireless.qa.mobileharness.shared.controller.stat.DeviceStat;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.ADB;
import com.google.wireless.qa.mobileharness.shared.proto.ADBOverUSB;
import com.google.wireless.qa.mobileharness.shared.proto.Communication;
import com.google.wireless.qa.mobileharness.shared.proto.CommunicationList;
import com.google.wireless.qa.mobileharness.shared.proto.USB;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import javax.annotation.Nullable;

/** Delegate class to implement functionality for the Android real device. */
public abstract class AndroidRealDeviceDelegate {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // The property written to the device before resetting. The value of this property will be checked
  // to confirm whether the device was reset successfully.
  private static final String RESET_PROPERTY_LABEL = "debug.mobileharness.before_reset";

  /** Last time record for checkPingGoogle method in order to call it every 30 minutes. */
  private Instant lastCheckPingGoogleTime = null;

  /** Last time record for successful setup. */
  private Instant lastSetupTime = null;

  /**
   * Synchronization lock for check device root.
   *
   * <p>Although this lock can not make sure the root operation will not be executed in different
   * processes at the same time, we think it is ok.
   */
  private final Object checkRootLock = new Object();

  private final String deviceId;

  private final AndroidDevice device;
  private final AndroidDeviceDelegate androidDeviceDelegate;
  private final DeviceStat deviceStat;
  private final Clock clock;

  private final AndroidAdbInternalUtil androidAdbInternalUtil;
  protected final AndroidAdbUtil androidAdbUtil;
  private final Adb adb;
  private final AndroidProcessUtil androidProcessUtil;
  private final AndroidSystemSettingUtil systemSettingUtil;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidFrpUtil androidFrpUtil;
  private final AndroidConnectivityUtil connectivityUtil;
  private final AndroidSystemStateUtil systemStateUtil;
  private final AndroidSystemSpecUtil systemSpecUtil;
  private final AndroidPackageManagerUtil androidPkgManagerUtil;
  private final AndroidMediaUtil androidMediaUtil;
  private final AndroidUserUtil androidUserUtil;
  private final RuntimeChargingUtil chargingUtil;
  private final NetworkUtil networkUtil;
  private final ApkInstaller apkInstaller;
  private final SystemStateManager systemStateManager;
  private final DeviceDaemonHelper deviceDaemonHelper;
  protected final Fastboot fastboot;
  private final LocalFileUtil fileUtil;
  private final AndroidDeviceHelper androidDeviceHelper;
  private final MtaasToolsInstantiator mtaasToolsInstantiator;
  private final TelephonyHelper telephonyHelper;

  private final DeviceAdminUtil deviceAdminUtil;

  private static final ImmutableMap<FastbootProperty, Dimension.Name>
      REQUIRED_FASTBOOT_PROPERTY_TO_DIMENSION_NAME =
          ImmutableMap.<FastbootProperty, Dimension.Name>builder()
              .put(FastbootProperty.PRODUCT, Dimension.Name.HARDWARE)
              .buildOrThrow();

  private static final ImmutableMap<FastbootProperty, Dimension.Name>
      OPTIONAL_FASTBOOT_PROPERTY_TO_DIMENSION_NAME =
          ImmutableMap.<FastbootProperty, Dimension.Name>builder()
              .put(FastbootProperty.UNLOCKED, Dimension.Name.OEM_UNLOCK)
              .put(FastbootProperty.HW_REVISION, Dimension.Name.REVISION)
              .put(FastbootProperty.SECURE_BOOT, Dimension.Name.SECURE_BOOT)
              .put(FastbootProperty.DEVKEY_ALLOW, Dimension.Name.DEVKEY_ALLOW)
              .put(FastbootProperty.SBDP_ALLOW, Dimension.Name.SBDP_ALLOW)
              .put(FastbootProperty.SBDP_AR_CHECK, Dimension.Name.SBDP_AR_CHECK)
              .put(FastbootProperty.SBDP_AR_UPDATE, Dimension.Name.SBDP_AR_UPDATE)
              .put(FastbootProperty.AP_AR_NS, Dimension.Name.AP_AR_NS)
              .put(FastbootProperty.AP_AR_S, Dimension.Name.AP_AR_S)
              .put(FastbootProperty.SOC_ID, Dimension.Name.SOC_ID)
              .put(FastbootProperty.AR_FORCE_UPDATE, Dimension.Name.AR_FORCE_UPDATE)
              .put(FastbootProperty.AR_UPDATE_ALLOW, Dimension.Name.AR_UPDATE_ALLOW)
              .put(FastbootProperty.SERIALNO, Dimension.Name.SERIAL)
              .buildOrThrow();

  protected AndroidRealDeviceDelegate(
      AndroidDevice device,
      AndroidDeviceDelegate androidDeviceDelegate,
      @Nullable DeviceStat deviceStat,
      Clock clock,
      AndroidAdbInternalUtil androidAdbInternalUtil,
      AndroidAdbUtil androidAdbUtil,
      Adb adb,
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
      LocalFileUtil fileUtil,
      DeviceAdminUtil deviceAdminUtil,
      MtaasToolsInstantiator mtaasToolsInstantiator,
      TelephonyHelper telephonyHelper,
      AndroidFrpUtil androidFrpUtil) {
    this.device = device;
    this.androidDeviceDelegate = androidDeviceDelegate;
    this.deviceStat = deviceStat;
    this.clock = clock;
    this.androidAdbInternalUtil = androidAdbInternalUtil;
    this.androidAdbUtil = androidAdbUtil;
    this.adb = adb;
    this.androidProcessUtil = androidProcessUtil;
    this.systemSettingUtil = systemSettingUtil;
    this.androidFileUtil = androidFileUtil;
    this.connectivityUtil = connectivityUtil;
    this.systemStateUtil = systemStateUtil;
    this.systemSpecUtil = systemSpecUtil;
    this.androidPkgManagerUtil = androidPkgManagerUtil;
    this.androidMediaUtil = androidMediaUtil;
    this.androidUserUtil = androidUserUtil;
    this.chargingUtil = chargingUtil;
    this.networkUtil = networkUtil;
    this.apkInstaller = apkInstaller;
    this.systemStateManager = systemStateManager;
    this.deviceDaemonHelper = deviceDaemonHelper;
    this.fastboot = fastboot;
    this.fileUtil = fileUtil;
    this.deviceAdminUtil = deviceAdminUtil;
    this.mtaasToolsInstantiator = mtaasToolsInstantiator;
    this.telephonyHelper = telephonyHelper;
    this.androidFrpUtil = androidFrpUtil;

    this.deviceId = device.getDeviceId();
    device.setProperty(
        AndroidRealDeviceConstants.PROPERTY_NAME_REBOOT_TO_STATE, DeviceState.DEVICE.name());
    this.androidDeviceHelper = new AndroidDeviceHelper(androidAdbUtil);
  }

  /**
   * Initializes the Android real device. Prepares the supported device types, dimensions,
   * drivers/decorators.
   */
  public void setUp() throws MobileHarnessException, InterruptedException {
    if (shouldSetUpAsOnlineModeDevice()) {
      logger.atInfo().log(
          "Set up online mode device (%s). SetupFailureTimes=%d",
          deviceId, deviceStat != null ? deviceStat.getConsecutiveSetupFailureTimes() : -1);
      setUpOnlineModeDevice();
    } else if (Flags.instance().enableFastbootInAndroidRealDevice.getNonNull()) {
      if (fastboot.getDeviceSerials().contains(deviceId)) {
        setUpFastbootModeDevice();
      } else if (androidAdbInternalUtil
          .getDeviceSerialsByState(DeviceState.RECOVERY)
          .contains(deviceId)) {
        setUpRecoveryModeDevice();
      } else {
        // Bad connection devices could not be detected sometime.
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_REAL_DEVICE_DELEGATE_UNDETECTED_DURING_INIT,
            "Device is undetectable. Please replug the usb cable or reboot the device.");
      }
    }

    device.updateDimension(Dimension.Name.SUPPORTS_GMSCORE, Dimension.Value.TRUE);
    extrasInSetUp();

    lastSetupTime = clock.instant();
  }

  /** Returns {@code true} if the device should be set up as online mode device. */
  protected abstract boolean shouldSetUpAsOnlineModeDevice()
      throws MobileHarnessException, InterruptedException;

  /** Extra things for device set up in the {@link #setUp()} */
  protected abstract void extrasInSetUp() throws MobileHarnessException, InterruptedException;

  @VisibleForTesting
  protected abstract void enforceFlashSafetyChecksIfNeeded()
      throws MobileHarnessException, InterruptedException;

  private void setUpFastbootModeDevice() throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Setting up fastboot mode device for device %s", deviceId);
    if (isWipeRecoveryDevice()
        && deviceStat != null
        && deviceStat.getConsecutiveSetupFailureTimes()
            <= AndroidRealDeviceConstants.CONSECUTIVE_SETUP_FAILURE_NUM_TO_FASTBOOT_MODE) {
      try {
        fastboot.wipe(deviceId, /* retryTask= */ null);
        logger.atInfo().log("Wipe device %s.", deviceId);
        // Cache for reboot the device.
        cacheDevice(deviceId, AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT);
        logger.atInfo().log("Start rebooting the wiped device %s.", deviceId);

        fastboot.reboot(deviceId);
        // Wait for device ready.
        systemStateUtil.waitForState(
            deviceId,
            DeviceConnectionState.DEVICE,
            AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT);
        systemStateUtil.waitUntilReady(deviceId);
        logger.atInfo().log("Reboot the wiped device %s successfully.", deviceId);

        // Become root
        systemStateManager.becomeRoot(device);
        logger.atInfo().log("Device %s becomes root successfully.", deviceId);

        // Disable setup wizard and reboot device if needed.
        if (systemSettingUtil.disableSetupWizard(deviceId) == PostSettingDeviceOp.REBOOT) {
          logger.atInfo().log("Device %s will reboot after skip setup wizard", deviceId);
          systemStateUtil.reboot(deviceId);
          systemStateUtil.waitForState(
              deviceId,
              DeviceConnectionState.DEVICE,
              AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT);
          systemStateUtil.waitUntilReady(deviceId);
        }
        logger.atInfo().log("Disable setup wizard and reboot device %s successfully.", deviceId);
      } catch (MobileHarnessException e) {
        AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
        logger.atWarning().log("%s", e.getMessage());
        throw e;
      } finally {
        invalidateCacheDevice(deviceId);
      }
      setUpOnlineModeDevice();
    } else if (isAllowedToRebootFastbootDeviceToRecover()) {
      // Set REBOOT_TO_STATE property to DeviceState.DEVICE and throw MobileHarnessException to
      // interrupt device setup so LocalDeviceRunner reboot device later.
      AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.DEVICE);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REAL_DEVICE_REBOOT_FASTBOOT_DEVICE_TO_RECOVER,
          String.format(
              "Device %s has dimension %s with value true, reboot device to normal mode later",
              deviceId, Ascii.toLowerCase(Dimension.Name.REBOOT_TO_RECOVER.name())));
    } else {
      device.addSupportedDriver(AndroidRealDeviceConstants.NO_OP_DRIVER);
      device.addSupportedDriver(AndroidRealDeviceConstants.MOBLY_TEST_DRIVER);
      device.addSupportedDriver(AndroidRealDeviceConstants.MOBLY_AOSP_TEST_DRIVER);
      ImmutableMap<FastbootProperty, String> requiredFastbootProperties =
          addFastbootPropertiesToDimension(
              REQUIRED_FASTBOOT_PROPERTY_TO_DIMENSION_NAME, /* isRequired= */ true);
      addFastbootPropertiesToDimension(
          OPTIONAL_FASTBOOT_PROPERTY_TO_DIMENSION_NAME, /* isRequired= */ false);

      addFastbootCommunication(deviceId);
    }
  }

  @CanIgnoreReturnValue
  private ImmutableMap<FastbootProperty, String> addFastbootPropertiesToDimension(
      ImmutableMap<FastbootProperty, Dimension.Name> fastbootPropertyToDimensionName,
      boolean isRequired)
      throws MobileHarnessException, InterruptedException {
    ImmutableMap.Builder<FastbootProperty, String> fastbootPropertyToValue = ImmutableMap.builder();
    for (FastbootProperty fastbootProperty : fastbootPropertyToDimensionName.keySet()) {
      try {
        String value = fastboot.getVar(deviceId, fastbootProperty);
        if (!Strings.isNullOrEmpty(value)) {
          device.updateDimension(
              fastbootPropertyToDimensionName.get(fastbootProperty), Ascii.toLowerCase(value));
          fastbootPropertyToValue.put(fastbootProperty, value);
        } else {
          fastbootPropertyToValue.put(fastbootProperty, "");
        }
      } catch (MobileHarnessException e) {
        if (isRequired) {
          throw e;
        } else {
          logger.atInfo().log(
              "Failed to get %s for fastboot device %s: %s",
              fastbootProperty.name(), deviceId, MoreThrowables.shortDebugString(e));
        }
      }
    }
    return fastbootPropertyToValue.buildOrThrow();
  }

  private Optional<USB.Builder> parseUsbLocation(String usbLocation) {
    logger.atInfo().log("Parsing usb location %s of device %s", usbLocation, deviceId);
    if (usbLocation.isEmpty()) {
      return Optional.empty();
    }
    List<String> usbBusAndPort = Splitter.onPattern("-").splitToList(usbLocation);
    if (usbBusAndPort.size() != 2) {
      return Optional.empty();
    }
    return Optional.of(
        USB.newBuilder()
            .setHostBus(Integer.parseInt(usbBusAndPort.get(0)))
            .setHostPort(usbBusAndPort.get(1)));
  }

  private void addFastbootCommunication(String deviceId) throws InterruptedException {
    Optional<USB.Builder> usbLocation = parseUsbLocation(fastboot.getUsbLocation(deviceId));
    device.setCommunicationDimensionAndProperty(
        CommunicationList.newBuilder()
            .addCommunication(
                Communication.newBuilder()
                    .setUsb(usbLocation.orElse(USB.newBuilder().setSerial(deviceId))))
            .build());
  }

  private void addAdbCommunication(String deviceId) throws InterruptedException {
    CommunicationList.Builder communicationList = CommunicationList.newBuilder();
    // Adds the ADB communication.
    ADB adbCommunication = ADB.newBuilder().setSerial(deviceId).build();
    // Maybe add the USB communication.
    Optional<UsbDeviceLocator> deviceLocator = Optional.empty();
    try {
      deviceLocator = Optional.of(systemSpecUtil.getUsbLocator(deviceId));
    } catch (MobileHarnessException e) {
      logger.atInfo().log("Failed to get USB locator: %s", e.getMessage());
    }
    if (deviceLocator.isPresent()) {
      communicationList.addCommunication(
          Communication.newBuilder()
              .setAdbOverUsb(
                  ADBOverUSB.newBuilder()
                      .setUsb(
                          USB.newBuilder()
                              .setHostBus(deviceLocator.get().hostBus())
                              .setHostPort(deviceLocator.get().hostPort()))
                      .setAdb(adbCommunication)));
    } else {
      communicationList.addCommunication(Communication.newBuilder().setAdb(adbCommunication));
    }
    device.setCommunicationDimensionAndProperty(communicationList.build());
  }

  /** Execute device validation during the device setup process once the device is ready. */
  protected abstract void validateDeviceOnceReady(String deviceId, String deviceClassName)
      throws MobileHarnessException, InterruptedException;

  private void setUpOnlineModeDevice() throws MobileHarnessException, InterruptedException {
    androidDeviceDelegate.ensureDeviceReady();
    resetDevice();
    validateDeviceOnceReady(deviceId, device.getClass().getSimpleName());
    androidDeviceDelegate.setUp(isRooted(), extraDimensionsForSetUpDevice());

    addRealDeviceBasicDimensionsAndProperties();
    addRealDeviceBasicSupportedDriversDecorators();
    addExtraRealDeviceBasicSupportedDriversDecorators();

    // Switch the user to default user before setting and apk installation.
    clearMultiUsers(deviceId, device.getSdkVersion() == null ? 0 : device.getSdkVersion());

    // Trigger checkin.
    installMtaasToolsAndTriggerCheckin();

    // The periodical check also applies to device initialization.
    checkOnlineModeDevice();

    if (!ifEnableFullStackFeatures()) {
      logger.atInfo().log("Device %s is ready", deviceId);
      return;
    }

    addRealDeviceFullStackSupportedDeviceTypesDriversDecorators();
    addExtraRealDeviceFullStackSupportedDriversDecorators();
    addRealDeviceFullStackDimensions();
    startActivityController();
    extraSettingsForFullStackDevice();
    execBeforeFinishSetupAdbShellCommands();
    logger.atInfo().log("Device %s is ready", deviceId);
  }

  /** Extra dimensions added to the device in the {@link #setUpOnlineModeDevice()} */
  protected abstract Multimap<Dimension.Name, String> extraDimensionsForSetUpDevice();

  /**
   * Resets the device if needed.
   *
   * <p>It will throw the exception out if it hit any error during the reset process so to restart
   * the device setup process later.
   */
  private void resetDevice() throws MobileHarnessException, InterruptedException {
    if (!Flags.instance().resetDeviceInAndroidRealDeviceSetup.get()) {
      return;
    }

    // Unlock the device admin policy before actually resetting the device. If the device is locked,
    // resetting device will be ignored by Android and we don't know if the reset was successful.
    if (Flags.instance().deviceAdminLockRequired.getNonNull()) {
      unlockWithDeviceAdmin();
    }
    int sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
    if (sdkVersion < AndroidVersion.ANDROID_10.getStartSdkVersion()) {
      logger.atInfo().log(
          "Skip resetting device %s because its SDK version %d is lower than Android 10.",
          deviceId, sdkVersion);
      return;
    }

    boolean isOverTcpDevice = DeviceUtil.isOverTcpDevice(deviceId);
    cacheDevice(deviceId, AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT);

    // Set the property to the device. Will check the value of this property after resetting to
    // ensure the reset is successful.
    androidAdbUtil.setProperty(deviceId, RESET_PROPERTY_LABEL, "true", /* ignoreError= */ false);
    try {
      logger.atInfo().log("Start to factory reset device %s via test harness", deviceId);
      androidFrpUtil.factoryResetViaTestHarnessWithFrpClear(
          device, /* waitTime= */ null, /* log= */ null);
      if (isOverTcpDevice) {
        systemStateUtil.waitForOverTcpDeviceConnection(deviceId, Duration.ofMinutes(5));
      }
    } finally {
      invalidateCacheDevice(deviceId);
    }

    Duration deviceReadyTimeout = Duration.ofMinutes(5);
    cacheDevice(deviceId, deviceReadyTimeout.multipliedBy(2));
    try {
      // `adb wait-for-device` exits with error code when using a proxied device. Therefore, we wait
      // for the device ready by using `systemStateUtil.waitUntilReady` instead.
      if (!DeviceUtil.isOverTcpDevice(deviceId)) {
        systemStateUtil.waitForState(deviceId, DeviceConnectionState.DEVICE, deviceReadyTimeout);
      }
      systemStateUtil.waitUntilReady(deviceId, deviceReadyTimeout);
    } finally {
      invalidateCacheDevice(deviceId);
    }

    try {
      if (!Strings.isNullOrEmpty(
          androidAdbUtil.getProperty(deviceId, ImmutableList.of(RESET_PROPERTY_LABEL)))) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_REAL_DEVICE_DELEGATE_RESET_DEVICE_FAILED,
            String.format(
                "The property %s is not deleted after resetting on device %s, which means the"
                    + " device is not reset successfully.",
                RESET_PROPERTY_LABEL, deviceId));
      }
    } finally {
      androidAdbUtil.setProperty(deviceId, RESET_PROPERTY_LABEL, "", /* ignoreError= */ true);
    }
    logger.atInfo().log("Device %s reset is done", deviceId);
  }

  /**
   * Adds real device basic dimensions and properties.
   *
   * <p>This method should not throw checked exceptions other than {@code InterruptedException}, to
   * ensure it won't interrupt the device setup process.
   */
  private void addRealDeviceBasicDimensionsAndProperties() throws InterruptedException {
    addAdbCommunication(deviceId);
    // Adds real device specific dimensions.
    device.addDimension(Dimension.Name.DEVICE_FORM, Dimension.Value.PHYSICAL);
    try {
      device.addDimension(
          Name.MACHINE_HARDWARE_NAME, systemSpecUtil.getMachineHardwareName(deviceId));
    } catch (MobileHarnessException e) {
      logger.atInfo().log("%s", e.getMessage());
    }
    try {
      device.addDimension(
          Dimension.Name.NUM_CPUS, String.valueOf(systemSpecUtil.getNumberOfCpus(deviceId)));
    } catch (MobileHarnessException e) {
      logger.atInfo().log("%s", e.getMessage());
    }
    int frequency = systemSpecUtil.getMaxCpuFrequency(deviceId);
    if (frequency != 0) {
      device.addDimension(
          Name.CPU_FREQ_IN_GHZ, String.format(Locale.US, "%.1f", frequency / 1000000f));
    }
    try {
      device.addDimension(Dimension.Name.MAC_ADDRESS, systemSpecUtil.getMacAddress(deviceId));
    } catch (MobileHarnessException e) {
      logger.atInfo().log("%s", e.getMessage());
    }
    try {
      device.addDimension(
          Dimension.Name.BLUETOOTH_MAC_ADDRESS, systemSpecUtil.getBluetoothMacAddress(deviceId));
    } catch (MobileHarnessException e) {
      logger.atInfo().log("%s", e.getMessage());
    }

    try {
      device.addDimension(
          Dimension.Name.MCC_MNC,
          androidAdbUtil.getProperty(
              deviceId, ImmutableList.of(AndroidRealDeviceConstants.DEVICE_PROP_NAME_MCC_MNC)));
    } catch (MobileHarnessException e) {
      logger.atInfo().log("%s", e.getMessage());
    }

    try {
      int totalMemInMb = systemSpecUtil.getTotalMem(deviceId) / 1024;
      device.addDimension(Dimension.Name.TOTAL_MEMORY, totalMemInMb + " MB");
      device.addDimension(
          Dimension.Name.SVELTE_DEVICE,
          String.valueOf(totalMemInMb <= AndroidRealDeviceConstants.MAX_SVELTE_MEMORY_IN_MB));
      Set<String> whiteListFeatures = getSystemFeaturesByWhitelist(deviceId);
      for (String whitelistFeature : whiteListFeatures) {
        device.addDimension(Dimension.Name.FEATURE, whitelistFeature);
      }
    } catch (MobileHarnessException e) {
      logger.atInfo().log("%s", e.getMessage());
    }

    try {
      device.addDimension(
          Name.MEMORY_CLASS_IN_MB, String.valueOf(systemSpecUtil.getMemoryClassInMb(deviceId)));
    } catch (MobileHarnessException e) {
      logger.atInfo().log("%s", e.getMessage());
    }

    // Gets the device external storage path.
    logger.atInfo().log("Checking device %s external storage...", deviceId);
    try {
      Integer sdkVersion = device.getSdkVersion();
      String externalStoragePath =
          androidFileUtil.getExternalStoragePath(deviceId, sdkVersion == null ? 0 : sdkVersion);
      device.addDimension(Dimension.Name.WRITABLE_EXTERNAL_STORAGE, externalStoragePath.trim());
      logger.atInfo().log("Device %s external storage writable: %s", deviceId, externalStoragePath);
    } catch (MobileHarnessException e) {
      logger.atWarning().log("%s", e.getMessage());
    }
    int storageLifetime = systemSpecUtil.getStorageLifetime(deviceId);
    if (0 <= storageLifetime && storageLifetime <= 100) {
      device.addDimension(Name.STORAGE_LIFETIME_PERCENTAGE, String.valueOf(storageLifetime));
    }
  }

  private void addRealDeviceBasicSupportedDriversDecorators()
      throws MobileHarnessException, InterruptedException {
    // HD Video decorator is supported by real device only.
    device.addSupportedDecorator("AndroidHdVideoDecorator");
  }

  /** Returns {@code true} if need to enable device flash and reset decorators. */
  protected abstract boolean ifEnableDeviceFlashAndResetDecorators();

  /** Adds extra supported drivers and decorators to the device. */
  protected abstract void addExtraRealDeviceBasicSupportedDriversDecorators()
      throws InterruptedException;

  /** Returns {@code true} if it enables full stack features. */
  protected abstract boolean ifEnableFullStackFeatures();

  /**
   * Adds drivers and decorators supported by rooted or non-rooted devices for full stack features.
   */
  private void addRealDeviceFullStackSupportedDeviceTypesDriversDecorators()
      throws MobileHarnessException, InterruptedException {
    // *************** More drivers ***************
  }

  /** Adds device extra supported drivers and decorators when the full stack features enabled. */
  protected abstract void addExtraRealDeviceFullStackSupportedDriversDecorators()
      throws InterruptedException;

  private void addRealDeviceFullStackDimensions()
      throws MobileHarnessException, InterruptedException {
    Integer sdkVersion = device.getSdkVersion();
    try {
      // Add IMEI dimension. Note that device may not have IMEI, if it does not support telephony.
      systemSpecUtil
          .getDeviceImei(deviceId, sdkVersion == null ? 0 : sdkVersion)
          .ifPresent(imei -> device.addDimension(Dimension.Name.IMEI, imei));
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Failed to get device %s IMEI: %s", deviceId, MoreThrowables.shortDebugString(e));
    }

    // Adds screenshot dimension.
    if (ifScreenshotAble(sdkVersion)) {
      device.addDimension(Dimension.Name.SCREENSHOT_ABLE, String.valueOf(true));
    }

    if (Flags.instance().checkAndroidDeviceSimCardType.getNonNull()) {
      try {
        telephonyHelper.updateSimDimensions(device);
      } catch (MobileHarnessException e) {
        logger.atInfo().log(
            "Failed to get device %s SIM card type: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
    }

    if (!isRooted()) {
      return;
    }

    try {
      // Add ICCID dimension. Note that device may not have ICCID, if it does not support telephony.
      systemSpecUtil
          .getDeviceIccid(deviceId, sdkVersion == null ? 0 : sdkVersion)
          .ifPresent(iccid -> device.addDimension(Dimension.Name.ICCID, iccid));
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Failed to get device %s ICCID: %s", deviceId, MoreThrowables.shortDebugString(e));
    }

    // Add a comma-delimited list of ICCIDS to device dimensions.
    try {
      device.addDimension(Dimension.Name.ICCIDS, getCommaSeparatedIccids());
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Failed to get list of SIM ICCIDS for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    }
  }

  /**
   * Returns {@code true} if the device supports the feature of taking screen shot and uploading it
   * to host machine.
   */
  protected abstract boolean ifScreenshotAble(Integer sdkVersion);

  /** Starts the activity controller on the device. */
  protected abstract void startActivityController() throws InterruptedException;

  /** Stops the activity controller on the device. */
  protected abstract void stopActivityController() throws InterruptedException;

  /** Extra settings for rooted or non-rooted devices for full stack features. */
  private void extraSettingsForFullStackDevice()
      throws MobileHarnessException, InterruptedException {
    // Locks the device with device admin if the flag --device_admin_lock_required is set to
    // true.
    // TODO Based on the current behavior, a lock failure will cause the device to fail
    // to setup and then reboot. Carefully consider whether we should catch and log the exception
    // and continue the setup process if the lock operation fails.
    if (Flags.instance().deviceAdminLockRequired.getNonNull()) {
      lockWithDeviceAdmin();
    }

    // Disables airplane mode, enables unknown source. Only works with SDK version >= 17.
    Integer sdkVersion = device.getSdkVersion();
    if (Flags.instance().enableDeviceSystemSettingsChange.getNonNull()
        && sdkVersion != null
        && sdkVersion >= 17) {
      logger.atInfo().log("Checking device %s airplane mode...", deviceId);
      try {
        boolean currentAirplaneMode = systemSettingUtil.getAirplaneMode(deviceId);
        boolean targetAirplaneMode = Flags.instance().enableDeviceAirplaneMode.getNonNull();
        if (currentAirplaneMode ^ targetAirplaneMode) {
          logger.atInfo().log(
              "Device %s current airplane mode is [%s], set its airplane mode to [%s].",
              deviceId, currentAirplaneMode, targetAirplaneMode);
          systemSettingUtil.setAirplaneMode(deviceId, targetAirplaneMode);
        } else {
          logger.atInfo().log(
              "Device %s current airplane mode is [%s], which is same as the target.",
              deviceId, currentAirplaneMode);
        }
      } catch (MobileHarnessException e) {
        logger.atInfo().log("%s", e.getMessage());
      }

      logger.atInfo().log("Disable device %s package verifier", deviceId);
      androidPkgManagerUtil.disablePackageVerifier(deviceId, sdkVersion);

      logger.atInfo().log("Enable device %s unknown sources...", deviceId);
      try {
        systemSettingUtil.enableUnknownSources(deviceId, sdkVersion);
        logger.atInfo().log("Device %s unknown sources is enabled", deviceId);
      } catch (MobileHarnessException e) {
        logger.atInfo().log(
            "Failed to enable device %s unknown source: %s", deviceId, e.getMessage());
      }
    }

    if (!isRooted()) {
      return;
    }

    // Lets the device know it is running in a test harness.
    logger.atInfo().log("Set device %s test properties", deviceId);
    // This function will make all flags working, if anyone wants to modify the flag in special
    // situation, please make sure the modification is running before this function.
    enableTestPropertiesAndDisablePackages();
    // Tries to keep device awake.
    logger.atInfo().log("Device %s stays awake", deviceId);
    try {
      systemSettingUtil.keepAwake(deviceId, /* alwaysAwake= */ true);
    } catch (MobileHarnessException e) {
      if (e.getMessage().contains("exit_code=137")) {
        // Some Samsung devices return exit_code=137 when keep awake. See b/402566355.
        logger.atInfo().log(
            "Ignore the failure of keeping device %s awake: %s", deviceId, e.getMessage());
      } else {
        throw e;
      }
    }

    // Forces USB to 'adb' mode only.
    logger.atInfo().log("Check device %s USB mode", deviceId);
    systemSettingUtil.forceUsbToAdbMode(deviceId);

    // Try to disable screen lock.
    // ScreenLock not used on Android Automotive devices due to b/135046763#comment51
    Set<String> features = systemSpecUtil.getSystemFeatures(deviceId);
    if (!features.contains(AndroidRealDeviceConstants.FEATURE_EMBEDDED)
        && !features.contains(AndroidRealDeviceConstants.FEATURE_AUTOMOTIVE)) {
      logger.atInfo().log("Disable device %s screen lock", deviceId);
      systemSettingUtil.disableScreenLock(deviceId, sdkVersion);
    }
  }

  private void execBeforeFinishSetupAdbShellCommands()
      throws MobileHarnessException, InterruptedException {
    Optional<ConditionedDeviceConfigs> conditionedDeviceConfigsOpt =
        ConditionedDeviceConfigUtil.getConditionedDeviceConfigsFromDimensions(device);
    if (conditionedDeviceConfigsOpt.isEmpty()) {
      return;
    }

    ImmutableList<String> adbCommands =
        ConditionedDeviceConfigUtil.getBeforeFinishSetupAdbCommandsByDevice(
            conditionedDeviceConfigsOpt.get(), device);
    for (String adbCommand : adbCommands) {
      logger.atInfo().log("Running adb command on device %s:\n\t%s", deviceId, adbCommand);
      String output = adb.runShell(deviceId, adbCommand);
      logger.atInfo().log("Output of adb command on device %s:\n\t%s", deviceId, output);
    }
  }

  private void setUpRecoveryModeDevice() throws MobileHarnessException, InterruptedException {
    if (!Flags.instance().enableDeviceStateChangeRecover.getNonNull()) {
      configureRecoveryDevice();
      return;
    }

    try {
      logger.atInfo().log("Try to reboot the recovery device %s", deviceId);
      // Cache for reboot the device.
      cacheDevice(
          deviceId,
          AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT.plus(
              Constants.DEFAULT_ADB_COMMAND_TIMEOUT));
      systemStateUtil.reboot(deviceId);
      systemStateUtil.waitForState(
          deviceId,
          DeviceConnectionState.DEVICE,
          AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT);
      systemStateUtil.waitUntilReady(deviceId);
    } catch (MobileHarnessException e) {
      logger.atWarning().log("Failed to reboot the recovery device %s", deviceId);
      // If the device is still on recovery mode, update its configuration
      if (androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.RECOVERY).contains(deviceId)) {
        configureRecoveryDevice();
        return;
      }
    } finally {
      invalidateCacheDevice(deviceId);
    }

    // Recovery device is rebooted to normal mode
    if (androidAdbInternalUtil.getRealDeviceSerials(/* online= */ true).contains(deviceId)) {
      setUpOnlineModeDevice();
    } else {
      AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.DEVICE);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REAL_DEVICE_RECOVER_RECOVERY_DEVICE_FAILED,
          String.format("Failed to recover recovery device %s, reboot device later.", deviceId));
    }
  }

  private void configureRecoveryDevice() throws MobileHarnessException, InterruptedException {
    device.addSupportedDriver(AndroidRealDeviceConstants.NO_OP_DRIVER);
    androidDeviceHelper.updateAndroidPropertyDimensions(device);
  }

  public boolean checkDevice() throws MobileHarnessException, InterruptedException {
    if (!ifSkipCheckAbnormalDevice()
        && Flags.instance().enableDeviceStateChangeRecover.getNonNull()) {
      Optional<Boolean> abnormalDeviceCheckResult = checkAbnormalDevice();
      if (abnormalDeviceCheckResult.isPresent()) {
        return abnormalDeviceCheckResult.get();
      }
    }
    return checkOnlineModeDevice();
  }

  /**
   * Returns {@code true} if skip checking device in abnormal modes like fastboot mode, recovery
   * mode.
   */
  protected abstract boolean ifSkipCheckAbnormalDevice();

  /**
   * Returns whether the device is changed, to notify the Device Manager to sync the new device
   * info.
   *
   * <p>When empty Optional returned, it means device is normal and needs to continue online mode
   * device regular check.
   */
  private Optional<Boolean> checkAbnormalDevice()
      throws MobileHarnessException, InterruptedException {
    // Any recovery mode device should not be found when checking device. Reboot it to fastboot
    // mode to notify the lab admins.
    if (androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.RECOVERY).contains(deviceId)) {
      logger.atInfo().log("Checking recovery device %s. Rebooting...", deviceId);
      AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REAL_DEVICE_DELEGATE_RECOVERY_DEVICE_TO_REBOOT,
          "Checking recovery device. Rebooting to fastboot mode.");
    }

    // Checks the device which is the fastboot mode.
    if (Flags.instance().enableFastbootInAndroidRealDevice.getNonNull()
        && fastboot.getDeviceSerials().contains(deviceId)) {
      if (device.getDeviceTypes().contains(AndroidRealDeviceConstants.ANDROID_FASTBOOT_DEVICE)) {
        if (isWipeRecoveryDevice()
            && clock
                .instant()
                .isAfter(lastSetupTime.plus(AndroidRealDeviceConstants.AUTO_FASTWIPE_TIMEOUT))) {
          AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_REAL_DEVICE_DELEGATE_FASTBOOT_DEVICE_TO_REBOOT,
              "Checking fastboot device. Rebooting to fastboot mode.");
        } else if (!isRecoveryDevice()
            && clock
                .instant()
                .isAfter(lastSetupTime.plus(AndroidRealDeviceConstants.AUTO_RECOVERY_TIMEOUT))) {
          AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_REAL_DEVICE_DELEGATE_FASTBOOT_DEVICE_TO_REBOOT,
              "Checking fastboot device. Rebooting to fastboot mode.");
        }
        return Optional.of(false);
      }
      logger.atInfo().log("Detected device %s becoming into fastboot mode.", deviceId);
      device.info().deviceTypes().clear();
      device.info().supportedDecorators().clear();
      device.info().supportedDrivers().clear();
      setUpFastbootModeDevice();
      return Optional.of(true);
    }

    // Reboots the device when the mode changed from fastboot/recovery to online.
    if (!device.getDeviceTypes().contains("AndroidRealDevice")) {
      logger.atInfo().log("Detected device %s becoming online.", deviceId);
      device.info().deviceTypes().clear();
      device.info().supportedDecorators().clear();
      device.info().supportedDrivers().clear();
      setUpOnlineModeDevice();
      return Optional.of(true);
    }
    return Optional.empty();
  }

  /** Checks a device which is in online mode. */
  @CanIgnoreReturnValue
  @SuppressWarnings("ShortCircuitBoolean")
  boolean checkOnlineModeDevice() throws MobileHarnessException, InterruptedException {
    boolean isDimensionChanged = androidDeviceDelegate.checkDevice();
    boolean isNetworkChanged =
        alwaysCheckNetwork()
            ? checkNetwork()
            : !Flags.instance().skipNetwork.getNonNull() && checkNetwork();
    if (onlyCheckNetworkWhenCheckOnlineModeDevice()) {
      return isDimensionChanged || isNetworkChanged;
    }
    // Device services should be available at this point but need to confirm, otherwise latter adb
    // commands may fail (b/134529577). In this case, try to reboot device to recover it.
    checkOnlineModeDeviceServiceAvailable(deviceId);
    // Uses non-circuit OR to make sure all checks are executed.
    isDimensionChanged =
        isDimensionChanged
            | isNetworkChanged
            | checkBattery()
            | checkAndCleanInternalStorage()
            | checkStorage(/* isExternal= */ true)
            | checkLaunchers()
            | checkIccids()
            | checkHingeAngle()
            | checkGmscoreSignature()
            | extraChecksForOnlineModeDevice();
    checkSuwAppDisabled();
    if (!Flags.instance().skipNetwork.getNonNull()) {
      isDimensionChanged = isDimensionChanged | checkWifiRssi();
    }
    if (Flags.instance().pingGoogle.getNonNull()) {
      Instant currentTime = clock.instant();
      // Checks if the time gap is more than 30 minutes.
      if (isNetworkChanged
          || lastCheckPingGoogleTime == null
          || currentTime.isAfter(
              lastCheckPingGoogleTime.plus(AndroidRealDeviceConstants.GOOGLE_PING_INTERVAL))) {
        isDimensionChanged = isDimensionChanged | checkPingGoogle();
        lastCheckPingGoogleTime = currentTime;
      }
    }

    // Install and start daemon apk on device.
    if (DeviceDaemonApkInfoProvider.isDeviceDaemonEnabled()) {
      boolean shouldInstallAndStartDaemon = false;

      // Shows dimension labels as a custom property.
      String dimensionLabels = Joiner.on('\n').join(device.getDimension(Dimension.Name.LABEL));
      String propLabels = device.getProperty(AndroidRealDeviceConstants.PROP_LABELS);
      if (propLabels == null) {
        device.setProperty(AndroidRealDeviceConstants.PROP_LABELS, dimensionLabels);
      } else if (!propLabels.equals(dimensionLabels)) {
        // Updates the labels shown on the device.
        device.setProperty(AndroidRealDeviceConstants.PROP_LABELS, dimensionLabels);
        shouldInstallAndStartDaemon = true;
      }

      // Show owner label.
      String ownerList = device.getOwners().toString();
      String propOwnerList = device.getProperty(AndroidRealDeviceConstants.PROP_OWNERS);
      if (!ownerList.equals(propOwnerList)) {
        device.setProperty(AndroidRealDeviceConstants.PROP_OWNERS, ownerList);
        shouldInstallAndStartDaemon = true;
      }

      // Show executor label.
      String executorList = device.getExecutors().toString();
      String propExecutorList = device.getProperty(AndroidRealDeviceConstants.PROP_EXECUTORS);
      if (!executorList.equals(propExecutorList)) {
        device.setProperty(AndroidRealDeviceConstants.PROP_EXECUTORS, executorList);
        shouldInstallAndStartDaemon = true;
      }

      // Sets the hostname shown on the device daemon.
      String hostname = null;
      try {
        hostname = networkUtil.getLocalHostName();
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to set host name to device daemon for device %s: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
      if (hostname != null) {
        // Hides the suffix from the hostname.
        String propHostName = device.getProperty(AndroidRealDeviceConstants.PROP_HOSTNAME);
        if (!hostname.equals(propHostName)) {
          device.setProperty(AndroidRealDeviceConstants.PROP_HOSTNAME, hostname);
          shouldInstallAndStartDaemon = true;
        }
      }

      if (shouldInstallAndStartDaemon) {
        deviceDaemonHelper.installAndStartDaemon(
            device,
            /* log= */ null,
            device.getProperty(AndroidRealDeviceConstants.PROP_LABELS),
            device.getProperty(AndroidRealDeviceConstants.PROP_HOSTNAME),
            device.getProperty(AndroidRealDeviceConstants.PROP_OWNERS),
            device.getProperty(AndroidRealDeviceConstants.PROP_EXECUTORS));
      }
    } else {
      logger.atInfo().log("Android device daemon is disabled on device %s", deviceId);
      // If lab server start with no daemon flag, then kill daemon server during initialization.
      deviceDaemonHelper.uninstallDaemonApk(device, /* log= */ null);
    }
    startActivityController();

    enforceSafeDischargeLevelIfNeeded();
    enforceFlashSafetyChecksIfNeeded();

    isDimensionChanged |= updateCheckinGroupStatus();

    return isDimensionChanged;
  }

  /** Returns {@code true} if always need to check device network. */
  protected abstract boolean alwaysCheckNetwork();

  /**
   * Returns {@code true} if only checking device network while skip other device checks like
   * battery, storage, launcher, etc.
   */
  protected abstract boolean onlyCheckNetworkWhenCheckOnlineModeDevice();

  /** Returns {@code true} if any dimensions changed after the device checks. */
  protected abstract boolean extraChecksForOnlineModeDevice() throws InterruptedException;

  /**
   * Checks and updates the device's checkin group dimension.
   *
   * @return whether the device's checkin group dimension changed.
   */
  boolean updateCheckinGroupStatus() throws MobileHarnessException, InterruptedException {
    if (Flags.instance().enforceMtaasDeviceCheckinGroup.getNonNull()) {
      if (device
          .getDimension(Dimension.Name.MTAAS_DEVICE_CHECKIN_GROUP)
          .contains(Dimension.Value.TRUE)) {
        return false;
      }
      if (mtaasToolsInstantiator.isMemberOfCheckinGroup(device)) {
        return device.updateDimension(
            Dimension.Name.MTAAS_DEVICE_CHECKIN_GROUP, Dimension.Value.TRUE);
      } else {
        triggerCheckin();
        return device.updateDimension(
            Dimension.Name.MTAAS_DEVICE_CHECKIN_GROUP, Dimension.Value.FALSE);
      }
    }
    return false;
  }

  /** Android real device preparations before running the test. */
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    if (!androidAdbInternalUtil.getRealDeviceSerials(/* online= */ true).contains(deviceId)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "preRunTest of AndroidRealDevice [%s] skipped because it cannot be detected online.",
              deviceId);
      return;
    }
    androidDeviceDelegate.preRunTest(testInfo, isRooted());
    prependedRealDevicePreparationBeforeTest(testInfo);

    if (!skipRealDeviceDefaultPreparationBeforeTest()) {
      if (DeviceDaemonApkInfoProvider.isDeviceDaemonEnabled()) {
        // Makes sure the device daemon is started/stopped before every test.
        if (testInfo.jobInfo().params().isTrue(AndroidRealDeviceSpec.PARAM_KILL_DAEMON_ON_TEST)) {
          testInfo.log().atInfo().alsoTo(logger).log("Kill device daemon on device %s", deviceId);
          deviceDaemonHelper.uninstallDaemonApk(device, testInfo.log());
        } else {
          deviceDaemonHelper.installAndStartDaemon(
              device,
              testInfo.log(),
              device.getProperty(AndroidRealDeviceConstants.PROP_LABELS),
              device.getProperty(AndroidRealDeviceConstants.PROP_HOSTNAME),
              device.getProperty(AndroidRealDeviceConstants.PROP_OWNERS),
              device.getProperty(AndroidRealDeviceConstants.PROP_EXECUTORS));
        }
      } else {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Device daemon is disabled on device %s", deviceId);
      }
      // Disable activity controller based on PARAM_DISABLE_ACTIVITY_CONTROLLER_ON_TEST
      if (testInfo
          .jobInfo()
          .params()
          .isTrue(AndroidRealDeviceConstants.PARAM_DISABLE_ACTIVITY_CONTROLLER_ON_TEST)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Disable activity controller on device %s", deviceId);
        stopActivityController();
      } else {
        startActivityController();
      }
      // Disables the NFC if feasible.
      if (Flags.instance().enableDeviceSystemSettingsChange.getNonNull()
          && device.getBooleanProperty(AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED)) {
        try {
          Integer sdkVersion = device.getSdkVersion();
          connectivityUtil.setNFC(
              deviceId, sdkVersion == null ? 0 : sdkVersion, /* enabled= */ false);
        } catch (MobileHarnessException e) {
          // Catches the exception to not disturb the normal running.
          logger.atInfo().log("Failed to disable device %s NFC: %s", deviceId, e.getMessage());
        }
      }
      // start charge in case the device got power off during long test. b/148306058
      enableDeviceChargeBeforeTest(testInfo);
    }
    if (testInfo
        .jobInfo()
        .params()
        .getBool(
            AndroidRealDeviceConstants.PARAM_CLEAR_GSERVICES_OVERRIDES, /* defaultValue= */ true)) {
      clearGServicesOverrides(testInfo);
    }
  }

  /**
   * Customized device preparation before running the test which is executed before the default one.
   */
  protected abstract void prependedRealDevicePreparationBeforeTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  /** Returns {@code true} if skip default device prepration before running the test. */
  protected abstract boolean skipRealDeviceDefaultPreparationBeforeTest();

  @CanIgnoreReturnValue
  private Optional<PostTestDeviceOp> checkRecovery()
      throws MobileHarnessException, InterruptedException {
    if (isWipeRecoveryDevice()) {
      // No permission to factory reset for non-rooted device.
      if (isRooted()) {
        // TODO: Add monitors for fast wipe.
        // If wipe recovery enabled, reboot the device to fastboot and wipe the device.
        logger.atInfo().log("Fast wipe device %s to clean all user data.", deviceId);
        AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
        return Optional.of(PostTestDeviceOp.REBOOT);
      } else {
        logger.atWarning().log(
            "Skip factory reset device %s since it‘s invalid for non-rooted devices.", deviceId);
        return Optional.empty();
      }
    } else if (shouldFactoryResetViaTestHarness()) {
      // add the dimension before checking sdk version to avoid device failing checking the sdk
      // version is regarded as a recoveried device (i.e., without recovery_status dimension).
      device.addDimension(Dimension.Name.RECOVERY_STATUS, "dirty");
      if (isQOrAboveBuild(deviceId)) {
        logger.atInfo().log("It'll factory reset device %s via Test Harness Mode.", deviceId);
        AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.DEVICE);
        return Optional.of(PostTestDeviceOp.REBOOT);
      } else {
        logger.atInfo().log(
            "Factory reset via Test Harness Mode is not supported on device %s.", deviceId);
        device.removeDimension(Dimension.Name.RECOVERY_STATUS);
        return Optional.empty();
      }
    } else {
      // If fast recovery enabled, reboot the device to recovery and clean all user data even if
      // the system build changed.
      logger.atInfo().log("Reboot device %s to recovery to clean all user data.", deviceId);
      AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.RECOVERY);
      return Optional.of(PostTestDeviceOp.REBOOT);
    }
  }

  /** Operations after a test and before resetting/reloading the driver. */
  @CanIgnoreReturnValue
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    // Removes the device cache after tests finish, otherwise device status may be wrong. b/32101092
    invalidateCacheDevice(device.info().deviceId().controlId());

    prependedRealDeviceAfterTestProcess(testInfo);

    // Unlocking should be done before resetting the device with test harness.
    // TODO Unlocking failure may cause the device fail to reset with test harness
    // mode.
    if (device.getDimension(Dimension.Name.DEVICE_ADMIN_LOCKED).contains(Dimension.Value.TRUE)) {
      unlockWithDeviceAdmin();
    }

    // Core lab devices won't have "recovery" device dimension.
    if (isRecoveryDevice()) {
      try {
        Optional<PostTestDeviceOp> postTestDeviceOp = checkRecovery();
        if (postTestDeviceOp.isPresent()) {
          return postTestDeviceOp.get();
        }
      } catch (MobileHarnessException e) {
        logger.atInfo().log(
            "Failed to check recovery status for device [%s]: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
    }

    if (!skipRealDeviceDefaultAfterTestProcess()) {
      // Reboot the device if system build changed.
      ImmutableSet<String> builds =
          device.getDimension(Ascii.toLowerCase(AndroidProperty.BUILD.name())).stream()
              .map(Ascii::toLowerCase)
              .collect(toImmutableSet());
      if (builds.size() == 1) {
        if (!Ascii.equalsIgnoreCase(
            builds.stream().findFirst().get(),
            androidAdbUtil.getProperty(deviceId, AndroidProperty.BUILD))) {
          logger.atInfo().log("Reboot device %s since the system build changed.", deviceId);
          AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.DEVICE);
          return PostTestDeviceOp.REBOOT;
        }
      } else {
        logger.atWarning().log("Found different build info for device %s: %s", deviceId, builds);
      }

      try {
        // Switch back to default user if test forget to do so before install application.
        clearMultiUsers(deviceId, systemSettingUtil.getDeviceSdkVersion(deviceId));

        // Reboot the device if PackageManager UID got exhausted.
        if (hasUidExhausted(deviceId)) {
          logger.atInfo().log("Reboot device %s since UID for PackageManager exhausted", deviceId);
          return PostTestDeviceOp.REBOOT;
        }
        // Reboot the device if the test fails to assign a valid uid when installing apks.
        if (testInfo.resultWithCause().get().causeProto().isPresent()) {
          ExceptionProto.ExceptionDetail exceptionDetail =
              testInfo.resultWithCause().get().causeProto().get();
          while (true) {
            if (ErrorIdComparator.equal(
                exceptionDetail.getSummary().getErrorId(),
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_FAILED_NO_VALID_UID_ASSIGNED)) {
              return PostTestDeviceOp.REBOOT;
            }
            if (!exceptionDetail.hasCause()) {
              break;
            }
            exceptionDetail = exceptionDetail.getCause();
          }
        }

        // Always brings the device daemon back after test.
        if (DeviceDaemonApkInfoProvider.isDeviceDaemonEnabled()) {
          deviceDaemonHelper.installAndStartDaemon(
              device,
              testInfo.log(),
              device.getProperty(AndroidRealDeviceConstants.PROP_LABELS),
              device.getProperty(AndroidRealDeviceConstants.PROP_HOSTNAME),
              device.getProperty(AndroidRealDeviceConstants.PROP_OWNERS),
              device.getProperty(AndroidRealDeviceConstants.PROP_EXECUTORS));
          if (testInfo.jobInfo().params().isTrue(AndroidRealDeviceSpec.PARAM_KILL_DAEMON_ON_TEST)) {
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Bring device daemon back on device %s", deviceId);
          }
        }
        startActivityController();
      } catch (MobileHarnessException e) {
        logger.atInfo().log(
            "Reboot device %s since exception caught for post run test: %s",
            deviceId, e.getMessage());
        // Reboot device instead of fail the test if:
        // 1. Failed to switch user to default user.
        // 2. Failed to install daemon application.
        return PostTestDeviceOp.REBOOT;
      }
    }

    // Reboot the device if it just finished AndroidTradefedTest execution which will always flash
    // device.
    if (Ascii.equalsIgnoreCase(testInfo.jobInfo().type().getDriver(), "AndroidTradefedTest")) {
      logger.atInfo().log("Reboot device %s after AndroidTradefedTest been executed.", deviceId);
      return PostTestDeviceOp.REBOOT;
    }

    return PostTestDeviceOp.NONE;
  }

  /**
   * Customized operations in the device after test step which is executed before the default one.
   */
  protected abstract void prependedRealDeviceAfterTestProcess(TestInfo testInfo)
      throws InterruptedException;

  /** Returns {@code true} if skip the default operations after the test. */
  protected abstract boolean skipRealDeviceDefaultAfterTestProcess()
      throws MobileHarnessException, InterruptedException;

  /** Reboots the Android real device. */
  public void reboot() throws MobileHarnessException, InterruptedException {
    if (Flags.instance().disableDeviceReboot.getNonNull()) {
      logger.atInfo().log("Device reboot is disabled, skip rebooting device %s.", deviceId);
      return;
    }
    // Only invokes the reboot command when the device is detected, otherwise will fail.
    if (androidAdbInternalUtil.getRealDeviceSerials(/* online= */ true).contains(deviceId)
        || androidAdbInternalUtil
            .getDeviceSerialsByState(DeviceState.RECOVERY)
            .contains(deviceId)) {
      switch (DeviceState.valueOf(
          device.getProperty(AndroidRealDeviceConstants.PROPERTY_NAME_REBOOT_TO_STATE))) {
        case DEVICE:
          if (shouldFactoryResetViaTestHarness()) {
            logger.atInfo().log("Factory reset device %s via Test Harness Mode.", deviceId);
            androidFrpUtil.factoryResetViaTestHarnessWithFrpClear(
                device, /* waitTime= */ null, /* log= */ null);
            device.removeDimension(Dimension.Name.RECOVERY_STATUS);
          } else {
            logger.atInfo().log("Device %s reboot to normal mode", deviceId);
            systemStateUtil.reboot(deviceId);
          }
          break;
        default:
          logger.atInfo().log("Device %s reboot to fastboot mode", deviceId);
          systemStateUtil.reboot(deviceId, RebootMode.BOOTLOADER);
          break;
      }
    } else if (Flags.instance().enableFastbootInAndroidRealDevice.getNonNull()
        && fastboot.getDeviceSerials().contains(deviceId)) {
      switch (DeviceState.valueOf(
          device.getProperty(AndroidRealDeviceConstants.PROPERTY_NAME_REBOOT_TO_STATE))) {
        case FASTBOOT:
          logger.atInfo().log("Device %s reboot to fastboot mode", deviceId);
          String unused = fastboot.rebootBootloader(deviceId);
          break;
        default:
          if (isAllowedToRebootFastbootDeviceToRecover()) {
            logger.atInfo().log(
                "Device %s has dimension %s with value true, reboot to normal mode",
                deviceId, Dimension.Name.REBOOT_TO_RECOVER.name());
            fastboot.reboot(deviceId, /* waitTime= */ Duration.ofSeconds(10));
          } else {
            // Skip rebooting fastboot mode device, because its data may be destroyed.
            logger.atInfo().log("Device %s is in the fastboot mode skip rebooting", deviceId);
          }
          break;
      }
    }
  }

  /** Takes device screenshot and returns the screenshot local path in the host machine. */
  public String takeScreenshot() throws MobileHarnessException, InterruptedException {
    String screensShotFilePathOnDevice =
        PathUtil.join(AndroidRealDeviceConstants.TEMP_SCREEN_SHOT_PATH, UUID.randomUUID() + ".png");
    String desFilePathOnHost = BaseDeviceHelper.getGenScreenshotPathWithDate(device);

    try {
      logger.atInfo().log(
          "Save screen shot for device %s: %s", deviceId, screensShotFilePathOnDevice);
      androidMediaUtil.takeScreenshot(deviceId, screensShotFilePathOnDevice);

      logger.atInfo().log(
          "Pull screen shot from device %s to host machine: %s", deviceId, desFilePathOnHost);
      logger.atInfo().log(
          "%s", androidFileUtil.pull(deviceId, screensShotFilePathOnDevice, desFilePathOnHost));

      fileUtil.grantFileOrDirFullAccess(desFilePathOnHost);
    } finally {
      try {
        androidFileUtil.removeFiles(deviceId, screensShotFilePathOnDevice);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to remove tmp screen shot for device %s: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
    }
    return desFilePathOnHost;
  }

  /** Gets the device log based on the log type. */
  public String getDeviceLog(DeviceLogType deviceLogType)
      throws MobileHarnessException, InterruptedException {
    try {
      String filePathPrefix = PathUtil.join(device.getGenFileDir(), UUID.randomUUID().toString());
      if (deviceLogType.equals(DeviceLogType.DEVICE_LOG_TYPE_ANDROID_LOGCAT)) {
        String rawLog = androidAdbUtil.logCat(deviceId, "", "*:Verbose");
        String desFilePathOnHost = filePathPrefix + "_logcat_dump.txt";
        fileUtil.writeToFile(desFilePathOnHost, rawLog);
        return desFilePathOnHost;
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.LAB_GET_DEVICE_LOG_LOGCAT_ERROR, "Failed to get logcat.", e);
    }
    throw new MobileHarnessException(
        InfraErrorId.LAB_GET_DEVICE_LOG_METHOD_UNSUPPORTED,
        String.format(
            "The device log type %s for the device %s[%s] is not supported.",
            deviceLogType, deviceId, device.getClass().getSimpleName()));
  }

  /**
   * We skip the "adb root" command for most "release-keys" devices and treat them as unrooted.
   *
   * <p>This is because some non-GED devices have trouble to run the "adb root" command, e.g.: 1)
   * Moto X (2nd gen) will become offline for several seconds. 2) Huawei Mate 7 will become
   * unresponsive to any adb commands.
   *
   * <p>But we do not skip the "adb root" on "nvidia" devices or TVs because they may both be rooted
   * and still have release keys at the same time. We need to run the "adb root" to check if they
   * are rooted. There seems no method to check root on a device with release keys which is more
   * reliable than the "adb root" command.
   */
  private boolean isRootable(String deviceId) throws MobileHarnessException, InterruptedException {
    String signKeys = androidAdbUtil.getProperty(deviceId, AndroidProperty.SIGN);
    if (!Ascii.equalsIgnoreCase(signKeys, "release-keys")) {
      return true;
    }
    String brandName = androidAdbUtil.getProperty(deviceId, AndroidProperty.BRAND);
    if (Ascii.equalsIgnoreCase(brandName, "nvidia")) {
      return true;
    }
    // http://b/191498942 Helios device contains release-keys, but can be rootable.
    String typeName = androidAdbUtil.getProperty(deviceId, AndroidProperty.TYPE);
    if (Ascii.equalsIgnoreCase(typeName, "helios")) {
      return true;
    }
    Set<String> features = systemSpecUtil.getSystemFeatures(deviceId);
    return features.contains(AndroidRealDeviceConstants.FEATURE_LEANBACK);
  }

  /**
   * Returns whether the device is rooted.
   *
   * <p>Ensures device is booted up and ready to respond before calling this, especially in the
   * device setup process, because it's likely the implementation of this method will issue some adb
   * commands to the device.
   */
  public boolean isRooted() throws MobileHarnessException, InterruptedException {
    if (!device.hasProperty(AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED)) {
      synchronized (checkRootLock) {
        if (!device.hasProperty(AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED)) {
          if (isRootable(deviceId)) {
            boolean rooted = becomeRoot();
            device.setProperty(
                AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED, Boolean.toString(rooted));
            if (rooted) {
              logger.atInfo().log("Device %s is rooted", deviceId);
            } else {
              logger.atInfo().log("Device %s is unrooted", deviceId);
            }
          } else {
            device.setProperty(
                AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED, Boolean.toString(false));
            logger.atInfo().log("Device %s is release-keys, treating as unrooted", deviceId);
          }
        }
      }
    }
    return device.getBooleanProperty(AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED);
  }

  /** Gets the timeout value for {@link #setUp()}. */
  public Duration getSetupTimeout() throws MobileHarnessException, InterruptedException {
    if (Flags.instance().enableFastbootInAndroidRealDevice.getNonNull()) {
      Optional<Duration> setupTimeoutForRecoveryAndFastbootDevice =
          getSetupTimeoutForRecoveryAndFastbootDevice();
      if (setupTimeoutForRecoveryAndFastbootDevice.isPresent()) {
        return setupTimeoutForRecoveryAndFastbootDevice.get();
      }
    }
    if (Flags.instance().resetDeviceInAndroidRealDeviceSetup.getNonNull()) {
      return BaseDeviceHelper.getBaseDeviceSetupTimeout()
          .plus(AndroidRealDeviceConstants.DEVICE_RESET_IN_SETUP_TIMEOUT_SHIFT);
    }
    // Add a timeout shift to make sure setup timeout is bigger than command timeout.
    // Check b/36156147 for more information.
    return BaseDeviceHelper.getBaseDeviceSetupTimeout()
        .plus(AndroidRealDeviceConstants.DEVICE_SETUP_TIMEOUT_SHIFT);
  }

  private Optional<Duration> getSetupTimeoutForRecoveryAndFastbootDevice()
      throws MobileHarnessException, InterruptedException {
    // Both recovery and fastboot device need more time for setup.
    // This is not necessory for shared lab. Remove this dependency on fastboot.
    if (!DeviceUtil.inSharedLab()
        && (androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.RECOVERY).contains(deviceId)
            || fastboot.getDeviceSerials().contains(deviceId))) {
      return Optional.of(Duration.ofHours(1L));
    }
    return Optional.empty();
  }

  /** Clears gservice flag overrides from the device if possible. */
  private void clearGServicesOverrides(TestInfo testInfo) throws InterruptedException {
    if (!device.getBooleanProperty(AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Not clearing GServices overrides because device %s is not rooted.", deviceId);
      return;
    }
    Integer sdkVersion = device.getSdkVersion();
    if (sdkVersion == null || sdkVersion < 18) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Not clearing GServices overrides because device %s version is unknown or too old.",
              deviceId);
      return;
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Clearing GServices overrides for device %s", deviceId);
    try {
      String unused =
          systemSettingUtil.clearGServicesOverrides(deviceId, sdkVersion == null ? 0 : sdkVersion);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Failed to clear GServices overrides for device %s: %s",
              deviceId, MoreThrowables.shortDebugString(e));
    }
  }

  /** Cleans up when the device becomes undetectable/disconnected. */
  public void tearDown() throws InterruptedException, MobileHarnessException {
    deviceTearDown();
  }

  protected abstract void deviceTearDown() throws InterruptedException, MobileHarnessException;

  /**
   * Sets device properties and packages to let the device know it is running in a test harness and
   * disable some unnecessary features for safety, such as disable phone call, mute, etc.
   *
   * <p>Note we can only set these properties after a device become root. Also, once set, the test
   * properties can not be overwritten until we reboot the device.
   *
   * @see <a href="http://b/14574172">background</a>
   */
  private void enableTestPropertiesAndDisablePackages()
      throws MobileHarnessException, InterruptedException {
    if (!ifTrySetDevicePropertiesAndDisablePackages()) {
      return;
    }

    // Creates read-only property settings.
    ImmutableList<ReadOnlyPropertySetting> readOnlyPropertySettings =
        createReadOnlyPropertySettings();
    ImmutableList<ReadOnlyPropertySetting> needRebootToClearReadOnlyProperties =
        readOnlyPropertySettings.stream()
            .filter(ReadOnlyPropertySetting::needRebootToSetProperty)
            .collect(toImmutableList());
    boolean canRebootToClearReadOnlyProperties =
        !Flags.instance().disableDeviceReboot.getNonNull()
            && !Flags.instance().disableDeviceRebootForRoProperties.getNonNull();

    // Reboots if necessary and allowed.
    boolean hasRebooted = false;
    if (!needRebootToClearReadOnlyProperties.isEmpty()) {
      if (canRebootToClearReadOnlyProperties) {
        logger.atInfo().log(
            "Reboot device %s to clear read-only properties %s",
            deviceId, needRebootToClearReadOnlyProperties);
        try {
          systemStateManager.reboot(device, /* log= */ null, /* deviceReadyTimeout= */ null);
          hasRebooted = true;
        } catch (MobileHarnessException e) {
          logger.atWarning().log(
              "Failed to reboot device %s to clear read-only properties %s",
              deviceId, MoreThrowables.shortDebugString(e));
        }
      } else {
        logger.atInfo().log(
            "Need reboot device %s to clear read-only properties %s but device reboot is disabled"
                + " by flags",
            deviceId, needRebootToClearReadOnlyProperties);
      }
    }

    // Sets read-only properties.
    for (ReadOnlyPropertySetting readOnlyPropertySetting : readOnlyPropertySettings) {
      setReadOnlyProperty(readOnlyPropertySetting, hasRebooted);
    }

    // Disables packages.
    if (Flags.instance().disableCellBroadcastReceiver.getNonNull()) {
      try {
        androidPkgManagerUtil.disablePackage(deviceId, "com.android.cellbroadcastreceiver");
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to disable package com.android.cellbroadcastreceiver when setup the device "
                + "%s: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
    }
  }

  @VisibleForTesting
  ImmutableList<ReadOnlyPropertySetting> createReadOnlyPropertySettings()
      throws MobileHarnessException, InterruptedException {
    return ImmutableList.of(
        ReadOnlyPropertySetting.create(
            AndroidProperty.DISABLE_CALL,
            Flags.instance().disableCalling.getNonNull() ? "true" : "false",
            !Flags.instance().disableCalling.getNonNull(),
            deviceId,
            androidAdbUtil),
        ReadOnlyPropertySetting.create(
            AndroidProperty.TEST_HARNESS,
            Flags.instance().setTestHarnessProperty.getNonNull() ? "1" : "0",
            !Flags.instance().setTestHarnessProperty.getNonNull(),
            deviceId,
            androidAdbUtil),
        ReadOnlyPropertySetting.create(
            AndroidProperty.SILENT,
            Flags.instance().muteAndroid.getNonNull() ? "1" : "0",
            !Flags.instance().muteAndroid.getNonNull(),
            deviceId,
            androidAdbUtil));
  }

  private void setReadOnlyProperty(
      ReadOnlyPropertySetting readOnlyPropertySetting, boolean hasRebooted)
      throws MobileHarnessException, InterruptedException {
    if (readOnlyPropertySetting.needSetProperty()) {
      if (readOnlyPropertySetting.needRebootToSetProperty() && !hasRebooted) {
        logger.atWarning().log(
            "Setting read-only property [%s] needs reboot device but device %s hasn't been rebooted"
                + " due to flags or reboot failure",
            readOnlyPropertySetting, deviceId);
      } else {
        logger.atInfo().log(
            "Set read-only property [%s] on device %s", readOnlyPropertySetting, deviceId);
        androidAdbUtil.setProperty(
            deviceId,
            readOnlyPropertySetting.property().getPrimaryPropertyKey(),
            readOnlyPropertySetting.expectedValue(),
            /* ignoreError= */ true);
      }
    } else {
      logger.atInfo().log(
          "Don't need to set read-only property [%s] on device %s",
          readOnlyPropertySetting, deviceId);
    }
  }

  @AutoValue
  abstract static class ReadOnlyPropertySetting {

    abstract AndroidProperty property();

    abstract String currentValue();

    abstract String expectedValue();

    /** Returns whether an empty current value is expected. */
    abstract boolean emptyValueExpected();

    @Memoized
    boolean needSetProperty() {
      return !((currentValue().isEmpty() && emptyValueExpected())
          || Ascii.equalsIgnoreCase(currentValue(), expectedValue()));
    }

    @Memoized
    boolean needRebootToSetProperty() {
      return !currentValue().isEmpty() && needSetProperty();
    }

    private static ReadOnlyPropertySetting create(
        AndroidProperty property,
        String expectedValue,
        boolean emptyValueExpected,
        String deviceId,
        AndroidAdbUtil androidAdbUtil)
        throws InterruptedException, MobileHarnessException {
      return of(
          /* property= */ property,
          /* currentValue= */ androidAdbUtil.getProperty(deviceId, property),
          /* expectedValue= */ expectedValue,
          /* emptyValueExpected= */ emptyValueExpected);
    }

    @VisibleForTesting
    static ReadOnlyPropertySetting of(
        AndroidProperty property,
        String currentValue,
        String expectedValue,
        boolean emptyValueExpected) {
      return new AutoValue_AndroidRealDeviceDelegate_ReadOnlyPropertySetting(
          /* property= */ property,
          /* currentValue= */ currentValue,
          /* expectedValue= */ expectedValue,
          /* emptyValueExpected= */ emptyValueExpected);
    }
  }

  /** Returns {@code true} if need to run {@link #enableTestPropertiesAndDisablePackages()}. */
  protected abstract boolean ifTrySetDevicePropertiesAndDisablePackages();

  /**
   * Connect to WIFI by given SSID and password, and doesn't ensure it is successful to connect to
   * WIFI. The devices of non-ged, release key and version 2.2.1 are all tested.
   *
   * @param ssid SSID of WIFI to be connected
   * @param pwd password of WIFI to be connected
   * @param scanSsid whether to scan the SSID
   */
  private void connectToWifi(
      String serial, int sdkVersion, String ssid, String pwd, boolean scanSsid)
      throws MobileHarnessException, InterruptedException {
    if (Flags.instance().disableWifiUtilFunc.getNonNull()) {
      logger.atInfo().log(
          "Wifi util functionality is disabled. Skip connecting device %s to wifi.", serial);
      return;
    }
    if (DeviceUtil.inSharedLab()) {
      logger.atInfo().log(
          "Ignoring attempt to connect device %s to WiFi while not managing devices.", serial);
      return;
    }
    WifiUtil wifiUtil = new WifiUtil();
    apkInstaller.installApkIfVersionMismatched(
        device, ApkInstallArgs.builder().setApkPath(wifiUtil.getWifiUtilApkPath()).build(), null);
    // It doesn't guarantee to connect to wifi successfully, so only use a short timeout to check.
    ConnectToWifiArgs.Builder argsBuilder =
        ConnectToWifiArgs.builder()
            .setSerial(serial)
            .setSdkVersion(sdkVersion)
            .setWifiSsid(ssid)
            .setScanSsid(scanSsid)
            .setWaitTimeout(Duration.ofSeconds(5));
    if (!Strings.isNullOrEmpty(pwd)) {
      argsBuilder.setWifiPsk(pwd);
    }
    if (!connectivityUtil.connectToWifi(argsBuilder.build(), /* log= */ null)) {
      logger.atWarning().log("Failed to connect device %s to SSID '%s'", serial, ssid);
    }
  }

  /**
   * Checks network connection of the device.
   *
   * @return whether there is any dimension changed
   */
  @VisibleForTesting
  boolean checkNetwork() throws MobileHarnessException, InterruptedException {
    if (needToInstallWifiApk()) {
      if (Flags.instance().disableWifiUtilFunc.getNonNull()) {
        logger.atInfo().log(
            "Wifi util functionality is disabled. Skip installing WifiUtil apk on device %s.",
            deviceId);
      } else {
        // Install the wifi apk for Satellite lab only. (b/200517628)
        try {
          WifiUtil wifiUtil = new WifiUtil();
          apkInstaller.installApk(
              device,
              ApkInstallArgs.builder()
                  .setApkPath(wifiUtil.getWifiUtilApkPath())
                  .setSkipIfCached(true)
                  .setSkipIfVersionMatch(false)
                  .setSkipDowngrade(false)
                  .setGrantPermissions(
                      false) // Do not grant permission, either the installation fails on some OEM
                  // device. {@link b/197480620#comment6}.
                  .build(),
              null);
        } catch (MobileHarnessException e) {
          logger.atWarning().log(
              "Failed to install WiFi apk: %s", MoreThrowables.shortDebugString(e));
        }
      }
    }

    Optional<String> currentSsid = bestEffortConnectNetworkSsid();
    boolean hasInternet = ifHasInternet();
    boolean isDimensionChanged = checkNetworkDimensions(currentSsid, hasInternet);
    return isDimensionChanged;
  }

  /** Returns {@code true} if need to install the wifi apk on the device. */
  protected abstract boolean needToInstallWifiApk();

  /** Returns {@code true} if the device has internet access. */
  protected abstract boolean ifHasInternet() throws InterruptedException;

  /**
   * Checks whether the device is connected to a WiFi, and if not, best effort to recover the WiFi
   * connection.
   *
   * @return the ssid.
   */
  private Optional<String> bestEffortConnectNetworkSsid()
      throws MobileHarnessException, InterruptedException {
    // When the device is locked with device admin, the wifi connection will be blocked. Temporarily
    // disable the WIFI restriction to avoid blocking the wifi re-connection.
    // If togglling on/off device admin wifi restriction fails, will throw an exception and kill the
    // current device runner. This is intended to avoid the device is in a risk state that the user
    // has permission to operate the wifi.
    toggleDeviceAdminWifiRestrictionIfLocked(/* enable= */ false);
    try {
      Optional<String> currentSsid = checkNetworkSsid();
      if (currentSsid.isEmpty()) {
        currentSsid = recoverWifiConnectionIfRequired();
      }
      return currentSsid;
    } finally {
      toggleDeviceAdminWifiRestrictionIfLocked(/* enable= */ true);
    }
  }

  /**
   * Checks whether the device is connected to a WiFi, and if not connected, try to connect to the
   * wifi which is set via api.
   *
   * @return the ssid.
   */
  private Optional<String> checkNetworkSsid() throws InterruptedException {
    String currentSsid = null;
    int sdkVersion = 0;
    try {
      sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get device %s SDK version, use default value 0: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    }
    try {
      currentSsid = connectivityUtil.getNetworkSsid(deviceId, sdkVersion);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get device %s network SSID: %s", deviceId, MoreThrowables.shortDebugString(e));
    }
    if (!ifCheckDefaultWifi()) {
      return Optional.ofNullable(currentSsid);
    }

    // Checks the default WIFI, if exists, will connect to it if allowed.
    ApiConfig apiConfig = device.getApiConfig();
    WifiConfig defaultWifi = apiConfig == null ? null : apiConfig.getDefaultWifi(deviceId);
    if (defaultWifi == null || defaultWifi.getSsid().isEmpty()) {
      logger.atInfo().log("Device %s has not set the default wifi", deviceId);
      return Optional.ofNullable(currentSsid);
    }
    // Set default wifi setup as device property for {@link AndroidSetWifiDecorator} to use.
    logger.atInfo().log(
        "Device %s has set the default wifi to be %s", deviceId, defaultWifi.getSsid());
    device.setProperty(
        Ascii.toLowerCase(AndroidSetWifiDecorator.DEFAULT_WIFI_SSID.name()), defaultWifi.getSsid());
    device.setProperty(
        Ascii.toLowerCase(AndroidSetWifiDecorator.DEFAULT_WIFI_PSK.name()), defaultWifi.getPsk());
    if (Flags.instance().skipConnectDeviceToWifi.getNonNull()) {
      // Don't reconnect the devices to the default wifi network
      logger.atInfo().log(
          "Skip connecting device %s to WIFI because --skip_connect_device_to_wifi is set to true.",
          deviceId);
      return Optional.ofNullable(currentSsid);
    }
    if (currentSsid != null && currentSsid.equals(defaultWifi.getSsid())) {
      // already connected to the default WiFi.
      return Optional.of(currentSsid);
    }
    try {
      connectToWifi(
          deviceId,
          sdkVersion,
          defaultWifi.getSsid(),
          defaultWifi.getPsk(),
          defaultWifi.getScanSsid());
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to connect device %s to WIFI %s with psk %s, scan_ssid %s: %s",
          deviceId,
          defaultWifi.getSsid(),
          defaultWifi.getPsk(),
          defaultWifi.getScanSsid(),
          MoreThrowables.shortDebugString(e));
    }
    // Regain the WiFi SSID
    try {
      currentSsid = connectivityUtil.getNetworkSsid(deviceId, sdkVersion);
    } catch (MobileHarnessException e) {
      currentSsid = null;
      logger.atWarning().log(
          "Failed to get device %s network SSID: %s", deviceId, MoreThrowables.shortDebugString(e));
    }
    if (currentSsid != null) {
      logger.atInfo().log("Device %s network connection OK, SSID=%s", deviceId, currentSsid);
      return Optional.of(currentSsid);
    } else {
      logger.atInfo().log("Device %s not connected to any SSID", deviceId);
      return Optional.empty();
    }
  }

  /** Returns {@code true} if need to check default wifi on the device. */
  protected abstract boolean ifCheckDefaultWifi();

  /**
   * Recovers the WiFi connection if it is required.
   *
   * @return the ssid.
   */
  private Optional<String> recoverWifiConnectionIfRequired() throws InterruptedException {
    if (Flags.instance().skipRecoverDeviceNetwork.getNonNull()) {
      logger.atInfo().log(
          "Skip recover device %s network because --skip_recover_device_network is set to true.",
          deviceId);
      return Optional.empty();
    }
    if (Flags.instance().skipConnectDeviceToWifi.getNonNull()) {
      logger.atInfo().log(
          "Skip recover device %s network because --skip_connect_device_to_wifi is set to true.",
          deviceId);
      return Optional.empty();
    }
    if (ifSkipRecoverDeviceNetwork()) {
      logger.atInfo().log("Skip recover device %s network.", deviceId);
      return Optional.empty();
    }
    if (!device.getBooleanProperty(AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED)) {
      logger.atInfo().log(
          "Skip recover device %s network because the device is not rooted.", deviceId);
      return Optional.empty();
    }
    boolean hasSavedSsidsAndPsks = false;
    Integer sdkVersion = device.getSdkVersion();
    if (sdkVersion != null && sdkVersion >= 13) {
      try {
        hasSavedSsidsAndPsks =
            !connectivityUtil.getSavedSsidsAndPsks(deviceId, sdkVersion).isEmpty();
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to get device %s saved SSIDs and PSKs: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
    }
    if (!hasSavedSsidsAndPsks) {
      logger.atInfo().log(
          "Skip recover device %s network because the device doesn't save SSIDs and PSKs.",
          deviceId);
      return Optional.empty();
    }
    logger.atInfo().log("Recovering device %s network...", deviceId);
    String currentSsid = null;
    try {
      connectivityUtil.reEnableWifi(deviceId, /* log= */ null);
      if (connectivityUtil.waitForNetwork(
          deviceId, sdkVersion, null, AndroidRealDeviceConstants.WAIT_FOR_INTERNET)) {
        logger.atInfo().log("Device %s connected to network after re-enabling Wifi", deviceId);
      } else {
        logger.atInfo().log(
            "Device %s still has no network connection after re-enabling Wifi", deviceId);
      }
      currentSsid = connectivityUtil.getNetworkSsid(deviceId, sdkVersion);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to recover device %s network: %s", deviceId, MoreThrowables.shortDebugString(e));
    }
    return Optional.ofNullable(currentSsid);
  }

  /** Returns {@code true} if skip device wifi recovery. */
  protected abstract boolean ifSkipRecoverDeviceNetwork();

  /**
   * Updates or removes network related dimensions based on the input. The dimensions include:
   * network_ssid, network_address, internet.
   *
   * @param currentSsid the WiFi SSID the device is connected to
   * @param hasInternet whether the device is connected to the Internet
   * @return whether any dimension is changed.
   */
  boolean checkNetworkDimensions(Optional<String> currentSsid, boolean hasInternet)
      throws InterruptedException {
    boolean isDimensionChanged = false;
    boolean isNetworkSsidDimensionUpdated =
        currentSsid.isPresent()
            ? device.updateDimension(Dimension.Name.NETWORK_SSID, currentSsid.get())
            : device.removeDimension(Dimension.Name.NETWORK_SSID);
    isDimensionChanged |= isNetworkSsidDimensionUpdated;

    String linkAddress = null;
    try {
      linkAddress =
          Joiner.on(StrUtil.DEFAULT_ENTRY_DELIMITER)
              .join(connectivityUtil.getNetworkLinkAddress(deviceId));
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get device %s network link address: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    }
    boolean isNetworkAddressDimensionUpdated =
        Strings.isNullOrEmpty(linkAddress)
            ? device.removeDimension(Dimension.Name.NETWORK_ADDRESS)
            : device.updateDimension(Dimension.Name.NETWORK_ADDRESS, linkAddress);
    isDimensionChanged |= isNetworkAddressDimensionUpdated;

    boolean isInternetDimensionUpdated =
        device.updateDimension(Dimension.Name.INTERNET, String.valueOf(hasInternet));
    isDimensionChanged |= isInternetDimensionUpdated;

    return isDimensionChanged;
  }

  /**
   * Checks the battery status of the device.
   *
   * @return whethere there is any dimension changed
   */
  private boolean checkBattery() throws InterruptedException {
    boolean isDimensionChanged = false;
    // Checks battery. Skips battery check if dimension "characteristics" contains "tv".
    if (!device.getDimension("characteristics").contains("tv")) {
      logger.atInfo().log("Checking device %s battery level...", deviceId);
      Optional<Integer> batteryLevel = Optional.empty();
      String batteryStatus = "unknown";
      try {
        batteryLevel = systemSettingUtil.getBatteryLevel(deviceId);
        if (batteryLevel.isPresent()) {
          logger.atInfo().log("Device %s battery level: %d", deviceId, batteryLevel.get());
          if (batteryLevel.map(level -> level < 20).orElse(false)) {
            batteryStatus = "low";
          } else {
            batteryStatus = "ok";
          }
        } else {
          logger.atInfo().log("Device %s battery level is not available", deviceId);
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to get device %s battery level: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
      logger.atInfo().log("Checking device %s battery temperature...", deviceId);
      int batteryTemperature = 0;
      try {
        Optional<Integer> temperature = systemSettingUtil.getBatteryTemperature(deviceId);
        if (temperature.isPresent()) {
          batteryTemperature = temperature.get();
          logger.atInfo().log("Device %s battery temperature: %d", deviceId, batteryTemperature);
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to get device %s battery temperature: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
      isDimensionChanged |= device.updateDimension(Dimension.Name.BATTERY_STATUS, batteryStatus);
      isDimensionChanged |=
          device.updateDimension(
              Dimension.Name.BATTERY_LEVEL, batteryLevel.map(String::valueOf).orElse("-1"));
      isDimensionChanged |=
          device.updateDimension(
              Dimension.Name.BATTERY_TEMPERATURE, String.valueOf(batteryTemperature));
    }
    return isDimensionChanged;
  }

  /**
   * Checks the list of ICCIDs of SIMs on the device.
   *
   * @return whether there is any dimension changed
   */
  private boolean checkIccids() throws InterruptedException {
    String iccids = "";
    logger.atInfo().log("Fetching device %s's ICCIDs...", deviceId);
    try {
      iccids = getCommaSeparatedIccids();
      logger.atInfo().log("ICCIDs of SIMS on device %s: %s", deviceId, iccids);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to fetch device %s's ICCIDs: %s", deviceId, MoreThrowables.shortDebugString(e));
    }

    return device.updateDimension(Dimension.Name.ICCIDS, iccids);
  }

  /** */
  private boolean checkGmscoreSignature() throws InterruptedException {
    String gmscoreSignature = "";
    logger.atInfo().log("Fetching device %s's GMS Core signature...", deviceId);
    try {
      gmscoreSignature =
          androidPkgManagerUtil.getAppSignature(deviceId, PackageConstants.PACKAGE_NAME_GMS);
      logger.atInfo().log("GMS Core signature on device %s: %s", deviceId, gmscoreSignature);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to fetch device %s's GMS Core signature: %s",
          deviceId, MoreThrowables.shortDebugString(e));
    }
    return device.updateDimension(Dimension.Name.GMSCORE_SIGNATURE, gmscoreSignature);
  }

  /** Returns whether the device is a foldable device. */
  private boolean isFoldableDevice() {
    List<String> features = device.getDimension(Dimension.Name.FEATURE);
    return features.contains("android.hardware.sensor.hinge_angle");
  }

  /**
   * Checks the hinge angle of the foldable device.
   *
   * @return whether there is any dimension updated or removed
   */
  boolean checkHingeAngle() throws MobileHarnessException, InterruptedException {
    if (!isFoldableDevice()) {
      return false;
    }
    if (isRooted()) {
      try {
        String hingeAngle = systemSpecUtil.getHingeAngle(deviceId);
        logger.atInfo().log("Hinge angle of device %s: %s", deviceId, hingeAngle);
        hingeAngle = hingeAngle.substring(0, hingeAngle.indexOf('.'));
        return device.updateDimension(Dimension.Name.HINGE_ANGLE, hingeAngle);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to fetch device %s's hinge angle: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
    }
    return device.removeDimension(Dimension.Name.HINGE_ANGLE);
  }

  /** Returns a comma-separated string of the ICCIDs of the SIMs on the device. */
  private String getCommaSeparatedIccids() throws MobileHarnessException, InterruptedException {
    ImmutableList<String> iccids = systemSpecUtil.getIccids(deviceId);
    return iccids == null ? "" : COMMA_JOINER.join(iccids);
  }

  private static final Joiner COMMA_JOINER = Joiner.on(",");

  /**
   * Checks network connection stability.
   *
   * @return whether there is any dimension changed
   */
  @VisibleForTesting
  boolean checkPingGoogle() throws InterruptedException {
    boolean isDimensionChanged = false;
    boolean hasInternetConnection = device.getDimension(Dimension.Name.INTERNET).contains("true");
    if (hasInternetConnection) {
      double pingGoogleSuccessRate = connectivityUtil.pingSuccessRate(deviceId, "google.com", 5);
      isDimensionChanged |=
          device.updateDimension(
              Dimension.Name.PING_GOOGLE_STABILITY, pingGoogleSuccessRate * 100 + "%");
    }
    return isDimensionChanged;
  }

  /**
   * Checks the external or internal storage of the device and updates dimensions. Sends mail if out
   * of space.
   *
   * @param isExternal whether checks external storage or internal storage
   * @return whether there is any dimension changed
   */
  @CanIgnoreReturnValue
  @VisibleForTesting
  boolean checkStorage(boolean isExternal) throws InterruptedException, MobileHarnessException {
    boolean isDimensionChanged = false;

    String storageFreePercentageStr;
    Dimension.Name dimensionStorageStatus;
    Dimension.Name dimensionFreeStorage;
    Dimension.Name dimensionFreeStoragePercentage;
    String externalOrInternal;
    int freeStorageAlertMb;
    StorageInfo storageInfo = null;

    if (isExternal) {
      dimensionStorageStatus = Dimension.Name.EXTERNAL_STORAGE_STATUS;
      dimensionFreeStorage = Dimension.Name.FREE_EXTERNAL_STORAGE;
      dimensionFreeStoragePercentage = Dimension.Name.FREE_EXTERNAL_STORAGE_PERCENTAGE;
      externalOrInternal = AndroidRealDeviceConstants.STRING_EXTERNAL;
      freeStorageAlertMb = AndroidRealDeviceConstants.FREE_EXTERNAL_STORAGE_ALERT_MB;
    } else {
      dimensionStorageStatus = Dimension.Name.INTERNAL_STORAGE_STATUS;
      dimensionFreeStorage = Dimension.Name.FREE_INTERNAL_STORAGE;
      dimensionFreeStoragePercentage = Dimension.Name.FREE_INTERNAL_STORAGE_PERCENTAGE;
      externalOrInternal = AndroidRealDeviceConstants.STRING_INTERNAL;
      freeStorageAlertMb = Flags.instance().internalStorageAlert.getNonNull();
    }
    logger.atInfo().log("Checking device %s %s storage usage...", deviceId, externalOrInternal);

    try {
      // Gets storage information and updates dimensions.
      storageInfo = androidFileUtil.getStorageInfo(deviceId, /* isExternal= */ isExternal);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get %s storage info (device_id=%s): %s",
          externalOrInternal, deviceId, MoreThrowables.shortDebugString(e));
    }
    if (storageInfo == null || storageInfo.totalKB() == 0) {
      isDimensionChanged |=
          device.updateDimension(dimensionStorageStatus, Dimension.Value.UNKNOWN_VALUE);
      isDimensionChanged |= device.removeDimension(dimensionFreeStorage);
      isDimensionChanged |= device.removeDimension(dimensionFreeStoragePercentage);
    } else {
      double diskFreePercentage =
          storageInfo.totalKB() > 0 ? (double) storageInfo.freeKB() / storageInfo.totalKB() : 0.0;
      storageFreePercentageStr = String.format("%.2f%%", diskFreePercentage * 100.0);
      logger.atInfo().log(
          "Device %s %s storage usage: totalKB=%d, freeKB=%d, freePercentage=%s",
          deviceId,
          externalOrInternal,
          storageInfo.totalKB(),
          storageInfo.freeKB(),
          storageFreePercentageStr);
      long freeStorageMb = storageInfo.freeKB() >> 10;

      boolean isOutOfStorageSpace = freeStorageMb < freeStorageAlertMb;
      isDimensionChanged |=
          device.updateDimension(
              dimensionFreeStorage, StrUtil.getHumanReadableSize(freeStorageMb << 20));
      isDimensionChanged |=
          device.updateDimension(dimensionFreeStoragePercentage, storageFreePercentageStr);
      isDimensionChanged |=
          device.updateDimension(
              dimensionStorageStatus,
              isOutOfStorageSpace ? Dimension.Value.LOW_VALUE : Dimension.Value.OK_VALUE);
    }

    return isDimensionChanged;
  }

  /**
   * Checks and cleans up the internal storage of the device.
   *
   * @return whether there is any dimension changed
   */
  private boolean checkAndCleanInternalStorage()
      throws InterruptedException, MobileHarnessException {
    boolean isDimensionChanged = false;

    // Cleans temp apks, checks disk free percentage and sends disk alert if out of space.
    logger.atInfo().log("Scanning temp apks on device %s...", deviceId);
    try {
      String output = androidFileUtil.listFiles(deviceId, AndroidRealDeviceConstants.TEMP_APK_PATH);
      if (!output.contains(AndroidRealDeviceConstants.OUTPUT_NO_FILE_OR_DIR)) {
        logger.atInfo().log(
            "Temp apks on device %s (%s):\n%s",
            deviceId, AndroidRealDeviceConstants.TEMP_APK_PATH, output);
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to scan device %s temp apks: %s", deviceId, MoreThrowables.shortDebugString(e));
    }

    logger.atInfo().log("Cleaning device %s temp apks...", deviceId);
    try {
      androidFileUtil.removeFiles(deviceId, AndroidRealDeviceConstants.TEMP_APK_PATH);
      logger.atInfo().log("Device %s temp apks cleared", deviceId);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to clean device %s temp apks: %s", deviceId, MoreThrowables.shortDebugString(e));
    }

    // Devices are getting full because of generated smlog files (b/65023626).
    logger.atInfo().log("Cleaning device %s smlogs files...", deviceId);
    try {
      androidFileUtil.removeFiles(deviceId, AndroidRealDeviceConstants.SMLOG_PATH);
      logger.atInfo().log(
          "Device %s %s are cleared", deviceId, AndroidRealDeviceConstants.SMLOG_PATH);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to clean %s on device %s: %s",
          AndroidRealDeviceConstants.SMLOG_PATH, deviceId, MoreThrowables.shortDebugString(e));
    }

    // Cleans up the lost+found files only if the device is rooted because /data and
    // /data/lost+found can not be accessed on a non-rooted device.
    if (device.getBooleanProperty(AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED)) {
      logger.atInfo().log("Cleaning device %s lost+found files...", deviceId);
      try {
        androidFileUtil.removeFiles(deviceId, AndroidRealDeviceConstants.LOST_FOUND_FILES_PATH);
        logger.atInfo().log("Device %s lost+found files cleared", deviceId);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to clean device %s lost+found files: %s",
            deviceId, MoreThrowables.shortDebugString(e));
      }
    } else {
      logger.atInfo().log(
          "Skip cleaning up device %s lost+found files because it is not rooted", deviceId);
    }

    // Checks internal storage and updates dimensions.
    checkStorage(/* isExternal= */ false);
    return isDimensionChanged;
  }

  /**
   * Adds dimensions about the launcher state; currently detects the original built-in launcher
   * ("launcher_1"), the newer launcher ("launcher_3") and the Google Experience Launcher
   * ("launcher_gel").
   *
   * @return whether there is any dimension changed
   */
  private boolean checkLaunchers() throws InterruptedException {
    boolean isDimensionChanged = false;
    try {
      Set<String> launchers = androidPkgManagerUtil.listPackages(deviceId, PackageType.LAUNCHER);
      if (launchers != null) {
        isDimensionChanged |=
            device.updateDimension(
                Dimension.Name.LAUNCHER_1,
                String.valueOf(
                    launchers.contains(AndroidRealDeviceConstants.PACKAGE_NAME_LAUNCHER_1)));
        isDimensionChanged |=
            device.updateDimension(
                Dimension.Name.LAUNCHER_3,
                String.valueOf(
                    launchers.contains(AndroidRealDeviceConstants.PACKAGE_NAME_LAUNCHER_3)));
        isDimensionChanged |=
            device.updateDimension(
                Dimension.Name.LAUNCHER_GEL,
                String.valueOf(
                    launchers.contains(AndroidRealDeviceConstants.PACKAGE_NAME_LAUNCHER_GEL)));
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log("%s", e.getMessage());
    }
    return isDimensionChanged;
  }

  /** Checks online mode device services available, otherwise reboot device to recover. */
  private void checkOnlineModeDeviceServiceAvailable(String deviceId)
      throws MobileHarnessException, InterruptedException {
    boolean serviceAvailable = false;
    for (String service : AndroidRealDeviceConstants.ONLINE_DEVICE_AVAILABLE_SERVICES) {
      serviceAvailable = androidProcessUtil.checkServiceAvailable(deviceId, service);
      if (!serviceAvailable) {
        break;
      }
    }
    if (!serviceAvailable && !Flags.instance().disableDeviceReboot.getNonNull()) {
      AndroidDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.DEVICE);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_REAL_DEVICE_ONLINE_DEVICE_NOT_READY,
          String.format(
              "Online mode device %s services aren't available yet. Reboot to recover.", deviceId));
    }
  }

  private void checkSuwAppDisabled() throws InterruptedException {
    for (String packageName : device.getDimension(Dimension.Name.SKIP_SUW_APP)) {
      logger.atInfo().log("Checking if %s is disabled on device %s", packageName, deviceId);
      try {
        checkAppDisabled(packageName);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to disable SUW app %s on device %s: %s",
            packageName, deviceId, MoreThrowables.shortDebugString(e));
      }
    }
  }

  /* This method has no effect if the package not installed or disabled. */
  private void checkAppDisabled(String packageName)
      throws MobileHarnessException, InterruptedException {
    if (androidPkgManagerUtil.isPackageEnabled(deviceId, packageName)) {
      androidPkgManagerUtil.userDisablePackage(deviceId, packageName);
      logger.atInfo().log("Disabled %s on device %s", packageName, deviceId);
    }
  }

  /**
   * Checks WIFI RSSI of the device. It is only supported in devices with sdk version larger than or
   * equal to 19.
   *
   * @return whether there is any dimension changed
   */
  private boolean checkWifiRssi() throws InterruptedException {
    boolean isDimensionChanged = false;
    Integer version = device.getSdkVersion();
    if (version == null || version < 19) {
      isDimensionChanged |= device.removeDimension(Dimension.Name.WIFI_RSSI);
    } else {
      String wifiRssiValue = null;
      try {
        wifiRssiValue = connectivityUtil.getWifiRssi(deviceId);
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to get device %s WIFI RSSI: %s", deviceId, MoreThrowables.shortDebugString(e));
      }
      if (wifiRssiValue == null) {
        isDimensionChanged |= device.removeDimension(Dimension.Name.WIFI_RSSI);
      } else {
        isDimensionChanged |= device.updateDimension(Dimension.Name.WIFI_RSSI, wifiRssiValue);
      }
    }
    return isDimensionChanged;
  }

  /**
   * Remove users except default user from device before any user related setting/installation
   * happens.
   *
   * @param serial device serial number
   * @param sdkVersion device sdk version number
   * @throws MobileHarnessException when failed to switch user to default user
   */
  private void clearMultiUsers(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    if (ifSkipClearMultiUsers(sdkVersion)) {
      logger.atInfo().log("Ignoring attempt to clear multi-user on device %s.", serial);
      return;
    }

    boolean headlessUserEnabled = isHeadlessSystemUser(serial, sdkVersion);

    if (headlessUserEnabled) {
      logger.atInfo().log(
          "Ignore attempt to clear multi-user on device %s since headless system is enabled.",
          serial);
      return;
    }

    List<Integer> existingUsers = null;
    try {
      existingUsers = androidUserUtil.listUsers(serial, sdkVersion);
      if (existingUsers.size() == 1) {
        logger.atInfo().log(
            "Ignoring clear multi-user on device %s as it is already running single user", serial);
        return;
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to list active user from device %s: %s",
          serial, MoreThrowables.shortDebugString(e));
    }

    // For headless system, default user should be a secondary user, as UserType.secondary.
    // TODO: add logic for clean up headless system.
    int defaultUser = AndroidRealDeviceConstants.DEFAULT_SYSTEM_USER;
    int currentUser = androidUserUtil.getCurrentUser(serial, sdkVersion);

    if (currentUser != defaultUser) {
      // Switch user back to default user.
      logger.atInfo().log(
          "Switch user on device %s from %s to %s", serial, currentUser, defaultUser);
      androidUserUtil.switchUser(serial, sdkVersion, defaultUser);
      // Stop the setup process or reboot device if failed to wait user ready.
      androidUserUtil.waitForUserReady(serial, sdkVersion, defaultUser);
    }

    if (existingUsers != null) {
      List<Integer> removedUsers = new ArrayList<>(existingUsers);
      ImmutableList<Integer> reservedUsers =
          ImmutableList.of(AndroidRealDeviceConstants.DEFAULT_SYSTEM_USER);
      removedUsers.removeAll(reservedUsers);

      for (int userId : removedUsers) {
        try {
          androidUserUtil.removeUser(serial, sdkVersion, userId);
        } catch (MobileHarnessException e) {
          // Only log the error message and continue remove other users.
          logger.atWarning().log(
              "Failed to remove user %s from device %s with error: %s",
              userId, serial, MoreThrowables.shortDebugString(e));
        }
      }
    }
  }

  /** Returns {@code true} if skip clearing multi users on the device. */
  protected abstract boolean ifSkipClearMultiUsers(int sdkVersion);

  /**
   * Gets the whitelisted system features {@link AndroidRealDeviceConstants#FEATURES_KEYWORDS} on
   * the device. Only select features existing on whitelist since there could be too many features
   * and some are not useful. The start characters "feature:" {@link
   * AndroidRealDeviceConstants#OUTPUT_FEATURE_STARTING_PATTERN} of each feature will be truncated
   * in the return set.
   *
   * @param serial serial number of the device
   * @return a set of system features on device
   * @throws MobileHarnessException if fail to get features info from the device
   */
  private Set<String> getSystemFeaturesByWhitelist(String serial)
      throws MobileHarnessException, InterruptedException {
    Set<String> features = new HashSet<>();
    for (String line : systemSpecUtil.getSystemFeatures(serial)) {
      if (AndroidRealDeviceConstants.PATTERN_FEATURES.matcher(line).find()) {
        features.add(
            line.substring(AndroidRealDeviceConstants.OUTPUT_FEATURE_STARTING_PATTERN.length()));
      }
    }
    return features;
  }

  /**
   * Check whether UID for Android package manager got exhausted (UID = 19999). Android
   * packagemanager has bug (b/24328998) which requires device reboot when UID reached 19999.
   *
   * @param serial serial number of the device
   * @return true or false
   */
  private boolean hasUidExhausted(String serial) throws InterruptedException {
    SortedMap<Integer, String> packages;
    Integer sdkVersion = device.getSdkVersion();
    try {
      packages = androidPkgManagerUtil.listPackagesWithUid(serial, sdkVersion);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get package list from device %s: %s",
          serial, MoreThrowables.shortDebugString(e));
      return false;
    }

    if (packages.isEmpty()) {
      logger.atWarning().log(
          "Failed to get package list from device %s with empty output.", serial);
      return false;
    }

    int largestUid = packages.lastKey();
    logger.atInfo().log("Largest PM UID on device %s is: %d", serial, largestUid);
    return largestUid >= AndroidRealDeviceConstants.ANDROID_PACKAGE_MANAGER_UID_THRESHOLD;
  }

  /**
   * Checks whether the device's system user should be "headless", i.e. system user runs only in the
   * background.
   */
  @VisibleForTesting
  boolean isHeadlessSystemUser(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    String prop;
    if (sdkVersion <= 28) { // sdk=28 is pi-car-dev
      prop = AndroidRealDeviceConstants.DEVICE_PROP_NAME_HEADLESS_USER_LEGACY;
    } else {
      prop = AndroidRealDeviceConstants.DEVICE_PROP_NAME_HEADLESS_USER;
    }
    return Boolean.parseBoolean(androidAdbUtil.getProperty(serial, ImmutableList.of(prop)));
  }

  /** Checks whether the device support recovering. */
  private boolean isRecoveryDevice() {
    List<String> dimensionList = device.getDimension(Dimension.Name.RECOVERY);
    return (dimensionList.size() == 1);
  }

  /** Checks whether the device support wiping recovering. */
  private boolean isWipeRecoveryDevice() {
    List<String> dimensionList = device.getDimension(Dimension.Name.RECOVERY);
    return (dimensionList.size() == 1
        && Ascii.equalsIgnoreCase(
            dimensionList.get(0), AndroidRealDeviceConstants.RECOVERY_TYPE_WIPE));
  }

  private boolean shouldFactoryResetViaTestHarness() {
    return !Flags.instance().keepTestHarnessFalse.getNonNull() && isTestHarnessRecoveryDevice();
  }

  /** Checks whether the device support test harness recovering. */
  private boolean isTestHarnessRecoveryDevice() {
    List<String> dimensionList = device.getDimension(Dimension.Name.RECOVERY);
    return (dimensionList.size() == 1
        && Ascii.equalsIgnoreCase(
            dimensionList.get(0), AndroidRealDeviceConstants.RECOVERY_TYPE_TEST_HARNESS));
  }

  /** Checks whether to reboot fastboot device to recover. */
  private boolean needRebootFastbootDeviceToRecover() {
    List<String> dimensionList = device.getDimension(Dimension.Name.REBOOT_TO_RECOVER);
    return (dimensionList.size() == 1 && Ascii.equalsIgnoreCase("true", dimensionList.get(0)));
  }

  private boolean isAllowedToRebootFastbootDeviceToRecover() {
    return needRebootFastbootDeviceToRecover()
        && deviceStat != null
        && deviceStat.getConsecutiveSetupFailureTimes()
            <= AndroidRealDeviceConstants.CONSECUTIVE_SETUP_FAILURE_NUM_TO_FASTBOOT_MODE;
  }

  /**
   * Toggles the charging on/off if needed.
   *
   * @param stopChargeLevel battery level at which charging should be stopped
   * @param startChargeLevel battery level at which charging should be started
   */
  private void toggleChargingForSafeDischarge(int stopChargeLevel, int startChargeLevel)
      throws MobileHarnessException, InterruptedException {
    Optional<Integer> batteryLevel = systemSettingUtil.getBatteryLevel(deviceId);
    if (batteryLevel.isEmpty()) {
      logger.atWarning().log(
          "Battery level is not available for device %s, skip toggling charging.", deviceId);
      return;
    }
    boolean enableCharging;
    if (batteryLevel.get() <= startChargeLevel) {
      enableCharging = true;
    } else if (batteryLevel.get() >= stopChargeLevel) {
      enableCharging = false;
    } else {
      return;
    }
    chargingUtil.charge(device, enableCharging);
    logger.atInfo().log(
        "%s the charging for device %s", enableCharging ? "Enable" : "Disable", deviceId);
  }

  /**
   * Tries to enforce safe discharge level on the device.
   *
   * <p>See {@link RuntimeChargingUtil.SupportedDeviceModel} for supported device models.
   */
  private void enforceSafeDischargeLevelIfNeeded() throws InterruptedException {
    if (notAllowSafeDischarge() || !Flags.instance().enforceSafeDischarge.getNonNull()) {
      return;
    }
    logger.atInfo().log("Enforcing safe discharge level for device %s", deviceId);
    Integer safeChargeLevelInt = Flags.instance().safeChargeLevel.getNonNull();
    Integer stopChargeLevelInt = Flags.instance().stopChargeLevel.getNonNull();
    Integer startChargeLevelInt = Flags.instance().startChargeLevel.getNonNull();
    try {
      chargingUtil.setFullChargeLevel(device, safeChargeLevelInt);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Error setting full charge level for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e));
      try {
        toggleChargingForSafeDischarge(stopChargeLevelInt, startChargeLevelInt);
      } catch (MobileHarnessException e2) {
        logger.atWarning().log(
            "Failed to enforce device %s safe discharge level: %s",
            deviceId, MoreThrowables.shortDebugString(e2));
      }
    }
  }

  /** Returns {@code true} if not allowing safe discharge on the device. */
  protected abstract boolean notAllowSafeDischarge();

  private boolean isQOrAboveBuild(String serial)
      throws MobileHarnessException, InterruptedException {
    return systemSettingUtil.getDeviceSdkVersion(serial) > 28
        || Ascii.equalsIgnoreCase(systemSettingUtil.getDeviceVersionCodeName(serial), "Q");
  }

  /**
   * Enable the device charge before test for supported devices. Tries to avoid the device get power
   * off during long running test. b/148306058.
   *
   * <p>See {@link RuntimeChargingUtil.SupportedDeviceModel} for supported device models.
   */
  private void enableDeviceChargeBeforeTest(TestInfo testInfo) throws InterruptedException {
    String testId = testInfo.locator().getId();
    if (!Flags.instance().enforceSafeDischarge.getNonNull()) {
      logger.atInfo().log(
          "Ignoring attempt to enable device %s charging before test %s because disabled"
              + " enforceSafeDischarge.",
          deviceId, testId);
      return;
    }

    try {
      chargingUtil.charge(device, /* charge= */ true);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Turn on charging on device %s before test %s", deviceId, testId);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Error when enabling charge for device %s before test %s: %s",
          deviceId, testInfo.locator().getId(), MoreThrowables.shortDebugString(e));
    }
  }

  private boolean becomeRoot() throws MobileHarnessException, InterruptedException {
    boolean rooted = systemStateManager.becomeRoot(device);
    if (needExtraForceRootDevice()) {
      // NOT caching device intentionally as this second rooting should finish very fast when the
      // above first device root succeeds. This is to ensure device is really rooted in case other
      // DMs reset or reboot device in the middle of SystemStateManager#becomeRoot. And if the reset
      // or reboot happens in this safety rooting which doesn't cached device, detectors would
      // likely kill the device runner and device will go thru the setup process again. b/241503198
      rooted = systemStateUtil.becomeRoot(deviceId);
      systemStateUtil.waitUntilReady(deviceId);
    }
    return rooted;
  }

  private void lockWithDeviceAdmin() throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Start to setup and lock device %s", deviceId);
    deviceAdminUtil.setupAndLock(deviceId);
    device.updateDimension(Dimension.Name.DEVICE_ADMIN_LOCKED, Dimension.Value.TRUE);
    device.updateDimension(Dimension.Name.DEVICE_ADMIN_WIFI_RESTRICTED, Dimension.Value.TRUE);
  }

  private void unlockWithDeviceAdmin() throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Start to unlock device %s", deviceId);
    // If the current user is not the default user, unlock the device will fail. See b/406939285.
    clearMultiUsers(deviceId, systemSettingUtil.getDeviceSdkVersion(deviceId));
    deviceAdminUtil.cleanupAndUnlock(deviceId);
    device.removeDimension(Dimension.Name.DEVICE_ADMIN_LOCKED);
    device.removeDimension(Dimension.Name.DEVICE_ADMIN_WIFI_RESTRICTED);
  }

  void installMtaasToolsAndTriggerCheckin() throws MobileHarnessException, InterruptedException {
    if (Flags.instance().enforceMtaasDeviceCheckinGroup.getNonNull()) {
      mtaasToolsInstantiator.install(device);
      triggerCheckin();
    }
  }

  private void triggerCheckin() throws MobileHarnessException, InterruptedException {
    if (device
        .getDimension(Ascii.toLowerCase(AndroidProperty.BUILD_TYPE.name()))
        .contains("userdebug")) {
      logger.atInfo().log("Using userdebug checkin method.");
      androidAdbUtil.broadcast(
          UtilArgs.builder().setSerial(deviceId).build(),
          IntentArgs.builder()
              .setAction("android.server.checkin.CHECKIN_NOW")
              .setComponent("com.google.android.gms")
              .build());
    } else {
      logger.atInfo().log("Using user checkin method.");
      androidAdbUtil.broadcast(
          UtilArgs.builder().setSerial(deviceId).build(),
          IntentArgs.builder().setAction("android.server.checkin.CHECKIN").build());
    }
    androidAdbUtil.broadcast(
        UtilArgs.builder().setSerial(deviceId).build(),
        IntentArgs.builder()
            .setAction("com.google.android.gms.gcm.ACTION_TRIGGER_TASK")
            .setComponent("com.google.android.gms/.phenotype.service.sync.PhenotypeConfigurator")
            .setExtras(ImmutableMap.of("tag", "oneoff"))
            .build());
  }

  /**
   * Toggles the wifi restriction on/off with device admin.
   *
   * @param enable whether to enable the wifi restriction
   */
  private void toggleDeviceAdminWifiRestrictionIfLocked(boolean enable)
      throws MobileHarnessException, InterruptedException {
    // Only toggle wifi restriction when the device is locked with device admin.
    if (device.getDimension(Dimension.Name.DEVICE_ADMIN_LOCKED).contains(Dimension.Value.TRUE)) {
      logger.atInfo().log("Toggling wifi restriction on device %s to status %s", deviceId, enable);
      deviceAdminUtil.toggleRestrictions(
          deviceId, ImmutableList.of("no_config_wifi"), /* enable= */ enable);
      if (enable) {
        device.updateDimension(Dimension.Name.DEVICE_ADMIN_WIFI_RESTRICTED, Dimension.Value.TRUE);
      } else {
        device.removeDimension(Dimension.Name.DEVICE_ADMIN_WIFI_RESTRICTED);
      }
    }
  }

  /**
   * Returns {@code true} if an extra device root operation when rooting the device. More details in
   * {@link #becomeRoot()}.
   */
  protected abstract boolean needExtraForceRootDevice();

  protected boolean getFlagClearAndroidDeviceMultiUsers() {
    return Flags.instance().clearAndroidDeviceMultiUsers.getNonNull();
  }

  protected boolean getFlagSkipCheckDeviceInternet() {
    return Flags.instance().skipCheckDeviceInternet.getNonNull();
  }

  private void cacheDevice(String deviceId, Duration expireTime) {
    DeviceCache.getInstance().cache(deviceId, device.getClass().getSimpleName(), expireTime);
  }

  private void invalidateCacheDevice(String deviceId) {
    DeviceCache.getInstance().invalidateCache(deviceId);
  }
}
