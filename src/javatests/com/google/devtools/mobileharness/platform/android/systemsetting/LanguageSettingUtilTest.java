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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.lab.DeviceInfo;
import com.google.devtools.mobileharness.api.model.lab.LiteDeviceInfoFactory;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidSettings;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil.AppOpsMode;
import com.google.devtools.mobileharness.platform.android.systemsetting.LanguageSettingUtil.LocaleRestoreState;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.android.AndroidPackages;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.time.Duration;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link LanguageSettingUtil}. */
@RunWith(JUnit4.class)
public final class LanguageSettingUtilTest {

  private static final String DEVICE_ID = "device_123";
  private static final String APK_PATH = "/tmp/switchlanguage.apk";
  private static final String PACKAGE_NAME = AndroidPackages.MH_SWITCH_LANGUAGE.getPackageName();
  private static final String ACTIVITY_NAME =
      AndroidPackages.MH_SWITCH_LANGUAGE.getActivityName().get();
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final UtilArgs API_33_USER_10_ARGS =
      UtilArgs.builder().setSerial(DEVICE_ID).setSdkVersion(33).setUserId("10").build();
  private static final AndroidSettings.Spec GET_SYSTEM_LOCALES =
      AndroidSettings.Spec.create(
          AndroidSettings.Command.GET,
          AndroidSettings.NameSpace.SYSTEM,
          LanguageSettingUtil.SETTING_SYSTEM_LOCALES);

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Device device;
  @Mock private ResUtil resUtil;
  @Mock private SystemSettingManager systemSettingManager;
  @Mock private ApkInstaller apkInstaller;
  @Mock private AndroidInstrumentationUtil instrumentationUtil;
  @Mock private AndroidPackageManagerUtil androidPackageManagerUtil;
  @Mock private AndroidSystemSettingUtil androidSystemSettingUtil;
  @Mock private AndroidUserUtil androidUserUtil;
  @Mock private AndroidAdbUtil adbUtil;
  @Mock private Sleeper sleeper;

  private final Log log = new Log(new Timing());
  private DeviceInfo deviceInfo;
  private LanguageSettingUtil languageSettingUtil;

  @Before
  public void setUp() throws Exception {
    deviceInfo = LiteDeviceInfoFactory.create(DEVICE_ID);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(resUtil.getResourceFile(
            LanguageSettingUtil.class, LanguageSettingUtil.SWITCH_LANGUAGE_APK_RES_PATH))
        .thenReturn(APK_PATH);

    languageSettingUtil =
        new LanguageSettingUtil(
            resUtil,
            systemSettingManager,
            apkInstaller,
            instrumentationUtil,
            androidPackageManagerUtil,
            androidSystemSettingUtil,
            androidUserUtil,
            adbUtil,
            sleeper);
  }

