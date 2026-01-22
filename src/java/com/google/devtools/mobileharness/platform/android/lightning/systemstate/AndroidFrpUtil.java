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

package com.google.devtools.mobileharness.platform.android.lightning.systemstate;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.time.Duration;
import javax.annotation.Nullable;

/** Deals with the Factory Reset Protection (FRP) status on the device. */
public class AndroidFrpUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SystemStateManager systemStateManager;

  private final AndroidSystemStateUtil androidSystemStateUtil;

  private final AndroidAdbUtil androidAdbUtil;

  private final Adb adb;

  @VisibleForTesting
  static final String CLEAR_FRP_SHELL_COMMAND_TEMPLATE = "dd if=/dev/zero of=%s count=1024";

  public AndroidFrpUtil() {
    this(new SystemStateManager(), new AndroidSystemStateUtil(), new Adb(), new AndroidAdbUtil());
  }

  @VisibleForTesting
  AndroidFrpUtil(
      SystemStateManager systemStateManager,
      AndroidSystemStateUtil androidSystemStateUtil,
      Adb adb,
      AndroidAdbUtil androidAdbUtil) {
    this.systemStateManager = systemStateManager;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.adb = adb;
    this.androidAdbUtil = androidAdbUtil;
  }

  public void clearFactoryResetProtection(Device device, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    String frpPersistentDataBlock =
        androidAdbUtil.getProperty(deviceId, ImmutableList.of("ro.frp.pst")).trim();

    if (systemStateManager.becomeRoot(device)
        && !StrUtil.isEmptyOrWhitespace(frpPersistentDataBlock)) {
      SharedLogUtil.logMsg(
          logger, log, "Clearing Factory Reset Protection (FRP) data on device %s", deviceId);
      String shellCmd = String.format(CLEAR_FRP_SHELL_COMMAND_TEMPLATE, frpPersistentDataBlock);
      adb.runShell(deviceId, shellCmd, /* timeout= */ null, /* lineCallback= */ null);
      SharedLogUtil.logMsg(logger, log, "FRP data is cleared on device %s", deviceId);
    }
  }

  /**
   * Factory reset Android Q+ devices via Test Harness Mode, which will also retain adb key before
   * factory reset and apply some pre-defined settings to device. Will clear frp and retry factory
   * reset if the 1st attempt fails due to frp is active.
   *
   * <p>Settings in Test Harness Mode include skipping setup wizard, staying device awake when
   * charging, etc. This mode works on user/userdebug builds on Q+.
   *
   * @param device the device to be factory reset.
   * @param waitTime wait time (seconds) for device to be disconnected after calling factory reset
   *     via test harness command, or null to use default wait time.
   */
  public void factoryResetViaTestHarnessWithFrpClear(
      Device device, @Nullable Duration waitTime, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    try {
      androidSystemStateUtil.factoryResetViaTestHarness(deviceId, waitTime);
    } catch (MobileHarnessException e) {
      if (e.getErrorId()
          == AndroidErrorId.ANDROID_SYSTEM_STATE_FACTORY_RESET_VIA_TEST_HARNESS_FRP_ERROR) {
        clearFactoryResetProtection(device, log);
        SharedLogUtil.logMsg(
            logger, log, "Retry factory reset via test harness for device %s", deviceId);
        androidSystemStateUtil.factoryResetViaTestHarness(deviceId, waitTime);
      } else {
        throw e;
      }
    }
  }
}
