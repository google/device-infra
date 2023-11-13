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

package com.google.devtools.mobileharness.platform.android.lightning.apkinstaller;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.app.AndroidAppVersion;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedPropertyUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.InstallCmdArgs;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.shared.constant.PackageConstants;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.checksum.ChecksumUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.ApkInfo;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nullable;

/**
 * Apk Installer for installing/uninstalling system and non-system apks on device.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class ApkInstaller {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Duration WAIT_FOR_STAGED_SESSION_READY_TIMEOUT = Duration.ofSeconds(60L);

  /** Suffix of apex files. */
  private static final String APEX_SUFFIX = ".apex";

  /** Default delimiter of the device property names for the installed apks. */
  @VisibleForTesting static final String DEVICE_PROP_PREFIX_DELIMITER = ":";

  /** Prefix of the device property names of the installed apks. */
  @VisibleForTesting static final String DEVICE_PROP_PREFIX_INSTALLED_APK = "installed_apk";

  /** Prefix of the device property names of the installed apks. */
  @VisibleForTesting static final String DEVICE_PROP_PREFIX_USER_ID = "user_";

  @VisibleForTesting
  static final String DEVICE_PROP_INSTALLED_APK_KEY_TEMPLATE =
      DEVICE_PROP_PREFIX_INSTALLED_APK
          + DEVICE_PROP_PREFIX_DELIMITER
          + DEVICE_PROP_PREFIX_USER_ID
          + "%s"
          + DEVICE_PROP_PREFIX_DELIMITER;

  @VisibleForTesting
  static final String INSTALL_VERSION_DOWNGRADE_ERROR =
      "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]";

  @VisibleForTesting
  static final String INSTALL_FAILED_UPDATE_INCOMPATIBLE =
      "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE";

  @VisibleForTesting static final String NOT_INSTALL_FOR_USER_ERROR = "Failure [not installed for";

  /** Default user id for API < 24. */
  @VisibleForTesting static final String MULTI_USER_DEFAULT_ID = "all";

  /** Limit multi-user feature to >= API 24, given the poor support in ADB. */
  @VisibleForTesting
  static final int MULTI_USER_START_SDK_VERSION = AndroidVersion.NOUGAT.getStartSdkVersion();

  @VisibleForTesting static final String PROPERTY_NAME_CACHED_ABI = "cached_abi";

  @VisibleForTesting
  static final String PROPERTY_NAME_CACHED_SCREEN_DENSITY = "cached_screen_density";

  /** Util class for generating MD5 of a file or directory. */
  private final ChecksumUtil md5Util;

  private final Aapt aapt;

  private final AndroidSystemSpecUtil systemSpecUtil;

  private final AndroidSystemSettingUtil systemSettingUtil;

  private final AndroidPackageManagerUtil androidPackageManagerUtil;

  private final AndroidFileUtil androidFileUtil;

  private final AndroidUserUtil androidUserUtil;

  private final Sleeper sleeper;

  public ApkInstaller() {
    this(
        new ChecksumUtil(Hashing.md5()),
        new Aapt(/* enableAaptOutputCache= */ true),
        new AndroidSystemSpecUtil(),
        new AndroidSystemSettingUtil(),
        new AndroidPackageManagerUtil(),
        new AndroidFileUtil(),
        new AndroidUserUtil(),
        Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  ApkInstaller(
      ChecksumUtil md5Util,
      Aapt aapt,
      AndroidSystemSpecUtil systemSpecUtil,
      AndroidSystemSettingUtil systemSettingUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidFileUtil androidFileUtil,
      AndroidUserUtil androidUserUtil,
      Sleeper sleeper) {
    this.md5Util = md5Util;
    this.aapt = aapt;
    this.systemSpecUtil = systemSpecUtil;
    this.systemSettingUtil = systemSettingUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidUserUtil = androidUserUtil;
    this.sleeper = sleeper;
  }

  /**
   * DO NOT use this method in any Lab Plugins.
   *
   * <p>Checks version info of an installed package. Will cache the version info in cache. If you
   * are installing 2 or more versions of the same app twice within the same test, this method will
   * return the first installed app version.
   *
   * @param apkName apk name for logging only
   * @return the app version info; or empty if failed to retrieve it from the device like app not
   *     installed
   */
  @CanIgnoreReturnValue
  @Beta
  public Optional<AndroidAppVersion> checkInstalledAppVersion(
      TestInfo testInfo, String deviceId, String packageName, @Nullable String apkName)
      throws InterruptedException {
    String formattedApkName = apkName == null ? "" : "(" + PathUtil.basename(apkName) + ")";
    final String logTemplate = "%s%s: version code %s, version name %s (retrieved from %s)";

    String packageInPropertyName = packageName.replace('.', '_');
    String versionCodePropertyName =
        Ascii.toLowerCase(ApkInfo.VERSION_CODE_.name()) + packageInPropertyName;
    String versionNamePropertyName =
        Ascii.toLowerCase(ApkInfo.VERSION_NAME_.name()) + packageInPropertyName;

    String versionCode = testInfo.properties().get(versionCodePropertyName);
    String versionName = testInfo.properties().get(versionNamePropertyName);

    if (!Strings.isNullOrEmpty(versionCode) && !Strings.isNullOrEmpty(versionName)) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              logTemplate,
              packageName,
              formattedApkName,
              versionCode,
              versionName,
              "test properties");
    } else {
      try {
        versionCode =
            String.valueOf(androidPackageManagerUtil.getAppVersionCode(deviceId, packageName));
        versionName = androidPackageManagerUtil.getAppVersionName(deviceId, packageName);
      } catch (MobileHarnessException e) {
        testInfo.errors().addAndLog(e, logger);
        return Optional.empty();
      }
      testInfo.properties().add(versionCodePropertyName, versionCode);
      testInfo.properties().add(versionNamePropertyName, versionName);

      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(logTemplate, packageName, formattedApkName, versionCode, versionName, "dumpsys");
    }
    return Optional.of(AndroidAppVersion.create(Integer.parseInt(versionCode), versionName));
  }

  /**
   * Clears the stored information of installed apks associated with a device. This is especially
   * useful for emulators, because emulators share device objects, but we will start a new emulator
   * on the fly. The information about the installed apks has to be cleared for the newly started
   * emulator.
   */
  public void clearInstalledApkProperties(Device device) {
    SharedPropertyUtil.clearPropertiesWithPrefix(
        device, DEVICE_PROP_PREFIX_INSTALLED_APK + DEVICE_PROP_PREFIX_DELIMITER);
  }

  /** Clears stored information of installed apk associated with a user on a device. */
  public void clearInstalledApkPropertiesForUser(Device device, String userId) {
    SharedPropertyUtil.clearPropertiesWithPrefix(
        device, String.format(DEVICE_PROP_INSTALLED_APK_KEY_TEMPLATE, userId));
  }

  /** Clears stored information of installed apk associated with current user on a device. */
  public void clearInstalledApkProperty(Device device, String packageName) {
    String deviceId = device.getDeviceId();
    String userId = MULTI_USER_DEFAULT_ID;

    try {
      int deviceSdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
      if (isMultiUserSupported(deviceSdkVersion)) {
        userId = getCurrentUser(deviceId, deviceSdkVersion).get();
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to get device %s sdk version.", deviceId);
    } catch (InterruptedException ie) {
      logger.atWarning().log(
          "Caught interrupted exception when checking device %s sdk version, interrupt current "
              + "thread:%n%s",
          deviceId, ie.getMessage());
      Thread.currentThread().interrupt();
    }

    clearInstalledApkPropertyForUser(device, packageName, userId);
  }

  /** Clears stored information of installed apk associated with given user on a device. */
  public void clearInstalledApkPropertyForUser(Device device, String packageName, String userId) {
    String propertyName =
        String.format(DEVICE_PROP_INSTALLED_APK_KEY_TEMPLATE, userId) + packageName;
    String installedApkMd5 = device.getProperty(propertyName);
    if (installedApkMd5 != null) {
      logger.atInfo().log(
          "Clearing device %s property '%s' which has value %s",
          device.getDeviceId(), propertyName, installedApkMd5);
      device.setProperty(propertyName, null);
    }
  }

  /**
   * Installs the APK into the android device.
   *
   * <p>Logs will be appended to the test's info. Once installed, it will set the device property to
   * avoid installing the same apk again when flag {@link Flags#instance()}.{@link
   * Flags#cacheInstalledApks cacheInstalledApks} is set to true.
   *
   * <p>NOTE: Do NOT use this method and {@link #installApkIfVersionMismatched} both for one APK.
   *
   * @param device the device that the APK is installed to
   * @param installArgs arguments wrapper for apk installation
   * @return the package name of the apk
   */
  @CanIgnoreReturnValue
  public String installApk(Device device, ApkInstallArgs installArgs, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String apkPath = installArgs.apkPath();
    String dexMetadataPath = installArgs.dexMetadataPath().orElse(null);
    boolean clearAppData = installArgs.clearAppData().orElse(false);
    boolean grantPermissions = installArgs.grantPermissions().orElse(true);
    boolean skipIfDowngrade = installArgs.skipDowngrade().orElse(false);
    boolean skipIfCached = installArgs.skipIfCached().orElse(true);
    boolean skipIfVersionMatch = installArgs.skipIfVersionMatch().orElse(false);
    boolean forceNoStreaming = installArgs.forceNoStreaming().orElse(DeviceUtil.inSharedLab());
    boolean forceQueryable = installArgs.forceQueryable().orElse(false);
    boolean bypassLowTargetSdkBlock = installArgs.bypassLowTargetSdkBlock().orElse(false);
    List<String> extraArgs = new ArrayList<>();

    String deviceId = device.getDeviceId();
    int deviceSdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
    String userId = MULTI_USER_DEFAULT_ID;
    if (isMultiUserSupported(deviceSdkVersion)) {
      userId = installArgs.userId().orElse(getCurrentUser(deviceId, deviceSdkVersion).get());
    }

    // Checks whether the apk has been installed.
    String packageName = aapt.getApkPackageName(apkPath);
    String apkName = new File(apkPath).getName();
    String apkMd5 = md5Util.fingerprint(apkPath);
    logger.atInfo().log("Md5 for apk %s is: %s", apkPath, apkMd5);
    if (skipIfCached
        && checkApkInstalledByMd5(
            device, deviceSdkVersion, apkMd5, apkName, packageName, userId, log)) {
      return packageName;
    }

    // Checks whether the apk can be installed.
    checkApkMinSdkVersion(deviceSdkVersion, apkPath, log);

    // Check if should skip install apk according to version code.
    Integer apkVersionCode = null;
    try {
      apkVersionCode = aapt.getApkVersionCode(apkPath);
    } catch (MobileHarnessException e) {
      // Apks may have empty version code, like
      // java/com/google/android/apps/common/testing/services/basic_services.apk
      SharedLogUtil.logMsg(
          logger,
          log,
          "Not found a valid version code for apk %s:%n%s",
          apkPath,
          MoreThrowables.shortDebugString(e, 0));
    }
    if (apkVersionCode != null
        && shouldSkipOnVersionCode(
            buildUtilArgs(deviceId, userId, deviceSdkVersion),
            packageName,
            apkVersionCode,
            skipIfDowngrade,
            skipIfVersionMatch,
            log)) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Skip installing apk %s (version:%s) on device %s, skipIfDowngrade = %s,"
              + " skipIfVersionMatch = %s.",
          apkName,
          apkVersionCode,
          deviceId,
          skipIfDowngrade,
          skipIfVersionMatch);
      return packageName;
    }

    boolean isGms = PackageConstants.PACKAGE_NAME_GMS.equals(packageName);
    if (isGms) {
      String apkVersionName = aapt.getApkVersionName(apkPath);
      if (!installArgs.skipGmsCompatCheck().orElse(false)) {
        checkGmsCompatibility(
            device, deviceSdkVersion, apkPath, apkVersionCode, apkVersionName, log);
      }
      if (clearAppData) {
        SharedLogUtil.logMsg(logger, "Clear GmsCore before installation", log);
        androidPackageManagerUtil.clearPackage(
            buildUtilArgs(deviceId, userId, deviceSdkVersion), PackageConstants.PACKAGE_NAME_GMS);
      }
    }

    // Actual installation.
    boolean success = false;
    try {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Start to install %s on device %s with user id %s",
          apkName,
          deviceId,
          userId);
      if (forceQueryable) {
        extraArgs.add("--force-queryable");
      }
      if (bypassLowTargetSdkBlock
          && deviceSdkVersion > AndroidVersion.ANDROID_13.getEndSdkVersion()) {
        extraArgs.add("--bypass-low-target-sdk-block");
      }
      // If the package name is gms, ignore the grant runtime permissions switch.
      installApkHelper(
          buildUtilArgs(deviceId, userId, deviceSdkVersion),
          packageName,
          apkPath,
          dexMetadataPath,
          isGms || grantPermissions,
          forceNoStreaming,
          installArgs.installTimeout().orElse(null),
          log,
          extraArgs.toArray(new String[0]));
      success = true;
      SharedLogUtil.logMsg(
          logger, log, "Successfully installed %s on device %s", apkName, deviceId);
    } finally {
      if (Flags.instance().cacheInstalledApks.get()) {
        device.setProperty(
            String.format(DEVICE_PROP_INSTALLED_APK_KEY_TEMPLATE, userId) + packageName,
            success ? apkMd5 : null);
      }
    }

    if (isGms) {
      if (installArgs.sleepAfterInstallGms().isPresent()) {
        sleeper.sleep(installArgs.sleepAfterInstallGms().get());
      }
      updateGmsDimension(device, log);
    }

    if (clearAppData) {
      SharedLogUtil.logMsg(logger, log, "Clear app %s after installation", packageName);
      androidPackageManagerUtil.clearPackage(
          buildUtilArgs(deviceId, userId, deviceSdkVersion), packageName);
    }

    return packageName;
  }

  /**
   * Reinstalls the APK into the android device, even if same apk with the same MD5 and package name
   * installed before.
   *
   * <p>Logs will be appended to the test's info. Once installed, it will set the device property to
   * avoid installing the same apk again when flag {@link Flags#instance()}.{@link
   * Flags#cacheInstalledApks cacheInstalledApks} is set to true.
   *
   * <p>NOTE: Do NOT use this method and {@link #installApkIfVersionMismatched} both for one APK.
   *
   * @param device the device that the APK is installed to
   * @param installArgs arguments wrapper for apk installation
   * @return the package name of the apk
   */
  @CanIgnoreReturnValue
  public String installApkIfExist(
      Device device, ApkInstallArgs installArgs, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    return installApk(device, installArgs.withSkipIfCached(false), log);
  }

  /**
   * Installs the APK into the android device, only if there is no apk with the same MD5 and package
   * name installed before.
   *
   * <p>Logs will be appended to the test's info. Once installed, it will set the device property to
   * avoid installing the same apk again when flag {@link Flags#instance()}.{@link
   * Flags#cacheInstalledApks cacheInstalledApks} is set to true.
   *
   * <p>NOTE: Do NOT use this method and {@link #installApkIfVersionMismatched} both for one APK.
   *
   * @param device the device that the APK is installed to
   * @param installArgs arguments wrapper for apk installation
   * @return the package name of the apk
   */
  @CanIgnoreReturnValue
  public String installApkIfNotExist(
      Device device, ApkInstallArgs installArgs, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    return installApk(
        device, installArgs.withSkipIfCached(true).withSkipIfVersionMatch(false), log);
  }

  /**
   * Installs the APK to the android device, only if there is no APK with the same package name and
   * version code installed before. Logs will be appended to the test's info.
   *
   * <p>NOTE: Version code check is less strict than MD5 check, so this method should only be
   * invoked for installing stable APK whose version code is meaningful. Otherwise, please use
   * {@link #installApkIfNotExist(Device, ApkInstallArgs, LogCollector)} instead.
   *
   * <p>NOTE: Do NOT use this method and {@link #installApkIfNotExist(Device, ApkInstallArgs,
   * LogCollector)} both for one APK when flag {@link Flags#instance()}.{@link
   * Flags#cacheInstalledApks cacheInstalledApks} is set to true.
   *
   * <p>NOTE: Do NOT use this method to install GMS.
   *
   * @param device the device that the APK is installed to
   * @param installArgs arguments wrapper for apk installation
   * @param log log collector for apk installation
   * @return the package name of the apk
   */
  @CanIgnoreReturnValue
  public String installApkIfVersionMismatched(
      Device device, ApkInstallArgs installArgs, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    return installApk(
        device,
        installArgs.withSkipIfCached(false).withSkipIfVersionMatch(true).withSkipDowngrade(false),
        log);
  }

  /**
   * Installs multiple packages on the device. None of the packages should be GMS since GMS requires
   * additional operations. It doesn't check if the apps are already installed. Only applied to real
   * device with SDK version >= 29.
   *
   * @param deviceId device id of the device.
   * @param userId all the packages are installed to the same user. If not specified, installation
   *     is for all users.
   * @param deviceSdkVersion must be >= 29.
   * @param packageMap multimap from app package name to the corresponding list of app files.
   * @param grantPermissions whether to grant all runtime permissions.
   * @param forceNoStreaming if true, always pushes APK to device and invoke Package Manager as
   *     separate steps.
   * @param installTimeout the timeout of the whole installation. If null, will use the default
   *     timeout.
   * @param log log collector.
   * @param extraArgs extra arguments to the install-multi-package command.
   */
  public void installMultiNonGmsPackages(
      String deviceId,
      @Nullable String userId,
      int deviceSdkVersion,
      Multimap<String, String> packageMap,
      boolean grantPermissions,
      boolean forceNoStreaming,
      @Nullable Duration installTimeout,
      @Nullable LogCollector<?> log,
      String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    if (packageMap.containsKey(PackageConstants.PACKAGE_NAME_GMS)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_INSTALLER_APPLY_MULTI_PACKAGE_INSTALL_TO_GMS,
          "Don't use install-multi-package to install GMS. Please install GMS separately.");
    }
    if (deviceSdkVersion <= AndroidVersion.PI.getEndSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_INSTALLER_DEVICE_SDK_TOO_LOW,
          String.format(
              "The device sdk version %d is too low for install-multi-package. Please try other"
                  + " ways",
              deviceSdkVersion));
    }
    installMultiPackageHelper(
        buildUtilArgs(deviceId, userId == null ? MULTI_USER_DEFAULT_ID : userId, deviceSdkVersion),
        packageMap,
        grantPermissions,
        forceNoStreaming,
        packageMap.values().stream().anyMatch(f -> f.endsWith(APEX_SUFFIX)),
        installTimeout,
        log,
        extraArgs);
  }

  /**
   * Uninstalls the given package from the android device.
   *
   * <p>Logs will be appended to the test's info. If failed, log the message without throwing out
   * any exceptions. No matter the uninstallation is successful or not, the device property of the
   * package will always be cleaned, so force to reinstall the APK in future tests.
   *
   * @param device the Android device
   * @param packageName the package of the APK to uninstall
   * @param logFailures whether to log any failure during uninstallation
   * @param log log collector for APK uninstallation
   */
  public void uninstallApk(
      Device device, String packageName, boolean logFailures, @Nullable LogCollector<?> log)
      throws InterruptedException {
    String deviceId = device.getDeviceId();
    try {
      int deviceSdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
      // Get user id if specified in params.
      // For sdkVersion >= MULTI_USER_START_SDK_VERSION, current user.
      // For sdkVersion < MULTI_USER_START_SDK_VERSION, user "all".
      String userId = MULTI_USER_DEFAULT_ID;
      if (isMultiUserSupported(deviceSdkVersion)) {
        userId = getCurrentUser(deviceId, deviceSdkVersion).get();
      }
      uninstallApk(device, userId, deviceSdkVersion, packageName, logFailures, log);
    } catch (MobileHarnessException e) {
      if (logFailures) {
        SharedLogUtil.logMsg(
            logger, Level.WARNING, log, e, "Failed to get device %s sdk version.", deviceId);
      }
    }
  }

  /**
   * Uninstalls the given package from the android device.
   *
   * <p>Logs will be appended to the test's info. If failed, log the message without throwing out
   * any exceptions. No matter the uninstallation is successful or not, the device property of the
   * package will always be cleaned, so force to reinstall the APK in future tests.
   *
   * @param device the Android device
   * @param userId the user id to uninstall APK
   * @param packageName the package of the APK to uninstall
   * @param logFailures whether to log any failure during uninstallation
   * @param log log collector for APK uninstallation
   */
  public void uninstallApk(
      Device device,
      String userId,
      String packageName,
      boolean logFailures,
      @Nullable LogCollector<?> log)
      throws InterruptedException {
    try {
      int deviceSdkVersion = systemSettingUtil.getDeviceSdkVersion(device.getDeviceId());
      uninstallApk(device, userId, deviceSdkVersion, packageName, logFailures, log);
    } catch (MobileHarnessException e) {
      if (logFailures) {
        SharedLogUtil.logMsg(
            logger,
            Level.WARNING,
            log,
            e,
            "Failed to get device %s sdk version.",
            device.getDeviceId());
      }
    }
  }

  /**
   * Uninstalls the given package from the android device.
   *
   * <p>Logs will be appended to the test's info. If failed, log the message without throwing out
   * any exceptions. No matter the uninstallation is successful or not, the device property of the
   * package will always be cleaned, so force to reinstall the APK in future tests.
   *
   * @param device the Android device
   * @param userId the user id to uninstall APK
   * @param deviceSdkVersion the target device sdk version
   * @param packageName the package of the APK to uninstall
   * @param logFailures whether to log any failure during uninstallation
   * @param log log collector for APK uninstallation
   */
  private void uninstallApk(
      Device device,
      String userId,
      int deviceSdkVersion,
      String packageName,
      boolean logFailures,
      @Nullable LogCollector<?> log)
      throws InterruptedException {
    String deviceId = device.getDeviceId();
    try {
      SharedLogUtil.logMsg(logger, log, "Start to uninstall: %s for user: %s", packageName, userId);
      androidPackageManagerUtil.uninstallApk(
          buildUtilArgs(deviceId, userId, deviceSdkVersion), packageName);
      SharedLogUtil.logMsg(
          logger, log, "Successfully uninstalled: %s for user: %s", packageName, userId);

      if (packageName.equals(PackageConstants.PACKAGE_NAME_GMS)) {
        updateGmsDimension(device, log);
      }
    } catch (MobileHarnessException e) {
      if (logFailures) {
        SharedLogUtil.logMsg(
            logger,
            Level.WARNING,
            log,
            e,
            "Failed to uninstall package %s for user: %s with exception: %s",
            packageName,
            userId,
            e.getMessage());
      } else {
        SharedLogUtil.logMsg(logger, log, "Skip uninstalling %s", packageName);
      }
    } finally {
      if (Flags.instance().cacheInstalledApks.get()) {
        SharedLogUtil.logMsg(
            logger, log, "Clear device property for cached installed apk: %s", packageName);
        device.setProperty(
            String.format(DEVICE_PROP_INSTALLED_APK_KEY_TEMPLATE, userId) + packageName, null);
      }
    }
  }

  /**
   * Build UtilArgs according to user ID, and don't set userId if userId is equals to
   * MULTI_USER_DEFAULT_ID.
   */
  private static UtilArgs buildUtilArgs(String deviceId, String userId, int deviceSdkVersion) {
    UtilArgs.Builder utilArgsBuilder =
        UtilArgs.builder().setSerial(deviceId).setSdkVersion(deviceSdkVersion);
    return MULTI_USER_DEFAULT_ID.equals(userId)
        ? utilArgsBuilder.build()
        : utilArgsBuilder.setUserId(userId).build();
  }

  /** Checks whether the exact same apk has been installed before or not by comparing MD5s. */
  private boolean checkApkInstalledByMd5(
      Device device,
      int sdkVersion,
      String apkMd5,
      String apkName,
      String packageName,
      String userId,
      @Nullable LogCollector<?> log)
      throws InterruptedException {
    // Tries to get md5 from device property, if failed, calculates it from the installed apk file.
    String deviceId = device.getDeviceId();
    String installedApkMd5 = null;
    String propertyName =
        String.format(DEVICE_PROP_INSTALLED_APK_KEY_TEMPLATE, userId) + packageName;
    if (Flags.instance().cacheInstalledApks.get()) {
      installedApkMd5 = device.getProperty(propertyName);
      SharedLogUtil.logMsg(
          logger,
          log,
          "Package %s md5 retrieved from device %s property `%s` is: %s",
          packageName,
          deviceId,
          propertyName,
          installedApkMd5);
    }
    if (installedApkMd5 == null) {
      try {
        // Do not set user ID if the ID is default value.
        String apkInstalledPathOnDevice =
            androidPackageManagerUtil.getInstalledPath(
                buildUtilArgs(deviceId, userId, sdkVersion), packageName);
        installedApkMd5 = androidFileUtil.md5(deviceId, sdkVersion, apkInstalledPathOnDevice);
        SharedLogUtil.logMsg(
            logger,
            log,
            "Md5 for package %s installed on device %s for user %s with path %s is: %s",
            packageName,
            deviceId,
            userId,
            apkInstalledPathOnDevice,
            installedApkMd5);
      } catch (MobileHarnessException e) {
        // Doesn't need to handle the failure of getting md5 from installed apk.
      }
    }

    // Skips installation if MD5s match.
    if (apkMd5.equals(installedApkMd5)) {
      if (Flags.instance().cacheInstalledApks.get()
          && Strings.isNullOrEmpty(device.getProperty(propertyName))) {
        device.setProperty(propertyName, apkMd5);
      }
      SharedLogUtil.logMsg(
          logger, log, "Skip installing %s which has been installed before", apkName);
      return true;
    } else {
      return false;
    }
  }

  /** Makes sure the device version >= min version required by the apk. */
  private void checkApkMinSdkVersion(
      int deviceSdkVersion, String apkPath, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String apkName = new File(apkPath).getName();
    int apkMinSdkVersion = aapt.getApkMinSdkVersion(apkPath);
    SharedLogUtil.logMsg(
        logger,
        log,
        "Min sdk version of %s: %s",
        apkName,
        apkMinSdkVersion > 0 ? apkMinSdkVersion : (apkMinSdkVersion < 0 ? "unknown" : "not set"));
    if (deviceSdkVersion < apkMinSdkVersion) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_INSTALLER_DEVICE_SDK_TOO_LOW,
          String.format(
              "Failed to install %s because device sdk version(%s) "
                  + "< required min sdk version(%s) of the apk",
              apkName, deviceSdkVersion, apkMinSdkVersion));
    }
  }

  /**
   * Checks GmsCore compatibility.
   *
   * @throws MobileHarnessException if GmsCore apk is incompatible with device, or an error occurs.
   * @throws InterruptedException if current thread is interrupted during this method
   */
  @VisibleForTesting
  void checkGmsCompatibility(
      Device device,
      int deviceSdkVersion,
      String apkPath,
      int apkVersionCode,
      String apkVersionName,
      @Nullable LogCollector<?> log) {
    return;
  }

  /* Get current running user or return user 0 if failed. */
  private Optional<String> getCurrentUser(String deviceId, int deviceSdkVersion)
      throws InterruptedException {
    // Try to get current running user or null.
    try {
      if (isMultiUserSupported(deviceSdkVersion)) {
        return Optional.of(
            Integer.toString(androidUserUtil.getCurrentUser(deviceId, deviceSdkVersion)));
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to get current user ID with exception %s", e.getMessage());
    }
    return Optional.of("0");
  }

  /**
   * Helper method for installing apk on device, which includes retry and special cases management.
   */
  private void installApkHelper(
      UtilArgs utilArgs,
      String packageName,
      String apkPath,
      @Nullable String dexMetadataPath,
      boolean grantPermissions,
      boolean forceNoStreaming,
      @Nullable Duration installTimeout,
      @Nullable LogCollector<?> log,
      String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    String serial = utilArgs.serial();
    boolean installThrowsException = false;
    String installExceptionMsg = "";
    try {
      androidPackageManagerUtil.installApk(
          utilArgs,
          apkPath,
          dexMetadataPath,
          grantPermissions,
          forceNoStreaming,
          installTimeout,
          extraArgs);
    } catch (MobileHarnessException e) {
      installThrowsException = true;
      installExceptionMsg = e.getMessage();
      SharedLogUtil.logMsg(
          logger,
          Level.WARNING,
          log,
          e,
          "First attempt to install apk %s on device %s failed",
          apkPath,
          serial);
    }
    if (!installThrowsException) {
      return;
    }

    if (installExceptionMsg.contains(INSTALL_VERSION_DOWNGRADE_ERROR)
        || installExceptionMsg.contains(INSTALL_FAILED_UPDATE_INCOMPATIBLE)) {
      try {
        androidPackageManagerUtil.uninstallApk(serial, packageName);
      } catch (MobileHarnessException e) {
        SharedLogUtil.logMsg(
            logger,
            Level.WARNING,
            log,
            e,
            "Failed to uninstall package %s on device %s due to error:%n%s%nPrior installation "
                + "error:%n%s",
            packageName,
            serial,
            e.getMessage(),
            installExceptionMsg);
      }
    }
    // Retry to install the apk again
    SharedLogUtil.logMsg(logger, log, "Retry to install apk %s on device %s...", apkPath, serial);
    androidPackageManagerUtil.installApk(
        utilArgs,
        apkPath,
        dexMetadataPath,
        grantPermissions,
        forceNoStreaming,
        installTimeout,
        extraArgs);
  }

  /** Helper method for installing multiple packages on device. */
  private void installMultiPackageHelper(
      UtilArgs utilArgs,
      Multimap<String, String> packageMap,
      boolean grantPermissions,
      boolean forceNoStreaming,
      boolean hasApex,
      @Nullable Duration installTimeout,
      @Nullable LogCollector<?> log,
      String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    SharedLogUtil.logMsg(
        logger, log, "Start to install multiple packages to device %s...", utilArgs.serial());
    InstallCmdArgs.Builder installCmdArgs =
        InstallCmdArgs.builder()
            .setReplaceExistingApp(true)
            .setGrantPermissions(grantPermissions)
            .setForceNoStreaming(forceNoStreaming);
    if (extraArgs.length > 0) {
      installCmdArgs.setExtraArgs(ImmutableList.copyOf(extraArgs));
    }
    androidPackageManagerUtil.installMultiPackage(
        utilArgs,
        installCmdArgs.build(),
        packageMap,
        hasApex ? WAIT_FOR_STAGED_SESSION_READY_TIMEOUT : null,
        installTimeout);
    SharedLogUtil.logMsg(
        logger,
        log,
        "Complete the install of multiple packages to device %s...",
        utilArgs.serial());
  }

  private static boolean isMultiUserSupported(int sdkVersion) {
    return sdkVersion >= MULTI_USER_START_SDK_VERSION;
  }

  /** Check if apk should be skipped for installing according to apk version code. */
  private boolean shouldSkipOnVersionCode(
      UtilArgs utilArgs,
      String packageName,
      int targetVersionCode,
      boolean skipIfDowngrade,
      boolean skipIfVersionMatch,
      @Nullable LogCollector<?> log)
      throws InterruptedException {
    Integer deviceAppVersionCode = null;
    String deviceId = utilArgs.serial();

    // Skip check apk version code if both flags are false.
    if (!skipIfDowngrade && !skipIfVersionMatch) {
      return false;
    }

    try {
      // Check if package is installed for user.
      String unused = androidPackageManagerUtil.getInstalledPath(utilArgs, packageName);
      deviceAppVersionCode = androidPackageManagerUtil.getAppVersionCode(deviceId, packageName);
    } catch (MobileHarnessException e) {
      // Nexus One/DroidX(2.3) may failed to get the version info of the GmsCore on the device.
      SharedLogUtil.logMsg(
          logger,
          Level.WARNING,
          log,
          e,
          "Skip checking app version code because package (%s) info not found on device %s:%n%s",
          packageName,
          deviceId,
          e.getMessage());
    }
    if (deviceAppVersionCode != null) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Original app (%s) version on device %s: %d%nBeing installed apk version: %d",
          packageName,
          deviceId,
          deviceAppVersionCode,
          targetVersionCode);
      if (skipIfDowngrade && (deviceAppVersionCode > targetVersionCode)) {
        return true;
      }
      if (skipIfVersionMatch && (deviceAppVersionCode == targetVersionCode)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Update device dimension "gms_version" after Gmscore APK installation/uninstallation.
   *
   * @return {@code True} only if dimension update successfully
   */
  @CanIgnoreReturnValue
  private boolean updateGmsDimension(Device device, @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    try {
      String version =
          androidPackageManagerUtil.getAppVersionName(deviceId, PackageConstants.PACKAGE_NAME_GMS);
      if ((version != null) && device.updateDimension(Dimension.Name.GMS_VERSION, version)) {
        SharedLogUtil.logMsg(
            logger,
            log,
            "Update Dimension %s to %s",
            Ascii.toLowerCase(Dimension.Name.GMS_VERSION.name()),
            version);
        return true;
      }
    } catch (MobileHarnessException e) {
      SharedLogUtil.logMsg(
          logger,
          Level.WARNING,
          log,
          e,
          "%s version info not found. Dimension not up to date: %s",
          PackageConstants.PACKAGE_NAME_GMS,
          e.getMessage());
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_APK_INSTALLER_UPDATE_DIMENSION_ERROR, e.getMessage(), e);
    }

    return false;
  }
}
