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

package com.google.devtools.mobileharness.platform.android.devicestate;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.PostSetDmVerityDeviceOp;
import com.google.devtools.mobileharness.platform.android.systemsetting.PostSettingDeviceOp;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * The device initialize util for setting up a new device, since the device will reboot several
 * times, need cache the device before calling if using it in lab server.
 */
public class AndroidDeviceInitializer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Device feature indicating it is an AndroidThings (IoT) device. */
  @VisibleForTesting
  static final String FEATURE_EMBEDDED = "feature:android.hardware.type.embedded";

  /** Device feature indicating it is an Android Automotive device. */
  @VisibleForTesting
  static final String FEATURE_AUTOMOTIVE = "feature:android.hardware.type.automotive";

  /** Default timeout of waiting device to be ready. */
  @VisibleForTesting static final Duration DEFAULT_DEVICE_READY_TIMEOUT = Duration.ofMinutes(5);

  private final AndroidSystemSettingUtil systemSettingUtil;
  private final AndroidSystemStateUtil systemStateUtil;
  private final AndroidSystemSpecUtil systemSpecUtil;
  private final AndroidPackageManagerUtil packageManagerUtil;
  private final Sleeper sleeper;

  public AndroidDeviceInitializer() {
    this(
        Sleeper.defaultSleeper(),
        new AndroidSystemSettingUtil(),
        new AndroidSystemStateUtil(),
        new AndroidSystemSpecUtil(),
        new AndroidPackageManagerUtil());
  }

  @VisibleForTesting
  AndroidDeviceInitializer(
      Sleeper sleeper,
      AndroidSystemSettingUtil systemSettingUtil,
      AndroidSystemStateUtil systemStateUtil,
      AndroidSystemSpecUtil systemSpecUtil,
      AndroidPackageManagerUtil packageManagerUtil) {
    this.sleeper = sleeper;
    this.systemSettingUtil = systemSettingUtil;
    this.systemStateUtil = systemStateUtil;
    this.systemSpecUtil = systemSpecUtil;
    this.packageManagerUtil = packageManagerUtil;
  }

  /**
   * Initializes the device.
   *
   * <p>Timeout of waiting for device to be ready is defaulted to {@link
   * #DEFAULT_DEVICE_READY_TIMEOUT}
   *
   * @param deviceId the device serial number
   * @param skipWizard whether to skip wizard
   */
  public void initialize(String deviceId, boolean skipWizard, boolean disableVerity)
      throws MobileHarnessException, InterruptedException {
    initialize(deviceId, skipWizard, disableVerity, Optional.of(DEFAULT_DEVICE_READY_TIMEOUT));
  }

  /**
   * Initializes the device:
   *
   * <ol>
   *   <li>become root.
   *   <li>skip wizard and dm verity if necessary.
   *   <li>allow mock location.
   *   <li>keep awake.
   *   <li>disable screen lock.
   *   <li>disable package verifier.
   *   <li>enable unknown resource.
   * </ol>
   *
   * <p>Cache the device out the function if you are using it in lab server to avoid device manager
   * kill the process.
   *
   * @param deviceId the device serial number
   * @param skipWizard whether to skip wizard
   * @param checkReadyTimeout max wait time for checking the readiness of device
   */
  public void initialize(
      String deviceId,
      boolean skipWizard,
      boolean disableVerity,
      Optional<Duration> checkReadyTimeout)
      throws MobileHarnessException, InterruptedException {
    Duration deviceReadyTimeout = checkReadyTimeout.orElse(DEFAULT_DEVICE_READY_TIMEOUT);
    systemStateUtil.waitForState(deviceId, DeviceConnectionState.DEVICE, Duration.ofMinutes(5));
    systemStateUtil.waitUntilReady(deviceId, deviceReadyTimeout);
    var unused = systemStateUtil.becomeRoot(deviceId);
    systemStateUtil.waitUntilReady(deviceId, Duration.ofMinutes(3));

    // If Test Harness Mode is enabled, we can skip some settings as it has been done for us.
    boolean isTestHarnessModeEnabled = systemSettingUtil.isTestHarnessModeEnabled(deviceId);
    logger.atInfo().log(
        "Test Harness Mode state on device %s: %s", deviceId, isTestHarnessModeEnabled);

    boolean needsRebootAfterSettingChanges = false;
    if (skipWizard && !isTestHarnessModeEnabled) {
      logger.atInfo().log("Skipping setup wizard");
      if (systemSettingUtil.disableSetupWizard(deviceId) == PostSettingDeviceOp.REBOOT) {
        needsRebootAfterSettingChanges = true;
      }
    }
    if (disableVerity) {
      if (systemSettingUtil.getDeviceSdkVersion(deviceId) >= 22
          && systemSettingUtil.setDmVerityChecking(deviceId, false)
              == PostSetDmVerityDeviceOp.REBOOT) {
        logger.atInfo().log("Disabled dm-verity.");
        needsRebootAfterSettingChanges = true;
      }
    }
    if (needsRebootAfterSettingChanges) {
      systemStateUtil.reboot(deviceId);
      systemStateUtil.waitForState(deviceId, DeviceConnectionState.DEVICE, Duration.ofMinutes(15));
      systemStateUtil.waitUntilReady(deviceId, deviceReadyTimeout);
      // Device will be unrooted after rebooting.
      unused = systemStateUtil.becomeRoot(deviceId);
      systemStateUtil.waitUntilReady(deviceId, Duration.ofMinutes(3));
    }

    var unusedString = systemSettingUtil.allowMockLocation(deviceId);
    systemStateUtil.waitUntilReady(deviceId, Duration.ofMinutes(3));
    if (!isTestHarnessModeEnabled) {
      if (!DeviceUtil.inSharedLab()) {
        try {
          systemSettingUtil.keepAwake(deviceId, /* alwaysAwake= */ true);
        } catch (MobileHarnessException e) {
          if (e.getMessage().contains("exit_code=137")) {
            // Some Samsung devices return exit_code=137 when keep awake. See b/402566355.
            logger.atInfo().log(
                "Ignore the failure of keeping device %s awake: %s", deviceId, e.getMessage());
          } else {
            throw e;
          }
        }
      } else {
        logger.atSevere().log(
            "Ignoring attempt to keep awake device %s while not managing devices.", deviceId);
      }
      systemStateUtil.waitUntilReady(deviceId, Duration.ofMinutes(3));
    }

    disableScreenLock(deviceId);

    // Try to enable unknown sources option.
    enableUnknownSources(deviceId);

    disablePackageVerifier(deviceId);
    logger.atInfo().log("waiting for %s ready", deviceId);
    systemStateUtil.waitUntilReady(deviceId, Duration.ofMinutes(3));
  }

  private void disableScreenLock(String deviceId)
      throws MobileHarnessException, InterruptedException {
    Set<String> features = systemSpecUtil.getSystemFeatures(deviceId);
    // ScreenLock not used on Android Automotive devices due to b/135046763#comment51
    if (!features.contains(FEATURE_EMBEDDED) && !features.contains(FEATURE_AUTOMOTIVE)) {
      // Try to disable screen lock.
      logger.atInfo().log("Disable screen lock");
      systemSettingUtil.disableScreenLock(
          deviceId, systemSettingUtil.getDeviceSdkVersion(deviceId));
      // Due to b/30934672, sleep 10ms waiting for soft reboot.
      sleeper.sleep(Duration.ofMillis(10));
    }
  }

  private void enableUnknownSources(String serial)
      throws MobileHarnessException, InterruptedException {
    if (!DeviceUtil.inSharedLab()) {
      systemSettingUtil.enableUnknownSources(serial, systemSettingUtil.getDeviceSdkVersion(serial));
    } else {
      logger.atSevere().log(
          "Ignoring attempt to enable unknown sources on device %s"
              + " while not managing devices.",
          serial);
    }
  }

  private void disablePackageVerifier(String serial)
      throws MobileHarnessException, InterruptedException {
    int sdkVersion;
    try {
      sdkVersion = systemSettingUtil.getDeviceSdkVersion(serial);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to get SDK version before disabling package verifier option, aborted.");
      return;
    }

    if (!DeviceUtil.inSharedLab()) {
      packageManagerUtil.disablePackageVerifier(serial, sdkVersion);
    } else {
      logger.atSevere().log(
          "Ignoring attempt to disable package verifier on device %s"
              + " while not managing devices.",
          serial);
    }
  }
}
