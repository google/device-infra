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

package com.google.devtools.mobileharness.platform.android.video;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.platform.android.media.ScreenRecordArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ScreenrecordTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String DEVICE_ID = "device_id";
  private static final String TEST_ID = "test_id";
  private static final String DEVICE_VIDEO_PATH = "/data/local/tmp/mh_screenrecord_test_id.mp4";

  @Mock private AndroidMediaUtil androidMediaUtil;
  @Mock private AndroidFileUtil androidFileUtil;
  @Mock private AndroidSystemSettingUtil systemSettingUtil;
  @Mock private CommandProcess commandProcess;
  @Mock private Adb adb;

  @Captor private ArgumentCaptor<ScreenRecordArgs> argsCaptor;

  private Path genDir;
  private Path hostVideoPath;
  private Screenrecord recorder;

  @Before
  public void setUp() throws Exception {
    genDir = tempFolder.getRoot().toPath();
    hostVideoPath = genDir.resolve(Screenrecord.VIDEO_FILE_NAME);
    when(androidMediaUtil.recordScreen(eq(DEVICE_ID), any(ScreenRecordArgs.class), any()))
        .thenReturn(commandProcess);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(34);
    recorder = new Screenrecord(androidMediaUtil, androidFileUtil, systemSettingUtil, adb);
  }

  @Test
  public void start_defaultSpecOnSdk34_recordsWithDefaultArgsAndNoTimeLimit() throws Exception {
    startWithDefaultSpec();

    verify(androidMediaUtil)
        .recordScreen(eq(DEVICE_ID), argsCaptor.capture(), eq(Screenrecord.MAX_RECORDING_DURATION));
    ScreenRecordArgs args = argsCaptor.getValue();
    assertThat(args.timeLimit()).hasValue(Duration.ZERO);
    assertThat(args.bitRate()).hasValue(Screenrecord.DEFAULT_BIT_RATE);
    assertThat(args.size()).isAbsent();
    assertThat(args.verbose()).isTrue();
  }

  @Test
  public void start_removesStaleDeviceVideo() throws Exception {
    startWithDefaultSpec();

    InOrder inOrder = inOrder(androidFileUtil, androidMediaUtil);
    inOrder.verify(androidFileUtil).removeFiles(DEVICE_ID, DEVICE_VIDEO_PATH);
    inOrder.verify(androidMediaUtil).recordScreen(eq(DEVICE_ID), any(), any());
  }

  @Test
  public void start_withMaxDuration_usesItAsTimeout() throws Exception {
    Duration maxDuration = Duration.ofMinutes(5);

    recorder.start(
        DEVICE_ID, TEST_ID, genDir, AndroidVideoDecoratorSpec.getDefaultInstance(), maxDuration);

    verify(androidMediaUtil)
        .recordScreen(eq(DEVICE_ID), any(ScreenRecordArgs.class), eq(maxDuration));
  }

  @Test
  public void start_sdkBelow34_recordsWithDefaultTimeLimit() throws Exception {
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(33);

    startWithDefaultSpec();

    assertThat(captureRecordScreenArgs().timeLimit()).isAbsent();
  }

  @Test
  public void start_getSdkVersionFails_recordsWithDefaultTimeLimit() throws Exception {
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_SDK_ERROR, "error"));

    startWithDefaultSpec();

    assertThat(captureRecordScreenArgs().timeLimit()).isAbsent();
  }

  @Test
  public void start_customConfig_usesBitRateAndResolution() throws Exception {
    recorder.start(
        DEVICE_ID,
        TEST_ID,
        genDir,
        hdScreenRecordSpec(
            AndroidVideoDecoratorSpec.HdScreenRecord.newBuilder()
                .setBitRate(1_500_000)
                .setResolution("720x1280")));

    ScreenRecordArgs args = captureRecordScreenArgs();
    assertThat(args.bitRate()).hasValue(1_500_000);
    assertThat(args.size()).hasValue("720x1280");
  }

  @Test
  public void start_bitRateBelowMin_clampsToMin() throws Exception {
    recorder.start(
        DEVICE_ID,
        TEST_ID,
        genDir,
        hdScreenRecordSpec(AndroidVideoDecoratorSpec.HdScreenRecord.newBuilder().setBitRate(1)));

    assertThat(captureRecordScreenArgs().bitRate()).hasValue(Screenrecord.MIN_BIT_RATE);
  }

  @Test
  public void start_bitRateAboveMax_clampsToMax() throws Exception {
    recorder.start(
        DEVICE_ID,
        TEST_ID,
        genDir,
        hdScreenRecordSpec(
            AndroidVideoDecoratorSpec.HdScreenRecord.newBuilder()
                .setBitRate(Screenrecord.MAX_BIT_RATE + 1)));

    assertThat(captureRecordScreenArgs().bitRate()).hasValue(Screenrecord.MAX_BIT_RATE);
  }

  @Test
  public void start_invalidResolution_isIgnored() throws Exception {
    recorder.start(
        DEVICE_ID,
        TEST_ID,
        genDir,
        hdScreenRecordSpec(
            AndroidVideoDecoratorSpec.HdScreenRecord.newBuilder().setResolution("invalid")));

    assertThat(captureRecordScreenArgs().size()).isAbsent();
  }

  @Test
  public void start_fpsSet_isIgnored() throws Exception {
    recorder.start(
        DEVICE_ID,
        TEST_ID,
        genDir,
        hdScreenRecordSpec(AndroidVideoDecoratorSpec.HdScreenRecord.newBuilder().setFps(30)));

    // screenrecord has no frame rate option, so the recording still starts with the defaults.
    ScreenRecordArgs args = captureRecordScreenArgs();
    assertThat(args.bitRate()).hasValue(Screenrecord.DEFAULT_BIT_RATE);
    assertThat(args.size()).isAbsent();
  }

  @Test
  public void stop_stopsPullsAndRemovesVideo() throws Exception {
    startWithDefaultSpec();
    simulatePulledVideo();

    List<Path> result = recorder.stop();

    InOrder inOrder = inOrder(androidMediaUtil, androidFileUtil);
    inOrder.verify(androidMediaUtil).stopScreenRecord(DEVICE_ID, Screenrecord.STOP_TIMEOUT);
    inOrder.verify(androidFileUtil).pull(DEVICE_ID, DEVICE_VIDEO_PATH, hostVideoPath.toString());
    inOrder.verify(androidFileUtil).removeFiles(DEVICE_ID, DEVICE_VIDEO_PATH);
    verify(adb, never()).runShell(anyString(), anyString());
    verify(commandProcess, never()).killForcibly();
    assertThat(result).containsExactly(hostVideoPath);
  }

  @Test
  public void stop_processStillAlive_killsForcibly() throws Exception {
    when(commandProcess.isAlive()).thenReturn(true);
    startWithDefaultSpec();
    simulatePulledVideo();

    assertThat(recorder.stop()).containsExactly(hostVideoPath);

    verify(commandProcess).killForcibly();
  }

  @Test
  public void stop_gracefulStopFails_forceStopsAndStillPullsVideo() throws Exception {
    stubGracefulStopFails();
    startWithDefaultSpec();
    simulatePulledVideo();

    List<Path> result = recorder.stop();

    verify(adb).runShell(DEVICE_ID, Screenrecord.FORCE_STOP_CMD);
    verify(androidFileUtil).pull(DEVICE_ID, DEVICE_VIDEO_PATH, hostVideoPath.toString());
    assertThat(result).containsExactly(hostVideoPath);
  }

  @Test
  public void stop_forceStopFails_stillPullsVideo() throws Exception {
    stubGracefulStopFails();
    when(adb.runShell(DEVICE_ID, Screenrecord.FORCE_STOP_CMD))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_FAILURE, "error"));
    startWithDefaultSpec();
    simulatePulledVideo();

    assertThat(recorder.stop()).containsExactly(hostVideoPath);
  }

  @Test
  public void stop_removeDeviceVideoFails_stillReturnsVideo() throws Exception {
    startWithDefaultSpec();
    simulatePulledVideo();
    doThrow(new MobileHarnessException(AndroidErrorId.ANDROID_FILE_UTIL_REMOVE_FILE_ERROR, "error"))
        .when(androidFileUtil)
        .removeFiles(DEVICE_ID, DEVICE_VIDEO_PATH);

    assertThat(recorder.stop()).containsExactly(hostVideoPath);
  }

  @Test
  public void stop_gracefulStopThrowsRuntimeException_stillKillsHostProcess() throws Exception {
    when(commandProcess.isAlive()).thenReturn(true);
    doThrow(new IllegalStateException("stop error"))
        .when(androidMediaUtil)
        .stopScreenRecord(anyString(), any());
    startWithDefaultSpec();

    assertThrows(IllegalStateException.class, () -> recorder.stop());

    verify(commandProcess).killForcibly();
    // The recorder is reset, so a second stop() is a no-op.
    assertThat(recorder.stop()).isEmpty();
  }

  @Test
  public void stop_pullFails_removesDeviceVideoAndThrows() throws Exception {
    doThrow(new MobileHarnessException(AndroidErrorId.ANDROID_FILE_UTIL_PULL_FILE_ERROR, "error"))
        .when(androidFileUtil)
        .pull(anyString(), anyString(), anyString());
    startWithDefaultSpec();
    clearInvocations(androidFileUtil);

    MobileHarnessException e = assertThrows(MobileHarnessException.class, () -> recorder.stop());

    assertThat(e.getErrorId()).isEqualTo(AndroidErrorId.ANDROID_FILE_UTIL_PULL_FILE_ERROR);
    verify(androidFileUtil).removeFiles(DEVICE_ID, DEVICE_VIDEO_PATH);
  }

  @Test
  public void stop_videoAbsent_throws() throws Exception {
    startWithDefaultSpec();

    MobileHarnessException e = assertThrows(MobileHarnessException.class, () -> recorder.stop());

    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_VIDEO_DECORATOR_SCREENRECORD_FILE_ABSENT);
  }

  @Test
  public void stop_videoEmpty_throws() throws Exception {
    startWithDefaultSpec();
    // Simulates pulling an empty video.
    Files.createFile(hostVideoPath);

    MobileHarnessException e = assertThrows(MobileHarnessException.class, () -> recorder.stop());

    assertThat(e.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_VIDEO_DECORATOR_SCREENRECORD_FILE_EMPTY);
  }

  @Test
  public void stop_notStarted_returnsEmptyList() throws Exception {
    assertThat(recorder.stop()).isEmpty();

    verify(androidMediaUtil, never()).stopScreenRecord(anyString(), any());
    verify(androidFileUtil, never()).pull(anyString(), anyString(), anyString());
  }

  private void startWithDefaultSpec() throws Exception {
    recorder.start(DEVICE_ID, TEST_ID, genDir, AndroidVideoDecoratorSpec.getDefaultInstance());
  }

  private static AndroidVideoDecoratorSpec hdScreenRecordSpec(
      AndroidVideoDecoratorSpec.HdScreenRecord.Builder config) {
    return AndroidVideoDecoratorSpec.newBuilder().setHdScreenRecord(config).build();
  }

  private ScreenRecordArgs captureRecordScreenArgs() throws Exception {
    verify(androidMediaUtil).recordScreen(eq(DEVICE_ID), argsCaptor.capture(), any());
    return argsCaptor.getValue();
  }

  private void stubGracefulStopFails() throws Exception {
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_MEDIA_UTIL_STOP_SCREEN_RECORD_TIMEOUT, "timeout"))
        .when(androidMediaUtil)
        .stopScreenRecord(anyString(), any());
  }

  /** Makes {@code androidFileUtil.pull()} write the host video, like a real pull. */
  private void simulatePulledVideo() throws Exception {
    doAnswer(
            invocation -> {
              Files.writeString(hostVideoPath, "fake video");
              return "";
            })
        .when(androidFileUtil)
        .pull(DEVICE_ID, DEVICE_VIDEO_PATH, hostVideoPath.toString());
  }
}