  @Test
  public void switchLanguage_byDeviceInfoWithCountryOnApi30_installsGrantsInstrumentsAndSetsProps()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("US");
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(30);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 30)).thenReturn(10);

    languageSettingUtil.switchLanguage(deviceInfo, "zh", "CN");

    UtilArgs utilArgs =
        UtilArgs.builder().setSerial(DEVICE_ID).setSdkVersion(30).setUserId("10").build();
    InOrder order =
        inOrder(
            apkInstaller,
            androidPackageManagerUtil,
            adbUtil,
            androidSystemSettingUtil,
            instrumentationUtil,
            sleeper);
    order
        .verify(apkInstaller)
        .installApkIfNotExist(
            deviceInfo,
            ApkInstallArgs.builder()
                .addApkPaths(APK_PATH)
                .setBypassLowTargetSdkBlock(true)
                .build());
    order.verify(sleeper).sleep(Duration.ofSeconds(3));
    order
        .verify(androidPackageManagerUtil)
        .grantPermission(
            utilArgs, PACKAGE_NAME, LanguageSettingUtil.SWITCH_LANGUAGE_PKG_EXTRA_PERMISSION);
    order
        .verify(adbUtil)
        .settings(
            utilArgs,
            AndroidSettings.Spec.create(
                AndroidSettings.Command.PUT,
                AndroidSettings.NameSpace.GLOBAL,
                "hidden_api_blacklist_exemptions \"\\*\"")); // InclusiveLanguage:ignore
    order
        .verify(androidSystemSettingUtil)
        .setAppOpsPermission(DEVICE_ID, PACKAGE_NAME, "WRITE_SETTINGS", AppOpsMode.ALLOW, "10");
    order.verify(sleeper).sleep(Duration.ofSeconds(1));
    order
        .verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            30,
            createInstrumentSetting(ImmutableMap.of("language", "zh", "country", "CN")),
            Duration.ofMinutes(1));
    order
        .verify(adbUtil)
        .waitForSignalInLog(
            DEVICE_ID,
            LanguageSettingUtil.LOG_SIGNAL,
            LanguageSettingUtil.DEFAULT_LOG_SIGNAL_TIMEOUT);
    verify(adbUtil).setProperty(DEVICE_ID, AndroidProperty.REGION.getPropertyKeys().get(0), "CN");
    verify(adbUtil).setProperty(DEVICE_ID, AndroidProperty.LANGUAGE.getPropertyKeys().get(0), "zh");
    verify(adbUtil)
        .setProperty(DEVICE_ID, AndroidProperty.LOCALE.getPropertyKeys().get(0), "zh-CN");
  }

  @Test
  public void switchLanguage_byDeviceInfoSameLocaleIgnoreCase_skipsSwitch() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("zh");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("cn");

    languageSettingUtil.switchLanguage(deviceInfo, "ZH", "CN");

    verifyNoInteractions(apkInstaller, androidPackageManagerUtil, instrumentationUtil);
  }

  @Test
  public void switchLanguage_byDeviceInfoEmptyLanguage_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> languageSettingUtil.switchLanguage(deviceInfo, "   ", "US"));

    assertThat(exception).hasMessageThat().ignoringCase().contains("language");
  }

  @Test
  public void switchLanguage_byDeviceInfoUnknownPackageOnFirstGrant_uninstallsAndRetries()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("US");
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(30);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 30)).thenReturn(0);
    UtilArgs utilArgs =
        UtilArgs.builder().setSerial(DEVICE_ID).setSdkVersion(30).setUserId("0").build();
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GRANT_PERMISSION_ERROR, "Unknown package"))
        .doNothing()
        .when(androidPackageManagerUtil)
        .grantPermission(
            utilArgs, PACKAGE_NAME, LanguageSettingUtil.SWITCH_LANGUAGE_PKG_EXTRA_PERMISSION);

    languageSettingUtil.switchLanguage(deviceInfo, "fr", "FR");

    verify(apkInstaller, times(2))
        .installApkIfNotExist(
            deviceInfo,
            ApkInstallArgs.builder()
                .addApkPaths(APK_PATH)
                .setBypassLowTargetSdkBlock(true)
                .build());
    verify(apkInstaller).uninstallApk(deviceInfo, PACKAGE_NAME, /* logFailures= */ true);
  }

  @Test
  public void switchLanguage_byDeviceWithCountry_returnsLocaleRestoreState() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(23);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 23)).thenReturn(0);

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    assertThat(result).hasValue(new LocaleRestoreState("en", "us", 23, true));
  }

  @Test
  public void switchLanguage_byDeviceWithCountry_installsViaApkInstaller() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(24);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 24)).thenReturn(0);

    languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    verify(apkInstaller)
        .installApkIfNotExist(
            device,
            ApkInstallArgs.builder().addApkPaths(APK_PATH).setBypassLowTargetSdkBlock(true).build(),
            log);
  }

  @Test
  public void switchLanguage_byDeviceSameLanguageWithoutCountry_returnsEmpty() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("zh");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", /* country= */ "", TIMEOUT, log);

    assertThat(result).isEmpty();
  }

  @Test
  public void switchLanguage_byDeviceSameLanguageAndCountry_skipsInstallAndInstrumentation()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("zh");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("cn");

    languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    verifyNoInteractions(apkInstaller, instrumentationUtil);
  }

  @Test
  public void switchLanguage_byDeviceGrantPermissionFailsTwice_throwsGrantPermissionError()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(24);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GRANT_PERMISSION_ERROR, "Unknown package"))
        .when(androidPackageManagerUtil)
        .grantPermission(
            UtilArgs.builder().setSerial(DEVICE_ID).setSdkVersion(24).setUserId("0").build(),
            PACKAGE_NAME,
            LanguageSettingUtil.SWITCH_LANGUAGE_PKG_EXTRA_PERMISSION);

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log));

    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_GRANT_PERMISSION_ERROR);
    assertThat(exception).hasMessageThat().contains("Unknown package");
  }

  @Test
  public void switchLanguage_byDeviceSignalTimeoutOnce_retriesAfterSleep() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(24);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_WAIT_FOR_SIGNAL_IN_LOG_ERROR, "Signal timeout"))
        .doNothing()
        .when(adbUtil)
        .waitForSignalInLog(DEVICE_ID, LanguageSettingUtil.LOG_SIGNAL, TIMEOUT);

    languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    verify(sleeper).sleep(Duration.ofMillis(500));
    verify(instrumentationUtil, times(2))
        .instrument(
            DEVICE_ID,
            24,
            createInstrumentSetting(ImmutableMap.of("language", "zh", "country", "cn")),
            Duration.ofMinutes(1));
  }

  @Test
  public void switchLanguage_byDeviceSignalTimeoutTwice_throwsTimeoutErrorWithInstrumentOutput()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(24);
    when(instrumentationUtil.instrument(
            eq(DEVICE_ID), eq(24), any(AndroidInstrumentationSetting.class), any(Duration.class)))
        .thenReturn("INSTRUMENTATION_FAILURE_DETAILS");
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_WAIT_FOR_SIGNAL_IN_LOG_ERROR, "Signal timeout"))
        .when(adbUtil)
        .waitForSignalInLog(DEVICE_ID, LanguageSettingUtil.LOG_SIGNAL, TIMEOUT);

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log));

    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SWITCH_LANGUAGE_DECORATOR_WAIT_SIGNAL_IN_LOGCAT_TIMEOUT);
    assertThat(exception).hasMessageThat().contains("INSTRUMENTATION_FAILURE_DETAILS");
  }

  @Test
  public void switchLanguage_byDeviceInstrumentationFails_propagatesExceptionDirectly()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(24);
    MobileHarnessException cause =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_START_ERROR, "Abort adb shell");
    when(instrumentationUtil.instrument(
            eq(DEVICE_ID), eq(24), any(AndroidInstrumentationSetting.class), any(Duration.class)))
        .thenThrow(cause);

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () -> languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log));

    assertThat(exception).isSameInstanceAs(cause);
  }

  @Test
  public void restoreLanguage_withRestoreRegionTrue_restoresLanguageCountryAndRegionProperties()
      throws Exception {
    LocaleRestoreState state = new LocaleRestoreState("en", "us", 24, true);

    languageSettingUtil.restoreLanguage(DEVICE_ID, state, TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            24,
            createInstrumentSetting(ImmutableMap.of("language", "en", "country", "us")),
            Duration.ofMinutes(1));
    verify(adbUtil).setProperty(DEVICE_ID, AndroidProperty.REGION.getPropertyKeys().get(0), "us");
    verify(adbUtil).setProperty(DEVICE_ID, AndroidProperty.LANGUAGE.getPropertyKeys().get(0), "en");
    verify(adbUtil)
        .setProperty(DEVICE_ID, AndroidProperty.LOCALE.getPropertyKeys().get(0), "en-US");
  }

  @Test
  public void restoreLanguage_withRestoreRegionFalse_skipsRegionProperty() throws Exception {
    LocaleRestoreState state = new LocaleRestoreState("en", "us", 24, false);

    languageSettingUtil.restoreLanguage(DEVICE_ID, state, TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            24,
            createInstrumentSetting(ImmutableMap.of("language", "en", "country", "us")),
            Duration.ofMinutes(1));
    verify(adbUtil, never())
        .setProperty(
            eq(DEVICE_ID), eq(AndroidProperty.REGION.getPropertyKeys().get(0)), any(String.class));
    verify(adbUtil)
        .setProperty(DEVICE_ID, AndroidProperty.LOCALE.getPropertyKeys().get(0), "en-US");
  }

  @Test
  public void restoreLanguage_withUnknownOriginalLanguage_doesNothing() throws Exception {
    LocaleRestoreState state = new LocaleRestoreState(null, null, 33, true);

    languageSettingUtil.restoreLanguage(DEVICE_ID, state, TIMEOUT, log);

    verifyNoInteractions(instrumentationUtil, adbUtil);
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithStaleLegacyProperties_switchesFromEffectiveLocale()
      throws Exception {
    // The legacy properties still claim the target locale, but the current user is in sv-SE.
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("zh");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("CN");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES))
        .thenReturn("sv-SE-u-fw-mon,en-US");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", "CN", TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            33,
            createInstrumentSetting(ImmutableMap.of("language", "zh", "country", "CN")),
            Duration.ofMinutes(1));
    verify(adbUtil, never()).settings(any(UtilArgs.class), anyPutSystemLocales());
    assertThat(result).hasValue(new LocaleRestoreState("sv", "SE", 33, true));
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithEffectiveLocaleAsTarget_skipsSwitch()
      throws Exception {
    // The legacy properties are stale, but the primary locale of the current user is the target.
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("US");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("zh-CN,en-US");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    assertThat(result).isEmpty();
    verifyNoInteractions(apkInstaller, instrumentationUtil);
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithSameCountryOtherLanguage_switches()
      throws Exception {
    // Only the country of the current user's locale matches the target.
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("en-CA");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "fr", "CA", TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            33,
            createInstrumentSetting(ImmutableMap.of("language", "fr", "country", "CA")),
            Duration.ofMinutes(1));
    assertThat(result).hasValue(new LocaleRestoreState("en", "CA", 33, true));
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithSameLanguageOtherCountry_switches()
      throws Exception {
    // Only the language of the current user's locale matches the target.
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("en-GB");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "en", "US", TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            33,
            createInstrumentSetting(ImmutableMap.of("language", "en", "country", "US")),
            Duration.ofMinutes(1));
    assertThat(result).hasValue(new LocaleRestoreState("en", "GB", 33, true));
  }

  @Test
  public void switchLanguage_byDeviceInfoOnApi33WithSameEffectiveLanguageNoCountry_skipsSwitch()
      throws Exception {
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("zh-TW");

    languageSettingUtil.switchLanguage(deviceInfo, "zh", /* country= */ null);

    verifyNoInteractions(apkInstaller, androidPackageManagerUtil, instrumentationUtil);
  }

  @Test
  public void switchLanguage_byDeviceInfoOnApi33WithLegacyLanguageCodes_skipsSwitch()
      throws Exception {
    // Android uses the legacy codes iw, in and ji for Hebrew, Indonesian and Yiddish.
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES))
        .thenReturn("he-IL", "id-ID", "yi");

    languageSettingUtil.switchLanguage(deviceInfo, "iw", "IL");
    languageSettingUtil.switchLanguage(deviceInfo, "in", "ID");
    languageSettingUtil.switchLanguage(deviceInfo, "ji", /* country= */ null);

    verifyNoInteractions(apkInstaller, androidPackageManagerUtil, instrumentationUtil);
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithLegacyCodeForDefaultLocale_storesSetting()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LOCALE)).thenReturn("he-IL");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("null");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "iw", "IL", TIMEOUT, log);

    verify(adbUtil).settings(API_33_USER_10_ARGS, putSystemLocales("he-IL"));
    assertThat(result).isEmpty();
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithUnsetSystemLocales_switchesFromDefaultLocale()
      throws Exception {
    // The legacy properties are stale, and the current user follows the device default locale.
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("zh");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("CN");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LOCALE)).thenReturn("sv-SE");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("null");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", "CN", TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            33,
            createInstrumentSetting(ImmutableMap.of("language", "zh", "country", "CN")),
            Duration.ofMinutes(1));
    verify(adbUtil, never()).settings(any(UtilArgs.class), anyPutSystemLocales());
    assertThat(result).hasValue(new LocaleRestoreState("sv", "SE", 33, true));
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithoutTargetCountry_restoresDefaultLocale()
      throws Exception {
    // The switch APK compares the country too, so it switches zh-TW to zh.
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LOCALE)).thenReturn("zh-TW");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("null");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", /* country= */ null, TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            33,
            createInstrumentSetting(ImmutableMap.of("language", "zh")),
            Duration.ofMinutes(1));
    verify(adbUtil, never()).settings(any(UtilArgs.class), anyPutSystemLocales());
    assertThat(result).hasValue(new LocaleRestoreState("zh", "TW", 33, false));
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithBlankLanguage_storesNoSetting() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LOCALE)).thenReturn("en-US");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("null");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "", "", TIMEOUT, log);

    verifyNoInteractions(instrumentationUtil);
    verify(adbUtil, never()).settings(any(UtilArgs.class), anyPutSystemLocales());
    assertThat(result).hasValue(new LocaleRestoreState("en", "US", 33, false));
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithBlankLanguageAndReadError_returnsEmpty()
      throws Exception {
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_SETTINGS_CMD_ERROR, "Failed to run settings"));

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "", "", TIMEOUT, log);

    verify(adbUtil, never()).settings(any(UtilArgs.class), anyPutSystemLocales());
    assertThat(result).isEmpty();
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WhenStoringSettingFails_stillSwitches()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LOCALE)).thenReturn("zh-CN");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("null");
    when(adbUtil.settings(API_33_USER_10_ARGS, putSystemLocales("zh-CN")))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_SETTINGS_CMD_ERROR, "Failed to run settings"));

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", "CN", TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            33,
            createInstrumentSetting(ImmutableMap.of("language", "zh", "country", "CN")),
            Duration.ofMinutes(1));
    assertThat(result).isEmpty();
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithDefaultLocaleAsTarget_storesSettingWithoutRestore()
      throws Exception {
    // The current user has no locale setting, and the device default locale is the target. The
    // switch APK then changes nothing, so the device default locale is stored as the setting,
    // including its script, for later switches to skip.
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LOCALE)).thenReturn("sr-Latn-RS");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "sr", "RS", TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            33,
            createInstrumentSetting(ImmutableMap.of("language", "sr", "country", "RS")),
            Duration.ofMinutes(1));
    verify(adbUtil).settings(API_33_USER_10_ARGS, putSystemLocales("sr-Latn-RS"));
    assertThat(result).isEmpty();
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithNoUserOrDefaultLocale_switchesWithoutBaseline()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("zh");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("CN");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LOCALE)).thenReturn("");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES)).thenReturn("null");

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", "CN", TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            33,
            createInstrumentSetting(ImmutableMap.of("language", "zh", "country", "CN")),
            Duration.ofMinutes(1));
    assertThat(result).hasValue(new LocaleRestoreState(null, null, 33, true));
  }

  @Test
  public void switchLanguage_byDeviceOnApi33WithSystemLocalesReadError_switchesWithoutBaseline()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("zh");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("CN");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(33);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 33)).thenReturn(10);
    when(adbUtil.settings(API_33_USER_10_ARGS, GET_SYSTEM_LOCALES))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_SETTINGS_CMD_ERROR, "Failed to run settings"));

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", "CN", TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            33,
            createInstrumentSetting(ImmutableMap.of("language", "zh", "country", "CN")),
            Duration.ofMinutes(1));
    assertThat(result).hasValue(new LocaleRestoreState(null, null, 33, true));
  }

  @Test
  public void switchLanguage_byDeviceOnApi23WithSameLegacyLocale_skipsSwitch() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("zh");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("CN");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(23);

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", "CN", TIMEOUT, log);

    assertThat(result).isEmpty();
    verifyNoInteractions(apkInstaller, instrumentationUtil);
  }

  private static AndroidInstrumentationSetting createInstrumentSetting(
      ImmutableMap<String, String> extras) {
    return AndroidInstrumentationSetting.create(
        PACKAGE_NAME,
        ACTIVITY_NAME,
        /* className= */ null,
        extras,
        /* async= */ false,
        /* showRawResults= */ false,
        /* prefixAndroidTest= */ false,
        /* noIsolatedStorage= */ false,
        /* useTestStorageService= */ false,
        /* enableCoverage= */ false);
  }

  private static AndroidSettings.Spec putSystemLocales(String languageTag) {
    return AndroidSettings.Spec.create(
        AndroidSettings.Command.PUT,
        AndroidSettings.NameSpace.SYSTEM,
        LanguageSettingUtil.SETTING_SYSTEM_LOCALES + " " + languageTag);
  }

  /** Matches any command that stores the {@code system_locales} setting. */
  private static AndroidSettings.Spec anyPutSystemLocales() {
    return argThat(spec ->
        spec.commandName().equals(AndroidSettings.Command.PUT.getCommandName())
            && String.valueOf(spec.extraArgs())
                .startsWith(LanguageSettingUtil.SETTING_SYSTEM_LOCALES + " "));
  }
}
