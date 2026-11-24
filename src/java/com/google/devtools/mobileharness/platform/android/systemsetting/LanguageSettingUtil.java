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
import com.google.devtools.mobileharness.shared.util.concurrent.MobileHarnessRunnable;
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

  /** The name of the extra for switching language. */
  @VisibleForTesting static final String EXTRA_NAME_LANGUAGE = "language";

  /** The name of the extra for switching language. */
  @VisibleForTesting static final String EXTRA_NAME_COUNTRY = "country";

  @VisibleForTesting static final String LOG_UNKNOWN_PACKAGE = "Unknown package";

  /** The target signal to search in the device log. */
  @VisibleForTesting static final String LOG_SIGNAL = "Successfully switch language";

  /** The system setting that holds the locale list of a user on API 24+. */
  @VisibleForTesting static final String SETTING_SYSTEM_LOCALES = "system_locales";

  /**
   * Snapshot of the device's original locale used to restore language settings after a test.
   *
   * <p>The original language is null or empty if the original locale is unknown. Then nothing is
   * restored.
   */
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
   * {@link LocaleRestoreState} if a switch was performed or {@link Optional#empty()} if it was
   * skipped or there is nothing to restore.
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
    int sdkVersion =
        device != null
            ? systemSettingManager.getDeviceSdkVersion(device)
            : androidSystemSettingUtil.getDeviceSdkVersion(deviceId);

    String originalLanguage;
    String originalCountry;
    boolean skipSwitch;
    Optional<EffectiveLocale> effectiveLocale = Optional.empty();
    if (sdkVersion < AndroidVersion.NOUGAT.getStartSdkVersion()) {
      originalLanguage = adbUtil.getProperty(deviceId, AndroidProperty.LANGUAGE);
      originalCountry = adbUtil.getProperty(deviceId, AndroidProperty.REGION);
      skipSwitch = isTargetLocale(language, country, originalLanguage, originalCountry);
    } else {
      // On API 24+, the platform no longer updates the legacy language and region properties, so
      // they can be stale. The effective locale of the current user is the restore baseline
      // instead. Only the user's own locale setting can skip the switch: the device default locale
      // is global, so it may not be what the current user sees.
      effectiveLocale = getEffectiveLocale(log, deviceId, sdkVersion);
      Optional<Locale> baseline = effectiveLocale.map(EffectiveLocale::locale);
      originalLanguage = baseline.map(Locale::getLanguage).orElse(null);
      originalCountry = baseline.map(Locale::getCountry).orElse(null);
      skipSwitch =
          effectiveLocale.map(EffectiveLocale::fromUserSetting).orElse(false)
              && isTargetLocale(language, country, originalLanguage, originalCountry);
    }

    if (skipSwitch) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Device language is already %s_%s. Skipping language switch setup.",
          language,
          Strings.nullToEmpty(country));
      return Optional.empty();
    }

    SharedLogUtil.logMsg(
        logger,
        log,
        "Device locale is [%s_%s]. Target is [%s_%s]. Switching language...",
        Strings.nullToEmpty(originalLanguage),
        Strings.nullToEmpty(originalCountry),
        language,
        Strings.nullToEmpty(country));

    boolean switchRegion = !StrUtil.isEmptyOrWhitespace(country);

    // Gets the resource file, installs the apk and gets the permission.
    String apkPath = resUtil.getResourceFile(getClass(), SWITCH_LANGUAGE_APK_RES_PATH);
    installApkAndGrantPermission(log, device, deviceInfo, deviceId, sdkVersion, apkPath);

    // Switch the language and country.
    runWithLogcatTimeoutRetry(() ->
        switchLocale(log, sdkVersion, deviceId, language, country, switchRegion, logSignalTimeout));

    if (isSameLocale(language, country, originalLanguage, originalCountry)) {
      // The original locale already is the target, so there is nothing to restore. The switch APK
      // then changes nothing and does not store the user's locale setting, which is why a user
      // without the setting gets here instead of skipping the switch. Store the user's locale as
      // the setting, so that later switches to it are skipped.
      if (effectiveLocale.isPresent()) {
        putSystemLocales(
            log,
            deviceId,
            sdkVersion,
            effectiveLocale.get().userId(),
            effectiveLocale.get().locale());
      }
      return Optional.empty();
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
      SharedLogUtil.logMsg(
          logger,
          log,
          "Original locale of device %s is unknown. Skipping language restore.",
          deviceId);
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

  /**
   * The effective locale of the current user on API 24+.
   *
   * @param userId the current user
   * @param locale the primary locale of the user's {@code system_locales} setting, or the device
   *     default locale if the user has no such setting
   * @param fromUserSetting whether {@code locale} comes from the user's {@code system_locales}
   *     setting
   */
  private record EffectiveLocale(int userId, Locale locale, boolean fromUserSetting) {}

  /**
   * Returns the effective locale of the current user on API 24+: the primary locale of the user's
   * {@code system_locales} setting or, if the user has no such setting, the device default locale.
   *
   * <p>Returns empty if the effective locale can not be read.
   */
  private Optional<EffectiveLocale> getEffectiveLocale(
      @Nullable LogCollector<?> log, String deviceId, int sdkVersion) throws InterruptedException {
    int userId;
    boolean fromUserSetting;
    String languageTags;
    try {
      userId = androidUserUtil.getCurrentUser(deviceId, sdkVersion);
      String systemLocales =
          Strings.nullToEmpty(
                  adbUtil.settings(
                      userArgs(deviceId, sdkVersion, userId),
                      AndroidSettings.Spec.create(
                          AndroidSettings.Command.GET,
                          AndroidSettings.NameSpace.SYSTEM,
                          SETTING_SYSTEM_LOCALES)))
              .trim();
      // The setting is a comma-separated list of BCP 47 language tags whose first entry is the
      // primary locale, e.g. "sv-SE,en-US". It is "null" if the user has no locale set; such a
      // user follows the device default locale, a single language tag in persist.sys.locale or
      // ro.product.locale.
      fromUserSetting = !systemLocales.isEmpty() && !systemLocales.equals("null");
      languageTags =
          fromUserSetting
              ? systemLocales
              : Strings.nullToEmpty(adbUtil.getProperty(deviceId, AndroidProperty.LOCALE)).trim();
    } catch (MobileHarnessException e) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Failed to read the effective locale of device %s: %s",
          deviceId,
          e.getMessage());
      return Optional.empty();
    }

    int separatorIndex = languageTags.indexOf(',');
    Locale primaryLocale =
        Locale.forLanguageTag(
            separatorIndex < 0 ? languageTags : languageTags.substring(0, separatorIndex));
    if (primaryLocale.getLanguage().isEmpty()) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Failed to get the effective locale of user %s on device %s from [%s]",
          userId,
          deviceId,
          languageTags);
      return Optional.empty();
    }
    SharedLogUtil.logMsg(
        logger,
        log,
        "Effective locale of user %s on device %s is [%s]%s",
        userId,
        deviceId,
        primaryLocale.toLanguageTag(),
        fromUserSetting ? "" : ", the device default locale");
    return Optional.of(new EffectiveLocale(userId, primaryLocale, fromUserSetting));
  }

  /** Stores the given locale as the {@code system_locales} setting of the user. */
  private void putSystemLocales(
      @Nullable LogCollector<?> log, String deviceId, int sdkVersion, int userId, Locale locale)
      throws InterruptedException {
    String languageTag = locale.toLanguageTag();
    try {
      adbUtil.settings(
          userArgs(deviceId, sdkVersion, userId),
          AndroidSettings.Spec.create(
              AndroidSettings.Command.PUT,
              AndroidSettings.NameSpace.SYSTEM,
              SETTING_SYSTEM_LOCALES + " " + languageTag));
    } catch (MobileHarnessException e) {
      SharedLogUtil.logMsg(
          logger,
          log,
          "Failed to set %s of user %s on device %s to [%s]: %s",
          SETTING_SYSTEM_LOCALES,
          userId,
          deviceId,
          languageTag,
          e.getMessage());
    }
  }

  private static UtilArgs userArgs(String deviceId, int sdkVersion, int userId) {
    return UtilArgs.builder()
        .setSerial(deviceId)
        .setSdkVersion(sdkVersion)
        .setUserId(String.valueOf(userId))
        .build();
  }

  /**
   * Returns whether the given language and country match the target language and country. The
   * country is ignored if the target country is empty.
   */
  private static boolean isTargetLocale(
      String targetLanguage,
      @Nullable String targetCountry,
      @Nullable String language,
      @Nullable String country) {
    return normalizeLanguage(targetLanguage).equals(normalizeLanguage(language))
        && (Strings.isNullOrEmpty(targetCountry)
            || Ascii.equalsIgnoreCase(targetCountry, Strings.nullToEmpty(country)));
  }

  /**
   * Returns whether the given language and country are exactly the target language and country,
   * which is the case in which the switch APK changes nothing.
   */
  private static boolean isSameLocale(
      String targetLanguage,
      @Nullable String targetCountry,
      @Nullable String language,
      @Nullable String country) {
    return normalizeLanguage(targetLanguage).equals(normalizeLanguage(language))
        && Ascii.equalsIgnoreCase(Strings.nullToEmpty(targetCountry), Strings.nullToEmpty(country));
  }

  /**
   * Returns the language code in lower case, with the legacy ISO 639 codes that Android still uses
   * (iw, in, ji) replaced by the current codes, which {@link Locale} uses on newer JDKs.
   */
  private static String normalizeLanguage(@Nullable String language) {
    String code = Ascii.toLowerCase(Strings.nullToEmpty(language));
    return switch (code) {
      case "iw" -> "he";
      case "in" -> "id";
      case "ji" -> "yi";
      default -> code;
    };
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
    var currentUserId = androidUserUtil.getCurrentUser(deviceId, sdkVersion);
    ApkInstallArgs installArgs =
        ApkInstallArgs.builder().addApkPaths(apkPath).setBypassLowTargetSdkBlock(true).build();

    for (int i = 1; i <= MAX_ATTEMPTS; i++) {
      if (device != null) {
        apkInstaller.installApkIfNotExist(device, installArgs, log);
      } else {
        apkInstaller.installApkIfNotExist(Preconditions.checkNotNull(deviceInfo), installArgs);
      }

      // Needed, otherwise setting appops permission success is flaky but the command still
      // succeeds without an error (b/505022628#comment16).
      sleeper.sleep(Duration.ofSeconds(3));

      // A explicitly permission grant to change config is needed via pm since this permission is
      // for system/dev only.
      try {
        UtilArgs utilArgs =
            UtilArgs.builder()
                .setSerial(deviceId)
                .setSdkVersion(sdkVersion)
                .setUserId(String.valueOf(currentUserId))
                .build();

        androidPackageManagerUtil.grantPermission(
            utilArgs, packageName, SWITCH_LANGUAGE_PKG_EXTRA_PERMISSION);
        adbUtil.settings(
            utilArgs,
            AndroidSettings.Spec.create(
                AndroidSettings.Command.PUT,
                AndroidSettings.NameSpace.GLOBAL,
                "hidden_api_blacklist_exemptions \"\\*\"")); // InclusiveLanguage:ignore
        androidSystemSettingUtil.setAppOpsPermission(
            deviceId,
            packageName,
            "WRITE_SETTINGS",
            AppOpsMode.ALLOW,
            String.valueOf(currentUserId));
        sleeper.sleep(Duration.ofSeconds(1));
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

    Map<String, String> extras = new HashMap<>();
    extras.put(EXTRA_NAME_LANGUAGE, language);
    if (!StrUtil.isEmptyOrWhitespace(country)) {
      extras.put(EXTRA_NAME_COUNTRY, country);
    }

    runSwitchInstrumentation(log, sdkVersion, deviceId, extras, timeout);

    Locale locale =
        StrUtil.isEmptyOrWhitespace(country)
            ? Locale.forLanguageTag(language.replace('_', '-'))
            : Locale.forLanguageTag(language.replace('_', '-') + "-" + country.trim());
    setLocaleProperties(log, deviceId, language, country, switchRegion, locale);
  }

  private void runSwitchInstrumentation(
      @Nullable LogCollector<?> log,
      int sdkVersion,
      String deviceId,
      Map<String, String> extras,
      Duration timeout)
      throws MobileHarnessException, InterruptedException {
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
  }

  private void runWithLogcatTimeoutRetry(MobileHarnessRunnable action)
      throws MobileHarnessException, InterruptedException {
    try {
      action.run();
    } catch (MobileHarnessException e) {
      if (e.getErrorId()
          == AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_WAIT_SIGNAL_IN_LOGCAT_TIMEOUT) {
        // Sleep for 500 ms to avoid race condition. Context: b/320747279
        sleeper.sleep(Duration.ofMillis(500));
        action.run();
      } else {
        throw e;
      }
    }
  }

  private void setLocaleProperties(
      @Nullable LogCollector<?> log,
      String deviceId,
      String language,
      @Nullable String country,
      boolean switchRegion,
      Locale locale)
      throws InterruptedException {
    if (switchRegion) {
      trySetProperties(log, deviceId, AndroidProperty.REGION, Strings.nullToEmpty(country));
    }
    trySetProperties(log, deviceId, AndroidProperty.LANGUAGE, language);
    trySetProperties(log, deviceId, AndroidProperty.LOCALE, locale.toLanguageTag());
  }

  private void trySetProperties(
      @Nullable LogCollector<?> log, String deviceId, AndroidProperty property, String value)
      throws InterruptedException {
    for (String key : property.getPropertyKeys()) {
      try {
        adbUtil.setProperty(deviceId, key, value);
      } catch (MobileHarnessException e) {
        SharedLogUtil.logMsg(logger, log, "%s", e.getMessage());
      }
    }
  }
}
