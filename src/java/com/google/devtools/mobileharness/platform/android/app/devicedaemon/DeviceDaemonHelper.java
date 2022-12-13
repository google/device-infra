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

package com.google.devtools.mobileharness.platform.android.app.devicedaemon;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.connectivity.AndroidConnectivityUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nullable;

/** Helper class for install/start/uninstall Mobile Harness Device Daemon app. */
public class DeviceDaemonHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Daemon activity extra for showing labels on daemon. */
  @VisibleForTesting static final String DEVICE_DAEMON_LABEL_EXTRA = "labels";

  /** Daemon activity extra for showing owners on daemon. */
  @VisibleForTesting static final String OWNERS_EXTRA = "owners";

  /** Daemon activity extra for showing id on daemon. */
  @VisibleForTesting static final String ID_EXTRA = "id";

  /** Daemon activity extra for showing hostname on daemon. */
  @VisibleForTesting static final String DEVICE_DAEMON_HOSTNAME_EXTRA = "hostname";

  /** Daemon activity extra for showing wifi SSID on daemon. */
  @VisibleForTesting static final String DEVICE_DAEMON_SSID_EXTRA = "ssid";

  @VisibleForTesting
  static final String GRANT_PERMISSION_CHANGE_CONFIGURATION_WARNING =
      "WARNING: linker: app_process has text relocations";

  private final ApkInstaller apkInstaller;

  /** Utility class for Android system setting. */
  private final AndroidSystemSettingUtil systemSettingUtil;

  /** Utility class for Android device wifi. */
  private final AndroidConnectivityUtil connectivityUtil;

  /** Provider of utility class for installing MH daemon app on Android devices. */
  private final DeviceDaemonApkInfoProvider deviceDaemonApkInfoProvider;

  private final AndroidPackageManagerUtil androidPackageManagerUtil;

  private final AndroidProcessUtil androidProcessUtil;

  public DeviceDaemonHelper() {
    this(
        new ApkInstaller(),
        new AndroidSystemSettingUtil(),
        new AndroidConnectivityUtil(),
        new DeviceDaemonApkInfoProvider(),
        new AndroidPackageManagerUtil(),
        new AndroidProcessUtil());
  }

  @VisibleForTesting
  DeviceDaemonHelper(
      ApkInstaller apkInstaller,
      AndroidSystemSettingUtil systemSettingUtil,
      AndroidConnectivityUtil connectivityUtil,
      DeviceDaemonApkInfoProvider deviceDaemonApkInfoProvider,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidProcessUtil androidProcessUtil) {
    this.apkInstaller = apkInstaller;
    this.systemSettingUtil = systemSettingUtil;
    this.connectivityUtil = connectivityUtil;
    this.deviceDaemonApkInfoProvider = deviceDaemonApkInfoProvider;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidProcessUtil = androidProcessUtil;
  }

  /**
   * Installs the daemon if it is not installed, or the installed version is lower than required.
   * Makes sure the device daemon is running.
   *
   * <p>Note: It'll do nothing if flag 'android_device_daemon' is set to false.
   *
   * @param device the device that the APK is installed to
   * @param log the log collector
   */
  public void installAndStartDaemon(Device device, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    installAndStartDaemon(
        device, log, /* labels= */ null, /* hostname= */ null, /* owners= */ null);
  }

  /**
   * Installs the daemon if it is not installed, or the installed version is lower than required.
   * Makes sure the device daemon is running and update the labels shown on the daemon.
   *
   * <p>Note: It'll do nothing if flag 'android_device_daemon' is set to false.
   *
   * @param device the device that the APK is installed to
   * @param log the log collector
   * @param labels device dimension labels passed to device daemon app
   * @param hostname device's hostname passed to device daemon app
   * @param owners device owners passed to device daemon app
   */
  public void installAndStartDaemon(
      Device device,
      @Nullable LogCollector<?> log,
      @Nullable String labels,
      @Nullable String hostname,
      @Nullable String owners)
      throws MobileHarnessException, InterruptedException {
    if (!DeviceDaemonApkInfoProvider.isDeviceDaemonEnabled()) {
      logMessage(
          log,
          "Device daemon app is not allowed to install and start because flag"
              + " 'android_device_daemon' is set to false.");
      return;
    }
    String deviceId = device.getDeviceId();
    int sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
    DeviceDaemonApkInfo deviceDaemonApkInfo =
        deviceDaemonApkInfoProvider.getDeviceDaemonApkInfoInstance(sdkVersion);
    String apkPath = deviceDaemonApkInfo.getApkPath();
    try {
      apkInstaller.installApkIfNotExist(
          device,
          ApkInstallArgs.builder().setApkPath(apkPath).setGrantPermissions(true).build(),
          log);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_DAEMON_HELPER_INSTALL_DAEMON_ERROR, e.getMessage(), e);
    }
    logMessage(
        log,
        "Starting device daemon %s on device %s...",
        deviceDaemonApkInfo.getActivityName(),
        deviceId);
    for (String permission : deviceDaemonApkInfo.getExtraPermissionNames()) {
      if ("android.permission.CHANGE_CONFIGURATION".equals(permission) && sdkVersion < 17) {
        continue;
      }
      grantPermission(deviceId, deviceDaemonApkInfo.getPackageName(), permission);
    }

    try {
      androidProcessUtil.startApplication(
          deviceId,
          deviceDaemonApkInfo.getPackageName(),
          deviceDaemonApkInfo.getActivityName(),
          prepareDeviceDaemonExtras(deviceId, sdkVersion, log, labels, hostname, owners),
          /* clearTop= */ true);
    } catch (MobileHarnessException e) {
      if (e.getMessage().contains(AndroidProcessUtil.OUTPUT_START_APP_FAILED)) {
        logMessage(
            log,
            "Failed to start device daemon app on device %s, uninstall daemon apk to recover.",
            deviceId);
        uninstallDaemonApk(device, log);
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_DAEMON_HELPER_START_DAEMON_ERROR,
          String.format("Failed to start device daemon app on device %s", deviceId),
          e);
    }
  }

  private ImmutableMap<String, String> prepareDeviceDaemonExtras(
      String deviceId,
      int sdkVersion,
      @Nullable LogCollector<?> log,
      @Nullable String labels,
      @Nullable String hostname,
      @Nullable String owners)
      throws MobileHarnessException, InterruptedException {
    Map<String, String> extras = new HashMap<>();
    labels = Strings.nullToEmpty(labels);
    hostname = Strings.nullToEmpty(hostname);
    owners = Strings.nullToEmpty(owners);
    String ssid = "";
    if (sdkVersion >= 28 && systemSettingUtil.isLocationServiceDisabled(deviceId)) {
      try {
        ssid = connectivityUtil.getNetworkSsid(deviceId, sdkVersion);
      } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_DEVICE_DAEMON_HELPER_GET_NETWORK_SSID_ERROR, e.getMessage());
      }
    }
    logMessage(
        log,
        "Prepare extras for device daemon with device id [%s], labels [%s], hostname [%s], "
            + "owners [%s], ssid [%s]",
        deviceId,
        labels,
        hostname,
        owners,
        ssid);
    if (!StrUtil.isEmptyOrWhitespace(labels)) {
      extras.put(DEVICE_DAEMON_LABEL_EXTRA, labels);
    }
    if (!StrUtil.isEmptyOrWhitespace(hostname)) {
      extras.put(DEVICE_DAEMON_HOSTNAME_EXTRA, hostname);
    }
    if (!StrUtil.isEmptyOrWhitespace(owners)) {
      extras.put(OWNERS_EXTRA, owners);
    }
    if (!StrUtil.isEmptyOrWhitespace(ssid)) {
      extras.put(DEVICE_DAEMON_SSID_EXTRA, ssid);
    }
    extras.put(ID_EXTRA, deviceId);
    return ImmutableMap.copyOf(extras);
  }

  public void uninstallDaemonApk(Device device, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    DeviceDaemonApkInfo deviceDaemonApkInfo =
        deviceDaemonApkInfoProvider.getDeviceDaemonApkInfoInstance(
            systemSettingUtil.getDeviceSdkVersion(deviceId));
    try {
      // Un-installation is the only way to stop the daemon service.
      if (androidPackageManagerUtil
          .listPackages(deviceId, PackageType.ALL)
          .contains(deviceDaemonApkInfo.getPackageName())) {
        apkInstaller.uninstallApk(
            device, deviceDaemonApkInfo.getPackageName(), /* logFailures= */ true, log);
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_DAEMON_HELPER_UNINSTALL_DAEMON_ERROR, e.getMessage());
    }
  }

  private void grantPermission(String deviceId, String packageName, String permission)
      throws MobileHarnessException, InterruptedException {
    try {
      androidPackageManagerUtil.grantPermission(deviceId, packageName, permission);
    } catch (MobileHarnessException e) {
      if (e.getMessage().contains(GRANT_PERMISSION_CHANGE_CONFIGURATION_WARNING)) {
        logger.atWarning().withCause(e).log("%s", e.getMessage());
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_DEVICE_DAEMON_HELPER_GRANT_DAEMON_PERMISSION_ERROR,
            e.getMessage(),
            e);
      }
    }
  }

  /**
   * Logging helper function which always logs with {@link FluentLogger} and mhLogger if provided.
   */
  @FormatMethod
  private void logMessage(
      @Nullable LogCollector<?> log, @FormatString String format, Object... args) {
    logMessage(Level.INFO, log, format, args);
  }

  @FormatMethod
  private void logMessage(
      Level level, @Nullable LogCollector<?> log, @FormatString String format, Object... args) {
    if (log != null) {
      log.at(level).log(format, args);
    }
    logger.at(level).logVarargs(format, args);
  }
}
