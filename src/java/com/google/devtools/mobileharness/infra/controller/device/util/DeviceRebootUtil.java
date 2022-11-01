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

package com.google.devtools.mobileharness.infra.controller.device.util;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import com.google.devtools.mobileharness.infra.controller.test.model.TestExecutionResult;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.controller.stat.DeviceStat;

/** Utilities for deciding whether a device needs to be rebooted. */
public class DeviceRebootUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Checks whether the device needs to be rebooted after a test. */
  public boolean needRebootUponTestResults(
      Device device,
      DeviceStat deviceStat,
      ApiConfig apiConfig,
      TestExecutionResult testExecutionResult)
      throws InterruptedException {
    if (device.canReboot()) {
      if (PostTestDeviceOp.REBOOT.equals(testExecutionResult.postTestDeviceOp())) {
        logger.atInfo().log(
            "Will reboot the device %s according to the PostRunTest result", device.getDeviceId());
        return true;
      } else if (TestResult.TIMEOUT.equals(testExecutionResult.testResult())) {
        logger.atInfo().log("Will reboot device %s because test TIMEOUT", device.getDeviceId());
        return true;
      } else if (TestResult.UNKNOWN.equals(testExecutionResult.testResult())) {
        // If a device runner is held by ProxyTestRunner but the test is closed before kicked off.
        logger.atInfo().log(
            "Do not reboot device %s for an unknown test result and an non-reboot post "
                + "operation suggestion",
            device.getDeviceId());
      } else {
        // Checks test history to see whether the device needs to reboot.
        return needRebootUponTestHistory(device, deviceStat, apiConfig, testExecutionResult);
      }
    } else {
      if (PostTestDeviceOp.REBOOT.equals(testExecutionResult.postTestDeviceOp())) {
        logger.atInfo().log(
            "Ignored the reboot request from the PostRunTest ops "
                + "because the device %s doesn't support reboot",
            device.getDeviceId());
      } else {
        logger.atInfo().log(
            "Skip checking consecutive test results because this device %s "
                + "doesn't support reboot",
            device.getDeviceId());
      }
    }
    return false;
  }

  /**
   * Checks whether needs to reboot the device according to historical test results since last
   * reboot. Here we assume the device is reboot-able and the device stat module is enabled.
   */
  private boolean needRebootUponTestHistory(
      Device device,
      DeviceStat deviceStat,
      ApiConfig apiConfig,
      TestExecutionResult testExecutionResult) {
    int maxConsecutiveFail = apiConfig.getMaxConsecutiveFail(device.getDeviceControlId());
    if (maxConsecutiveFail <= 0) {
      // Will reboot the device no matter the test is passed or failed.
      logger.atInfo().log(
          "Will reboot device %s because max_consecutive_fail = %s",
          device.getDeviceId(), maxConsecutiveFail);
      return true;
    }

    int maxConsecutiveTest = apiConfig.getMaxConsecutiveTest(device.getDeviceControlId());
    if (maxConsecutiveTest <= 1) {
      logger.atInfo().log(
          "Will reboot device %s because max_consecutive_test = %s",
          device.getDeviceId(), maxConsecutiveTest);
      return true;
    }

    int consecutiveTest = deviceStat.getTestNumSinceLastReboot();
    if (consecutiveTest >= maxConsecutiveTest) {
      logger.atInfo().log(
          "Will reboot device %s:\n - Consecutive tests on the device: %s"
              + "\n - max_consecutive_test setting: %s",
          device.getDeviceId(), consecutiveTest, maxConsecutiveFail);
      return true;
    }

    boolean needReboot = false;
    switch (testExecutionResult.testResult()) {
      case PASS:
        break;
      case ERROR:
      case FAIL:
        int consecutiveFail = deviceStat.getConsecutiveFinishedFail();
        if (consecutiveFail % maxConsecutiveFail == 0) {
          logger.atInfo().log(
              "Will reboot device %s:\n - Consecutive fail tests on the device: %s"
                  + "\n - max_consecutive_fail setting: %s",
              device.getDeviceId(), consecutiveFail, maxConsecutiveFail);
          needReboot = true;
        } else {
          logger.atInfo().log(
              "Skip rebooting device %s:\n - Consecutive fail tests on the device: %s"
                  + "\n - max_consecutive_fail setting: %s",
              device.getDeviceId(), consecutiveFail, maxConsecutiveFail);
        }
        break;
      case TIMEOUT:
        logger.atInfo().log("Will reboot device %s because test TIMEOUT", device.getDeviceId());
        needReboot = true;
        break;
      case UNKNOWN:
      default:
        logger.atSevere().log(
            "Will reboot device %s for unknown test result: %s",
            device.getDeviceId(), testExecutionResult.testResult());
        needReboot = true;
        break;
    }
    return needReboot;
  }
}
