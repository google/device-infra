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

package com.google.devtools.mobileharness.api.testrunner.step.android;

import static java.lang.Math.max;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.device.cache.DeviceCache;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.DeviceDaemonHelper;
import com.google.devtools.mobileharness.platform.android.device.AndroidDeviceHelper;
import com.google.devtools.mobileharness.platform.android.devicestate.AndroidDeviceInitializer;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidRealDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nullable;

/** Utility methods used for device initialization in after-flash and after-factory-reset. */
public class DeviceInitializationStep {

  @ParamAnnotation(
      required = false,
      help =
          "If set to 'true', setup wizard will be skipped after flashing or factory reset, default"
              + " to be 'false'. It works on Google Experience Device with userdebug build only."
              + " Currently this param needs to be set in the job params level.")
  public static final String PARAM_SKIP_SETUP_WIZARD = "skip_setup_wizard";

  @ParamAnnotation(
      required = false,
      help = "If true, disable dm-verity after flashing or factory reset device.")
  public static final String PARAM_DISABLE_VERITY = "disable_verity";

  @ParamAnnotation(
      required = false,
      help = "Timeout to wait for device online after flashing or factory reset.")
  public static final String PARAM_DEVICE_READY_TIMEOUT_SEC = "device_ready_timeout_sec";

  @ParamAnnotation(
      required = false,
      help = "The number of attempts for waiting for device initialization. Default is 3.")
  public static final String PARAM_WAIT_FOR_DEVICE_INIT_ATTEMPTS = "wait_for_device_init_attempts";

  // Timeout to wait for device online after flashing or factory reset.
  @VisibleForTesting static final Duration DEFAULT_DEVICE_READY_TIMEOUT = Duration.ofMinutes(15);

  /** Minimum device ready time for {@link #PARAM_DEVICE_READY_TIMEOUT_SEC} */
  public static final Duration MIN_DEVICE_READY_TIMEOUT = Duration.ofMinutes(1);

  /** Maximum device ready time for {@link #PARAM_DEVICE_READY_TIMEOUT_SEC} */
  public static final Duration MAX_DEVICE_READY_TIMEOUT = Duration.ofMinutes(30);

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration CACHE_TIME_INIT = Duration.ofMinutes(25);

  private static final Duration DEVICE_INIT_EXTRA_CACHE_DEVICE_TIME = Duration.ofMinutes(5);

  private final AndroidDeviceInitializer androidDeviceInitializer;

  private final DeviceDaemonHelper deviceDaemonHelper;

  private final Sleeper sleeper;

  private final AndroidDeviceHelper androidDeviceHelper;

  public DeviceInitializationStep() {
    this(
        new AndroidDeviceInitializer(),
        new DeviceDaemonHelper(),
        Sleeper.defaultSleeper(),
        new AndroidDeviceHelper());
  }

  @VisibleForTesting
  DeviceInitializationStep(
      AndroidDeviceInitializer androidDeviceInitializer,
      DeviceDaemonHelper deviceDaemonHelper,
      Sleeper sleeper,
      AndroidDeviceHelper androidDeviceHelper) {
    this.androidDeviceInitializer = androidDeviceInitializer;
    this.deviceDaemonHelper = deviceDaemonHelper;
    this.sleeper = sleeper;
    this.androidDeviceHelper = androidDeviceHelper;
  }

  /**
   * Initialize device after flash or factory reset.
   *
   * <p>It'll update device property for sdk version by default.
   */
  public void initializeDevice(Device device, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    initializeDevice(
        device,
        testInfo,
        /* skipUpdateDevicePropertiesAndDimensions= */ false,
        /* skipCacheDevice= */ false,
        /* initializationArgs= */ null);
  }

  /**
   * Initialize device after flash or factory reset without additional information from TestInfo.
   *
   * <p>It'll update device property for sdk version by default.
   */
  public void initializeDevice(Device device) throws MobileHarnessException, InterruptedException {
    initializeDevice(
        device,
        /* testInfo= */ null,
        /* skipUpdateDevicePropertiesAndDimensions= */ false,
        /* skipCacheDevice= */ false,
        /* initializationArgs= */ null);
  }

