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

package com.google.devtools.mobileharness.platform.android.app.mtaastools;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.IntentArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Instantiator for installing and using mtaas tools app.
 *
 * <p>This class provides a centralized way to install the mtaas tools app, ensuring that the app is
 * installed correctly and that the necessary permissions are granted. It also provides a convenient
 * way to check if a device is a member of the checkin group and to recover persistent USB ADB
 * configuration after a TestHarness factory reset.
 */
public class MtaasToolsInstantiator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  static final String MTAAS_TOOLS_APK_RES_PATH = "/com/google/android/apps/mtaas/tools/tools.apk";
  static final String MTAAS_TOOLS_PACKAGE_NAME = "com.google.android.apps.mtaas.tools";
  static final String MTAAS_GSERVICES_FLAG_NAME = "mtaas.device";

  @VisibleForTesting
  static final String ADB_BOUNCER_SERVICE_NAME =
      "com.google.android.apps.mtaas.tools.services.AdbBouncerService";

  @VisibleForTesting static final String PERSIST_SYS_USB_CONFIG_PROP = "persist.sys.usb.config";
  private static final ImmutableList<String> MTAAS_TOOLS_PERMISSIONS =
      ImmutableList.of("android.permission.DUMP", "android.permission.WRITE_SECURE_SETTINGS");

  private static final int MAX_ADB_RESET_ATTEMPTS = 3;
  private static final Duration ADB_OFFLINE_WAIT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration ADB_ONLINE_WAIT_TIMEOUT = Duration.ofSeconds(15);

  private static final Supplier<Optional<String>> MTAAS_TOOLS_APK_PATH =
      Suppliers.memoize(MtaasToolsInstantiator::getMtaasToolsApkPath);

  private final AndroidInstrumentationUtil androidInstrumentationUtil;
  private final ApkInstaller apkInstaller;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidProcessUtil androidProcessUtil;
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidSystemStateUtil androidSystemStateUtil;
  private final DeviceCache deviceCache;

  public MtaasToolsInstantiator() {
    this(
        new AndroidInstrumentationUtil(),
        new ApkInstaller(),
        new AndroidSystemSettingUtil(),
        new AndroidPackageManagerUtil(),
        new AndroidProcessUtil(),
        new AndroidAdbUtil(),
        new AndroidSystemStateUtil(),
        DeviceCache.getInstance());
  }

  @VisibleForTesting
  MtaasToolsInstantiator(
      AndroidInstrumentationUtil androidInstrumentationUtil,
      ApkInstaller apkInstaller,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidProcessUtil androidProcessUtil,
      AndroidAdbUtil androidAdbUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      DeviceCache deviceCache) {
    this.androidInstrumentationUtil = androidInstrumentationUtil;
    this.apkInstaller = apkInstaller;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidProcessUtil = androidProcessUtil;
    this.androidAdbUtil = androidAdbUtil;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.deviceCache = deviceCache;
  }

  /**
   * Installs the mtaas tools app on the device and grants required permissions.
   *
   * @param device the device to install the app on
   * @throws MobileHarnessException if failed to install the app
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public void install(Device device) throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    logger.atInfo().log("Installing mtaas tools on device %s", deviceId);
    String apkPath =
        MTAAS_TOOLS_APK_PATH
            .get()
            .orElseThrow(
                () ->
                    new MobileHarnessException(
                        AndroidErrorId.ANDROID_MTAAS_TOOLS_APK_NOT_FOUND,
                        "Failed to get mtaas tools apk path"));
    apkInstaller.installApkIfNotExist(
        device,
        ApkInstallArgs.builder().addApkPaths(apkPath).setGrantPermissions(true).build(),
        null);
    int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);
    UtilArgs utilArgs = UtilArgs.builder().setSerial(deviceId).setSdkVersion(sdkVersion).build();
    for (String permission : MTAAS_TOOLS_PERMISSIONS) {
      try {
        androidPackageManagerUtil.grantPermission(utilArgs, MTAAS_TOOLS_PACKAGE_NAME, permission);
      } catch (MobileHarnessException e) {
        logger.atInfo().log(
            "Failed to grant permission %s to %s on device %s: %s",
            permission, MTAAS_TOOLS_PACKAGE_NAME, deviceId, e.getMessage());
      }
    }
  }

  /**
   * Checks if {@code persist.sys.usb.config} contains {@code adb}, and if not, installs {@code
   * tools.apk} and starts {@code AdbBouncerService} to toggle {@code Settings.Global.ADB_ENABLED}
   * (0 -> 500ms -> 1) so Android's {@code AdbService} persists {@code persist.sys.usb.config=adb}
   * across subsequent reboots. See b/452760233 and b/579118590.
   */
  public void resetAdbIfNecessary(Device device) throws InterruptedException {
    String deviceId = device.getDeviceId();
    if (DeviceUtil.isOverTcpDevice(deviceId)) {
      return;
    }
    try {
      if (isUsbAdbConfigPersisted(deviceId)) {
        return;
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check %s on device %s", PERSIST_SYS_USB_CONFIG_PROP, deviceId);
      return;
    }

    logger.atInfo().log(
        "Property %s does not contain 'adb' on device %s; bouncing ADB via AdbBouncerService.",
        PERSIST_SYS_USB_CONFIG_PROP, deviceId);
    try {
      install(device);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Skipping ADB bounce on device %s because mtaas tools could not be installed.", deviceId);
      return;
    }

    Duration cacheTimeout = ADB_OFFLINE_WAIT_TIMEOUT.plus(ADB_ONLINE_WAIT_TIMEOUT).multipliedBy(2);
    for (int attempt = 1; attempt <= MAX_ADB_RESET_ATTEMPTS; attempt++) {
      logger.atInfo().log(
          "Bouncing ADB on device %s (attempt %d/%d)", deviceId, attempt, MAX_ADB_RESET_ATTEMPTS);
      deviceCache.cache(deviceId, device.getClass().getSimpleName(), cacheTimeout);
      try {
        UtilArgs utilArgs = UtilArgs.builder().setSerial(deviceId).build();
        IntentArgs intentArgs =
            IntentArgs.builder()
                .setComponent(MTAAS_TOOLS_PACKAGE_NAME + "/" + ADB_BOUNCER_SERVICE_NAME)
                .build();
        androidProcessUtil.startService(utilArgs, intentArgs);
        try {
          androidSystemStateUtil.waitForState(
              deviceId, DeviceConnectionState.DISCONNECT, ADB_OFFLINE_WAIT_TIMEOUT);
        } catch (MobileHarnessException e) {
          logger.atInfo().log(
              "Device %s did not observe DISCONNECT within %s during ADB bounce (attempt %d/%d);"
                  + " waiting for device to be online.",
              deviceId, ADB_OFFLINE_WAIT_TIMEOUT, attempt, MAX_ADB_RESET_ATTEMPTS);
        }
        androidSystemStateUtil.waitForState(
            deviceId, DeviceConnectionState.DEVICE, ADB_ONLINE_WAIT_TIMEOUT);
        androidSystemStateUtil.waitUntilReady(deviceId, ADB_ONLINE_WAIT_TIMEOUT);
        if (isUsbAdbConfigPersisted(deviceId)) {
          logger.atInfo().log(
              "Successfully persisted %s=adb on device %s on attempt %d.",
              PERSIST_SYS_USB_CONFIG_PROP, deviceId, attempt);
          return;
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to bounce ADB on device %s (attempt %d/%d)",
            deviceId, attempt, MAX_ADB_RESET_ATTEMPTS);
      } finally {
        deviceCache.invalidateCache(deviceId);
      }
    }
    logger.atWarning().log(
        "Property %s still does not contain 'adb' on device %s after %d attempts.",
        PERSIST_SYS_USB_CONFIG_PROP, deviceId, MAX_ADB_RESET_ATTEMPTS);
  }

  private boolean isUsbAdbConfigPersisted(String deviceId)
      throws MobileHarnessException, InterruptedException {
    String usbConfig =
        Strings.nullToEmpty(
                androidAdbUtil.getProperty(deviceId, ImmutableList.of(PERSIST_SYS_USB_CONFIG_PROP)))
            .trim();
    // If the property is not defined on the device (empty), skip bouncing ADB. When TestHarness
    // resets USB configuration on real devices, persist.sys.usb.config is set to "none" or "mtp".
    return usbConfig.isEmpty() || usbConfig.contains("adb");
  }

  /**
   * Checks if the device is a member of the checkin group.
   *
   * @param device the device to check
   * @return true if the device is a member of the checkin group, false otherwise
   * @throws MobileHarnessException if failed to check the checkin group
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public boolean isMemberOfCheckinGroup(Device device)
      throws MobileHarnessException, InterruptedException {
    int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(device.getDeviceId());
    String instrumentationOutput =
        androidInstrumentationUtil.instrument(
            device.getDeviceId(),
            sdkVersion,
            AndroidInstrumentationSetting.create(
                MTAAS_TOOLS_PACKAGE_NAME,
                ".instrumentations.GetGservicesFlag",
                /* className= */ null,
                ImmutableMap.of("flag", MTAAS_GSERVICES_FLAG_NAME, "defaultValue", "false"),
                /* async= */ false,
                /* showRawResults= */ false,
                /* prefixAndroidTest= */ false,
                /* noIsolatedStorage= */ false,
                /* useTestStorageService= */ false,
                /* enableCoverage= */ false),
            /* timeout= */ Duration.ofMinutes(1));
    return instrumentationOutput.contains(MTAAS_GSERVICES_FLAG_NAME + "=true");
  }

  private static Optional<String> getMtaasToolsApkPath() {
    ResUtil resUtil = new ResUtil();
    return resUtil
        .getExternalResourceFile(MTAAS_TOOLS_APK_RES_PATH)
        .or(
            () -> {
              try {
                return Optional.of(
                    resUtil.getResourceFile(
                        MtaasToolsInstantiator.class, MTAAS_TOOLS_APK_RES_PATH));
              } catch (MobileHarnessException e) {
                logger.atWarning().withCause(e).log("Failed to get mtaas tools apk path");
                return Optional.empty();
              }
            });
  }
}
