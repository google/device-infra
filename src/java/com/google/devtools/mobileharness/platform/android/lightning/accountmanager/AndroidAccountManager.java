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

package com.google.devtools.mobileharness.platform.android.lightning.accountmanager;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.accountmanager.AccountCredentialType;
import com.google.devtools.mobileharness.platform.android.accountmanager.AndroidAccountManagerUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * Account manager for managing Google accounts on Android device.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class AndroidAccountManager {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String COMPLETED_WITH_WARNINGS = "Completed with warning(s)";

  /** Resource path of the account_manager.apk. Use the Java APIs from auth_test_support app. */
  public static final String ACCOUNT_MANAGER_APK_PATH =
      "/com/google/wireless/qa/mobileharness/tool/android/account/account_manager.apk";

  public static final String ACCOUNT_MANAGER_SIGNED_APK_PATH =
      "/com/google/wireless/qa/mobileharness/tool/android/account/signed-account_manager.apk";

  /**
   * Resource path of the auth_test_support_debug.apk supporting by auth teams. The
   * auth_test_support app needs to be installed for supporting Java APIs for the test.
   */
  public static final String AUTH_TEST_SUPPORT_DEBUG_APK_PATH =
      "/com/google/android/apps/auth/test/support/auth_test_support_debug.apk";

  public static final String AUTH_TEST_SUPPORT_SIGNED_APK_PATH =
      "/com/google/wireless/qa/mobileharness/tool/android/account/signed-auth_test_support.apk";

  @VisibleForTesting static final int DEFAULT_ADD_ACCOUNT_ATTEMPTS = 10;

  private final AndroidAccountManagerUtil androidAccountManagerUtil;

  private final ApkInstaller apkInstaller;

  private final ResUtil resUtil;

  private final SystemStateManager systemStateManager;

  private final AndroidSystemSettingUtil systemSettingUtil;

  private final Sleeper sleeper;

  // Whether to force install the signed version disregarding the build installed in the device.
  // Workaround solution for {@link b/199420696}.
  private boolean forceInstallSignedVersion = false;

  public AndroidAccountManager() {
    this(
        new AndroidAccountManagerUtil(),
        new ApkInstaller(),
        new ResUtil(),
        new SystemStateManager(),
        new AndroidSystemSettingUtil(),
        Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  AndroidAccountManager(
      AndroidAccountManagerUtil androidAccountManagerUtil,
      ApkInstaller apkInstaller,
      ResUtil resUtil,
      SystemStateManager systemStateManager,
      AndroidSystemSettingUtil systemSettingUtil,
      Sleeper sleeper) {
    this.androidAccountManagerUtil = androidAccountManagerUtil;
    this.apkInstaller = apkInstaller;
    this.resUtil = resUtil;
    this.systemStateManager = systemStateManager;
    this.systemSettingUtil = systemSettingUtil;
    this.sleeper = sleeper;
  }

  /**
   * Sets forceInstallSignedVersion to true, so to install the signed version of the apks that help
   * to sign in accounts.
   *
   * @param log log of the currently running test, usually from {@code TestInfo}
   */
  public void allowForceInstallSignedAccountHelperApks(@Nullable LogCollector<?> log) {
    SharedLogUtil.logMsg(logger, "Force to install the signed version for account manager.", log);
    forceInstallSignedVersion = true;
  }

  /**
   * Adds a Google account on device.
   *
   * <p>Before adding account, this method will prepare device if needed.
   *
   * @param device the device on which the Google account is being added
   * @param accountArgs argument wrapper for adding Google account
   * @param log log of the currently running test, usually from {@code TestInfo}
   * @throws MobileHarnessException if fails in checking if device is rooted, or using LST to login
   *     but device is unrooted, or fails to prepare device before adding account, or fails to log
   *     in account. Can ignore the login error if {@code accountArgs} sets ignoreLoginError with
   *     {@code true}.
   * @throws InterruptedException if the thread executing the commands is interrupted.
   */
  public void addAccount(Device device, AddAccountArgs accountArgs, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    String email = accountArgs.accountSetting().email();
    int maxAttempts = accountArgs.maxAttempts().orElse(DEFAULT_ADD_ACCOUNT_ATTEMPTS);
    boolean ignoreLoginError = accountArgs.ignoreLoginError().orElse(false);

    if (AccountCredentialType.LOGIN_SCOPED_TOKEN.equals(
        accountArgs.accountSetting().credentialType())) {
      boolean deviceIsRooted = false;
      try {
        deviceIsRooted = systemStateManager.becomeRoot(device);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ACCOUNT_MANAGER_CHECK_DEVICE_ROOTED_ERROR, e.getMessage(), e);
      }
      if (!deviceIsRooted) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ACCOUNT_MANAGER_UNROOTED_DEVICE_NOT_SUPPORT_LST,
            "LOGIN_SCOPED_TOKEN can not be supported by release-key device");
      }
    }

    prepareAddAndroidAccount(device, log);
    SharedLogUtil.logMsg(
        logger, log, "Successfully prepared device %s before adding Google account.", deviceId);
    try {
      androidAccountManagerUtil.addGoogleAccount(
          deviceId, accountArgs.accountSetting(), maxAttempts);
      SharedLogUtil.logMsg(
          logger, log, "Successfully add Google account %s on device %s", email, deviceId);
    } catch (MobileHarnessException e) {
      MobileHarnessException newException =
          new MobileHarnessException(
              AndroidErrorId.ANDROID_ACCOUNT_MNGR_UTIL_WRONG_PASSWORD.equals(e.getErrorId())
                  ? AndroidErrorId.ANDROID_ACCOUNT_MANAGER_ACCOUNT_WRONG_PASSWORD
                  : AndroidErrorId.ANDROID_ACCOUNT_MANAGER_ADD_ACCOUNT_ERROR,
              String.format(
                  "Failed to add Google account %s on device %s. \n originalMessage: %s",
                  email, deviceId, e.getMessage()),
              e);
      if (ignoreLoginError) {
        SharedLogUtil.logMsg(
            logger,
            Level.WARNING,
            log,
            newException,
            "Login failed on device %s but ignored error as request",
            deviceId);
      } else {
        throw newException;
      }
    }
  }

  /**
   * Remove existing Google accounts that are not included in new accounts on device. If force
   * remove option is true, remove all existing Google accounts in new account list on device.
   *
   * @param device the device which is being removed Android accounts from
   * @param forceRemove whether to remove all existing accounts on device
   * @param newAccounts new accounts which are required to be added on device
   * @param log log of the currently running test, usually from {@code TestInfo}
   * @return the list of accounts that need to be updated (the existing Google accounts that are
   *     included in new accounts).
   * @throws MobileHarnessException if it fails to remove account from device or fails to prepare
   *     device
   * @throws InterruptedException if current thread is interrupted during this method
   */
  @CanIgnoreReturnValue
  public ImmutableList<String> removeExistingAndGetAccountsToUpdate(
      Device device,
      boolean forceRemove,
      @Nullable List<String> newAccounts,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    prepareAddAndroidAccount(device, log);
    String deviceId = device.getDeviceId();
    SharedLogUtil.logMsg(
        logger, log, "Successfully prepared device %s before removing Google account.", deviceId);
    List<String> existingAccounts =
        androidAccountManagerUtil.getNoObfuscatingGoogleAccounts(deviceId);
    List<String> accountsToUpdate = new ArrayList<>();
    List<String> accountsToDelete = new ArrayList<>();
    if (newAccounts == null || newAccounts.isEmpty()) {
      // If there is no new_account specified, remove all existing accounts.
      accountsToDelete = existingAccounts;
    } else {
      for (String existingAccount : existingAccounts) {
        if (newAccounts.contains(existingAccount)) {
          accountsToUpdate.add(existingAccount);
        } else {
          accountsToDelete.add(existingAccount);
        }
      }
    }
    if (forceRemove) {
      accountsToDelete = existingAccounts;
    }
    try {
      for (String accountToDelete : accountsToDelete) {
        androidAccountManagerUtil.removeGoogleAccount(deviceId, accountToDelete);
        SharedLogUtil.logMsg(
            logger,
            log,
            "Successfully removed Google account %s on device %s",
            accountToDelete,
            deviceId);
      }

      if (!accountsToDelete.isEmpty()) {
        // Sleep after removing accounts, we had race conditions when add account is called
        // immediately after remove account: b/466294977#comment6
        sleeper.sleep(Duration.ofSeconds(5));
      }

    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ACCOUNT_MANAGER_REMOVE_ACCOUNT_ERROR, e.getMessage(), e);
    }
    return ImmutableList.copyOf(accountsToUpdate);
  }

  private enum ApkType {
    ACCOUNT_MANAGER,
    AUTH_TEST_SUPPORT,
  }

  /**
   * Install specific apk to help add/remove account if apk is not installed on device.
   *
   * @param device the device being prepared for adding/removing Android account
   * @param log log of the currently running test, usually from {@code TestInfo}
   * @throws MobileHarnessException if errors occur during installing apk
   * @throws InterruptedException if current thread is interrupted during this method
   */
  @VisibleForTesting
  void prepareAddAndroidAccount(Device device, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    // Makes sure the supporting apk files is installed.
    String deviceId = device.getDeviceId();
    boolean installSignedVersion = installSignedVersion(device.getDeviceId(), log);

    if (!installSignedVersion) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Debug/Test-keys detected, installing debug-key apps on device %s",
          deviceId);
    }

    installApkHelper(device, ApkType.ACCOUNT_MANAGER, installSignedVersion, log);
    installApkHelper(device, ApkType.AUTH_TEST_SUPPORT, installSignedVersion, log);
  }

  // Determine whether to install signed version by detecting production build.
  private boolean installSignedVersion(String deviceId, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    if (forceInstallSignedVersion) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "User sets force_install_signed as true, installing signed-key apps on device %s",
          deviceId);
      return true;
    }
    if (systemSettingUtil.isProductionBuild(deviceId)) {
      SharedLogUtil.logMsg(
          logger, log, "Release-keys detected, installing signed-key apps on device %s", deviceId);
      return true;
    }
    return false;
  }

  private void installApkHelper(
      Device device, ApkType apkType, boolean installSignedVersion, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    try {
      apkInstaller.installApkIfNotExist(
          device,
          ApkInstallArgs.builder().setApkPath(getApkPath(apkType, installSignedVersion)).build(),
          log);
    } catch (MobileHarnessException e) {
      if (e.getMessage() != null && e.getMessage().contains(COMPLETED_WITH_WARNINGS)) {
        // b/455567423 Vivo devices may throw error when installing the signed-account_manager.apk.
        // We will ignore the error since the apk is successfully installed.
        SharedLogUtil.logMsg(
            logger,
            Level.WARNING,
            log,
            e,
            "Installation failed on device %s but completed with warning(s), ignore it.",
            device.getDeviceId());
        return;
      }
      ErrorId errorId = AndroidErrorId.ANDROID_ACCOUNT_MANAGER_APK_INSTALL_ERROR;
      if (AndroidErrorId.ANDROID_PKG_MNGR_UTIL_INSTALLATION_INSUFFICIENT_STORAGE.equals(
          e.getErrorId())) {
        errorId = AndroidErrorId.ANDROID_ACCOUNT_MANAGER_APK_INSTALL_INSUFFICIENT_STORAGE;
      }
      throw new MobileHarnessException(errorId, e.getMessage(), e);
    }
  }

  private String getApkPath(ApkType apkType, boolean installSignedVersion)
      throws MobileHarnessException {
    switch (apkType) {
      case ACCOUNT_MANAGER:
        if (installSignedVersion) {
          String apkPathFromFlag = Flags.instance().androidAccountManagerSignedApkPath.get();
          if (!isNullOrEmpty(apkPathFromFlag)) {
            return apkPathFromFlag;
          } else {
            return getResourceFile(ACCOUNT_MANAGER_SIGNED_APK_PATH);
          }
        } else {
          String apkPathFromFlag = Flags.instance().androidAccountManagerApkPath.get();
          if (!isNullOrEmpty(apkPathFromFlag)) {
            return apkPathFromFlag;
          } else {
            return getResourceFile(ACCOUNT_MANAGER_APK_PATH);
          }
        }
      case AUTH_TEST_SUPPORT:
        if (installSignedVersion) {
          String apkPathFromFlag = Flags.instance().androidAuthTestSupportSignedApkPath.get();
          if (!isNullOrEmpty(apkPathFromFlag)) {
            return apkPathFromFlag;
          } else {
            return getResourceFile(AUTH_TEST_SUPPORT_SIGNED_APK_PATH);
          }
        } else {
          String apkPathFromFlag = Flags.instance().androidAuthTestSupportApkPath.get();
          if (!isNullOrEmpty(apkPathFromFlag)) {
            return apkPathFromFlag;
          } else {
            return getResourceFile(AUTH_TEST_SUPPORT_DEBUG_APK_PATH);
          }
        }
    }
    throw new AssertionError("Unsupported apk type: " + apkType);
  }

  private String getResourceFile(String resPath) throws MobileHarnessException {
    Optional<String> externalResFile = resUtil.getExternalResourceFile(resPath);
    if (externalResFile.isPresent()) {
      return externalResFile.get();
    }
    return resUtil.getResourceFile(this.getClass(), resPath);
  }
}
