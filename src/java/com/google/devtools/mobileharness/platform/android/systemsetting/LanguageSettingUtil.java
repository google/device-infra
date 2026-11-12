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

package com.google.devtools.mobileharness.platform.android.systemsetting;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.InstallCmdArgs;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidSettings;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil.AppOpsMode;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.android.AndroidPackages;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.log.LogCollector;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Utility for switching Android device language and country/region via the bundled {@code
 * switchlanguage.apk} instrumentation tool.
 */
public class LanguageSettingUtil {

  /** Default maximum period to wait for the language switch signal in device logcat. */
  public static final Duration DEFAULT_LOG_SIGNAL_TIMEOUT = Duration.ofSeconds(30);

  /** Max attempts to install and grant permission for the switch language APK. */
  @VisibleForTesting static final int MAX_ATTEMPTS = 2;

  /** Resource path of the switch language app packed in the jar. */
  @VisibleForTesting
  static final String SWITCH_LANGUAGE_APK_RES_PATH =
      "/com/google/wireless/qa/mobileharness/tool/android/language/switchlanguage.apk";

  /** Extra permission name of the switch language app. */
  @VisibleForTesting
  static final String SWITCH_LANGUAGE_PKG_EXTRA_PERMISSION =
      "android.permission.CHANGE_CONFIGURATION";

  /** Extra argument key for SDK version when instrumenting the switch language app. */
  @VisibleForTesting static final String EXTRA_NAME_SDK_VERSION = "sdk_version";

  /** Extra argument key for target language when instrumenting the switch language app. */
  @VisibleForTesting static final String EXTRA_NAME_LANGUAGE = "language";

  /** Extra argument key for target country when instrumenting the switch language app. */
  @VisibleForTesting static final String EXTRA_NAME_COUNTRY = "country";

  @VisibleForTesting static final String LOG_UNKNOWN_PACKAGE = "Unknown package";

  /** Target signal printed in logcat upon successful language switch. */
  @VisibleForTesting static final String LOG_SIGNAL = "Successfully switch language";

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration INSTRUMENTATION_TIMEOUT = Duration.ofMinutes(1);
  private static final Duration RETRY_SIGNAL_SLEEP = Duration.ofMillis(500);
  private static final Duration GRANT_PERMISSION_SETTLE_SLEEP = Duration.ofSeconds(3);
  private static final Duration APPOPS_SETTLE_SLEEP = Duration.ofSeconds(1);

  /** Snapshot of the device's original locale used to restore language settings after a test. */
  public record LocaleRestoreState(
      @Nullable String originalLanguage,
      @Nullable String originalCountry,
      int sdkVersion,
      boolean restoreRegion) {}

  private final ResUtil resUtil;
  private final SystemSettingManager systemSettingManager;
  private final ApkInstaller apkInstaller;
  private final AndroidInstrumentationUtil instrumentationUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidUserUtil androidUserUtil;
  private final AndroidAdbUtil adbUtil;
  private final Sleeper sleeper;

  @Inject
  LanguageSettingUtil(
      ResUtil resUtil,
      SystemSettingManager systemSettingManager,
      ApkInstaller apkInstaller,
      AndroidInstrumentationUtil instrumentationUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidUserUtil androidUserUtil,
      AndroidAdbUtil adbUtil,
      Sleeper sleeper) {
    this.resUtil = resUtil;
    this.systemSettingManager = systemSettingManager;
    this.apkInstaller = apkInstaller;
    this.instrumentationUtil = instrumentationUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidUserUtil = androidUserUtil;
    this.adbUtil = adbUtil;
    this.sleeper = sleeper;
  }

  /**
   * Switches the language and optional country/region on the specified device.
   *
   * <p>Skips switching if the device is already configured with the requested language and country.
   */
  public void switchLanguage(String deviceId, String language, @Nullable String country)
      throws MobileHarnessException, InterruptedException {
    Preconditions.checkArgument(
        !StrUtil.isEmptyOrWhitespace(language), "Language must be provided.");
    String originalLanguage = adbUtil.getProperty(deviceId, AndroidProperty.LANGUAGE);
    String originalCountry = adbUtil.getProperty(deviceId, AndroidProperty.REGION);
    if (isTargetLocaleAlreadySet(language, country, originalLanguage, originalCountry)) {
      logger.atInfo().log(
          "Device language is already %s_%s. Skipping language switch.",
          language, Strings.nullToEmpty(country));
      return;
    }

    logger.atInfo().log(
        "Device locale is [%s_%s]. Target is [%s_%s]. Switching language...",
        originalLanguage, originalCountry, language, Strings.nullToEmpty(country));
    int sdkVersion = androidSystemSettingUtil.getDeviceSdkVersion(deviceId);
    performSwitch(
        deviceId,
        sdkVersion,
        createDeviceIdApkOperator(deviceId, sdkVersion),
        language,
        country,
        DEFAULT_LOG_SIGNAL_TIMEOUT,
        /* log= */ null);
  }

