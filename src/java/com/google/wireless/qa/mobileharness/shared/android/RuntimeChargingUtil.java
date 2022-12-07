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
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import java.util.List;
import javax.annotation.Nullable;

/** Utility methods for controlling the Android runtime charging. */
public class RuntimeChargingUtil {

  /** Device model of an Android device. */
  public enum SupportedDeviceModel {
    NEXUS_5X("nexus 5x", 1),
    NEXUS_6P("nexus 6p", 1),
    PIXEL("pixel", 1, 0),
    PIXEL_XL("pixel xl", 1, 0),
    PIXEL_2("pixel 2", 5),
    PIXEL_2_XL("pixel 2 xl", 5),
    PIXEL_3("pixel 3", 5, 1),
    PIXEL_3_XL("pixel 3 xl", 5, 1),
    // Daydream/Tango devices.
    TANGO_ASUSSIVA("asus_a002", 1);
    private final String model;
    private final int chargerControlMethodsIndex;
    private final int setFullChargeLevelMethodIndex;

    SupportedDeviceModel(
        String model, int chargerControlMethodsIndex, int setFullChargeLevelMethodIndex) {
      this.model = model;
      this.chargerControlMethodsIndex = chargerControlMethodsIndex;
      this.setFullChargeLevelMethodIndex = setFullChargeLevelMethodIndex;
    }

    SupportedDeviceModel(String model, int chargerControlMethodsIndex) {
      this(model, chargerControlMethodsIndex, /* setFullChargeLevelMethodIndex= */ -1);
    }
  }

  @VisibleForTesting
  static final String[][] ADB_SHELL_BATTERY_CHARGER_CONTROL_METHODS = {
    new String[] {
      "echo -n 0xc0 > /d/bq24192/INPUT_SRC_CONT", "echo -n 0x4a > /d/bq24192/INPUT_SRC_CONT"
    },
    new String[] {
      "echo -n 0 > /sys/class/power_supply/battery/charging_enabled",
      "echo -n 1 > /sys/class/power_supply/battery/charging_enabled"
    },
    new String[] {
      "mount -t debugfs debugfs /sys/kernel/debug; \\"
          + "echo -n 0x08 > /d/pm8921_chg/CHG_CNTRL_3; \\"
          + "echo -n 0 > /sys/class/power_supply/battery/charger_control",
      "echo -n 0x88 > /d/pm8921_chg/CHG_CNTRL_3; \\"
          + "echo -n 1 > /sys/class/power_supply/battery/charger_control"
    },
    new String[] {
      "echo -n 0 > /sys/class/power_supply/battery/charger_control",
      "echo -n 1 > /sys/class/power_supply/battery/charger_control"
    },
    new String[] {
      "echo -n 0x40 > /d/bq24296/00_BQ00_INPUT_SRC_CONT",
      "echo -n 0x42 > /d/bq24296/00_BQ00_INPUT_SRC_CONT"
    },
    new String[] {
      "echo -n 1 > /sys/class/power_supply/battery/input_suspend",
      "echo -n 0 > /sys/class/power_supply/battery/input_suspend"
    },
  };

  static final String[] ADB_SHELL_BATERRY_FULL_LEVEL_METHODS = {
    "echo -n %d > /sys/class/power_supply/battery/full_level",
    "setprop persist.vendor.charge.stop.level %d"
  };

  private final Adb adb;

  public RuntimeChargingUtil() {
    this(new Adb());
  }

  @VisibleForTesting
  RuntimeChargingUtil(Adb adb) {
    this.adb = adb;
  }

  /**
   * Sets the system level discharge/charge status of the supported devices.
   *
   * <p>See {@link RuntimeChargingUtil.SupportedDeviceModel} for supported device models.
   *
   * @param device device
   * @param charge set to true for runtime charging the device
   */
  public void charge(Device device, boolean charge)
      throws MobileHarnessException, InterruptedException {
    // An exception will be thrown if the device is not supported.
    SupportedDeviceModel model = getRuntimeChargingModel(device);
    if (model == null) {
      throw new MobileHarnessException(
          ErrorCode.ANDROID_RUNTIME_CHARGING_ERROR,
          "Runtime charge is not supported for given device.");
    }
    String unused =
        adb.runShellWithRetry(
            device.getDeviceId(),
            ADB_SHELL_BATTERY_CHARGER_CONTROL_METHODS[model.chargerControlMethodsIndex][
                charge ? 1 : 0]);
  }

  /**
   * Sets full charge level for the device.
   *
   * @param device device
   * @param level full charge level, 0-100
   * @throws MobileHarnessException if device doesn't support settng full charge level
   */
  public void setFullChargeLevel(Device device, int level)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessException.checkArgument(level > 0 && level <= 100, "Incorrect battery level");

    // An exception will be thrown if the device is not supported.
    SupportedDeviceModel model = getFullChargingLevelModel(device);
    if (model == null) {
      throw new MobileHarnessException(
          ErrorCode.ANDROID_RUNTIME_CHARGING_ERROR,
          "Setting full charge level is not supported for given device.");
    }

    String unused =
        adb.runShellWithRetry(
            device.getDeviceId(),
            String.format(
                ADB_SHELL_BATERRY_FULL_LEVEL_METHODS[model.setFullChargeLevelMethodIndex], level));
  }

  private @Nullable SupportedDeviceModel getSupportedDeviceModel(Device device)
      throws MobileHarnessException {
    List<String> deviceModels = device.getDimension(Name.MODEL);
    if (deviceModels == null || deviceModels.isEmpty()) {
      throw new MobileHarnessException(
          ErrorCode.ANDROID_RUNTIME_CHARGING_ERROR,
          "Cannot get the model name of the give device.");
    }
    String model = deviceModels.get(0);
    for (SupportedDeviceModel supportedDeviceModel : SupportedDeviceModel.values()) {
      if (supportedDeviceModel.model.equals(model)) {
        return supportedDeviceModel;
      }
    }
    return null;
  }

  public @Nullable SupportedDeviceModel getFullChargingLevelModel(Device device)
      throws MobileHarnessException {
    SupportedDeviceModel model = getSupportedDeviceModel(device);
    if (model == null || model.setFullChargeLevelMethodIndex < 0) {
      return null;
    }
    return model;
  }

  public SupportedDeviceModel getRuntimeChargingModel(Device device) throws MobileHarnessException {
    return getSupportedDeviceModel(device);
  }
}
