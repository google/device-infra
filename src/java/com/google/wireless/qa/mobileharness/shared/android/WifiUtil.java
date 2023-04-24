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

package com.google.wireless.qa.mobileharness.shared.android;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import javax.annotation.Nullable;

/** Util methods for using WIFI APIs to interact with Android devices. */
public class WifiUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Local path of the WifiUtil.apk. */
  @Nullable private final String wifiApkPath;

  /* Apk package name of WifiUtil.apk. */
  private final String apkPackageName;

  /** Apk version code. */
  private final int apkVersionCode;

  /** Lazy initializer for deploying the WifiUtil.apk. */
  private static class LazyInitializer {

    /** Resource path of the WifiUtil.apk. */
    private static final String WIFI_UTIL_APK_RES_PATH =
        "/com/google/devtools/mobileharness/platform/android/app/binary/wifiutil/WifiUtil.apk";

    private static final String WIFI_UTIL_APK_PACKAGE_NAME;

    @Nullable private static final String WIFI_UTIL_APK_PATH;

    private static final int WIFI_UTIL_APK_VERSION_CODE;

    static {
      String name = null;
      String path = null;
      int versionCode = 1;
      ResUtil resUtil = new ResUtil();
      Aapt aapt = new Aapt();
      try {
        path = resUtil.getResourceFile(WifiUtil.class, WIFI_UTIL_APK_RES_PATH);
        if (path != null) {
          // The apk must have the package name while it may not have the version code, so get the
          // package name first
          name = aapt.getApkPackageName(path);
          versionCode = aapt.getApkVersionCode(path);
        }
      } catch (MobileHarnessException e) {
        logger.atSevere().log(
            "Cannot locate WifiUtil.apk file or version for resource: %s", e.getMessage());
      } catch (InterruptedException e) {
        logger.atSevere().log(
            "Interrupted to get WifiUtil.apk version from resource: %s", e.getMessage());
        Thread.currentThread().interrupt();
      }
      WIFI_UTIL_APK_PACKAGE_NAME = name;
      WIFI_UTIL_APK_PATH = path;
      WIFI_UTIL_APK_VERSION_CODE = versionCode;
      logger.atInfo().log("WifiUtil.apk (name %s, version %d) path: %s", name, versionCode, path);
    }
  }

  public WifiUtil() {
    wifiApkPath = LazyInitializer.WIFI_UTIL_APK_PATH;
    apkPackageName = LazyInitializer.WIFI_UTIL_APK_PACKAGE_NAME;
    apkVersionCode = LazyInitializer.WIFI_UTIL_APK_VERSION_CODE;
  }

  @VisibleForTesting
  WifiUtil(String wifiApkPath, String apkPackageName, int apkVersionCode) {
    this.wifiApkPath = wifiApkPath;
    this.apkPackageName = apkPackageName;
    this.apkVersionCode = apkVersionCode;
  }

  /** Get WifiUtil.apk path. */
  public String getWifiUtilApkPath() throws MobileHarnessException {
    return MobileHarnessException.checkNotNull(
        wifiApkPath, ErrorCode.FILE_NOT_FOUND, "WifiUtil.apk file not found");
  }

  /** Get WifiUtil.apk package name. */
  public String getWifiUtilApkPackageName() throws MobileHarnessException {
    MobileHarnessException.checkNotNull(
        wifiApkPath, ErrorCode.FILE_NOT_FOUND, "WifiUtil.apk file not found");

    return apkPackageName;
  }

  /** Get WifiUtil.apk version code. */
  public int getWifiUtilApkVersionCode() throws MobileHarnessException {
    MobileHarnessException.checkNotNull(
        wifiApkPath, ErrorCode.FILE_NOT_FOUND, "WifiUtil.apk file not found");

    return apkVersionCode;
  }
}
