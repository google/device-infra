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
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidService;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidSettings;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidSvc;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.IntentArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.util.ScreenResolution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class AndroidSystemSettingUtilTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Adb adb;
  @Mock private Sleeper sleeper;
  @Mock private Clock clock;
  @Mock private AndroidAdbUtil adbUtil;
  @Mock private AndroidSystemStateUtil systemStateUtil;

  private static final String DEVICE_ID = "device_id";
  private static final String TEST_PACKAGE_NAME = "package.name";
  private static final String APPOPS_OUTPUT_WITH_LEGACY_STORAGE =
      "Uid mode: COARSE_LOCATION: foreground\n" + "LEGACY_STORAGE: allow";
  private static final String APPOPS_OUTPUT_WITH_OUT_LEGACY_STORAGE =
      "Uid mode: COARSE_LOCATION: foreground\n";

  private AndroidSystemSettingUtil settingUtil;

  @Before
  public void setUp() {
    settingUtil = new AndroidSystemSettingUtil(adb, sleeper, clock, adbUtil, systemStateUtil);
  }

  @Test
  public void checkSystemTime() throws Exception {
    Instant now = Clock.systemUTC().instant();
    when(adb.runShell(DEVICE_ID, AndroidSystemSettingUtil.ADB_SHELL_GET_SECONDS_UTC))
        .thenReturn("123123")
        .thenReturn("1999")
        .thenReturn(String.valueOf(now.getEpochSecond()))
        .thenReturn(String.valueOf(now.minusSeconds(10).getEpochSecond()))
        .thenReturn(String.valueOf(now.minusSeconds(10).getEpochSecond()))
        .thenReturn(String.valueOf(now.minusSeconds(10).getEpochSecond()));
    when(clock.instant()).thenReturn(now);

    assertThat(settingUtil.checkSystemTime(DEVICE_ID)).isFalse();
    assertThat(settingUtil.checkSystemTime(DEVICE_ID)).isFalse();
    assertThat(settingUtil.checkSystemTime(DEVICE_ID)).isTrue();
    assertThat(settingUtil.checkSystemTime(DEVICE_ID, Duration.ofSeconds(5))).isFalse();
    assertThat(settingUtil.checkSystemTime(DEVICE_ID, Duration.ofSeconds(10))).isFalse();
    assertThat(settingUtil.checkSystemTime(DEVICE_ID, Duration.ofSeconds(15))).isTrue();
  }

  @Test
  public void disableAirplaneMode_success() throws Exception {
    when(adbUtil.settings(
            any(UtilArgs.class), any(AndroidSettings.Spec.class), any(Duration.class)))
        .thenReturn("");
    when(adbUtil.broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(AndroidSystemSettingUtil.ADB_SHELL_BROADCAST_AIRPLANE_MODE)
                .setExtrasBoolean(ImmutableMap.of("state", false))
                .build(),
            /* checkCmdOutput= */ false,
            AndroidSystemSettingUtil.BROADCAST_AIRPLANE_MODE_TIMEOUT))
        .thenReturn(AndroidAdbUtil.OUTPUT_BROADCAST_SUCCESS);

    settingUtil.setAirplaneMode(DEVICE_ID, /* enable= */ false);
  }

  @Test
  public void disableAirplaneMode_successWithBroadcastPermissionDenial() throws Exception {
    when(adbUtil.settings(
            any(UtilArgs.class), any(AndroidSettings.Spec.class), any(Duration.class)))
        .thenReturn("");
    String errorMessage =
        "java.lang.SecurityException: Permission Denial: not allowed"
            + " to send broadcast android.intent.action.AIRPLANE_MODE from pid=5146, uid=2000";
    MobileHarnessException permissionDenial =
        new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_BROADCAST_ERROR, errorMessage);
    when(adbUtil.broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(AndroidSystemSettingUtil.ADB_SHELL_BROADCAST_AIRPLANE_MODE)
                .setExtrasBoolean(ImmutableMap.of("state", false))
                .build(),
            /* checkCmdOutput= */ false,
            AndroidSystemSettingUtil.BROADCAST_AIRPLANE_MODE_TIMEOUT))
        .thenThrow(permissionDenial);
    when(adbUtil.settings(
            any(UtilArgs.class), any(AndroidSettings.Spec.class), any(Duration.class)))
        .thenReturn("0");

    settingUtil.setAirplaneMode(DEVICE_ID, /* enable= */ false);
  }

  @Test
  public void disableAirplaneMode_failure() throws Exception {
    when(adbUtil.settings(
            any(UtilArgs.class), any(AndroidSettings.Spec.class), any(Duration.class)))
        .thenReturn("");
    String errorMessage = "Empty";
    when(adbUtil.broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(AndroidSystemSettingUtil.ADB_SHELL_BROADCAST_AIRPLANE_MODE)
                .setExtrasBoolean(ImmutableMap.of("state", false))
                .build(),
            /* checkCmdOutput= */ false,
            AndroidSystemSettingUtil.BROADCAST_AIRPLANE_MODE_TIMEOUT))
        .thenReturn(errorMessage);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> settingUtil.setAirplaneMode(DEVICE_ID, /* enable= */ false))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_DISABLE_AIRPLANE_MODE_ERROR);
  }

  @Test
  public void enableAirplaneMode_success() throws Exception {
    when(adbUtil.settings(
            any(UtilArgs.class), any(AndroidSettings.Spec.class), any(Duration.class)))
        .thenReturn("");
    when(adbUtil.broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(AndroidSystemSettingUtil.ADB_SHELL_BROADCAST_AIRPLANE_MODE)
                .setExtrasBoolean(ImmutableMap.of("state", true))
                .build(),
            /* checkCmdOutput= */ false,
            AndroidSystemSettingUtil.BROADCAST_AIRPLANE_MODE_TIMEOUT))
        .thenReturn(AndroidAdbUtil.OUTPUT_BROADCAST_SUCCESS);

    settingUtil.setAirplaneMode(DEVICE_ID, /* enable= */ true);
  }

  @Test
  public void disableSetupWizard_fourCornerExit() throws Exception {
    when(clock.instant()).thenReturn(Clock.systemUTC().instant());
    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class))).thenReturn("1");

    settingUtil.disableSetupWizard(DEVICE_ID);

    verify(adb, never())
        .runShell(DEVICE_ID, AndroidSystemSettingUtil.ADB_SHELL_DISABLE_SETUP_WIZARD);
  }

  @Test
  public void disableSetupWizard_localProperty() throws Exception {
    long nowMs = 1;
    when(clock.instant())
        .thenReturn(
            Instant.ofEpochMilli(nowMs),
            Instant.ofEpochMilli(nowMs).plus(AndroidSystemSettingUtil.CHECK_READY_TIMEOUT));
    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class))).thenReturn("0");
    when(adb.runShell(DEVICE_ID, AndroidSystemSettingUtil.ADB_SHELL_DISABLE_SETUP_WIZARD))
        .thenReturn("");

    assertThat(settingUtil.disableSetupWizard(DEVICE_ID)).isEqualTo(PostSettingDeviceOp.REBOOT);
  }

  @Test
  public void disableSetupWizard_errorWithException() throws Exception {
    long nowMs = 1;
    when(clock.instant())
        .thenReturn(
            Instant.ofEpochMilli(nowMs),
            Instant.ofEpochMilli(nowMs).plus(AndroidSystemSettingUtil.CHECK_READY_TIMEOUT));
    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class))).thenReturn("0");
    when(adb.runShell(DEVICE_ID, AndroidSystemSettingUtil.ADB_SHELL_DISABLE_SETUP_WIZARD))
        .thenReturn("Error");

    try {
      settingUtil.disableSetupWizard(DEVICE_ID);
      fail(
          String.format(
              "skipSetupWizard() should failed if cmd \"%s\" failed",
              AndroidSystemSettingUtil.ADB_SHELL_DISABLE_SETUP_WIZARD));
    } catch (MobileHarnessException e) {
      assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_ERROR);
    }
  }

  @Test
  public void enableGpsLocation() throws Exception {
    AndroidSettings.Spec trueSpec =
        mockLocationSettingsSpec(true, Ascii.toLowerCase(LocationProvider.GPS.name()));
    settingUtil.enableGpsLocation(DEVICE_ID, /* enable= */ true);
    verify(adbUtil).settings(UtilArgs.builder().setSerial(DEVICE_ID).build(), trueSpec);

    AndroidSettings.Spec falseSpec =
        mockLocationSettingsSpec(false, Ascii.toLowerCase(LocationProvider.GPS.name()));
    settingUtil.enableGpsLocation(DEVICE_ID, /* enable= */ false);
    verify(adbUtil).settings(UtilArgs.builder().setSerial(DEVICE_ID).build(), falseSpec);
  }

  @Test
  public void enableNetworkLocation() throws Exception {
    AndroidSettings.Spec trueSpec =
        mockLocationSettingsSpec(true, Ascii.toLowerCase(LocationProvider.NETWORK.name()));
    settingUtil.enableNetworkLocation(DEVICE_ID, /* enable= */ true);
    verify(adbUtil).settings(UtilArgs.builder().setSerial(DEVICE_ID).build(), trueSpec);

    AndroidSettings.Spec falseSpec =
        mockLocationSettingsSpec(false, Ascii.toLowerCase(LocationProvider.NETWORK.name()));
    settingUtil.enableNetworkLocation(DEVICE_ID, /* enable= */ false);
    verify(adbUtil).settings(UtilArgs.builder().setSerial(DEVICE_ID).build(), falseSpec);
  }

  @Test
  public void enableUnknownSources_secure() throws Exception {
    when(adbUtil.settings(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            AndroidSettings.Spec.create(
                AndroidSettings.Command.GET,
                AndroidSettings.NameSpace.SECURE,
                AndroidSystemSettingUtil.ADB_SHELL_SETTINGS_UNKNOWN_SOURCES),
            AndroidSystemSettingUtil.SHORT_COMMAND_TIMEOUT))
        .thenReturn("0");
    when(adbUtil.settings(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            AndroidSettings.Spec.create(
                AndroidSettings.Command.GET,
                AndroidSettings.NameSpace.GLOBAL,
                AndroidSystemSettingUtil.ADB_SHELL_SETTINGS_UNKNOWN_SOURCES),
            AndroidSystemSettingUtil.SHORT_COMMAND_TIMEOUT))
        .thenReturn("Null");

    settingUtil.enableUnknownSources(DEVICE_ID, /* sdkVersion= */ 17);

    verify(adbUtil)
        .settings(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            AndroidSettings.Spec.create(
                AndroidSettings.Command.PUT,
                AndroidSettings.NameSpace.SECURE,
                String.format(
                    AndroidSystemSettingUtil.ADB_SHELL_SETTINGS_SET_UNKNOWN_SOURCES_TEMPLATE, "1")),
            AndroidSystemSettingUtil.SHORT_COMMAND_TIMEOUT);
  }

  @Test
  public void enableUnknownSources_global() throws Exception {
    when(adbUtil.settings(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            AndroidSettings.Spec.create(
                AndroidSettings.Command.GET,
                AndroidSettings.NameSpace.SECURE,
                AndroidSystemSettingUtil.ADB_SHELL_SETTINGS_UNKNOWN_SOURCES),
            AndroidSystemSettingUtil.SHORT_COMMAND_TIMEOUT))
        .thenReturn("null");
    when(adbUtil.settings(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            AndroidSettings.Spec.create(
                AndroidSettings.Command.GET,
                AndroidSettings.NameSpace.GLOBAL,
                AndroidSystemSettingUtil.ADB_SHELL_SETTINGS_UNKNOWN_SOURCES),
            AndroidSystemSettingUtil.SHORT_COMMAND_TIMEOUT))
        .thenReturn("0");

    settingUtil.enableUnknownSources(DEVICE_ID, /* sdkVersion= */ 17);

    verify(adbUtil)
        .settings(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            AndroidSettings.Spec.create(
                AndroidSettings.Command.PUT,
                AndroidSettings.NameSpace.GLOBAL,
                String.format(
                    AndroidSystemSettingUtil.ADB_SHELL_SETTINGS_SET_UNKNOWN_SOURCES_TEMPLATE, "1")),
            AndroidSystemSettingUtil.SHORT_COMMAND_TIMEOUT);
  }

  @Test
  public void getAirplaneMode_success() throws Exception {
    when(adbUtil.settings(
            any(UtilArgs.class), any(AndroidSettings.Spec.class), any(Duration.class)))
        .thenReturn("1");
    when(adbUtil.broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(AndroidSystemSettingUtil.ADB_SHELL_BROADCAST_AIRPLANE_MODE)
                .build(),
            /* checkCmdOutput= */ false,
            AndroidSystemSettingUtil.BROADCAST_AIRPLANE_MODE_TIMEOUT))
        .thenReturn(AndroidAdbUtil.OUTPUT_BROADCAST_SUCCESS);

    assertThat(settingUtil.getAirplaneMode(DEVICE_ID)).isTrue();
  }

  @Test
  public void getAirplaneMode_successWithBroadcastPermissionDenial() throws Exception {
    when(adbUtil.settings(
            any(UtilArgs.class), any(AndroidSettings.Spec.class), any(Duration.class)))
        .thenReturn("1");
    String errorMessage =
        "java.lang.SecurityException: Permission Denial: not allowed"
            + " to send broadcast android.intent.action.AIRPLANE_MODE from pid=5146, uid=2000";
    MobileHarnessException permissionDenial =
        new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_BROADCAST_ERROR, errorMessage);
    when(adbUtil.broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(AndroidSystemSettingUtil.ADB_SHELL_BROADCAST_AIRPLANE_MODE)
                .build(),
            /* checkCmdOutput= */ false,
            AndroidSystemSettingUtil.BROADCAST_AIRPLANE_MODE_TIMEOUT))
        .thenThrow(permissionDenial);

    assertThat(settingUtil.getAirplaneMode(DEVICE_ID)).isTrue();
  }

  @Test
  public void getAirplaneMode_failure() throws Exception {
    when(adbUtil.settings(
            any(UtilArgs.class), any(AndroidSettings.Spec.class), any(Duration.class)))
        .thenReturn("1");
    String errorMessage = "Empty";
    when(adbUtil.broadcast(
            UtilArgs.builder().setSerial(DEVICE_ID).build(),
            IntentArgs.builder()
                .setAction(AndroidSystemSettingUtil.ADB_SHELL_BROADCAST_AIRPLANE_MODE)
                .build(),
            /* checkCmdOutput= */ false,
            AndroidSystemSettingUtil.BROADCAST_AIRPLANE_MODE_TIMEOUT))
        .thenReturn(errorMessage);

    assertThat(
            assertThrows(MobileHarnessException.class, () -> settingUtil.getAirplaneMode(DEVICE_ID))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_BROADCAST_AIRPLANE_MODE_ERROR);
  }

  @Test
  public void getBatteryLevel() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERY))
        .thenReturn(
            "* daemon not running. starting it now on port 5037 *\n"
                + "* daemon started successfully *\n"
                + "Current Battery Service state:\n"
                + "  AC powered: false\n"
                + "  USB powered: true\n"
                + "  status: 2\n"
                + "  health: 2\n"
                + "  present: true\n"
                + "  level: 98\n"
                + "  scale: 100\n"
                + "  voltage:4083\n"
                + "  temperature: 360\n"
                + "  technology: Li-ion");

    assertThat(settingUtil.getBatteryLevel(DEVICE_ID)).isEqualTo(98);
  }

  @Test
  public void getBatteryTemperature() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERY))
        .thenReturn(
            "* daemon not running. starting it now on port 5037 *\n"
                + "* daemon started successfully *\n"
                + "Current Battery Service state:\n"
                + "  AC powered: false\n"
                + "  USB powered: true\n"
                + "  status: 2\n"
                + "  health: 2\n"
                + "  present: true\n"
                + "  level: 98\n"
                + "  scale: 100\n"
                + "  voltage:4083\n"
                + "  temperature: 360\n"
                + "  technology: Li-ion");

    Optional<Integer> temperature = settingUtil.getBatteryTemperature(DEVICE_ID);
    assertThat(temperature).isPresent();
    int batteryTemperature = temperature.get();
    assertThat(batteryTemperature).isEqualTo(36);
  }

  @Test
  public void setBatteryLogicalDischarge() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERY, "set ac 0")).thenReturn("");
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERY, "set usb 0")).thenReturn("");
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERY, "set wireless 0")).thenReturn("");
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERY, "reset")).thenReturn("");

    settingUtil.setBatteryLogicalDischarge(DEVICE_ID, true);
    settingUtil.setBatteryLogicalDischarge(DEVICE_ID, false);
  }

  @Test
  public void setBatteryStats() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERYSTATS, "--reset"))
        .thenReturn("error: device not found")
        .thenReturn("Battery stats reset.");
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERYSTATS, "--write"))
        .thenReturn("error: device not found")
        .thenReturn("Battery stats written.");
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERYSTATS, "enable no-auto-reset"))
        .thenReturn("error: device not found")
        .thenReturn("Enabled: no-auto-reset");
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERYSTATS, "disable no-auto-reset"))
        .thenReturn("error: device not found")
        .thenReturn("Disabled: no-auto-reset");
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.BATTERYSTATS, "--checkin")).thenReturn("a,b");

    settingUtil.resetBatteryStats(DEVICE_ID);
    settingUtil.setBatteryStatsWrite(DEVICE_ID);
    settingUtil.setBatteryStatsNoAutoReset(DEVICE_ID, true);
    settingUtil.setBatteryStatsNoAutoReset(DEVICE_ID, false);
    assertThat(settingUtil.getBatteryStatsCSV(DEVICE_ID)).isEqualTo("a,b");
  }

  @Test
  public void getDeviceSdkVersion() throws Exception {
    when(adbUtil.getIntProperty(DEVICE_ID, AndroidProperty.SDK_VERSION)).thenReturn(28);
    when(adbUtil.getProperty(DEVICE_ID, AndroidProperty.PREVIEW_SDK_VERSION)).thenReturn("");
    assertThat(settingUtil.getDeviceSdkVersion(DEVICE_ID)).isEqualTo(28);

    when(adbUtil.getIntProperty(DEVICE_ID, AndroidProperty.SDK_VERSION))
        .thenThrow(MobileHarnessException.class);
    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> settingUtil.getDeviceSdkVersion(DEVICE_ID))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_SDK_ERROR);
  }

  @Test
  public void getPackageLegacyStorageMode() throws Exception {
    when(adbUtil.cmd(DEVICE_ID, AndroidService.APPOPS, new String[] {"get", TEST_PACKAGE_NAME}))
        .thenReturn(APPOPS_OUTPUT_WITH_LEGACY_STORAGE);

    assertThat(settingUtil.getPackageLegacyStorageMode(DEVICE_ID, TEST_PACKAGE_NAME)).isTrue();

    when(adbUtil.cmd(DEVICE_ID, AndroidService.APPOPS, new String[] {"get", TEST_PACKAGE_NAME}))
        .thenReturn(APPOPS_OUTPUT_WITH_OUT_LEGACY_STORAGE);

    assertThat(settingUtil.getPackageLegacyStorageMode(DEVICE_ID, TEST_PACKAGE_NAME)).isFalse();
  }

  @Test
  public void getPackageOperationMode() throws Exception {
    String appOpsGetOutput =
        "Uid mode: LEGACY_STORAGE: ignore\n"
            + "READ_MEDIA_AUDIO: allow; time=+4h58m15s464ms ago\n"
            + "WRITE_MEDIA_AUDIO: deny; rejectTime=+4h58m17s926ms ago\n"
            + "READ_MEDIA_VIDEO: allow; time=+4h58m15s463ms ago\n"
            + "WRITE_MEDIA_VIDEO: deny; rejectTime=+4h58m17s918ms ago\n"
            + "READ_MEDIA_IMAGES: allow; time=+4h58m15s462ms ago\n"
            + "WRITE_MEDIA_IMAGES: deny; rejectTime=+4h58m17s917ms ago\n"
            + "MANAGE_EXTERNAL_STORAGE: default; rejectTime=+4h58m17s984ms ago\n"
            + "NO_ISOLATED_STORAGE: deny; rejectTime=+4h58m17s984ms ago";
    when(adbUtil.cmd(DEVICE_ID, AndroidService.APPOPS, new String[] {"get", TEST_PACKAGE_NAME}))
        .thenReturn(appOpsGetOutput);

    assertThat(
            settingUtil.getPackageOperationMode(
                DEVICE_ID, TEST_PACKAGE_NAME, "MANAGE_EXTERNAL_STORAGE"))
        .hasValue(AppOperationMode.DEFAULT);
    assertThat(
            settingUtil.getPackageOperationMode(DEVICE_ID, TEST_PACKAGE_NAME, "WRITE_MEDIA_IMAGES"))
        .hasValue(AppOperationMode.DENY);
    assertThat(
            settingUtil.getPackageOperationMode(DEVICE_ID, TEST_PACKAGE_NAME, "READ_MEDIA_IMAGES"))
        .hasValue(AppOperationMode.ALLOW);
    assertThat(settingUtil.getPackageOperationMode(DEVICE_ID, TEST_PACKAGE_NAME, "NON_EXIST_OP"))
        .isEmpty();
  }

  @Test
  public void getScreenResolution_resolutionPattern() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.WINDOW))
        .thenReturn("xyz init=1080x1920 420dpi cur=540x960 app=1080x1794");

    assertThat(settingUtil.getScreenResolution(DEVICE_ID))
        .isEqualTo(ScreenResolution.createWithOverride(1080, 1920, 540, 960));
  }

  @Test
  public void getScreenResolution_displayPattern() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.WINDOW))
        .thenReturn("xyz DisplayWidth=123 xyz DisplayHeight=321 xyz");

    assertThat(settingUtil.getScreenResolution(DEVICE_ID))
        .isEqualTo(ScreenResolution.create(123, 321));
  }

  @Test
  public void getScreenResolution_invalidWindowInfo_throwsException() throws Exception {
    when(adbUtil.dumpSys(DEVICE_ID, DumpSysType.WINDOW)).thenReturn("xyz xyz");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> settingUtil.getScreenResolution(DEVICE_ID))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_PARSE_RESOLUTION_ERROR);
  }

  @Test
  public void getSystemTime() throws Exception {
    when(adb.runShell(DEVICE_ID, AndroidSystemSettingUtil.ADB_SHELL_GET_SECONDS_UTC))
        .thenReturn("123123")
        .thenReturn("199999999")
        .thenReturn("18979817523")
        .thenReturn("-199999999")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR,
                "Failed to parse the number of second: "
                    + new NumberFormatException().getMessage()));

    assertThat(settingUtil.getSystemTime(DEVICE_ID)).isEqualTo(Instant.ofEpochSecond(123123L));
    assertThat(settingUtil.getSystemTime(DEVICE_ID)).isEqualTo(Instant.ofEpochSecond(199999999L));
    assertThat(settingUtil.getSystemTime(DEVICE_ID)).isEqualTo(Instant.ofEpochSecond(18979817523L));
    assertThat(settingUtil.getSystemTime(DEVICE_ID)).isEqualTo(Instant.ofEpochSecond(-199999999L));
    assertThat(
            assertThrows(MobileHarnessException.class, () -> settingUtil.getSystemTime(DEVICE_ID))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_SYSTEM_TIME_ERROR);
  }

  @Test
  public void getSystemTimeMillis() throws Exception {
    double microSecond = 1514933229.172278;
    when(adb.runShell(DEVICE_ID, AndroidSystemSettingUtil.ADB_SHELL_GET_EPOCH_TIME))
        .thenReturn(String.valueOf(microSecond));

    assertThat(settingUtil.getSystemTimeMillis(DEVICE_ID)).isEqualTo((long) (1000L * microSecond));
    assertThat(settingUtil.getSystemTimeMicros(DEVICE_ID))
        .isEqualTo((long) (1000L * 1000L * microSecond));
  }

  @Test
  public void isGpsEnabled() throws Exception {
    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class))).thenReturn("gps");
    assertThat(settingUtil.isGpsEnabled(DEVICE_ID)).isTrue();

    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class)))
        .thenReturn("gps,network");
    assertThat(settingUtil.isGpsEnabled(DEVICE_ID)).isTrue();

    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class))).thenReturn("\n");
    assertThat(settingUtil.isGpsEnabled(DEVICE_ID)).isFalse();
  }

  @Test
  public void isIsolatedStorageEnabled() throws Exception {
    when(adbUtil.getProperty(
            DEVICE_ID,
            ImmutableList.of(AndroidSystemSettingUtil.ANDROID_PROPERTY_ISOLATED_STORAGE_MODE)))
        .thenReturn("true");
    assertThat(settingUtil.isIsolatedStorageEnabled(DEVICE_ID)).isTrue();

    when(adbUtil.getProperty(
            DEVICE_ID,
            ImmutableList.of(AndroidSystemSettingUtil.ANDROID_PROPERTY_ISOLATED_STORAGE_MODE)))
        .thenReturn("false");
    assertThat(settingUtil.isIsolatedStorageEnabled(DEVICE_ID)).isFalse();

    when(adbUtil.getProperty(
            DEVICE_ID,
            ImmutableList.of(AndroidSystemSettingUtil.ANDROID_PROPERTY_ISOLATED_STORAGE_MODE)))
        .thenReturn("NON-BOOLEAN");
    assertThat(settingUtil.isIsolatedStorageEnabled(DEVICE_ID)).isFalse();

    when(adbUtil.getProperty(
            DEVICE_ID,
            ImmutableList.of(AndroidSystemSettingUtil.ANDROID_PROPERTY_ISOLATED_STORAGE_MODE)))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_UTIL_GET_DEVICE_PROPERTY_ERROR, "Error"));
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> settingUtil.isIsolatedStorageEnabled(DEVICE_ID))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_ISOLATED_STORAGE_MODE_ERROR);
  }

  @Test
  public void isLocationServiceDisabled() throws Exception {
    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class))).thenReturn("gps");
    assertThat(settingUtil.isLocationServiceDisabled(DEVICE_ID)).isFalse();

    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class))).thenReturn("\n");
    assertThat(settingUtil.isLocationServiceDisabled(DEVICE_ID)).isTrue();
  }

  @Test
  public void isNetworkLocationEnabled() throws Exception {
    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class)))
        .thenReturn("network");
    assertThat(settingUtil.isNetworkLocationEnabled(DEVICE_ID)).isTrue();

    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class)))
        .thenReturn("gps,network");
    assertThat(settingUtil.isNetworkLocationEnabled(DEVICE_ID)).isTrue();

    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class))).thenReturn("\n");
    assertThat(settingUtil.isNetworkLocationEnabled(DEVICE_ID)).isFalse();
  }

  @Test
  public void isSetupWizardDisabled_trueAndFalse() throws Exception {
    when(adbUtil.settings(any(UtilArgs.class), any(AndroidSettings.Spec.class)))
        .thenReturn("1")
        .thenReturn("0")
        .thenReturn("None Number")
        .thenThrow(new InterruptedException("Exception Message"));

    assertThat(settingUtil.isSetupWizardDisabled(UtilArgs.builder().setSerial(DEVICE_ID).build()))
        .isTrue();
    assertThat(settingUtil.isSetupWizardDisabled(UtilArgs.builder().setSerial(DEVICE_ID).build()))
        .isFalse();
    assertThat(settingUtil.isSetupWizardDisabled(UtilArgs.builder().setSerial(DEVICE_ID).build()))
        .isFalse();
    assertThat(settingUtil.isSetupWizardDisabled(UtilArgs.builder().setSerial(DEVICE_ID).build()))
        .isFalse();
  }

  @Test
  public void isTestHarnessModeEnabled_false_propertyValueIsntOne() throws Exception {
    when(adbUtil.getProperty(
            DEVICE_ID,
            ImmutableList.of(AndroidSystemSettingUtil.ANDROID_PROPERTY_TEST_HARNESS_MODE)))
        .thenReturn("");

    assertThat(settingUtil.isTestHarnessModeEnabled(DEVICE_ID)).isFalse();

    when(adbUtil.getProperty(
            DEVICE_ID,
            ImmutableList.of(AndroidSystemSettingUtil.ANDROID_PROPERTY_TEST_HARNESS_MODE)))
        .thenReturn("0");

    assertThat(settingUtil.isTestHarnessModeEnabled(DEVICE_ID)).isFalse();
  }

  @Test
  public void isTestHarnessModeEnabled_true_propertyValueIsOne() throws Exception {
    when(adbUtil.getProperty(
            DEVICE_ID,
            ImmutableList.of(AndroidSystemSettingUtil.ANDROID_PROPERTY_TEST_HARNESS_MODE)))
        .thenReturn("1");

    assertThat(settingUtil.isTestHarnessModeEnabled(DEVICE_ID)).isTrue();
  }

  @Test
  public void keepAwake_true() throws Exception {
    settingUtil.keepAwake(DEVICE_ID, /* alwaysAwake= */ true);

    verify(adbUtil)
        .svc(
            DEVICE_ID,
            AndroidSvc.builder()
                .setCommand(AndroidSvc.Command.POWER)
                .setOtherArgs("stayon true")
                .build());
  }

  @Test
  public void keepAwake_false() throws Exception {
    settingUtil.keepAwake(DEVICE_ID, /* alwaysAwake= */ false);

    verify(adbUtil)
        .svc(
            DEVICE_ID,
            AndroidSvc.builder()
                .setCommand(AndroidSvc.Command.POWER)
                .setOtherArgs("stayon false")
                .build());
  }

  @Test
  public void setDmVerityChecking_success() throws Exception {
    when(adb.runWithRetry(
            eq(DEVICE_ID), aryEq(new String[] {AndroidSystemSettingUtil.ADB_ARG_ENABLE_VERITY})))
        .thenReturn("dm-verity already enable");

    assertThat(settingUtil.setDmVerityChecking(DEVICE_ID, true))
        .isEqualTo(PostSetDmVerityDeviceOp.NONE);
  }

  @Test
  public void setDmVerityChecking_failure() throws Exception {
    when(adb.runWithRetry(
            eq(DEVICE_ID), aryEq(new String[] {AndroidSystemSettingUtil.ADB_ARG_DISABLE_VERITY})))
        .thenReturn("Failed");

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> settingUtil.setDmVerityChecking(DEVICE_ID, false))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_VERITY_ERROR);
  }

  @Test
  public void setDmVerityChecking_needReboot() throws Exception {
    when(adb.runWithRetry(
            eq(DEVICE_ID), aryEq(new String[] {AndroidSystemSettingUtil.ADB_ARG_ENABLE_VERITY})))
        .thenReturn("dm-verity enabled\nNow reboot your device for settings to take effect");

    assertThat(settingUtil.setDmVerityChecking(DEVICE_ID, true))
        .isEqualTo(PostSetDmVerityDeviceOp.REBOOT);

    when(adb.runWithRetry(
            eq(DEVICE_ID), aryEq(new String[] {AndroidSystemSettingUtil.ADB_ARG_ENABLE_VERITY})))
        .thenReturn("dm-verity enabled\nReboot the device for new settings to take effect");

    assertThat(settingUtil.setDmVerityChecking(DEVICE_ID, true))
        .isEqualTo(PostSetDmVerityDeviceOp.REBOOT);
  }

  @Test
  public void setLogLevelProperty_nullFilterSpecs()
      throws MobileHarnessException, InterruptedException {
    settingUtil.setLogLevelProperty(DEVICE_ID, null);
  }

  @Test
  public void setLogLevelProperty() throws MobileHarnessException, InterruptedException {
    String filterSpecs = "TAG1:VERBOSE TAG2:D TAG3:INFO *:V TAG5";

    settingUtil.setLogLevelProperty(DEVICE_ID, filterSpecs);

    verify(adbUtil).setProperty(DEVICE_ID, "log.tag.TAG1", "VERBOSE");
    verify(adbUtil).setProperty(DEVICE_ID, "log.tag.TAG2", "DEBUG");
  }

  @Test
  public void setPackageLegacyStorageMode() throws Exception {
    when(adbUtil.cmd(
            DEVICE_ID,
            AndroidService.APPOPS,
            new String[] {
              "set", TEST_PACKAGE_NAME, AndroidSystemSettingUtil.LEGACY_STORAGE_KEY_NAME, "allow"
            }))
        .thenReturn("");

    settingUtil.setPackageLegacyStorageMode(DEVICE_ID, TEST_PACKAGE_NAME, true);

    when(adbUtil.cmd(
            DEVICE_ID,
            AndroidService.APPOPS,
            new String[] {
              "set", TEST_PACKAGE_NAME, AndroidSystemSettingUtil.LEGACY_STORAGE_KEY_NAME, "default"
            }))
        .thenThrow(new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_CMD_ERROR, "Error"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        settingUtil.setPackageLegacyStorageMode(
                            DEVICE_ID, TEST_PACKAGE_NAME, false))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_PACKAGE_STORAGE_MODE_ERROR);
  }

  @Test
  public void setPackageOperationMode() throws Exception {
    when(adbUtil.cmd(
            DEVICE_ID,
            AndroidService.APPOPS,
            new String[] {"set", TEST_PACKAGE_NAME, "MANAGE_EXTERNAL_STORAGE", "allow"}))
        .thenReturn("");

    settingUtil.setPackageOperationMode(
        DEVICE_ID, TEST_PACKAGE_NAME, "MANAGE_EXTERNAL_STORAGE", AppOperationMode.ALLOW);

    when(adbUtil.cmd(
            DEVICE_ID,
            AndroidService.APPOPS,
            new String[] {"set", TEST_PACKAGE_NAME, "MANAGE_EXTERNAL_STORAGE", "default"}))
        .thenThrow(new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_CMD_ERROR, "Error"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        settingUtil.setPackageOperationMode(
                            DEVICE_ID,
                            TEST_PACKAGE_NAME,
                            "MANAGE_EXTERNAL_STORAGE",
                            AppOperationMode.DEFAULT))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_PACKAGE_OP_MODE_ERROR);
  }

  @Test
  public void setSystemTime_api23() throws Exception {
    Date now = new Date();
    Instant nowInstant = now.toInstant();
    when(adb.runShell(DEVICE_ID, AndroidSystemSettingUtil.ADB_SHELL_GET_TIME_ZONE_OFFSET))
        .thenReturn("+7877", "+112", "+0810");
    Calendar calendar = Calendar.getInstance(AndroidSystemSettingUtil.UTC_TIME_ZONE);
    calendar.setTime(now);
    calendar.add(Calendar.HOUR_OF_DAY, 8);
    calendar.add(Calendar.MINUTE, 10);
    when(adb.runShell(
            DEVICE_ID,
            String.format(
                AndroidSystemSettingUtil.ADB_SHELL_TEMPLATE_SET_SYSTEM_TIME,
                "-s",
                AndroidSystemSettingUtil.SET_SYSTEM_TIME_FORMAT.format(calendar.toInstant()))))
        .thenReturn(AndroidSystemSettingUtil.SET_SYSTEM_TIME_RETURN_FORMAT.format(nowInstant));

    // Wrong time zone offset format
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> settingUtil.setSystemTime(DEVICE_ID, /* sdkVersion= */ 23, nowInstant))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_TIME_ZONE_OFFSET_ERROR);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> settingUtil.setSystemTime(DEVICE_ID, /* sdkVersion= */ 23, nowInstant))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_TIME_ZONE_OFFSET_ERROR);

    settingUtil.setSystemTime(DEVICE_ID, /* sdkVersion= */ 23, nowInstant);
  }

  @Test
  public void setSystemTime_api24() throws Exception {
    Date now = new Date();
    Instant nowInstant = now.toInstant();
    when(adb.runShell(
            DEVICE_ID,
            String.format(
                AndroidSystemSettingUtil.ADB_SHELL_TEMPLATE_SET_SYSTEM_TIME,
                "",
                AndroidSystemSettingUtil.SET_SYSTEM_TIME_FORMAT.format(nowInstant))))
        .thenReturn(AndroidSystemSettingUtil.SET_SYSTEM_TIME_RETURN_FORMAT.format(nowInstant));

    settingUtil.setSystemTime(DEVICE_ID, /* sdkVersion= */ 24, nowInstant);
  }

  private AndroidSettings.Spec mockLocationSettingsSpec(boolean enable, String value) {
    String extraArgs =
        String.format(
            enable
                ? AndroidSystemSettingUtil.ADB_SHELL_SETTINGS_ENABLE_LOCATION_PROVIDER_TEMPLATE
                : AndroidSystemSettingUtil.ADB_SHELL_SETTINGS_DISABLE_LOCATION_PROVIDER_TEMPLATE,
            value);
    return AndroidSettings.Spec.create(
        AndroidSettings.Command.PUT, AndroidSettings.NameSpace.SECURE, extraArgs);
  }
}
