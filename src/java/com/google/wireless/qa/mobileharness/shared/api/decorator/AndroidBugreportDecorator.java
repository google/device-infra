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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidBugreportSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Duration;

/**
 * Decorator for retrieving Android device bugreport of a test. It saves the file and send to client
 * upon failure/error.
 */
@DecoratorAnnotation(
    help =
        "For retrieving Android device bugreport of a test. It saves the file and "
            + "send to client"
            + " upon failure/error.")
public class AndroidBugreportDecorator extends LifecycleDecorator implements AndroidBugreportSpec {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration WAIT_FOR_BUGREPORT_TIMEOUT = Duration.ofMinutes(15);

  /** {@code AndroidAdbUtil} for Android ADB commands. */
  private final AndroidAdbUtil androidAdbUtil;

  /** {@code AndroidFileUtil} for Android file operations. */
  private final AndroidFileUtil androidFileUtil;

  /** {@code AndroidSystemSettingUtil} for Android system settings. */
  private final AndroidSystemSettingUtil androidSystemSettingUtil;

  /** {@code SystemStateManager} for Android system state management. */
  private final SystemStateManager systemStateManager;

  /** {@code FileUtil} for writing device log to file. */
  private final LocalFileUtil fileUtil;

  private final DeviceCache deviceCache;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the lab server
   * framework.
   */
  public AndroidBugreportDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new AndroidAdbUtil(),
        new AndroidFileUtil(),
        new AndroidSystemSettingUtil(),
        new SystemStateManager(),
        new LocalFileUtil(),
        DeviceCache.getInstance());
  }

  /** Constructor for testing only. */
  @VisibleForTesting
  AndroidBugreportDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidAdbUtil androidAdbUtil,
      AndroidFileUtil androidFileUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      SystemStateManager systemStateManager,
      LocalFileUtil fileUtil,
      DeviceCache deviceCache) {
    super(decoratedDriver, testInfo);
    this.systemStateManager = systemStateManager;
    this.androidAdbUtil = androidAdbUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.fileUtil = fileUtil;
    this.deviceCache = deviceCache;
  }

  @Override
  protected void setUp(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String serial = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    if (jobInfo.params().getBool(PARAM_NO_AUTO_RESET, false)) {
      try {
        androidSystemSettingUtil.setBatteryStatsNoAutoReset(serial, /* enable= */ true);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_ENABLE_NO_AUTO_RESET_ERROR, e.getMessage());
      }
    }
    if (jobInfo.params().getBool(PARAM_LOGICAL_DISCHARGE, false)) {
      try {
        androidSystemSettingUtil.setBatteryLogicalDischarge(serial, /* discharge= */ false);
        androidSystemSettingUtil.setBatteryLogicalDischarge(serial, /* discharge= */ true);
        androidSystemSettingUtil.resetBatteryStats(serial);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_SET_LOGICAL_DISCHARGE_ERROR, e.getMessage());
      }
    }
  }

  @CanIgnoreReturnValue
  private String dumpBugreportFromDevice(TestInfo testInfo) throws InterruptedException {
    String deviceId = getDevice().getDeviceId();
    String bugreportFilePath;
    String bugreportFileName = null;
    try {
      int sdkVersion;
      try {
        sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_GET_DEVICE_SDK_ERROR, e.getMessage());
      }
      if (sdkVersion >= 24) {
        // In SDK >= 24 the bugreport command writes the report to the given zip file.
        bugreportFileName = AndroidBugreportSpec.BUGREPORT_ZIP_FILE_NAME;
        bugreportFilePath = PathUtil.join(testInfo.getGenFileDir(), bugreportFileName);
        dumpBugreport(deviceId, bugreportFilePath);
      } else {
        // In SDK < 24 the bugreport command writes the report to stdout.
        bugreportFileName = AndroidBugreportSpec.BUGREPORT_TXT_FILE_NAME;
        bugreportFilePath = PathUtil.join(testInfo.getGenFileDir(), bugreportFileName);
        String bugreport = dumpBugreport(deviceId, /* bugreportFilePath= */ null);
        try {
          fileUtil.writeToFile(bugreportFilePath, bugreport);
        } catch (MobileHarnessException e) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_SAVE_BUGREPORT_ERROR, e.getMessage());
        }
      }
      deleteBugreportOnDevice(testInfo);
    } catch (MobileHarnessException e) {
      testInfo.warnings().addAndLog(e, logger);
    }
    return bugreportFileName;
  }

  @CanIgnoreReturnValue
  private String dumpBugreport(String deviceId, String bugreportFilePath)
      throws MobileHarnessException, InterruptedException {
    try {
      deviceCache.cache(
          deviceId, getDevice().getClass().getSimpleName(), WAIT_FOR_BUGREPORT_TIMEOUT);
      return androidAdbUtil.bugreport(deviceId, bugreportFilePath);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_DUMP_BUGREPORT_ERROR, e.getMessage(), e);
    } finally {
      deviceCache.invalidateCache(deviceId);
    }
  }

  private void deleteBugreportOnDevice(TestInfo testInfo) throws InterruptedException {
    String deviceId = getDevice().getDeviceId();
    if (testInfo.jobInfo().params().getBool(PARAM_BUGREPORT_DELETE, true)) {
      try {
        String bugreportDirectory = androidAdbUtil.bugreportDirectory(deviceId);
        if (!bugreportDirectory.isEmpty()) {
          androidFileUtil.removeFiles(deviceId, bugreportDirectory + "/*");
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Generated bugreport under %s has been deleted.", bugreportDirectory);
        }
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Failed to delete generated bugreport on the device.\n%s", e.getMessage());
      }
    }
  }

  @Override
  protected void tearDown(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    JobInfo jobInfo = testInfo.jobInfo();
    String deviceId = getDevice().getDeviceId();
    try {
      if (testInfo.getRootTest().resultWithCause().get().type() == TestResult.PASS
          && !jobInfo.params().getBool(PARAM_BUGREPORT_ON_PASS, false)) {
        testInfo.log().atInfo().alsoTo(logger).log("Skip bugreport when test passed");
      } else if (!isDeviceOnline(deviceId)) {
        // Only invokes the bugreport command when the device is detected
        testInfo
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_DEVICE_NOT_FOUND,
                    String.format(
                        "Skip retrieving bugreport because device %s is disconnected", deviceId)),
                logger);
      } else {
        dumpBugreportFromDevice(testInfo);
        deleteBugreportOnDevice(testInfo);
      }
    } finally {
      if (isDeviceOnline(deviceId)) {
        try {
          if (jobInfo.params().getBool(PARAM_LOGICAL_DISCHARGE, false)) {
            androidSystemSettingUtil.setBatteryLogicalDischarge(deviceId, /* discharge= */ false);
          }
          if (jobInfo.params().getBool(PARAM_NO_AUTO_RESET, false)) {
            androidSystemSettingUtil.setBatteryStatsNoAutoReset(deviceId, /* enable= */ false);
          }
        } catch (MobileHarnessException e) {
          testInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_RESET_BATTERY_ERROR,
                      String.format("Failed to reset battery settings for device %s", deviceId),
                      e),
                  logger);
        }
      }
    }
  }

  private boolean isDeviceOnline(String deviceId)
      throws MobileHarnessException, InterruptedException {
    try {
      return systemStateManager.isOnline(deviceId);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_BUGREPORT_DECORATOR_GET_ONLINE_DEVICES_ERROR, e.getMessage(), e);
    }
  }
}