  /**
   * Switches the language and optional country/region on a managed {@link Device}, returning a
   * {@link LocaleRestoreState} if a switch was performed or {@link Optional#empty()} if skipped.
   */
  @CanIgnoreReturnValue
  public Optional<LocaleRestoreState> switchLanguage(
      Device device,
      String language,
      @Nullable String country,
      Duration logSignalTimeout,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    String originalLanguage = adbUtil.getProperty(deviceId, AndroidProperty.LANGUAGE);
    String originalCountry = adbUtil.getProperty(deviceId, AndroidProperty.REGION);
    if (isTargetLocaleAlreadySet(language, country, originalLanguage, originalCountry)) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Device language is already %s_%s. Skipping language switch setup.",
          language,
          Strings.nullToEmpty(country));
      return Optional.empty();
    }

    int sdkVersion = systemSettingManager.getDeviceSdkVersion(device);
    SharedLogUtil.logMsg(
        logger,
        log,
        "Device locale is [%s_%s]. Target is [%s_%s]. Switching language...",
        originalLanguage,
        originalCountry,
        language,
        Strings.nullToEmpty(country));
    boolean restoreRegion = !StrUtil.isEmptyOrWhitespace(country);
    performSwitch(
        deviceId,
        sdkVersion,
        createDeviceApkOperator(device, log),
        language,
        country,
        logSignalTimeout,
        log);
    return Optional.of(
        new LocaleRestoreState(originalLanguage, originalCountry, sdkVersion, restoreRegion));
  }

  /** Restores the device's original locale captured in {@link LocaleRestoreState}. */
  public void restoreLanguage(
      String deviceId,
      LocaleRestoreState restoreState,
      Duration logSignalTimeout,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String originalLanguage = restoreState.originalLanguage();
    if (StrUtil.isEmptyOrWhitespace(originalLanguage)) {
      return;
    }
    String originalCountry = restoreState.originalCountry();
    runSwitchInstrumentation(
        deviceId,
        restoreState.sdkVersion(),
        originalLanguage,
        originalCountry,
        logSignalTimeout,
        log);
    if (restoreState.restoreRegion()) {
      setPropertiesIgnoringErrors(
          deviceId,
          AndroidProperty.REGION.getPropertyKeys(),
          Strings.nullToEmpty(originalCountry),
          log);
    }
    updateLocaleProperties(deviceId, originalLanguage, originalCountry, log);
  }

  private static boolean isTargetLocaleAlreadySet(
      String language,
      @Nullable String country,
      @Nullable String originalLanguage,
      @Nullable String originalCountry) {
    return Ascii.equalsIgnoreCase(language, Strings.nullToEmpty(originalLanguage))
        && (Strings.isNullOrEmpty(country)
            || Ascii.equalsIgnoreCase(country, Strings.nullToEmpty(originalCountry)));
  }

  private void performSwitch(
      String deviceId,
      int sdkVersion,
      ApkOperator apkOperator,
      String language,
      @Nullable String country,
      Duration logSignalTimeout,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    int currentUserId = sdkVersion >= 23 ? androidUserUtil.getCurrentUser(deviceId, sdkVersion) : 0;
    String apkPath =
        resUtil.getResourceFile(LanguageSettingUtil.class, SWITCH_LANGUAGE_APK_RES_PATH);
    installApkAndGrantPermission(deviceId, sdkVersion, currentUserId, apkPath, apkOperator, log);
    switchLocaleWithRetry(deviceId, sdkVersion, language, country, logSignalTimeout, log);
  }

  private void installApkAndGrantPermission(
      String deviceId,
      int sdkVersion,
      int currentUserId,
      String apkPath,
      ApkOperator apkOperator,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String packageName = AndroidPackages.MH_SWITCH_LANGUAGE.getPackageName();
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      apkOperator.install(apkPath, currentUserId);
      if (sdkVersion < 17) {
        return;
      }

      // Needed, otherwise setting appops permission success is flaky while the command still
      // exits without an error (b/505022628#comment16).
      sleeper.sleep(GRANT_PERMISSION_SETTLE_SLEEP);

      try {
        grantPermissions(deviceId, sdkVersion, currentUserId, packageName);
        return;
      } catch (MobileHarnessException e) {
        if (!isRetryableGrantError(e, attempt)) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_GRANT_PERMISSION_ERROR,
              e.getMessage(),
              e);
        }
        // "pm grant" may fail to find the package even after installation; uninstall and retry
        // (b/110907264).
        SharedLogUtil.logMsg(
            logger,
            log,
            "Failed to grant permission to package %s, uninstall and retry",
            packageName);
        apkOperator.uninstall(packageName, currentUserId);
      }
    }
  }

  private static boolean isRetryableGrantError(MobileHarnessException err, int attempt) {
    return attempt < MAX_ATTEMPTS
        && err.getErrorId() == AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GRANT_PERMISSION_ERROR
        && Strings.nullToEmpty(err.getMessage()).contains(LOG_UNKNOWN_PACKAGE);
  }

  private void grantPermissions(
      String deviceId, int sdkVersion, int currentUserId, String packageName)
      throws MobileHarnessException, InterruptedException {
    UtilArgs utilArgs = buildUtilArgs(deviceId, sdkVersion, currentUserId);
    androidPackageManagerUtil.grantPermission(
        utilArgs, packageName, SWITCH_LANGUAGE_PKG_EXTRA_PERMISSION);
    var unused =
        adbUtil.settings(
            utilArgs,
            AndroidSettings.Spec.create(
                AndroidSettings.Command.PUT,
                AndroidSettings.NameSpace.GLOBAL,
                "hidden_api_blacklist_exemptions \"\\*\"")); // InclusiveLanguage:ignore
    if (sdkVersion >= 23) {
      androidSystemSettingUtil.setAppOpsPermission(
          deviceId, packageName, "WRITE_SETTINGS", AppOpsMode.ALLOW, String.valueOf(currentUserId));
      sleeper.sleep(APPOPS_SETTLE_SLEEP);
    }
  }

  private void switchLocaleWithRetry(
      String deviceId,
      int sdkVersion,
      String language,
      @Nullable String country,
      Duration logSignalTimeout,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    if (StrUtil.isEmptyOrWhitespace(language)) {
      return;
    }
    try {
      runSwitchInstrumentation(deviceId, sdkVersion, language, country, logSignalTimeout, log);
    } catch (MobileHarnessException e) {
      if (e.getErrorId()
          != AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_WAIT_SIGNAL_IN_LOGCAT_TIMEOUT) {
        throw e;
      }
      // Sleep for 500 ms to avoid race condition (b/320747279).
      sleeper.sleep(RETRY_SIGNAL_SLEEP);
      runSwitchInstrumentation(deviceId, sdkVersion, language, country, logSignalTimeout, log);
    }
    if (!StrUtil.isEmptyOrWhitespace(country)) {
      setPropertiesIgnoringErrors(deviceId, AndroidProperty.REGION.getPropertyKeys(), country, log);
    }
    updateLocaleProperties(deviceId, language, country, log);
  }

  private void runSwitchInstrumentation(
      String deviceId,
      int sdkVersion,
      String language,
      @Nullable String country,
      Duration timeout,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    ImmutableMap.Builder<String, String> extras = ImmutableMap.builder();
    extras.put(EXTRA_NAME_SDK_VERSION, String.valueOf(sdkVersion));
    extras.put(EXTRA_NAME_LANGUAGE, language);
    if (!StrUtil.isEmptyOrWhitespace(country)) {
      extras.put(EXTRA_NAME_COUNTRY, country);
    }

    String packageName = AndroidPackages.MH_SWITCH_LANGUAGE.getPackageName();
    String instrumentOutput =
        instrumentationUtil.instrument(
            deviceId,
            sdkVersion,
            AndroidInstrumentationSetting.create(
                packageName,
                AndroidPackages.MH_SWITCH_LANGUAGE.getActivityName().get(),
                /* className= */ null,
                extras.buildOrThrow(),
                /* async= */ false,
                /* showRawResults= */ false,
                /* prefixAndroidTest= */ false,
                /* noIsolatedStorage= */ false,
                /* useTestStorageService= */ false,
                /* enableCoverage= */ false),
            INSTRUMENTATION_TIMEOUT);
    try {
      adbUtil.waitForSignalInLog(deviceId, LOG_SIGNAL, timeout);
    } catch (MobileHarnessException e) {
      String dumpsysAppopsOutput =
          adbUtil.dumpSys(deviceId, DumpSysType.APPOPS, "--package", packageName);
      String dumpsysPackageOutput = adbUtil.dumpSys(deviceId, DumpSysType.PACKAGE, packageName);
      SharedLogUtil.logMsg(logger, log, "dumpsys appsops output: %s", dumpsysAppopsOutput);
      SharedLogUtil.logMsg(logger, log, "dumpsys package output: %s", dumpsysPackageOutput);
      String errorMessage =
          String.format("%s.\n The instrument output is: %s.", e.getMessage(), instrumentOutput);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_WAIT_SIGNAL_IN_LOGCAT_TIMEOUT,
          errorMessage,
          e);
    }
  }

  private void updateLocaleProperties(
      String deviceId, String language, @Nullable String country, @Nullable LogCollector<?> log)
      throws InterruptedException {
    setPropertiesIgnoringErrors(
        deviceId, AndroidProperty.LANGUAGE.getPropertyKeys(), language, log);
    Locale locale =
        StrUtil.isEmptyOrWhitespace(country)
            ? Locale.forLanguageTag(language.replace('_', '-'))
            : Locale.forLanguageTag(language.replace('_', '-') + "-" + country.trim());
    setPropertiesIgnoringErrors(
        deviceId, AndroidProperty.LOCALE.getPropertyKeys(), locale.toLanguageTag(), log);
  }

  private void setPropertiesIgnoringErrors(
      String deviceId,
      ImmutableList<String> properties,
      String value,
      @Nullable LogCollector<?> log)
      throws InterruptedException {
    for (String property : properties) {
      try {
        adbUtil.setProperty(deviceId, property, value);
      } catch (MobileHarnessException e) {
        SharedLogUtil.logMsg(logger, log, "%s", e.getMessage());
      }
    }
  }

  private interface ApkOperator {
    void install(String apkPath, int currentUserId)
        throws MobileHarnessException, InterruptedException;

    void uninstall(String packageName, int currentUserId) throws InterruptedException;
  }

  private ApkOperator createDeviceApkOperator(Device device, @Nullable LogCollector<?> log) {
    return new ApkOperator() {
      @Override
      public void install(String apkPath, int currentUserId)
          throws MobileHarnessException, InterruptedException {
        apkInstaller.installApkIfNotExist(
            device,
            ApkInstallArgs.builder().setApkPath(apkPath).setBypassLowTargetSdkBlock(true).build(),
            log);
      }

      @Override
      public void uninstall(String packageName, int currentUserId) throws InterruptedException {
        apkInstaller.uninstallApk(device, packageName, /* logFailures= */ true, log);
      }
    };
  }

  private ApkOperator createDeviceIdApkOperator(String deviceId, int sdkVersion) {
    return new ApkOperator() {
      @Override
      public void install(String apkPath, int currentUserId)
          throws MobileHarnessException, InterruptedException {
        androidPackageManagerUtil.installPackage(
            buildUtilArgs(deviceId, sdkVersion, currentUserId),
            InstallCmdArgs.builder()
                .setReplaceExistingApp(true)
                .setAllowTestPackages(true)
                .setAllowVersionCodeDowngrade(true)
                .setGrantPermissions(true)
                .setBypassLowTargetSdkBlock(true)
                .build(),
            AndroidPackages.MH_SWITCH_LANGUAGE.getPackageName(),
            ImmutableList.of(apkPath),
            /* isRemoteInstall= */ false,
            /* installTimeout= */ null);
      }

      @Override
      public void uninstall(String packageName, int currentUserId) throws InterruptedException {
        try {
          androidPackageManagerUtil.uninstallApk(
              buildUtilArgs(deviceId, sdkVersion, currentUserId), packageName);
        } catch (MobileHarnessException e) {
          SharedLogUtil.logMsg(
              logger,
              Level.WARNING,
              /* log= */ null,
              e,
              "Failed to uninstall package %s on device %s",
              packageName,
              deviceId);
        }
      }
    };
  }

  private static UtilArgs buildUtilArgs(String deviceId, int sdkVersion, int currentUserId) {
    UtilArgs.Builder builder = UtilArgs.builder().setSerial(deviceId).setSdkVersion(sdkVersion);
    if (sdkVersion >= AndroidVersion.NOUGAT.getStartSdkVersion()) {
      builder.setUserId(String.valueOf(currentUserId));
    }
    return builder.build();
  }
}