  /**
   * Initialize device after flash or factory reset.
   *
   * @param skipUpdateDevicePropertiesAndDimensions {@code true} if want to skip updating device
   *     properties and dimensions.
   */
  public void initializeDevice(
      Device device, TestInfo testInfo, boolean skipUpdateDevicePropertiesAndDimensions)
      throws MobileHarnessException, InterruptedException {
    initializeDevice(
        device,
        testInfo,
        skipUpdateDevicePropertiesAndDimensions,
        /* skipCacheDevice= */ false,
        /* initializationArgs= */ null);
  }

  /**
   * Initialize device after flash or factory reset.
   *
   * @param skipCacheDevice {@code true} if want to skip caching device during initialization. If
   *     skip cache device, you need to explicitly cache the device before the initialization starts
   *     and invalidate the cache after it ends.
   */
  public void initializeDevice(
      Device device,
      @Nullable TestInfo testInfo,
      boolean skipUpdateDevicePropertiesAndDimensions,
      boolean skipCacheDevice,
      @Nullable InitializationArgs initializationArgs)
      throws MobileHarnessException, InterruptedException {
    waitForDeviceInitializedWithRetry(device, testInfo, skipCacheDevice, initializationArgs);
    if (!skipUpdateDevicePropertiesAndDimensions) {
      updateDevicePropertiesAndDimensions(device);
    }
    installAndStartDaemon(device, testInfo);
  }

