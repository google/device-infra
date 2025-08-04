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
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.android.AndroidPackages;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidSwitchLanguageDecoratorSpec;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Driver decorator for setting device language and country before running test. */
@DecoratorAnnotation(
    help = "For switching device language and country before running test on rooted device.")
public class AndroidSwitchLanguageDecorator extends BaseDecorator
    implements SpecConfigable<AndroidSwitchLanguageDecoratorSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

  /** Utility class for getting the switch language app from jar package. */
  private final ResUtil resUtil;

  private final SystemSettingManager systemSettingManager;

  private final ApkInstaller apkInstaller;

  private final AndroidInstrumentationUtil instrumentationUtil;

  private final AndroidPackageManagerUtil androidPackageManagerUtil;

  private final AndroidAdbUtil adbUtil;

  private AndroidSwitchLanguageDecoratorSpec spec;

  private final Sleeper sleeper;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the lab server
   * framework.
   */
  public AndroidSwitchLanguageDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new ResUtil(),
        new SystemSettingManager(),
        new ApkInstaller(),
        new AndroidInstrumentationUtil(),
        new AndroidPackageManagerUtil(),
        new AndroidAdbUtil(),
        Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  AndroidSwitchLanguageDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      ResUtil resUtil,
      SystemSettingManager systemSettingManager,
      ApkInstaller apkInstaller,
      AndroidInstrumentationUtil instrumentationUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidAdbUtil adbUtil,
      Sleeper sleeper) {
    super(decoratedDriver, testInfo);
    this.resUtil = resUtil;
    this.systemSettingManager = systemSettingManager;
    this.apkInstaller = apkInstaller;
    this.instrumentationUtil = instrumentationUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.adbUtil = adbUtil;
    this.sleeper = sleeper;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    Device device = getDevice();
    String deviceId = device.getDeviceId();

    spec = testInfo.jobInfo().combinedSpec(this, deviceId);

    int sdkVersion = systemSettingManager.getDeviceSdkVersion(device);
    final String language = spec.getLanguage();
    final String country = spec.getCountry();
    final String originalLanguage = adbUtil.getProperty(deviceId, AndroidProperty.LANGUAGE);
    final String originalCountry = adbUtil.getProperty(deviceId, AndroidProperty.REGION);
    final boolean switchRegion = !Strings.isNullOrEmpty(country);
    final Duration logSignalTimeout = Duration.ofSeconds(spec.getLogSignalTimeoutSec());

    // Gets the resource file, installs the apk and gets the permission.
    String apkPath = resUtil.getResourceFile(getClass(), SWITCH_LANGUAGE_APK_RES_PATH);
    installApkAndGrantPermission(testInfo, device, sdkVersion, apkPath);

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Switch language and country: [%s, %s]", language, country);
    // Switch the language and country.
    try {
      switchLocale(
          testInfo, sdkVersion, deviceId, language, country, switchRegion, logSignalTimeout);
    } catch (MobileHarnessException e) {
      if (e.getErrorId()
          == AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_WAIT_SIGNAL_IN_LOGCAT_TIMEOUT) {
        // Sleep for 500 ms to avoid race condition. Context: b/320747279
        sleeper.sleep(Duration.ofMillis(500));
        switchLocale(
            testInfo, sdkVersion, deviceId, language, country, switchRegion, logSignalTimeout);
      } else {
        throw e;
      }
    }

    try {
      // Runs the actual tests after language and country switch.
      getDecorated().run(testInfo);
    } finally {
      // Try to switch the language and country back to what they were originally. If this fails
      // the test will still succeed.
      try {
        if (spec.getRestoreLanguageAfterTest()) {
          switchLocale(
              testInfo,
              sdkVersion,
              deviceId,
              originalLanguage,
              originalCountry,
              switchRegion,
              logSignalTimeout);
        }
      } catch (MobileHarnessException e) {
        testInfo.warnings().addAndLog(e, logger);
      }
    }
  }

  private void installApkAndGrantPermission(
      TestInfo testInfo, Device device, int sdkVersion, String apkPath)
      throws MobileHarnessException, InterruptedException {
    String deviceId = device.getDeviceId();
    String packageName = AndroidPackages.MH_SWITCH_LANGUAGE.getPackageName();

    for (int i = 1; i <= MAX_ATTEMPTS; i++) {
      apkInstaller.installApkIfNotExist(
          device,
          ApkInstallArgs.builder().setApkPath(apkPath).setBypassLowTargetSdkBlock(true).build(),
          testInfo.log());
      if (sdkVersion < 17) {
        break;
      }

      // In API >= 17, an explicitly permission grant to change config is needed via pm
      // since this permission is for system/dev only.
      try {
        androidPackageManagerUtil.grantPermission(
            UtilArgs.builder().setSerial(deviceId).setSdkVersion(sdkVersion).build(),
            packageName,
            SWITCH_LANGUAGE_PKG_EXTRA_PERMISSION);
        break;
      } catch (MobileHarnessException e) {
        // "pm grant" may failed to find the package even it has already installed on device.
        // uninstall the apk and try again. See b/110907264 for more detail.
        if (AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GRANT_PERMISSION_ERROR.equals(e.getErrorId())
            && e.getMessage().contains(LOG_UNKNOWN_PACKAGE)
            && i < MAX_ATTEMPTS) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Failed to grant permission to package %s, uninstall and retry", packageName);
          apkInstaller.uninstallApk(device, packageName, /* logFailures= */ true, testInfo.log());
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
      TestInfo testInfo,
      int sdkVersion,
      String deviceId,
      String language,
      String country,
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
                /* testArgs= */ null,
                /* enableCoverage= */ false),
            /* timeout= */ Duration.ofMinutes(1));
    try {
      adbUtil.waitForSignalInLog(deviceId, LOG_SIGNAL, timeout);
    } catch (MobileHarnessException e) {
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
          adbUtil.setProperty(deviceId, property, country);
        } catch (MobileHarnessException e) {
          testInfo.log().atInfo().alsoTo(logger).log("%s", e.getMessage());
        }
      }
    }
    for (String property : AndroidProperty.LANGUAGE.getPropertyKeys()) {
      try {
        adbUtil.setProperty(deviceId, property, language);
      } catch (MobileHarnessException e) {
        testInfo.log().atInfo().alsoTo(logger).log("%s", e.getMessage());
      }
    }
    @SuppressWarnings("UnsafeLocaleUsage")
    Locale locale =
        country == null
            ? Locale.forLanguageTag(language.replace('_', '-'))
            : new Locale(language, country);
    for (String property : AndroidProperty.LOCALE.getPropertyKeys()) {
      try {
        adbUtil.setProperty(deviceId, property, locale.toLanguageTag());
      } catch (MobileHarnessException e) {
        testInfo.log().atInfo().alsoTo(logger).log("%s", e.getMessage());
      }
    }
  }
}
