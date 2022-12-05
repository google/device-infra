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

package com.google.devtools.mobileharness.platform.android.media;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidContent;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidMediaUtilTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Adb adb;
  @Mock private AndroidAdbUtil adbUtil;
  @Mock private Sleeper sleeper;
  @Mock private Clock clock;
  @Mock private AndroidProcessUtil androidProcessUtil;

  private static final String SERIAL = "363005dc750400ec";
  private AndroidMediaUtil androidMediaUtil;

  @Before
  public void setUp() {
    androidMediaUtil = new AndroidMediaUtil(adb, adbUtil, sleeper, clock, androidProcessUtil);
  }

  @Test
  public void enterVrMode() throws Exception {
    when(adb.runShell(SERIAL, AndroidMediaUtil.ADB_SHELL_ENTER_VR_MODE))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    androidMediaUtil.enterVrMode(SERIAL);
    verify(adb).runShell(SERIAL, AndroidMediaUtil.ADB_SHELL_ENTER_VR_MODE);

    assertThat(
            assertThrows(MobileHarnessException.class, () -> androidMediaUtil.enterVrMode(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_ENTER_VR_MODE_ERROR);
  }

  @Test
  public void getScreenOrientation_adbException() throws Exception {
    when(adbUtil.dumpSys(SERIAL, DumpSysType.INPUT)).thenReturn("whatever");
    when(adbUtil.dumpSys(SERIAL, DumpSysType.WINDOW))
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_DUMPSYS_ERROR, "Exception"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.getScreenOrientation(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_DUMPSYS_ORIENTATION_INFO_ERROR);
  }

  @Test
  public void getScreenOrientation_invalidDumpsysOutput() throws Exception {
    when(adbUtil.dumpSys(SERIAL, DumpSysType.INPUT)).thenReturn("wrong output line 1");
    when(adbUtil.dumpSys(SERIAL, DumpSysType.WINDOW)).thenReturn("wrong output line 2");

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.getScreenOrientation(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_GET_SCREEN_ORIENTATION_ERROR);
  }

  @Test
  public void getScreenOrientation_invalidSurfaceOrientation() throws Exception {
    when(adbUtil.dumpSys(SERIAL, DumpSysType.INPUT))
        .thenReturn("... ...\n SurfaceOrientation: x\n... ... ");
    when(adbUtil.dumpSys(SERIAL, DumpSysType.WINDOW)).thenReturn("... ...");

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.getScreenOrientation(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_GET_SCREEN_ORIENTATION_ERROR);
  }

  @Test
  public void getScreenOrientation_invalidSurfaceOrientationNumber() throws Exception {
    when(adbUtil.dumpSys(SERIAL, DumpSysType.INPUT))
        .thenReturn("... ...\n SurfaceOrientation: 4\n... ... ");
    when(adbUtil.dumpSys(SERIAL, DumpSysType.WINDOW)).thenReturn("... ...");

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.getScreenOrientation(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_GET_SCREEN_ORIENTATION_ERROR);
  }

  @Test
  public void getScreenOrientation_success() throws Exception {
    when(adbUtil.dumpSys(SERIAL, DumpSysType.INPUT))
        .thenReturn("... ...\n SurfaceOrientation: 3\n... ... ");
    when(adbUtil.dumpSys(SERIAL, DumpSysType.WINDOW)).thenReturn("... ...");
    assertThat(androidMediaUtil.getScreenOrientation(SERIAL))
        .isEqualTo(ScreenOrientation.values()[3]);
  }

  @Test
  public void inputText() throws Exception {
    String text = "some input text";
    when(adb.runShellWithRetry(SERIAL, AndroidMediaUtil.ADB_SHELL_INPUT_TEXT + " " + text))
        .thenReturn("")
        .thenReturn("error")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    androidMediaUtil.inputText(SERIAL, text);

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> androidMediaUtil.inputText(SERIAL, text))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_INPUT_TEXT_ERROR);
    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> androidMediaUtil.inputText(SERIAL, text))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_INPUT_TEXT_ERROR);
  }

  @Test
  public void recordScreen() throws Exception {
    String outputFileOnDevice = "/path/to/device/file";
    int bitRate = 4000000;
    String videoSize = "1280x720";

    ScreenRecordArgs args =
        ScreenRecordArgs.builder(outputFileOnDevice)
            .setBitRate(bitRate)
            .setSize(videoSize)
            .setVerbose(true)
            .build();

    androidMediaUtil.recordScreen(SERIAL, args, Duration.ofMinutes(10));
    verify(adb).runShellAsync(SERIAL, args.toShellCmd(), Duration.ofMinutes(10));

    args =
        ScreenRecordArgs.builder(outputFileOnDevice).setBitRate(bitRate).setVerbose(true).build();

    androidMediaUtil.recordScreen(SERIAL, args, Duration.ofMinutes(10));
    verify(adb).runShellAsync(SERIAL, args.toShellCmd(), Duration.ofMinutes(10));

    when(adb.runShellAsync(eq(SERIAL), anyString(), any(Duration.class)))
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_ADB_ASYNC_CMD_START_ERROR, "Error"));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        androidMediaUtil.recordScreen(
                            SERIAL,
                            outputFileOnDevice,
                            bitRate,
                            /* size= */ null,
                            Duration.ofMinutes(10)))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_RECORD_SCREEN_ERROR);
  }

  @Test
  public void recordScreenVr() throws Exception {
    String outputFileOnDevice = "/path/to/device/file";
    int bitRate = 4000000;

    androidMediaUtil = spy(new AndroidMediaUtil(adb, adbUtil, sleeper, clock, androidProcessUtil));
    InOrder inOrder = Mockito.inOrder(androidMediaUtil, sleeper);

    androidMediaUtil.recordScreenVr(SERIAL, outputFileOnDevice, bitRate);

    inOrder.verify(androidMediaUtil).stopScreenRecordVr(SERIAL);
    inOrder.verify(sleeper).sleep(AndroidMediaUtil.RECORDER_SERVICE_DELAY);
    inOrder.verify(androidMediaUtil).startScreenRecordVr(SERIAL, outputFileOnDevice, bitRate);
  }

  @Test
  public void rotateScreen() throws Exception {
    when(adbUtil.content(any(UtilArgs.class), any(AndroidContent.class)))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_CONTENT_ERROR, "Error"));

    androidMediaUtil.rotateScreen(SERIAL, ScreenOrientation.PORTRAIT);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.rotateScreen(SERIAL, ScreenOrientation.PORTRAIT))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_ROTATE_SCREEN_ERROR);
  }

  @Test
  public void setAccelerometerRotation() throws Exception {
    when(adbUtil.content(any(UtilArgs.class), any(AndroidContent.class)))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(AndroidErrorId.ANDROID_ADB_UTIL_CONTENT_ERROR, "Error"));

    androidMediaUtil.setAccelerometerRotation(SERIAL, true);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.setAccelerometerRotation(SERIAL, false))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_SET_ACCELEROMETER_ROTATION_ERROR);
  }

  @Test
  public void startScreenRecordVr() throws Exception {
    String outputFileOnDevice = "/path/to/device/file";
    int bitRate = 4000000;
    String cmd =
        String.format(
            AndroidMediaUtil.ADB_SHELL_VR_RECORDER_SERVICE
                + AndroidMediaUtil.ADB_ARG_INTENT_EXTRA_STRING
                + AndroidMediaUtil.ADB_ARG_INTENT_EXTRA_STRING
                + AndroidMediaUtil.ADB_ARG_INTENT_EXTRA_INT,
            "command",
            "START",
            "path",
            outputFileOnDevice,
            "bitRate",
            bitRate);
    when(adb.runShellWithRetry(SERIAL, cmd))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    androidMediaUtil.startScreenRecordVr(SERIAL, outputFileOnDevice, bitRate);
    verify(adb).runShellWithRetry(SERIAL, cmd);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.startScreenRecordVr(SERIAL, outputFileOnDevice, bitRate))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_START_SCREEN_RECORD_VR_ERROR);
  }

  @Test
  public void stopScreenRecord() throws Exception {
    String screenRecordProcessId = "104";
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));
    when(androidProcessUtil.getProcessId(any(UtilArgs.class), eq("screenrecord")))
        .thenReturn(screenRecordProcessId)
        .thenReturn(screenRecordProcessId)
        .thenReturn(null);

    androidMediaUtil.stopScreenRecord(SERIAL, Duration.ofSeconds(5));

    verify(androidProcessUtil).stopProcess(SERIAL, screenRecordProcessId, KillSignal.SIGINT);
  }

  @Test
  public void stopScreenRecord_timeout() throws Exception {
    String screenRecordProcessId = "104";
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(1))
        .thenReturn(Instant.ofEpochMilli(100))
        .thenReturn(Instant.ofEpochMilli(200));
    when(androidProcessUtil.getProcessId(any(UtilArgs.class), eq("screenrecord")))
        .thenReturn(screenRecordProcessId)
        .thenReturn(screenRecordProcessId);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.stopScreenRecord(SERIAL, Duration.ofMillis(100)))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_STOP_SCREEN_RECORD_TIMEOUT);
  }

  @Test
  public void stopScreenRecordVr() throws Exception {
    String cmd =
        AndroidMediaUtil.ADB_SHELL_VR_RECORDER_SERVICE
            + String.format(AndroidMediaUtil.ADB_ARG_INTENT_EXTRA_STRING, "command", "STOP");
    when(adb.runShellWithRetry(SERIAL, cmd))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    androidMediaUtil.stopScreenRecordVr(SERIAL);
    verify(adb).runShellWithRetry(SERIAL, cmd);

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> androidMediaUtil.stopScreenRecordVr(SERIAL))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_STOP_SCREEN_RECORD_VR_ERROR);
  }

  @Test
  public void takeScreenshot() throws Exception {
    Timestamp timestamp = new Timestamp(Clock.systemUTC().millis());
    String outputFileOnDevice = "/data/local/tmp/" + timestamp + ".png";
    String args =
        String.format(AndroidMediaUtil.ADB_SHELL_TEMPLATE_SCREEN_SHOT, outputFileOnDevice);

    when(adb.runShellWithRetry(SERIAL, args))
        .thenReturn("")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Mocked adb shell exception"))
        .thenReturn("warning");

    androidMediaUtil.takeScreenshot(SERIAL, outputFileOnDevice);

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.takeScreenshot(SERIAL, outputFileOnDevice))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_TAKE_SCREEN_SHOT_ERROR);
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> androidMediaUtil.takeScreenshot(SERIAL, outputFileOnDevice))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_MEDIA_UTIL_TAKE_SCREEN_SHOT_ERROR);
  }
}
