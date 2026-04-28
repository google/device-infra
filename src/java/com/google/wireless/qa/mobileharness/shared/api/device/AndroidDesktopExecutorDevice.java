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

import static com.google.common.labs.base.Substring.first;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.PostTestDeviceOp;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.androiddesktop.device.AndroidDesktopDeviceHelper;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.util.Objects;
import javax.annotation.Nullable;

/** A placeholder device for Android Desktop executor devices. */
public class AndroidDesktopExecutorDevice extends BaseDevice {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TEST_ARG_DUT_NAME = "dut_name";
  private static final String TEST_ARG_NEEDS_PROVISION_REPAIR = "needs_provision_repair";
  private volatile String deviceIdOverride;

  private final AndroidDesktopDeviceHelper androidDesktopDeviceHelper;
  private final AndroidAdbInternalUtil adbInternalUtil;

  public AndroidDesktopExecutorDevice(String deviceId) {
    this(deviceId, new AndroidDesktopDeviceHelper(), new AndroidAdbInternalUtil());
  }

  @VisibleForTesting
  AndroidDesktopExecutorDevice(
      String deviceId,
      AndroidDesktopDeviceHelper androidDesktopDeviceHelper,
      AndroidAdbInternalUtil adbInternalUtil) {
    super(deviceId);
    this.androidDesktopDeviceHelper = androidDesktopDeviceHelper;
    this.adbInternalUtil = adbInternalUtil;
  }

  @Override
  public void prepare() throws MobileHarnessException, InterruptedException {
    super.prepare();
    this.addSupportedDriver("NoOpDriver");
    this.addSupportedDriver("TradefedTest");
    // For Mobly tests.
    this.addSupportedDriver("MoblyAospTest");
    this.addSupportedDriver("MoblyTest");
    // For AndroidInstrumentation tests.
    this.addSupportedDriver("AndroidInstrumentation");
    // Decorators for Mobly tests.
    this.addSupportedDecorator("AndroidAccountDecorator");
    this.addSupportedDecorator("AndroidAdbShellDecorator");
    this.addSupportedDecorator("AndroidFilePullerDecorator");
    this.addSupportedDecorator("AndroidFilePusherDecorator");
    this.addSupportedDecorator("AndroidInstallAppsDecorator");
    this.addSupportedDecorator("AndroidLogCatDecorator");
    this.addSupportedDecorator("AndroidPerfettoDecorator");
    this.addSupportedDecorator("AndroidSwitchLanguageDecorator");
    this.addSupportedDecorator("CrosLsNexusDecorator");
    this.addSupportedDecorator("CrossOverAndroidDesktopProvisionDecorator");
    this.addSupportedDecorator("CrosDutTopologyDecorator");
    if (getDimension("network_zone").isEmpty()) {
      addDimension("network_zone", "unspecified");
    }
    String executorGroup = Flags.instance().androidDesktopExecutorGroup.getNonNull();
    if (!executorGroup.isEmpty()) {
      updateDimension("network_zone", executorGroup);
    }
  }

  @Override
  public String getDeviceId() {
    String deviceId = deviceIdOverride;
    String id = deviceId != null ? deviceId : super.getDeviceId();
    logger.atInfo().log("getDeviceId: %s", id);
    return id;
  }

  @Override
  public void preRunTest(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    super.preRunTest(testInfo);
    deviceIdOverride = getDeviceIdOverride(testInfo);
    testInfo.log().atInfo().alsoTo(logger).log("deviceIdOverride: %s", deviceIdOverride);
    if (deviceIdOverride != null) {
      String dutName =
          deviceIdOverride.contains(":")
              ? first(":").toEnd().removeFrom(deviceIdOverride)
              : deviceIdOverride;
      testInfo.properties().add("dut_name", dutName);
      try {
        // TODO: Support multi-duts units in the future.
        adbInternalUtil.connect(deviceIdOverride);
      } catch (MobileHarnessException e) {
        testInfo.log().atWarning().alsoTo(logger).log("Failed to connect to %s", deviceIdOverride);
        testInfo.log().atWarning().log(
            "Failed to connect to %s. Setting device to needs_repair.", deviceIdOverride);
        try {
          androidDesktopDeviceHelper.updateDeviceDutState(
              getDeviceId(),
              "needs_repair",
              /* provision= */ true,
              /* reimage= */ false,
              /* usbkey= */ false,
              /* clearRepairRequests= */ false);
        } catch (MobileHarnessException | InterruptedException ex) {
          testInfo.log().atWarning().log(
              "Failed to update device DUT state to needs_repair: %s", ex.getMessage());
          e.addSuppressed(ex);
        }
        throw e;
      }
    }
  }

  @Override
  public PostTestDeviceOp postRunTest(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    try {
      boolean skipHealthCheck = false;
      if (!skipHealthCheck) {
        if (testInfo.resultWithCause() == null
            || !Objects.equals(testInfo.resultWithCause().get().type(), TestResult.PASS)) {
          if (testInfo.properties().getBoolean(TEST_ARG_NEEDS_PROVISION_REPAIR).orElse(false)) {
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log(
                    "Test failed with repair_force_provision=true. Skipping health check and"
                        + " marking device as needs_repair.");
            androidDesktopDeviceHelper.updateDeviceDutState(
                getDeviceId(),
                "needs_repair",
                /* provision= */ true,
                /* reimage= */ false,
                /* usbkey= */ false,
                /* clearRepairRequests= */ false);
          } else {
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log(
                    "Test failed. Running health check. Result: %s",
                    androidDesktopDeviceHelper.isDeviceHealthy(getDeviceId()));
          }
        } else {
          testInfo.log().atInfo().alsoTo(logger).log("Test passed, skipping health check.");
        }
      }
    } finally {
      if (deviceIdOverride != null) {
        try {
          // TODO: Support multi-duts units in the future.
          adbInternalUtil.disconnect(deviceIdOverride);
        } catch (MobileHarnessException e) {
          testInfo
              .log()
              .atWarning()
              .alsoTo(logger)
              .log("Failed to disconnect from %s", deviceIdOverride);
        }
      }
      deviceIdOverride = null;
    }
    return super.postRunTest(testInfo);
  }

  /**
   * Gets the device ID override from the test info.
   *
   * <p>If dutName contains ":" it is assumed to be in host:port format. If it doesn't contain ":"
   * it is assumed the port is not specified, and we append default port 5555.
   */
  @VisibleForTesting
  @Nullable
  String getDeviceIdOverride(TestInfo testInfo) {
    String dutName = testInfo.jobInfo().params().get(TEST_ARG_DUT_NAME);
    if (dutName == null) {
      return null;
    }
    if (dutName.contains(":")) {
      return dutName;
    }
    return dutName + ":5555";
  }
}
