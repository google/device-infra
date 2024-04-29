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
import com.google.common.base.Splitter;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceState;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.validator.ValidatorFactory;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.ADB;
import com.google.wireless.qa.mobileharness.shared.proto.Communication;
import com.google.wireless.qa.mobileharness.shared.proto.CommunicationList;
import com.google.wireless.qa.mobileharness.shared.proto.TCP;
import java.util.List;
import javax.annotation.Nullable;

/** Android emulator which is created and started manually. */
public class AndroidLocalEmulator extends AndroidDevice {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidSystemStateUtil androidSystemStateUtil;
  private final AndroidAdbInternalUtil androidAdbInternalUtil;

  /**
   * Creates an emulator with the given ID.
   *
   * @param deviceId serial number of the Android emulator
   */
  public AndroidLocalEmulator(String deviceId) {
    this(
        deviceId,
        ApiConfig.getInstance(),
        new ValidatorFactory(),
        new AndroidSystemSettingUtil(),
        new AndroidSystemStateUtil(),
        new AndroidAdbInternalUtil());
  }

  @VisibleForTesting
  AndroidLocalEmulator(
      String deviceId,
      @Nullable ApiConfig apiConfig,
      @Nullable ValidatorFactory validatorFactory,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidSystemStateUtil androidSystemStateUtil,
      AndroidAdbInternalUtil androidAdbInternalUtil) {
    super(deviceId, apiConfig, validatorFactory);
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidSystemStateUtil = androidSystemStateUtil;
    this.androidAdbInternalUtil = androidAdbInternalUtil;
  }

  @Override
  public void setUp() throws MobileHarnessException, InterruptedException {
    super.setUp();
    addSupportedDeviceType(AndroidLocalEmulator.class.getSimpleName());
    addDimension(DIMENSION_NAME_INTERNET, String.valueOf(true));
    // enable ACID on AVD
    addSupportedDriver("AcidRemoteDriver");
    updateDimension(
        Ascii.toLowerCase(Dimension.Name.SUPPORTS_GMSCORE.name()), Dimension.Value.TRUE);
    setCommunication(getDeviceId());
    logger.atInfo().log("Ready");
  }

  private void setCommunication(String deviceId) {
    ADB.Builder adb = ADB.newBuilder().setSerial(deviceId);
    if (deviceId.startsWith("localhost:")) {
      List<String> words = Splitter.onPattern(":").splitToList(deviceId);
      adb.setTcp(TCP.newBuilder().setIp(words.get(0)).setPort(Integer.parseInt(words.get(1))));
    }
    setCommunicationDimensionAndProperty(
        CommunicationList.newBuilder()
            .addCommunication(Communication.newBuilder().setAdb(adb))
            .build());
  }

  @Override
  public void preRunTest(TestInfo testInfo)
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    // Dismiss lockscreen
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("AndroidLocalEmulator preRunTest - Dismissing lockscreen...");
    int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(getDeviceId());
    androidSystemSettingUtil.dismissLockScreen(getDeviceId(), sdkVersion);

    // Gets root permission for Ansible cloud emulator
    androidSystemStateUtil.becomeRoot(getDeviceId());

    super.preRunTest(testInfo);
  }

  @Override
  public boolean canReboot() {
    return true;
  }

  @Override
  public void reboot()
      throws com.google.devtools.mobileharness.api.model.error.MobileHarnessException,
          InterruptedException {
    if (canReboot()) {
      String deviceId = getDeviceId();
      // Only invokes the reboot command when the device is detected, otherwise will fail.
      if (androidAdbInternalUtil.getDeviceSerialsByState(DeviceState.DEVICE).contains(deviceId)) {
        androidSystemStateUtil.softReboot(deviceId);
        logger.atInfo().log(
            "Soft rebooting. Only soft reboot supported on Forge emulator currently.");
      }
    } else {
      logger.atSevere().log(
          "Unexpected attempt to reboot a non-rebootable emulator: %s", getDeviceId());
    }
  }

  @Override
  public boolean isRooted() {
    //  We suppose all emulators are rooted.
    return true;
  }
}
