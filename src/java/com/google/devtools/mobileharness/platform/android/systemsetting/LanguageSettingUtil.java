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
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceInfo;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.shared.SharedLogUtil;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;

/** Utility for setting device language and country. */
public class LanguageSettingUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Default maximum period to wait for the language switch signal in device logcat. */
  public static final Duration DEFAULT_LOG_SIGNAL_TIMEOUT = Duration.ofSeconds(30);

  /** Max attempts to install and grant permission for APK */
  @VisibleForTesting static final int MAX_ATTEMPTS = 2;

  /** Resource path of the switch language app which is packed in the lab server jar. */
  @VisibleForTesting
  static final String SWITCH_LANGUAGE_APK_RES_PATH =
      "/com/google/wireless/qa/mobileharness/tool/android/language/switchlanguage.apk";

  /** Extra permission name of the switch language app. */
  @VisibleForTesting
  static final String SWITCH_LANGUAGE_PKG_EXTRA_PERMISSION =
      "android.permission.CHANGE_CONFIGURATION";

  /** The sdkVersion of the extra for switching language. */
  @VisibleForTesting static final String EXTRA_NAME_SDK_VERSION = "sdk_version";

  /** The name of the extra for switching language. */
  @VisibleForTesting static final String EXTRA_NAME_LANGUAGE = "language";

  /** The name of the extra for switching language. */
  @VisibleForTesting static final String EXTRA_NAME_COUNTRY = "country";

  @VisibleForTesting static final String LOG_UNKNOWN_PACKAGE = "Unknown package";

  /** The target signal to search in the device log. */
  @VisibleForTesting static final String LOG_SIGNAL = "Successfully switch language";

  /** Snapshot of the device's original locale used to restore language settings after a test. */
  public record LocaleRestoreState(
      @Nullable String originalLanguage,
      @Nullable String originalCountry,
      int sdkVersion,
      boolean restoreRegion) {}

  /** Utility class for getting the switch language app from jar package. */
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

  /** Switches the language and optional country/region on the specified device. */
  public void switchLanguage(DeviceInfo deviceInfo, String language, @Nullable String country)
      throws MobileHarnessException, InterruptedException {
    Preconditions.checkNotNull(deviceInfo);
    Preconditions.checkArgument(
        !StrUtil.isEmptyOrWhitespace(language), "Language must be provided.");
    switchLanguageInternal(
        /* device= */ null,
        deviceInfo,
        deviceInfo.deviceId().controlId(),
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
    return switchLanguageInternal(
        device,
        /* deviceInfo= */ null,
        device.getDeviceId(),
        language,
        country,
        logSignalTimeout,
        log);
  }

  @CanIgnoreReturnValue
  private Optional<LocaleRestoreState> switchLanguageInternal(
      @Nullable Device device,
      @Nullable DeviceInfo deviceInfo,
      String deviceId,
      String language,
      @Nullable String country,
      Duration logSignalTimeout,
      @Nullable LogCollector<?> log)
      throws MobileHarnessException, InterruptedException {
    String originalLanguage = adbUtil.getProperty(deviceId, AndroidProperty.LANGUAGE);
    String originalCountry = adbUtil.getProperty(deviceId, AndroidProperty.REGION);

    boolean skipSwitch =
        Ascii.equalsIgnoreCase(language, Strings.nullToEmpty(originalLanguage))
            && (Strings.isNullOrEmpty(country)
                || Ascii.equalsIgnoreCase(country, Strings.nullToEmpty(originalCountry)));

    if (skipSwitch) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Device language is already %s_%s. Skipping language switch setup.",
          language,
          Strings.nullToEmpty(country));
      return Optional.empty();
    }

    int sdkVersion =
        device != null
            ? systemSettingManager.getDeviceSdkVersion(device)
            : androidSystemSettingUtil.getDeviceSdkVersion(deviceId);

    SharedLogUtil.logMsg(
        logger,
        log,
        "Device locale is [%s_%s]. Target is [%s_%s]. Switching language...",
        originalLanguage,
        originalCountry,
        language,
        Strings.nullToEmpty(country));

    boolean switchRegion = !StrUtil.isEmptyOrWhitespace(country);

    // Gets the resource file, installs the apk and gets the permission.
    String apkPath = resUtil.getResourceFile(getClass(), SWITCH_LANGUAGE_APK_RES_PATH);
    installApkAndGrantPermission(log, device, deviceInfo, deviceId, sdkVersion, apkPath);

    // Switch the language and country.
    try {
      switchLocale(log, sdkVersion, deviceId, language, country, switchRegion, logSignalTimeout);
    } catch (MobileHarnessException e) {
      if (e.getErrorId()
          == AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_WAIT_SIGNAL_IN_LOGCAT_TIMEOUT) {
        // Sleep for 500 ms to avoid race condition. Context: b/320747279
        sleeper.sleep(Duration.ofMillis(500));
        switchLocale(log, sdkVersion, deviceId, language, country, switchRegion, logSignalTimeout);
      } else {
        throw e;
      }
    }
    return Optional.of(
        new LocaleRestoreState(originalLanguage, originalCountry, sdkVersion, switchRegion));
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
    switchLocale(
        log,
        restoreState.sdkVersion(),
        deviceId,
        originalLanguage,
        restoreState.originalCountry(),
        restoreState.restoreRegion(),
        logSignalTimeout);
  }

  private void installApkAndGrantPermission(
      @Nullable LogCollector<?> log,
      @Nullable Device device,
      @Nullable DeviceInfo deviceInfo,
      String deviceId,
      int sdkVersion,
      String apkPath)
      throws MobileHarnessException, InterruptedException {
    String packageName = AndroidPackages.MH_SWITCH_LANGUAGE.getPackageName();
    var currentUserId = sdkVersion >= 23 ? androidUserUtil.getCurrentUser(deviceId, sdkVersion) : 0;
    ApkInstallArgs installArgs =
        ApkInstallArgs.builder().addApkPaths(apkPath).setBypassLowTargetSdkBlock(true).build();

    for (int i = 1; i <= MAX_ATTEMPTS; i++) {
      if (device != null) {
        apkInstaller.installApkIfNotExist(device, installArgs, log);
      } else {
        apkInstaller.installApkIfNotExist(Preconditions.checkNotNull(deviceInfo), installArgs);
      }
      if (sdkVersion < 17) {
        break;
      }

      // Needed, otherwise setting appops permission success is flaky but the command still
      // succeeds without an error (b/505022628#comment16).
      sleeper.sleep(Duration.ofSeconds(3));

      // In API >= 17, an explicitly permission grant to change config is needed via pm
      // since this permission is for system/dev only.
      try {
        UtilArgs.Builder utilArgsBuilder =
            UtilArgs.builder().setSerial(deviceId).setSdkVersion(sdkVersion);
        if (sdkVersion >= AndroidVersion.NOUGAT.getStartSdkVersion()) {
          utilArgsBuilder.setUserId(String.valueOf(currentUserId));
        }
        UtilArgs utilArgs = utilArgsBuilder.build();

        androidPackageManagerUtil.grantPermission(
            utilArgs, packageName, SWITCH_LANGUAGE_PKG_EXTRA_PERMISSION);
        adbUtil.settings(
            utilArgs,
            AndroidSettings.Spec.create(
                AndroidSettings.Command.PUT,
                AndroidSettings.NameSpace.GLOBAL,
                "hidden_api_blacklist_exemptions \"\\*\"")); // InclusiveLanguage:ignore
        if (sdkVersion >= 23) {
          androidSystemSettingUtil.setAppOpsPermission(
              deviceId,
              packageName,
              "WRITE_SETTINGS",
              AppOpsMode.ALLOW,
              String.valueOf(currentUserId));
          sleeper.sleep(Duration.ofSeconds(1));
        }
        break;
      } catch (MobileHarnessException e) {
        // "pm grant" may failed to find the package even it has already installed on device.
        // uninstall the apk and try again. See b/110907264 for more detail.
        if (e.getErrorId() == AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GRANT_PERMISSION_ERROR
            && e.getMessage().contains(LOG_UNKNOWN_PACKAGE)
            && i < MAX_ATTEMPTS) {
          SharedLogUtil.logMsg(
              logger,
              log,
              "Failed to grant permission to package %s, uninstall and retry",
              packageName);
          if (device != null) {
            apkInstaller.uninstallApk(device, packageName, /* logFailures= */ true, log);
          } else {
            apkInstaller.uninstallApk(
                Preconditions.checkNotNull(deviceInfo), packageName, /* logFailures= */ true);
          }
        } else {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_GRANT_PERMISSION_ERROR,
              e.getMessage(),
              e);
        }
      }
    }
  }

  private void switchLocale(
      @Nullable LogCollector<?> log,
      int sdkVersion,
      String deviceId,
      String language,
      @Nullable String country,
      boolean switchRegion,
      Duration timeout)
      throws MobileHarnessException, InterruptedException {

    if (StrUtil.isEmptyOrWhitespace(language)) {
      // Don't change if language isn't valid.
      return;
    }

    String sdkVersionStr = String.valueOf(sdkVersion);
    Map<String, String> extras = new HashMap<>(3);
    if (!StrUtil.isEmptyOrWhitespace(sdkVersionStr)) {
      extras.put(EXTRA_NAME_SDK_VERSION, sdkVersionStr);
    }
    if (!StrUtil.isEmptyOrWhitespace(language)) {
      extras.put(EXTRA_NAME_LANGUAGE, language);
    }
    if (!StrUtil.isEmptyOrWhitespace(country)) {
      extras.put(EXTRA_NAME_COUNTRY, country);
    }

    // Starts the instrumentation.
    String instrumentOutput =
        instrumentationUtil.instrument(
            deviceId,
            sdkVersion,
            AndroidInstrumentationSetting.create(
                AndroidPackages.MH_SWITCH_LANGUAGE.getPackageName(),
                AndroidPackages.MH_SWITCH_LANGUAGE.getActivityName().get(),
                /* className= */ null,
                extras,
                /* async= */ false,
                /* showRawResults= */ false,
                /* prefixAndroidTest= */ false,
                /* noIsolatedStorage= */ false,
                /* useTestStorageService= */ false,
                /* enableCoverage= */ false),
            /* timeout= */ Duration.ofMinutes(1));
    try {
      adbUtil.waitForSignalInLog(deviceId, LOG_SIGNAL, timeout);
    } catch (MobileHarnessException e) {
      var dumpsysAppsopsOutput =
          adbUtil.dumpSys(
              deviceId,
              DumpSysType.APPOPS,
              "--package",
              AndroidPackages.MH_SWITCH_LANGUAGE.getPackageName());
      var dumpsysPackageOutput =
          adbUtil.dumpSys(
              deviceId, DumpSysType.PACKAGE, AndroidPackages.MH_SWITCH_LANGUAGE.getPackageName());
      SharedLogUtil.logMsg(logger, log, "dumpsys appsops output: %s", dumpsysAppsopsOutput);
      SharedLogUtil.logMsg(logger, log, "dumpsys package output: %s", dumpsysPackageOutput);
      String errorMessage =
          String.format("%s.\n The instrument output is: %s.", e.getMessage(), instrumentOutput);
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_WAIT_SIGNAL_IN_LOGCAT_TIMEOUT,
          errorMessage,
          e);
    }

    // Sets the properties, ignore the error and log down.
    if (switchRegion) {
      for (String property : AndroidProperty.REGION.getPropertyKeys()) {
        try {
          adbUtil.setProperty(deviceId, property, Strings.nullToEmpty(country));
        } catch (MobileHarnessException e) {
          SharedLogUtil.logMsg(logger, log, "%s", e.getMessage());
        }
      }
    }
    for (String property : AndroidProperty.LANGUAGE.getPropertyKeys()) {
      try {
        adbUtil.setProperty(deviceId, property, language);
      } catch (MobileHarnessException e) {
        SharedLogUtil.logMsg(logger, log, "%s", e.getMessage());
      }
    }
    Locale locale =
        StrUtil.isEmptyOrWhitespace(country)
            ? Locale.forLanguageTag(language.replace('_', '-'))
            : Locale.forLanguageTag(language.replace('_', '-') + "-" + country.trim());
    for (String property : AndroidProperty.LOCALE.getPropertyKeys()) {
      try {
        adbUtil.setProperty(deviceId, property, locale.toLanguageTag());
      } catch (MobileHarnessException e) {
        SharedLogUtil.logMsg(logger, log, "%s", e.getMessage());
      }
    }
  }
}
