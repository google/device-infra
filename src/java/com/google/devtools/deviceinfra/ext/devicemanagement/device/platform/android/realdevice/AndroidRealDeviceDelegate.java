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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.common.metrics.stability.model.proto.ExceptionProto;
import com.google.devtools.common.metrics.stability.util.ErrorIdComparator;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.BaseDeviceHelper;
import com.google.devtools.deviceinfra.ext.devicemanagement.device.platform.android.AndroidDeviceDelegate;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Enums.FastbootProperty;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.deviceconfig.proto.Basic.WifiConfig;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLogType;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.infra.container.sandbox.device.DeviceSandboxController;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.DeviceDaemonApkInfoProvider;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.DeviceDaemonHelper;
import com.google.devtools.mobileharness.platform.android.connectivity.AndroidConnectivityUtil;
import com.google.devtools.mobileharness.platform.android.connectivity.ConnectToWifiArgs;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.file.StorageInfo;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.UsbDeviceLocator;
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
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.android.RuntimeChargingUtil;
import com.google.wireless.qa.mobileharness.shared.android.WifiUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidRealDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidSetWifiDecorator;
import com.google.wireless.qa.mobileharness.shared.controller.stat.DeviceStat;
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
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import javax.annotation.Nullable;

/** Delegate class to implement functionality for the Android real device. */
public abstract class AndroidRealDeviceDelegate {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidProcessUtil androidProcessUtil;
  private final AndroidSystemSettingUtil systemSettingUtil;
  private final AndroidFileUtil androidFileUtil;
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
  private final Fastboot fastboot;
  private final LocalFileUtil fileUtil;

  protected AndroidRealDeviceDelegate(
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
      LocalFileUtil fileUtil) {
    this.device = device;
    this.androidDeviceDelegate = androidDeviceDelegate;
    this.deviceStat = deviceStat;
    this.clock = clock;
    this.androidAdbInternalUtil = androidAdbInternalUtil;
    this.androidAdbUtil = androidAdbUtil;
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
    this.deviceId = device.getDeviceId();
    device.setProperty(
        AndroidRealDeviceConstants.PROPERTY_NAME_REBOOT_TO_STATE, DeviceState.DEVICE.name());
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
    } else if (fastboot.getDeviceSerials().contains(deviceId)) {
      setUpFastbootModeDevice();
    } else if (androidAdbInternalUtil
        .getDeviceSerialsByState(DeviceState.RECOVERY)
        .contains(deviceId)) {
      setUpRecoveryModeDevice();
    } else {
      // Bad connection devices could not be detected sometime.
      throw new MobileHarnessException(
          ErrorCode.ANDROID_INIT_ERROR,
          "Device is undetectable. Please replug the usb cable or reboot the device.");
    }

    device.updateDimension(Dimension.Name.SUPPORTS_GMSCORE, Dimension.Value.TRUE);
    extrasInSetUp();

