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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationSetting;
import com.google.devtools.mobileharness.platform.android.instrumentation.AndroidInstrumentationUtil;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.InstallCmdArgs;
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
  private LanguageSettingUtil languageSettingUtil;

  @Before
  public void setUp() throws Exception {
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
  public void switchLanguage_byDeviceIdWithCountryOnApi30_installsGrantsInstrumentsAndSetsProps()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("US");
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(30);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 30)).thenReturn(10);

    languageSettingUtil.switchLanguage(DEVICE_ID, "zh", "CN");

    UtilArgs utilArgs =
        UtilArgs.builder().setSerial(DEVICE_ID).setSdkVersion(30).setUserId("10").build();
    InOrder order =
        inOrder(
            androidPackageManagerUtil,
            adbUtil,
            androidSystemSettingUtil,
            instrumentationUtil,
            sleeper);
    order
        .verify(androidPackageManagerUtil)
        .installPackage(
            eq(utilArgs),
            any(InstallCmdArgs.class),
            eq(PACKAGE_NAME),
            eq(ImmutableList.of(APK_PATH)),
            eq(false),
            isNull());
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
            createInstrumentSetting(
                ImmutableMap.of("language", "zh", "country", "CN", "sdk_version", "30")),
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
  public void switchLanguage_byDeviceIdWithoutCountryOnApi23_omitsInstallUserIdAndSkipsRegionProp()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("US");
    when(androidSystemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(23);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 23)).thenReturn(0);

    languageSettingUtil.switchLanguage(DEVICE_ID, "ja", /* country= */ "");

    verify(androidPackageManagerUtil)
        .installPackage(
            eq(UtilArgs.builder().setSerial(DEVICE_ID).setSdkVersion(23).build()),
            any(InstallCmdArgs.class),
            eq(PACKAGE_NAME),
            eq(ImmutableList.of(APK_PATH)),
            eq(false),
            isNull());
    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            23,
            createInstrumentSetting(ImmutableMap.of("language", "ja", "sdk_version", "23")),
            Duration.ofMinutes(1));
    verify(adbUtil, never())
        .setProperty(
            eq(DEVICE_ID), eq(AndroidProperty.REGION.getPropertyKeys().get(0)), any(String.class));
    verify(adbUtil).setProperty(DEVICE_ID, AndroidProperty.LOCALE.getPropertyKeys().get(0), "ja");
  }

  @Test
  public void switchLanguage_byDeviceIdSameLocaleIgnoreCase_skipsSwitch() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("zh");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("cn");

    languageSettingUtil.switchLanguage(DEVICE_ID, "ZH", "CN");

    verifyNoInteractions(androidPackageManagerUtil, instrumentationUtil);
  }

  @Test
  public void switchLanguage_byDeviceIdEmptyLanguage_throwsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> languageSettingUtil.switchLanguage(DEVICE_ID, "   ", "US"));

    assertThat(exception).hasMessageThat().ignoringCase().contains("language");
  }

  @Test
  public void switchLanguage_byDeviceIdUnknownPackageOnFirstGrant_uninstallsAndRetries()
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

    languageSettingUtil.switchLanguage(DEVICE_ID, "fr", "FR");

    verify(androidPackageManagerUtil, times(2))
        .installPackage(
            eq(utilArgs),
            any(InstallCmdArgs.class),
            eq(PACKAGE_NAME),
            eq(ImmutableList.of(APK_PATH)),
            eq(false),
            isNull());
    verify(androidPackageManagerUtil).uninstallApk(utilArgs, PACKAGE_NAME);
  }

  @Test
  public void switchLanguage_byDeviceWithCountryOnApi23_returnsLocaleRestoreState()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(23);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 23)).thenReturn(0);

    Optional<LocaleRestoreState> result =
        languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    assertThat(result).hasValue(new LocaleRestoreState("en", "us", 23, true));
  }

  @Test
  public void switchLanguage_byDeviceWithCountryOnApi23_installsViaApkInstaller() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(23);
    when(androidUserUtil.getCurrentUser(DEVICE_ID, 23)).thenReturn(0);

    var unused = languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    verify(apkInstaller)
        .installApkIfNotExist(
            device,
            ApkInstallArgs.builder().setApkPath(APK_PATH).setBypassLowTargetSdkBlock(true).build(),
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

    var unused = languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    verifyNoInteractions(apkInstaller, instrumentationUtil);
  }

  @Test
  public void switchLanguage_byDeviceOnApi16_skipsPermissionGrant() throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(16);

    var unused = languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    verifyNoInteractions(androidPackageManagerUtil, androidSystemSettingUtil, androidUserUtil);
    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            16,
            createInstrumentSetting(
                ImmutableMap.of("language", "zh", "country", "cn", "sdk_version", "16")),
            Duration.ofMinutes(1));
  }

  @Test
  public void switchLanguage_byDeviceGrantPermissionFailsTwice_throwsGrantPermissionError()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(17);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_PKG_MNGR_UTIL_GRANT_PERMISSION_ERROR, "Unknown package"))
        .when(androidPackageManagerUtil)
        .grantPermission(
            UtilArgs.builder().setSerial(DEVICE_ID).setSdkVersion(17).build(),
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
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(17);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_WAIT_FOR_SIGNAL_IN_LOG_ERROR, "Signal timeout"))
        .doNothing()
        .when(adbUtil)
        .waitForSignalInLog(DEVICE_ID, LanguageSettingUtil.LOG_SIGNAL, TIMEOUT);

    var unused = languageSettingUtil.switchLanguage(device, "zh", "cn", TIMEOUT, log);

    verify(sleeper).sleep(Duration.ofMillis(500));
    verify(instrumentationUtil, times(2))
        .instrument(
            DEVICE_ID,
            17,
            createInstrumentSetting(
                ImmutableMap.of("language", "zh", "country", "cn", "sdk_version", "17")),
            Duration.ofMinutes(1));
  }

  @Test
  public void switchLanguage_byDeviceSignalTimeoutTwice_throwsTimeoutErrorWithInstrumentOutput()
      throws Exception {
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.LANGUAGE)).thenReturn("en");
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.REGION)).thenReturn("us");
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(17);
    when(instrumentationUtil.instrument(
            eq(DEVICE_ID), eq(17), any(AndroidInstrumentationSetting.class), any(Duration.class)))
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
    when(systemSettingManager.getDeviceSdkVersion(device)).thenReturn(17);
    MobileHarnessException cause =
        new MobileHarnessException(
            AndroidErrorId.ANDROID_INSTRUMENTATION_COMMAND_START_ERROR, "Abort adb shell");
    when(instrumentationUtil.instrument(
            eq(DEVICE_ID), eq(17), any(AndroidInstrumentationSetting.class), any(Duration.class)))
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
    LocaleRestoreState state = new LocaleRestoreState("en", "us", 23, true);

    languageSettingUtil.restoreLanguage(DEVICE_ID, state, TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            23,
            createInstrumentSetting(
                ImmutableMap.of("language", "en", "country", "us", "sdk_version", "23")),
            Duration.ofMinutes(1));
    verify(adbUtil).setProperty(DEVICE_ID, AndroidProperty.REGION.getPropertyKeys().get(0), "us");
    verify(adbUtil).setProperty(DEVICE_ID, AndroidProperty.LANGUAGE.getPropertyKeys().get(0), "en");
    verify(adbUtil)
        .setProperty(DEVICE_ID, AndroidProperty.LOCALE.getPropertyKeys().get(0), "en-US");
  }

  @Test
  public void restoreLanguage_withRestoreRegionFalse_skipsRegionProperty() throws Exception {
    LocaleRestoreState state = new LocaleRestoreState("en", "us", 17, false);

    languageSettingUtil.restoreLanguage(DEVICE_ID, state, TIMEOUT, log);

    verify(instrumentationUtil)
        .instrument(
            DEVICE_ID,
            17,
            createInstrumentSetting(
                ImmutableMap.of("language", "en", "country", "us", "sdk_version", "17")),
            Duration.ofMinutes(1));
    verify(adbUtil, never())
        .setProperty(
            eq(DEVICE_ID), eq(AndroidProperty.REGION.getPropertyKeys().get(0)), any(String.class));
    verify(adbUtil)
        .setProperty(DEVICE_ID, AndroidProperty.LOCALE.getPropertyKeys().get(0), "en-US");
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
}