  private void waitForDeviceInitializedWithRetry(
      Device device,
      @Nullable TestInfo testInfo,
      boolean skipCacheDevice,
      @Nullable InitializationArgs initializationArgs)
      throws MobileHarnessException, InterruptedException {
    Log log = testInfo != null ? testInfo.log() : null;
    String deviceId = device.getDeviceId();
    SharedLogUtil.logMsg(logger, log, "Starting device initialization for %s", deviceId);

    Duration sleepLength = Duration.ofSeconds(30);
    int deviceInitAttempts = getDeviceInitAttempts(testInfo, initializationArgs);
    Optional<Duration> deviceReadyTimeout = getDeviceReadyTimeout(testInfo);
    if (!skipCacheDevice) {
      cacheDevice(
          device,
          CACHE_TIME_INIT
              .plus(deviceReadyTimeout.orElse(DEVICE_INIT_EXTRA_CACHE_DEVICE_TIME))
              .plus(sleepLength)
              .multipliedBy(deviceInitAttempts));
    }
    try {
      for (int i = 0; i < deviceInitAttempts; i++) {
        SharedLogUtil.logMsg(
            logger,
            log,
            "Waiting for %s init with timeout %s at attempt %d/%d",
            deviceId,
            deviceReadyTimeout,
            i + 1,
            deviceInitAttempts);
        try {
          androidDeviceInitializer.initialize(
              deviceId,
              ifSkipSetupWizard(testInfo, initializationArgs),
              testInfo != null && testInfo.jobInfo().params().isTrue(PARAM_DISABLE_VERITY),
              deviceReadyTimeout);
          break;
        } catch (MobileHarnessException e) {
          if (i + 1 == deviceInitAttempts) {
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_DEVICE_INIT_STEP_INIT_DEVICE_ERROR,
                String.format(
                    "Failed all initialization attempts (%d attempts). Original failure was set"
                        + " as cause of this exception.",
                    deviceInitAttempts),
                e);
          }

          if (testInfo != null) {
            Duration remainingTime = testInfo.timer().remainingTimeJava();
            if (remainingTime.compareTo(sleepLength) < 0) {
              throw new MobileHarnessException(
                  AndroidErrorId.ANDROID_DEVICE_INIT_STEP_INIT_DEVICE_NO_ENOUGH_TIME_LEFT,
                  String.format(
                      "Failed device initialization and no time left to retry. Remaining time %dms,"
                          + " at least %dms needed.",
                      remainingTime.toMillis(), sleepLength.toMillis()),
                  e);
            }
          }

          SharedLogUtil.logMsg(
              logger,
              Level.WARNING,
              log,
              /* cause= */ null,
              "Failed device initialization attempt %d/%d due to %s, retrying",
              i + 1,
              deviceInitAttempts,
              e.getMessage());
          sleeper.sleep(sleepLength);
        }
      }
    } finally {
      if (!skipCacheDevice) {
        invalidateCacheDevice(device);
      }
    }
  }

  private void updateDevicePropertiesAndDimensions(Device device)
      throws MobileHarnessException, InterruptedException {
    if (device instanceof AndroidDevice) {
      AndroidDevice androidDevice = ((AndroidDevice) device);
      logger.atInfo().log(
          "Updating Android device %s properties and dimensions if needed.", device.getDeviceId());
      androidDeviceHelper.updateAndroidPropertyDimensions(androidDevice);
    }
  }

  private void installAndStartDaemon(Device device, @Nullable TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    try {
      if (testInfo == null) {
        deviceDaemonHelper.installAndStartDaemon(device, /* log= */ null);
      } else if (!testInfo
          .jobInfo()
          .params()
          .getBool(AndroidRealDeviceSpec.PARAM_KILL_DAEMON_ON_TEST, false /* default value */)) {
        deviceDaemonHelper.installAndStartDaemon(device, testInfo.log());
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_INIT_STEP_START_DEVICE_DAEMON_ERROR,
          String.format("Failed to install and start MH Daemon on device %s", device.getDeviceId()),
          e);
    }
  }

  private void cacheDevice(Device device, Duration expireTime) {
    DeviceCache.getInstance()
        .cache(device.getDeviceId(), device.getClass().getSimpleName(), expireTime);
  }

  private void invalidateCacheDevice(Device device) {
    DeviceCache.getInstance().invalidateCache(device.getDeviceId());
  }

  private Optional<Duration> getDeviceReadyTimeout(@Nullable TestInfo testInfo)
      throws MobileHarnessException {
    if (testInfo == null) {
      return Optional.of(DEFAULT_DEVICE_READY_TIMEOUT);
    }

    if (StrUtil.isEmptyOrWhitespace(
        testInfo.jobInfo().params().get(PARAM_DEVICE_READY_TIMEOUT_SEC))) {
      return Optional.empty();
    }

    Duration deviceReadyTimeout = null;
    try {
      // No need to check if deviceReadyTimeout is in correct range. It has been verified in
      // validator.
      deviceReadyTimeout =
          Duration.ofSeconds(testInfo.jobInfo().params().getLong(PARAM_DEVICE_READY_TIMEOUT_SEC));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_INIT_STEP_GET_JOB_PARAM_ERROR, e.getMessage(), e);
    }
    return Optional.of(deviceReadyTimeout);
  }

  private boolean ifSkipSetupWizard(
      @Nullable TestInfo testInfo, @Nullable InitializationArgs initializationArgs) {
    if (testInfo != null && testInfo.jobInfo().params().isTrue(PARAM_SKIP_SETUP_WIZARD)) {
      return true;
    }
    if (initializationArgs != null && initializationArgs.skipSetupWizard().isPresent()) {
      return initializationArgs.skipSetupWizard().get();
    }
    return false;
  }

  @VisibleForTesting
  int getDeviceInitAttempts(
      @Nullable TestInfo testInfo, @Nullable InitializationArgs initializationArgs)
      throws MobileHarnessException {
    if (testInfo == null) {
      return 1;
    }

    if (initializationArgs != null && initializationArgs.deviceOnlineWaitAttempts().isPresent()) {
      SharedLogUtil.logMsg(
          logger, testInfo.log(), "Overriding device init attempts with deviceOnlineWaitAttempts.");
      return max(1, initializationArgs.deviceOnlineWaitAttempts().get());
    }
    SharedLogUtil.logMsg(logger, testInfo.log(), "Using device init attempts from job info params");
    return max(
        1,
        testInfo
            .jobInfo()
            .params()
            .getInt(PARAM_WAIT_FOR_DEVICE_INIT_ATTEMPTS, /* defaultValue= */ 3));
  }
}