    lastSetupTime = clock.instant();
  }

  /** Returns {@code true} if the device should be set up as online mode device. */
  protected abstract boolean shouldSetUpAsOnlineModeDevice()
      throws MobileHarnessException, InterruptedException;

  /** Extra things for device set up in the {@link #setUp()} */
  protected abstract void extrasInSetUp() throws InterruptedException;

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
        DeviceCache.getInstance()
            .cache(
                deviceId,
                getClass().getSimpleName(),
                AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT);
        logger.atInfo().log("Start rebooting the wiped device %s.", deviceId);

        fastboot.reboot(deviceId);
        // Wait for device ready.
        systemStateUtil.waitForDevice(deviceId, AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT);
        systemStateUtil.waitUntilReady(deviceId);
        logger.atInfo().log("Reboot the wiped device %s successfully.", deviceId);

        // Become root
        systemStateManager.becomeRoot(device);
        logger.atInfo().log("Device %s becomes root successfully.", deviceId);

        // Disable setup wizard and reboot device if needed.
        if (systemSettingUtil.disableSetupWizard(deviceId) == PostSettingDeviceOp.REBOOT) {
          logger.atInfo().log("Device %s will reboot after skip setup wizard", deviceId);
          systemStateUtil.reboot(deviceId);
          systemStateUtil.waitForDevice(
              deviceId, AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT);
          systemStateUtil.waitUntilReady(deviceId);
        }
        logger.atInfo().log("Disable setup wizard and reboot device %s successfully.", deviceId);
      } catch (MobileHarnessException e) {
        AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
        logger.atWarning().log("%s", e.getMessage());
        throw e;
      } finally {
        DeviceCache.getInstance().invalidateCache(deviceId);
      }
      setUpOnlineModeDevice();
    } else {
      device.addSupportedDeviceType(AndroidRealDeviceConstants.ANDROID_FASTBOOT_DEVICE);
      device.addSupportedDeviceType(AndroidRealDeviceConstants.ANDROID_FLASHABLE_DEVICE);
      device.addSupportedDriver(AndroidRealDeviceConstants.NO_OP_DRIVER);
      device.addSupportedDriver(AndroidRealDeviceConstants.MOBLY_TEST_DRIVER);
      device.addSupportedDriver(AndroidRealDeviceConstants.MOBLY_AOSP_TEST_DRIVER);
      device.addSupportedDriver(AndroidRealDeviceConstants.ACID_REMOTE_DRIVER);
      String hardware = fastboot.getVar(deviceId, FastbootProperty.PRODUCT);
      if (!Strings.isNullOrEmpty(hardware)) {
        device.updateDimension(Dimension.Name.HARDWARE, hardware);
      }
      String unlocked = fastboot.getVar(deviceId, FastbootProperty.UNLOCKED);
      if (!Strings.isNullOrEmpty(unlocked)) {
        device.updateDimension(Dimension.Name.OEM_UNLOCK, unlocked);
      }

      try {
        String serialno = fastboot.getVar(deviceId, FastbootProperty.SERIALNO);
        if (!Strings.isNullOrEmpty(serialno)) {
          device.updateDimension(Ascii.toLowerCase(AndroidProperty.SERIAL.name()), serialno);
        }
      } catch (MobileHarnessException e) {
        logger.atInfo().log(
            "Failed to get serialno for fastboot device %s: %s",
            deviceId, MoreThrowables.shortDebugString(e, 0));
      }

      device.addSupportedDecorator("AndroidFlashDeviceDecorator");
      device.addSupportedDecorator("AndroidFlashstationDecorator");
      device.addSupportedDecorator("AndroidAutomotiveFlashDecorator");
      addFastbootCommunication(deviceId);

      // TODO: update this to support any amlogic devices without hardcoding them in
      if ("atom".equals(hardware)
          || "Beast".equals(hardware)
          || "deadpool".equals(hardware)
          || "sabrina".equals(hardware)
          || "boreal".equals(hardware)) {
        // to be flashed by appropriate decorator
        device.addSupportedDecorator("AndroidAmlogicFlashDecorator");
        // to be able to just reboot into adb.
        device.addSupportedDecorator("AndroidFactoryResetDecorator");
      }
    }
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

  private void addADBCommunication(String deviceId) throws InterruptedException {
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
    validateDeviceOnceReady(deviceId, device.getClass().getSimpleName());
    androidDeviceDelegate.setUp(isRooted(), extraDimensionsForSetUpDevice());

    // Indicates the device is online.
    device.addSupportedDeviceType(AndroidRealDeviceConstants.ANDROID_ONLINE_DEVICE);

    addRealDeviceBasicDimensionsAndProperties();
    addRealDeviceBasicSupportedDriversDecorators();
    addExtraRealDeviceBasicSupportedDriversDecorators();

    // Switch the user to default user before setting and apk installation.
    clearMultiUsers(deviceId, device.getSdkVersion() == null ? 0 : device.getSdkVersion());

    // The periodical check also applies to device initialization.
    checkOnlineModeDevice();

    checkExtraSupport();

    if (!ifEnableFullStackFeatures()) {
      logger.atInfo().log("Device %s is ready", deviceId);
      return;
    }

    addRealDeviceFullStackSupportedDeviceTypesDriversDecorators();
    addExtraRealDeviceFullStackSupportedDriversDecorators();
    addRealDeviceFullStackDimensions();
    startActivityController();
    extraSettingsForFullStackDevice();
    logger.atInfo().log("Device %s is ready", deviceId);
  }

  /** Extra dimensions added to the device in the {@link #setUpOnlineModeDevice()} */
  protected abstract Multimap<Dimension.Name, String> extraDimensionsForSetUpDevice();

  /**
   * Adds real device basic dimensions and properties.
   *
   * <p>This method should not throw checked exceptions other than {@code InterruptedException}, to
   * ensure it won't interrupt the device setup process.
   */
  private void addRealDeviceBasicDimensionsAndProperties() throws InterruptedException {
    addADBCommunication(deviceId);
    // Adds real device specific dimensions.
    try {
      device.addDimension(
          Dimension.Name.NUM_CPUS, String.valueOf(systemSpecUtil.getNumberOfCpus(deviceId)));
    } catch (MobileHarnessException e) {
      logger.atInfo().log("%s", e.getMessage());
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
  }

  private void addRealDeviceBasicSupportedDriversDecorators()
      throws MobileHarnessException, InterruptedException {
    // The flash decorators are added before checkOnlineModeDevice() to avoid becoming FailedDevice
    // without being able to recover with flashing.
    if (ifEnableDeviceFlashAndResetDecorators()) {
      Set<String> features = systemSpecUtil.getSystemFeatures(deviceId);
      // FlashDevice decorator is only meaningful with google device, sw system or watch.
      List<String> characteristics =
          device.getDimension(Ascii.toLowerCase(AndroidProperty.CHARACTERISTICS.name()));
      String allCharacteristics = Joiner.on(' ').join(characteristics);
      List<String> brandNames =
          device.getDimension(Ascii.toLowerCase(AndroidProperty.BRAND.name()));
      List<String> deviceNames =
          device.getDimension(Ascii.toLowerCase(AndroidProperty.DEVICE.name()));
      List<String> prodBoards =
          device.getDimension(Ascii.toLowerCase(AndroidProperty.PRODUCT_BOARD.name()));
      if (brandNames.contains("google")
          || brandNames.contains("ape_acme")
          || brandNames.contains("android")
          || brandNames.contains("qti")
          || brandNames.contains("exynos")
          || brandNames.contains("jio")
          || deviceNames.contains("moohan")
          || deviceNames.contains("xrdk1")
          || deviceNames.contains("xrdk2")
          || deviceNames.contains("xrvst2")
          || allCharacteristics.contains("watch")
          || features.contains(AndroidRealDeviceConstants.FEATURE_IOT)
          || features.contains(AndroidRealDeviceConstants.FEATURE_EMBEDDED)
          || features.contains(AndroidRealDeviceConstants.FEATURE_DAYDREAM_STANDALONE)
          || features.contains(AndroidRealDeviceConstants.FEATURE_LEANBACK)) {
        device.addSupportedDeviceType(AndroidRealDeviceConstants.ANDROID_FLASHABLE_DEVICE);
        device.addSupportedDecorator("AndroidOtaUpdateDecorator");
        device.addSupportedDecorator("AndroidFactoryResetDecorator");
        if (features.contains(AndroidRealDeviceConstants.FEATURE_EMBEDDED)) {
          device.addSupportedDecorator("AndroidThingsFlashDeviceDecorator");
        } else if (prodBoards.contains("atom")
            || prodBoards.contains("beast")
            || prodBoards.contains("deadpool")
            || prodBoards.contains("sabrina")
            || prodBoards.contains("boreal")) {
          // TODO: update this to support any amlogic devices without hard-coding them
          // in
          device.addSupportedDecorator("AndroidAmlogicFlashDecorator");
          device.addSupportedDecorator("AndroidFlashstationDecorator");
        } else {
          device.addSupportedDecorator("AndroidFlashDeviceDecorator");
          device.addSupportedDecorator("AndroidFlashstationDecorator");
          device.addSupportedDecorator("AndroidAutomotiveFlashDecorator");
        }
      }
    }
    // HD Video decorator is supported by real device only.
    device.addSupportedDecorator("AndroidHdVideoDecorator");

    // BatteryStats and CpuTime decorators (run "dumpsys battery set usb 0" and then parse "dumpsys
    // batterystats") are currently meaningful with real device (L & above) only.
    device.addSupportedDecorator("AndroidBatteryStatsDecorator");

    // Phonesky Self Update specific decorator.
    device.addSupportedDecorator("AndroidPhoneskySelfUpdateDecorator");

    // Framerate data collection for VR Apps provided from VrCore.
    device.addSupportedDecorator("AndroidVRFramerateDecorator");

    // Go through popup dialogs while installing apps.
    device.addSupportedDecorator("AndroidInstallAppPopupHelperDecorator");

    if (isRooted()) {
      // Runs tcpdump on device.
      device.addSupportedDecorator("AndroidNetworkDecorator");
      // Runs performance locking on device.
      device.addSupportedDecorator("AndroidPerformanceLockDecorator");
    } else if (AndroidRealDeviceDelegateHelper.isInSupportedAllowlistForPerformanceLockDecorator(
        device)) {
      // Runs performance locking on device.
      device.addSupportedDecorator("AndroidPerformanceLockDecorator");
    }
  }

  /** Returns {@code true} if need to enable device flash and reset decorators. */
  protected abstract boolean ifEnableDeviceFlashAndResetDecorators();

  /** Adds extra supported drivers and decorators to the device. */
  protected abstract void addExtraRealDeviceBasicSupportedDriversDecorators()
      throws InterruptedException;

  /** Checks if device provides extra supports. */
  protected abstract void checkExtraSupport() throws InterruptedException;

  /** Returns {@code true} if it enables full stack features. */
  protected abstract boolean ifEnableFullStackFeatures();

  /**
   * Adds drivers and decorators supported by rooted or non-rooted devices for full stack features.
   */
  private void addRealDeviceFullStackSupportedDeviceTypesDriversDecorators()
      throws MobileHarnessException, InterruptedException {
    // *************** More drivers ***************

    // Afw Test Harness is supported by real device only and it requires flash/adb.
    device.addSupportedDriver("AndroidForWorkTestHarness");

    // Add support for comms testing using Acts.
    device.addSupportedDriver("AndroidCommsActsTest");

    // Add support for google tradefed wrapper.
    device.addSupportedDriver("GoogleTradefedDriver");

    // Add support for Platform Tradefed tests execution through the ATE library
    device.addSupportedDriver("AndroidTradefedTest");

    // Add support for running xTS tradefed test.
    device.addSupportedDriver("XtsTradefedTest");

    // *************** More decorators ***************

    // Disable auto-updates in Play Store before running any tests
    device.addSupportedDecorator("AndroidDisableAutoUpdatesDecorator");

    // Set Wifi ssid on a real device before running a test.
    device.addSupportedDecorator("AndroidSetWifiDecorator");

    device.addSupportedDecorator("AndroidKibbleDecorator");

    // Mobly Monsoon decorator operates on Mobly sub-devices: AndroidRealDevice.
    device.addSupportedDecorator("MoblyMonsoonDecorator");

    if (isRooted()) {
      device.addSupportedDecorator("AndroidMonsoonVideoDecorator");
    }
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
          "Failed to get device %s IMEI: %s", deviceId, MoreThrowables.shortDebugString(e, 0));
    }

    // Adds screenshot dimension.
    if (ifScreenshotAble(sdkVersion)) {
      device.addDimension(Dimension.Name.SCREENSHOT_ABLE, String.valueOf(true));
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
          "Failed to get device %s ICCID: %s", deviceId, MoreThrowables.shortDebugString(e, 0));
    }

    // Add a comma-delimited list of ICCIDS to device dimensions.
    try {
      device.addDimension(Dimension.Name.ICCIDS, getCommaSeparatedIccids());
    } catch (MobileHarnessException e) {
      logger.atInfo().log(
          "Failed to get list of SIM ICCIDS for device %s: %s",
          deviceId, MoreThrowables.shortDebugString(e, 0));
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
    // Disables airplane mode, enables unknown source. Only works with SDK version >= 17.
    Integer sdkVersion = device.getSdkVersion();
    if (sdkVersion != null && sdkVersion >= 17) {
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
    enableTestPropertiesAndDisablePackages(deviceId);
    // Tries to keep device awake.
    logger.atInfo().log("Device %s stays awake", deviceId);
    systemSettingUtil.keepAwake(deviceId, /* alwaysAwake= */ true);

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

  private void setUpRecoveryModeDevice() throws MobileHarnessException, InterruptedException {
    try {
      logger.atInfo().log("Try to reboot the recovery device %s", deviceId);
      // Cache for reboot the device.
      DeviceCache.getInstance()
          .cache(
              deviceId,
              getClass().getSimpleName(),
              AndroidRealDeviceConstants.WAIT_FOR_REBOOT_TIMEOUT);
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
      DeviceCache.getInstance().invalidateCache(deviceId);
    }

    // Recovery device is rebooted to normal mode
    if (androidAdbInternalUtil.getRealDeviceSerials(/* online= */ true).contains(deviceId)) {
      setUpOnlineModeDevice();
    } else {
      AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.DEVICE);
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          AndroidErrorId.ANDROID_REAL_DEVICE_RECOVER_RECOVERY_DEVICE_FAILED,
          String.format("Failed to recover recovery device %s, reboot device later.", deviceId));
    }
  }

  private void configureRecoveryDevice() throws MobileHarnessException, InterruptedException {
    device.addSupportedDeviceType(AndroidRealDeviceConstants.ANDROID_RECOVERY_DEVICE);
    device.addSupportedDeviceType(AndroidRealDeviceConstants.ANDROID_FLASHABLE_DEVICE);
    device.addSupportedDriver(AndroidRealDeviceConstants.NO_OP_DRIVER);
    device.addSupportedDecorator("AndroidFlashstationDecorator");
    androidDeviceDelegate.updateAndroidPropertyDimensions(deviceId);
  }

  public boolean checkDevice() throws MobileHarnessException, InterruptedException {
    if (!ifSkipCheckAbnormalDevice()) {
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
    /*
     Any recovery mode device should not be found when checking device. Reboot it to fastboot
     mode to notify the lab admins.
    */
    if (androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.RECOVERY).contains(deviceId)) {
      logger.atInfo().log("Checking recovery device %s. Rebooting...", deviceId);
      AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
      throw new MobileHarnessException(
          ErrorCode.ANDROID_INIT_ERROR, "Checking recovery device. Rebooting to fastboot mode.");
    }

    // Checks the device which is the fastboot mode.
    if (fastboot.getDeviceSerials().contains(deviceId)) {
      if (device.getDeviceTypes().contains(AndroidRealDeviceConstants.ANDROID_FASTBOOT_DEVICE)) {
        if (isWipeRecoveryDevice()
            && clock
                .instant()
                .isAfter(lastSetupTime.plus(AndroidRealDeviceConstants.AUTO_FASTWIPE_TIMEOUT))) {
          AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
          throw new MobileHarnessException(
              ErrorCode.ANDROID_INIT_ERROR,
              "Checking fastboot device. Rebooting to fastboot mode.");
        } else if (!isRecoveryDevice()
            && clock
                .instant()
                .isAfter(lastSetupTime.plus(AndroidRealDeviceConstants.AUTO_RECOVERY_TIMEOUT))) {
          AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
          throw new MobileHarnessException(
              ErrorCode.ANDROID_INIT_ERROR,
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
    isDimensionChanged =
        isDimensionChanged
            | isNetworkChanged
            | checkBattery()
            | checkAndCleanInternalStorage()
            | checkStorage(/* isExternal= */ true)
            | checkLaunchers()
            | checkIccids()
            | extraChecksForOnlineModeDevice();
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

      // Sets the hostname shown on the device daemon.
      String hostname = null;
      try {
        hostname = networkUtil.getLocalHostName();
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to set host name to device daemon for device %s: %s",
            deviceId, MoreThrowables.shortDebugString(e, 0));
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
            device.getProperty(AndroidRealDeviceConstants.PROP_OWNERS));
      }
    } else {
      logger.atInfo().log("Android device daemon is disabled on device %s", deviceId);
      // If lab server start with no daemon flag, then kill daemon server during initialization.
      deviceDaemonHelper.uninstallDaemonApk(device, /* log= */ null);
    }
    startActivityController();

    enforceSafeDischargeLevelIfNeeded();

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

  /** Gets the sandbox controller. */
  public DeviceSandboxController getSandboxController() {
    return getSandboxControllerImpl();
  }

  protected abstract DeviceSandboxController getSandboxControllerImpl();

  /** Android real device preparations before running the test. */
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    if (!androidAdbInternalUtil.getRealDeviceSerials(/* online= */ true).contains(deviceId)) {
      testInfo
          .toNewTestInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "preRunTest of AndroidRealDevice [%s] skipped because it cannot be detected online.",
              deviceId);
      return;
    }
    androidDeviceDelegate.preRunTest(testInfo.toNewTestInfo(), isRooted());
    prependedRealDevicePreparationBeforeTest(testInfo);

    if (!skipRealDeviceDefaultPreparationBeforeTest()) {
      if (DeviceDaemonApkInfoProvider.isDeviceDaemonEnabled()) {
        // Makes sure the device daemon is started/stopped before every test.
        if (testInfo.getJobInfo().isParamTrue(AndroidRealDeviceSpec.PARAM_KILL_DAEMON_ON_TEST)) {
          testInfo
              .toNewTestInfo()
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Kill device daemon on device %s", deviceId);
          deviceDaemonHelper.uninstallDaemonApk(device, testInfo.toNewTestInfo().log());
        } else {
          deviceDaemonHelper.installAndStartDaemon(
              device,
              testInfo.toNewTestInfo().log(),
              device.getProperty(AndroidRealDeviceConstants.PROP_LABELS),
              device.getProperty(AndroidRealDeviceConstants.PROP_HOSTNAME),
              device.getProperty(AndroidRealDeviceConstants.PROP_OWNERS));
        }
      } else {
        testInfo
            .toNewTestInfo()
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Device daemon is disabled on device %s", deviceId);
      }
      // Disable activity controller based on PARAM_DISABLE_ACTIVITY_CONTROLLER_ON_TEST
      if (testInfo
          .getJobInfo()
          .isParamTrue(AndroidRealDeviceConstants.PARAM_DISABLE_ACTIVITY_CONTROLLER_ON_TEST)) {
        testInfo
            .toNewTestInfo()
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Disable activity controller on device %s", deviceId);
        stopActivityController();
      } else {
        startActivityController();
      }
      // Disables the NFC if feasible.
      if (device.getBooleanProperty(AndroidRealDeviceConstants.PROPERTY_NAME_ROOTED)) {
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
        .getJobInfo()
        .getBoolParam(
            AndroidRealDeviceConstants.PARAM_CLEAR_GSERVICES_OVERRIDES, true /* default */)) {
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

  /** Operations after a test and before resetting/reloading the driver. */
  @CanIgnoreReturnValue
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    // Removes the device cache after tests finish, otherwise device status may be wrong. b/32101092
    DeviceCache.getInstance().invalidateCache(device.info().deviceId().controlId());

    prependedRealDeviceAfterTestProcess(testInfo);

    if (!skipRealDeviceDefaultAfterTestProcess()) {
      if (isRecoveryDevice()) {
        if (isWipeRecoveryDevice()) {
          // No permission to factory reset for non-rooted device.
          if (isRooted()) {
            // TODO: Add monitors for fast wipe.
            // If wipe recovery enabled, reboot the device to fastboot and wipe the device.
            logger.atInfo().log("Fast wipe device %s to clean all user data.", deviceId);
            AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.FASTBOOT);
            return PostTestDeviceOp.REBOOT;
          } else {
            logger.atWarning().log(
                "Skip factory reset device %s since it‘s invalid for non-rooted devices.",
                deviceId);
          }
        } else if (isTestHarnessRecoveryDevice()) {
          if (isQOrAboveBuild(deviceId)) {
            logger.atInfo().log("It'll factory reset device %s via Test Harness Mode.", deviceId);
            AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.DEVICE);
            return PostTestDeviceOp.REBOOT;
          } else {
            logger.atInfo().log(
                "Factory reset via Test Harness Mode is not supported on device %s.", deviceId);
          }
        } else {
          // If fast recovery enabled, reboot the device to recovery and clean all user data even if
          // the system build changed.
          logger.atInfo().log("Reboot device %s to recovery to clean all user data.", deviceId);
          AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.RECOVERY);
          return PostTestDeviceOp.REBOOT;
        }
      }

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
          AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.DEVICE);
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
        if (testInfo.toNewTestInfo().resultWithCause().get().causeProto().isPresent()) {
          ExceptionProto.ExceptionDetail exceptionDetail =
              testInfo.toNewTestInfo().resultWithCause().get().causeProto().get();
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
              testInfo.toNewTestInfo().log(),
              device.getProperty(AndroidRealDeviceConstants.PROP_LABELS),
              device.getProperty(AndroidRealDeviceConstants.PROP_HOSTNAME),
              device.getProperty(AndroidRealDeviceConstants.PROP_OWNERS));
          if (testInfo.getJobInfo().isParamTrue(AndroidRealDeviceSpec.PARAM_KILL_DAEMON_ON_TEST)) {
            testInfo
                .toNewTestInfo()
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
    if (Ascii.equalsIgnoreCase(
        testInfo.getJobInfo().getType().getDriver(), "AndroidTradefedTest")) {
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
    // Only invokes the reboot command when the device is detected, otherwise will fail.
    if (androidAdbInternalUtil.getRealDeviceSerials(/* online= */ true).contains(deviceId)
        || androidAdbInternalUtil
            .getDeviceSerialsByState(DeviceState.RECOVERY)
            .contains(deviceId)) {
      switch (DeviceState.valueOf(
          device.getProperty(AndroidRealDeviceConstants.PROPERTY_NAME_REBOOT_TO_STATE))) {
        case DEVICE:
          if (isTestHarnessRecoveryDevice()) {
            logger.atInfo().log("Factory reset device %s via Test Harness Mode.", deviceId);
            systemStateUtil.factoryResetViaTestHarness(deviceId, /* waitTime= */ null);
          } else {
            logger.atInfo().log("Device %s reboot to normal mode", deviceId);
            systemStateUtil.reboot(deviceId);
          }
          break;
        default:
          logger.atInfo().log("Device %s reboot to fastboot mode", deviceId);
          systemStateUtil.rebootToBootloader(deviceId);
          break;
      }
    } else if (fastboot.getDeviceSerials().contains(deviceId)) {
      switch (DeviceState.valueOf(
          device.getProperty(AndroidRealDeviceConstants.PROPERTY_NAME_REBOOT_TO_STATE))) {
        case FASTBOOT:
          logger.atInfo().log("Device %s reboot to fastboot mode", deviceId);
          String unused = fastboot.rebootBootloader(deviceId);
          break;
        default:
          // Skip rebooting fastboot mode device, because its data may be destroyed.
          logger.atInfo().log("Device %s is in the fastboot mode skip rebooting", deviceId);
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
            deviceId, MoreThrowables.shortDebugString(e, 0));
      }
    }
    return desFilePathOnHost;
  }

  /** Gets the device log based on the log type. */
  public String getDeviceLog(DeviceLogType deviceLogType)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    try {
      String filePathPrefix = PathUtil.join(device.getGenFileDir(), UUID.randomUUID().toString());
      if (deviceLogType.equals(DeviceLogType.DEVICE_LOG_TYPE_ANDROID_LOGCAT)) {
        String rawLog = androidAdbUtil.logCat(deviceId, "", "*:Verbose");
        String desFilePathOnHost = filePathPrefix + "_logcat_dump.txt";
        fileUtil.writeToFile(desFilePathOnHost, rawLog);
        return desFilePathOnHost;
      }
    } catch (MobileHarnessException e) {
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          InfraErrorId.LAB_GET_DEVICE_LOG_LOGCAT_ERROR, "Failed to get logcat.", e);
    }
    throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
        InfraErrorId.LAB_GET_DEVICE_LOG_METHOD_UNSUPPORTED,
        String.format(
            "The device log type %s for the device %s[%s] is not supported.",
            deviceLogType, deviceId, getClass().getSimpleName()));
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
    Optional<Duration> setupTimeoutForRecoveryAndFastbootDevice =
        getSetupTimeoutForRecoveryAndFastbootDevice();
    if (setupTimeoutForRecoveryAndFastbootDevice.isPresent()) {
      return setupTimeoutForRecoveryAndFastbootDevice.get();
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
          .toNewTestInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Not clearing GServices overrides because device %s is not rooted.", deviceId);
      return;
    }
    Integer sdkVersion = device.getSdkVersion();
    if (sdkVersion == null || sdkVersion < 18) {
      testInfo
          .toNewTestInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Not clearing GServices overrides because device %s version is unknown or too old.",
              deviceId);
      return;
    }
    testInfo
        .toNewTestInfo()
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Clearing GServices overrides for device %s", deviceId);
    try {
      String unused = systemSettingUtil.clearGServicesOverrides(deviceId);
    } catch (MobileHarnessException e) {
      testInfo
          .toNewTestInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Failed to clear GServices overrides for device %s: %s", deviceId, e.getMessage());
    }
  }

  /** Cleans up when the device becomes undetectable/disconnected. */
  public void tearDown()
      throws InterruptedException,
          com.google.devtools.mobileharness.api.model.error.MobileHarnessException {
    deviceTearDown();
  }

  protected abstract void deviceTearDown()
      throws InterruptedException,
          com.google.devtools.mobileharness.api.model.error.MobileHarnessException;

  /**
   * Sets device properties and packages to let the device know it is running in a test harness and
   * disable some unnecessary features for safety, such as disable phone call, mute, etc.
   *
   * <p>Note we can only set these properties after a device become root. Also, once set, the test
   * properties can not be overwritten until we reboot the device.
   *
   * @see <a href="http://b/14574172">background</a>
   */
  private void enableTestPropertiesAndDisablePackages(String serial)
      throws MobileHarnessException, InterruptedException {
    if (!ifTrySetDevicePropertiesAndDisablePackages()) {
      return;
    }
    try {
      if (needRebootToClearReadOnlyTestProperties()) {
        logger.atInfo().log("Reboot device %s to clear ro properties", serial);
        systemStateManager.reboot(device, /* log= */ null, /* deviceReadyTimeout= */ null);
      }
    } catch (com.google.devtools.mobileharness.api.model.error.MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to check device %s read only properties: %s",
          serial, MoreThrowables.shortDebugString(e, 0));
    }
    if (Flags.instance().disableCalling.getNonNull()) {
      logger.atInfo().log("Disable calling on device %s", serial);
      androidAdbUtil.setProperty(serial, "ro.telephony.disable-call", "true", true);
    }
    if (Flags.instance().setTestHarnessProperty.getNonNull()) {
      logger.atInfo().log("Set property ro.test_harness to 1 on device %s", serial);
      androidAdbUtil.setProperty(serial, "ro.test_harness", "1", true);
    }
    if (Flags.instance().muteAndroid.getNonNull()) {
      logger.atInfo().log("Mute audio on device %s", serial);
      androidAdbUtil.setProperty(serial, "ro.audio.silent", "1", true);
    }
    if (Flags.instance().disableCellBroadcastReceiver.getNonNull()) {
      try {
        androidPkgManagerUtil.disablePackage(serial, "com.android.cellbroadcastreceiver");
      } catch (com.google.devtools.mobileharness.api.model.error.MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to disable package com.android.cellbroadcastreceiver when setup the device "
                + "%s: %s",
            serial, MoreThrowables.shortDebugString(e, 0));
      }
    }
  }

  @VisibleForTesting
  boolean needRebootToClearReadOnlyTestProperties()
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    if (!ifClearAnyReadOnlyTestProperties()) {
      return false;
    }
    return needRebootToClearTestProperty(
            "ro.telephony.disable-call",
            Flags.instance().disableCalling.getNonNull() ? "true" : "false")
        || needRebootToClearTestProperty(
            "ro.test_harness", Flags.instance().setTestHarnessProperty.getNonNull() ? "1" : "0")
        || needRebootToClearTestProperty(
            "ro.audio.silent", Flags.instance().muteAndroid.getNonNull() ? "1" : "0");
  }

  private boolean ifClearAnyReadOnlyTestProperties() {
    return !Flags.instance().disableCalling.getNonNull()
        || !Flags.instance().setTestHarnessProperty.getNonNull()
        || !Flags.instance().muteAndroid.getNonNull();
  }

  private boolean needRebootToClearTestProperty(String roPropName, String newValue)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    String devicePropValue = androidAdbUtil.getProperty(deviceId, ImmutableList.of(roPropName));
    // If current read only prop is not set, no need device reboot.
    if (Strings.isNullOrEmpty(devicePropValue)) {
      return false;
    }
    return !Ascii.equalsIgnoreCase(devicePropValue, newValue);
  }

  /**
   * Returns {@code true} if need to run {@link #enableTestPropertiesAndDisablePackages(String)}.
   */
  protected abstract boolean ifTrySetDevicePropertiesAndDisablePackages();

  /**
   * Connect to WIFI by given SSID and password, and does't ensure it is successful to connect to
   * WIFI. The devices of non-ged, release key and version 2.2.1 are all tested.
   *
   * @param ssid SSID of WIFI to be connected
   * @param pwd password of WIFI to be connected
   */
  private void connectToWifi(String serial, int sdkVersion, String ssid, String pwd)
      throws MobileHarnessException, InterruptedException {
    WifiUtil wifiUtil = new WifiUtil();
    apkInstaller.installApkIfVersionMismatched(
        device, ApkInstallArgs.builder().setApkPath(wifiUtil.getWifiUtilApkPath()).build(), null);
    // It doesn't guarantee to connect to wifi successfully, so only use a short timeout to check.
    ConnectToWifiArgs.Builder argsBuilder =
        ConnectToWifiArgs.builder()
            .setSerial(serial)
            .setSdkVersion(sdkVersion)
            .setWifiSsid(ssid)
            .setScanSsid(false)
            .setWaitTimeout(Duration.ofSeconds(5));
    if (!Strings.isNullOrEmpty(pwd)) {
      argsBuilder.setWifiPsk(pwd);
    }
    if (!connectivityUtil.connectToWifi(argsBuilder.build(), /* log= */ null)) {
      logger.atWarning().log("Failed to connect device %s to SSID '%S'", serial, ssid);
    }
  }

  /**
   * Checks network connection of the device.
   *
   * @return whether there is any dimension changed
   */
  @VisibleForTesting
  boolean checkNetwork() throws InterruptedException {
    if (needToInstallWifiApk()) {
      // Install the wifi apk for Stallite lab only. (b/200517628)
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
            "Failed to install WiFi apk: %s", MoreThrowables.shortDebugString(e, 0));
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
  private Optional<String> bestEffortConnectNetworkSsid() throws InterruptedException {
    Optional<String> currentSsid = checkNetworkSsid();
    if (currentSsid.isEmpty()) {
      currentSsid = recoverWifiConnectionIfRequired();
    }
    return currentSsid;
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
          deviceId, MoreThrowables.shortDebugString(e, 0));
    }
    try {
      currentSsid = connectivityUtil.getNetworkSsid(deviceId, sdkVersion);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get device %s network SSID: %s",
          deviceId, MoreThrowables.shortDebugString(e, 0));
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
      connectToWifi(deviceId, sdkVersion, defaultWifi.getSsid(), defaultWifi.getPsk());
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to connect device %s to WIFI %s with psk %s: %s",
          deviceId,
          defaultWifi.getSsid(),
          defaultWifi.getPsk(),
          MoreThrowables.shortDebugString(e, 0));
    }
    // Regain the WiFi SSID
    try {
      currentSsid = connectivityUtil.getNetworkSsid(deviceId, sdkVersion);
    } catch (MobileHarnessException e) {
      currentSsid = null;
      logger.atWarning().log(
          "Failed to get device %s network SSID: %s",
          deviceId, MoreThrowables.shortDebugString(e, 0));
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
            deviceId, MoreThrowables.shortDebugString(e, 0));
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
          "Failed to recover device %s network: %s",
          deviceId, MoreThrowables.shortDebugString(e, 0));
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
          deviceId, MoreThrowables.shortDebugString(e, 0));
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
      int batteryLevel = -1;
      String batteryStatus = "unknown";
      try {
        batteryLevel = systemSettingUtil.getBatteryLevel(deviceId);
        logger.atInfo().log("Device %s battery level: %d", deviceId, batteryLevel);
        if (batteryLevel < 20) {
          batteryStatus = "low";
        } else {
          batteryStatus = "ok";
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().log(
            "Failed to get device %s battery level: %s",
            deviceId, MoreThrowables.shortDebugString(e, 0));
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
            deviceId, MoreThrowables.shortDebugString(e, 0));
      }
      isDimensionChanged |= device.updateDimension(Dimension.Name.BATTERY_STATUS, batteryStatus);
      isDimensionChanged |=
          device.updateDimension(Dimension.Name.BATTERY_LEVEL, String.valueOf(batteryLevel));
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
          "Failed to fetch device %s's ICCIDs: %s",
          deviceId, MoreThrowables.shortDebugString(e, 0));
    }

    return device.updateDimension(Dimension.Name.ICCIDS, iccids);
  }

  /** Returns a comma-separated string of the ICCIDs of the SIMs on the device. */
  private String getCommaSeparatedIccids() throws MobileHarnessException, InterruptedException {
    ImmutableList<String> iccids = systemSpecUtil.getIccids(deviceId);
    return iccids == null ? "" : Joiner.on(",").join(iccids);
  }

  /**
   * Checks network connection stability.
   *
   * @return whethere there is any dimension changed
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
   * @return whethere there is any dimension changed
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
    int freeStorageAlertMB;
    StorageInfo storageInfo = null;

    if (isExternal) {
      dimensionStorageStatus = Dimension.Name.EXTERNAL_STORAGE_STATUS;
      dimensionFreeStorage = Dimension.Name.FREE_EXTERNAL_STORAGE;
      dimensionFreeStoragePercentage = Dimension.Name.FREE_EXTERNAL_STORAGE_PERCENTAGE;
      externalOrInternal = AndroidRealDeviceConstants.STRING_EXTERNAL;
      freeStorageAlertMB = AndroidRealDeviceConstants.FREE_EXTERNAL_STORAGE_ALERT_MB;
    } else {
      dimensionStorageStatus = Dimension.Name.INTERNAL_STORAGE_STATUS;
      dimensionFreeStorage = Dimension.Name.FREE_INTERNAL_STORAGE;
      dimensionFreeStoragePercentage = Dimension.Name.FREE_INTERNAL_STORAGE_PERCENTAGE;
      externalOrInternal = AndroidRealDeviceConstants.STRING_INTERNAL;
      freeStorageAlertMB = Flags.instance().internalStorageAlert.getNonNull();
    }
    logger.atInfo().log("Checking device %s %s storage usage...", deviceId, externalOrInternal);

    try {
      // Gets storage information and updates dimensions.
      storageInfo = androidFileUtil.getStorageInfo(deviceId, /* isExternal= */ isExternal);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to get %s storage info (device_id=%s): %s",
          externalOrInternal, deviceId, MoreThrowables.shortDebugString(e, 0));
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
      long freeStorageMB = storageInfo.freeKB() >> 10;

      boolean isOutOfStorageSpace = freeStorageMB < freeStorageAlertMB;
      isDimensionChanged |=
          device.updateDimension(
              dimensionFreeStorage, StrUtil.getHumanReadableSize(freeStorageMB << 20));
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
          "Failed to scan device %s temp apks: %s",
          deviceId, MoreThrowables.shortDebugString(e, 0));
    }

    logger.atInfo().log("Cleaning device %s temp apks...", deviceId);
    try {
      androidFileUtil.removeFiles(deviceId, AndroidRealDeviceConstants.TEMP_APK_PATH);
      logger.atInfo().log("Device %s temp apks cleared", deviceId);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to clean device %s temp apks: %s",
          deviceId, MoreThrowables.shortDebugString(e, 0));
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
          AndroidRealDeviceConstants.SMLOG_PATH, deviceId, MoreThrowables.shortDebugString(e, 0));
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
            deviceId, MoreThrowables.shortDebugString(e, 0));
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
   * @return whethere there is any dimension changed
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
    if (!serviceAvailable) {
      AndroidRealDeviceDelegateHelper.setRebootToStateProperty(device, DeviceState.DEVICE);
      throw new com.google.devtools.mobileharness.api.model.error.MobileHarnessException(
          AndroidErrorId.ANDROID_REAL_DEVICE_ONLINE_DEVICE_NOT_READY,
          String.format(
              "Online mode device %s services aren't available yet. Reboot to recover.", deviceId));
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
            "Failed to get device %s WIFI RSSI: %s",
            deviceId, MoreThrowables.shortDebugString(e, 0));
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
          serial, MoreThrowables.shortDebugString(e, 0));
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
              userId, serial, MoreThrowables.shortDebugString(e, 0));
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
          serial, MoreThrowables.shortDebugString(e, 0));
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

  /** Checks whether the device support test harness recovering. */
  private boolean isTestHarnessRecoveryDevice() {
    List<String> dimensionList = device.getDimension(Dimension.Name.RECOVERY);
    return (dimensionList.size() == 1
        && Ascii.equalsIgnoreCase(
            dimensionList.get(0), AndroidRealDeviceConstants.RECOVERY_TYPE_TEST_HARNESS));
  }

  /**
   * Toggles the charging on/off if needed.
   *
   * @param stopChargeLevel battery level at which charging should be stopped
   * @param startChargeLevel battery level at which charging should be started
   */
  private void toggleChargingForSafeDischarge(int stopChargeLevel, int startChargeLevel)
      throws MobileHarnessException, InterruptedException {
    int batteryLevel = systemSettingUtil.getBatteryLevel(deviceId);
    boolean enableCharging;
    if (batteryLevel <= startChargeLevel) {
      enableCharging = true;
    } else if (batteryLevel >= stopChargeLevel) {
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
          deviceId, MoreThrowables.shortDebugString(e, 0));
      try {
        toggleChargingForSafeDischarge(stopChargeLevelInt, startChargeLevelInt);
      } catch (MobileHarnessException e2) {
        logger.atWarning().log(
            "Failed to enforce device %s safe discharge level: %s",
            deviceId, MoreThrowables.shortDebugString(e2, 0));
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

  /*
   * Enable the device charge before test for supported devices. Tries to avoid the device get power
   * off during long running test. b/148306058.
   *
   * <p>See {@link RuntimeChargingUtil.SupportedDeviceModel} for supported device models.
   *
   */
  private void enableDeviceChargeBeforeTest(TestInfo testInfo) throws InterruptedException {
    String testId = testInfo.getId();
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
          .toNewTestInfo()
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Turn on charging on device %s before test %s", deviceId, testId);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Error when enabling charge for device %s before test %s: %s",
          deviceId, testInfo.getId(), MoreThrowables.shortDebugString(e, 0));
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
}
