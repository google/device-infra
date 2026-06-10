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

package com.google.devtools.mobileharness.platform.android.lightning.networkconnector;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.connectivity.AndroidConnectivityUtil;
import com.google.devtools.mobileharness.platform.android.connectivity.ConnectToWifiArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.android.WifiUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Duration;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * Network connector for managing Android device network.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class NetworkConnector {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final WifiUtil wifiUtil;

  private final AndroidConnectivityUtil connectivityUtil;

  private final ApkInstaller apkInstaller;

  private final AndroidSystemSettingUtil systemSettingUtil;

  public NetworkConnector() {
    this(
        new WifiUtil(),
        new AndroidConnectivityUtil(),
        new ApkInstaller(),
        new AndroidSystemSettingUtil());
  }

  @VisibleForTesting
  NetworkConnector(
      WifiUtil wifiUtil,
      AndroidConnectivityUtil connectivityUtil,
      ApkInstaller apkInstaller,
      AndroidSystemSettingUtil systemSettingUtil) {
    this.wifiUtil = wifiUtil;
    this.connectivityUtil = connectivityUtil;
    this.apkInstaller = apkInstaller;
    this.systemSettingUtil = systemSettingUtil;
  }

  /**
   * Connects device to a WiFi network.
   *
   * @param device the device that connects to given wifi
   * @param connectArgs argument wrapper for connecting wifi
   * @param log log of the currently running test, usually from {@code TestInfo}
   * @return {@code true} if device connects to given wifi successfully, or {@code false} if failed
   *     connecting device to given wifi, or it's not a MH managed device.
   * @throws MobileHarnessException if logFailuresOnly is {@code false} and it failed connecting to
   *     wifi, or failed to get wifiutil apk path, or falied to install wifiutil on device.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  @CanIgnoreReturnValue
  public boolean connectToWifi(
      Device device, WifiConnectArgs connectArgs, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    if (Flags.disableWifiUtilFunc.getNonNull()) {
      SharedLogUtil.logMsg(
          logger,
          Level.INFO,
          log,
          /* cause= */ null,
          "Ignoring attempt to connect device %s to WiFi while WifiUtil functionality is"
              + " disabled.",
          deviceId);
      return false;
    }
    if (DeviceUtil.inSharedLab()) {
      SharedLogUtil.logMsg(
          logger,
          Level.SEVERE,
          log,
          /* cause= */ null,
          "Ignoring attempt to connect device %s to WiFi while not managing devices.",
          deviceId);
      return false;
    }

    String wifiSsid = connectArgs.wifiSsid();
    String wifiPsk = connectArgs.wifiPsk().orElse(null);
    boolean scanSsid = connectArgs.scanSsid().orElse(false);
    boolean logFailuresOnly = connectArgs.logFailuresOnly().orElse(false);
    Duration waitTimeout = connectArgs.waitTimeout().orElse(Duration.ofMinutes(2));
    int retryNum = connectArgs.retryNum().orElse(0);

    int sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
    String currentSsid = null;
    try {
      currentSsid = connectivityUtil.getNetworkSsid(deviceId, sdkVersion);
    } catch (MobileHarnessException e) {
      SharedLogUtil.logMsg(
          logger,
          Level.WARNING,
          log,
          e,
          "Failed to get current SSID for device %s, try to connect it to WiFi(SSID=%s) anyway",
          deviceId,
          wifiSsid);
    }
    if (wifiSsid.equals(currentSsid)) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Device %s has already connected to WiFi(SSID=%s), skipped",
          deviceId,
          wifiSsid);
      return true;
    }
    SharedLogUtil.logMsg(logger, log, "Install WifiUtil for %s", deviceId);
    String wifiUtilApkPath;
    try {
      wifiUtilApkPath = wifiUtil.getWifiUtilApkPath();
      apkInstaller.installApkIfNotExist(
          device,
          ApkInstallArgs.builder().setApkPath(wifiUtilApkPath).setGrantPermissions(true).build(),
          log);
    } catch (MobileHarnessException e) {
      String msg =
          String.format("Failed installing WifiUtil on device %s:%n%s", deviceId, e.getMessage());
      if (logFailuresOnly) {
        SharedLogUtil.logMsg(logger, Level.WARNING, log, e, "%s", msg);
        return false;
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.NETWORK_CONNECTOR_INSTALL_WIFIUTIL_ERROR, msg, e);
      }
    }

    SharedLogUtil.logMsg(logger, log, "Connecting to WiFi(SSID=%s) for %s", wifiSsid, deviceId);
    boolean connectedToWifi = false;
    Exception connectWifiException = null;
    String msg = String.format("Failed connecting to WiFi(SSID=%s) for %s", wifiSsid, deviceId);
    if (retryNum != 0) {
      msg =
          String.format(
              "Failed connecting to WiFi(SSID=%s) for %s after %d retries",
              wifiSsid, deviceId, retryNum);
    }
    int retryCnt = retryNum;
    ConnectToWifiArgs.Builder argsBuilder =
        ConnectToWifiArgs.builder()
            .setSerial(deviceId)
            .setSdkVersion(sdkVersion)
            .setWifiSsid(wifiSsid)
            .setScanSsid(scanSsid)
            .setWaitTimeout(waitTimeout);
    if (!Strings.isNullOrEmpty(wifiPsk)) {
      argsBuilder.setWifiPsk(wifiPsk);
    }

    do {
      if (retryCnt > 0 && retryNum == 0) {
        // Forces connecting to wifi at the last retry when the given retry number > 0.
        argsBuilder.setForceTryConnect(true);
      }
      try {
        connectedToWifi = connectivityUtil.connectToWifi(argsBuilder.build(), log);
      } catch (MobileHarnessException e) {
        connectWifiException = e;
      }
      if (connectedToWifi) {
        return true;
      }
      if (retryNum > 0) {
        try {
          connectivityUtil.reEnableWifi(device.getDeviceId(), log);
        } catch (MobileHarnessException e) {
          connectWifiException = e;
        }
      }
    } while (--retryNum >= 0);

    SharedLogUtil.logMsg(logger, Level.WARNING, log, connectWifiException, "%s", msg);

    if (!logFailuresOnly) {
      if (connectWifiException != null) {
        throw new MobileHarnessException(
            AndroidErrorId.NETWORK_CONNECTOR_CONNECT_TO_WIFI_ERROR, msg, connectWifiException);
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.NETWORK_CONNECTOR_CONNECT_TO_WIFI_FAILURE, msg);
      }
    }
    return false;
  }
}
