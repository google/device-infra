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

package com.google.devtools.mobileharness.platform.android.app.devicedaemon.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.DeviceDaemonApkInfo;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import javax.annotation.Nullable;

/** Implementation of {@link DeviceDaemonApkInfo} for Mobile Harness Device Daemon app v1. */
public final class DeviceDaemonApkInfoImplV1 implements DeviceDaemonApkInfo {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int DEVICE_DAEMON_VERSION_CODE = 21;

  /** Activity name of the Android device daemon app v1. */
  private static final String DEVICE_DAEMON_ACTIVITY_NAME =
      "com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v1"
          + ".DaemonActivity";

  /** Extra permission names of the Android device daemon app v1. */
  private static final ImmutableList<String> DEVICE_DAEMON_EXTRA_PERMISSION_NAMES =
      ImmutableList.of("android.permission.CHANGE_CONFIGURATION");

  /** Local path of the daemon v1 apk. */
  @Nullable private final String daemonApkPath;

  /** Package name of the Android device daemon app v1. */
  private static final String DEVICE_DAEMON_PACKAGE_NAME =
      "com.google.devtools.mobileharness.platform.android.app.binary.devicedaemon.v1";

  /** Lazy initializer for deploying the daemon v1 apk. */
  private static class LazyInitializer {

    /** Resource path of the daemon v1 apk. */
    private static final String DEVICE_DAEMON_APK_RES_PATH =
        "/com/google/devtools/mobileharness/platform/android/app/binary/devicedaemon/prebuilt/v1/daemon.apk";

    @Nullable private static final String DEVICE_DAEMON_APK_PATH;

    static {
      String path = null;
      ResUtil resUtil = new ResUtil();
      try {
        path = resUtil.getResourceFile(DeviceDaemonApkInfoImplV1.class, DEVICE_DAEMON_APK_RES_PATH);
      } catch (MobileHarnessException e) {
        logger.atSevere().log("Cannot locate daemon v1 apk file for resource: %s", e.getMessage());
      }
      DEVICE_DAEMON_APK_PATH = path;
      logger.atInfo().log("daemon v1 apk path: %s", path);
    }
  }

  public DeviceDaemonApkInfoImplV1() {
    daemonApkPath = LazyInitializer.DEVICE_DAEMON_APK_PATH;
  }

  @VisibleForTesting
  DeviceDaemonApkInfoImplV1(String daemonApkPath) {
    this.daemonApkPath = daemonApkPath;
  }

  @Override
  public String getActivityName() {
    return DEVICE_DAEMON_ACTIVITY_NAME;
  }

  @Override
  public String getApkPath() throws MobileHarnessException {
    if (daemonApkPath == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_DAEMON_V1_APK_NOT_FOUND, "daemon v1 apk file not found");
    }
    return daemonApkPath;
  }

  @Override
  public int getApkVersionCode() {
    return DEVICE_DAEMON_VERSION_CODE;
  }

  @Override
  public ImmutableList<String> getExtraPermissionNames() {
    return DEVICE_DAEMON_EXTRA_PERMISSION_NAMES;
  }

  @Override
  public String getPackageName() {
    return DEVICE_DAEMON_PACKAGE_NAME;
  }
}
