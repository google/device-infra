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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.video.EmulatorConsoleRecorder;
import com.google.devtools.mobileharness.platform.android.video.Screenrecord;
import com.google.devtools.mobileharness.platform.android.video.ScreenshotRecorder;
import com.google.devtools.mobileharness.platform.android.video.proto.VideoOutput;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.CountDownTimer;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log.Api;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidVideoDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String DEVICE_ID = "localhost:12345";
  private static final String TEST_ID = "fake_test_id";
  private static final Duration REMAINING_TIME = Duration.ofMinutes(30);

  @Mock private Driver decoratedDriver;
  @Mock private TestInfo testInfo;
  @Mock private TestLocator testLocator;
  @Mock private JobInfo jobInfo;
  @Mock private CountDownTimer testTimer;
  @Mock private ScreenshotRecorder screenshotRecorder;
  @Mock private EmulatorConsoleRecorder emulatorConsoleRecorder;
  @Mock private Screenrecord screenrecord;
  @Mock private AndroidSystemSettingUtil systemSettingUtil;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private Device device;
  @Mock private Log log;
  @Mock private Api api;
  @Mock private Result result;

  private AndroidVideoDecorator decorator;

  @Before
  public void setUp() throws Exception {
    when(decoratedDriver.getDevice()).thenReturn(device);
    when(device.getDeviceId()).thenReturn(DEVICE_ID);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.locator()).thenReturn(testLocator);
    when(testLocator.getId()).thenReturn(TEST_ID);
    when(testInfo.timer()).thenReturn(testTimer);
    when(testTimer.remainingTimeJava()).thenReturn(REMAINING_TIME);
    when(testInfo.log()).thenReturn(log);
    when(log.atInfo()).thenReturn(api);
    when(log.atWarning()).thenReturn(api);
    when(api.alsoTo(any(FluentLogger.class))).thenReturn(api);
    when(api.withCause(any())).thenReturn(api);
    when(testInfo.resultWithCause()).thenReturn(result);
    when(result.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, null));
    when(testInfo.getGenFileDir()).thenReturn(tempFolder.getRoot().getAbsolutePath());

    decorator = createDecorator();
  }

  @Test
  public void run_autoMode_emulator_usesEmulatorConsoleRecorder() throws Exception {
    useDevice(FakeAndroidEmulator.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setAutoDetect(AndroidVideoDecoratorSpec.AutoDetect.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    decorator.run(testInfo);

    Path genDir = tempFolder.getRoot().toPath();
    verify(emulatorConsoleRecorder).start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    verify(emulatorConsoleRecorder).stop();
    verify(screenshotRecorder, never()).start(any(), any(), any(), any(), any());
    verify(systemSettingUtil, never()).getDeviceSdkVersion(any());
  }

  @Test
  public void run_autoMode_realDeviceSdk34_usesScreenrecord() throws Exception {
    useDevice(FakeAndroidRealDevice.class);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(34);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setAutoDetect(AndroidVideoDecoratorSpec.AutoDetect.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    when(screenrecord.stop()).thenReturn(ImmutableList.of());

    decorator.run(testInfo);

    Path genDir = tempFolder.getRoot().toPath();
    verify(screenrecord).start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    verify(screenrecord).stop();
    verify(screenshotRecorder, never()).start(any(), any(), any(), any(), any());
  }

  @Test
  public void run_autoMode_realDeviceSdkWithoutVideoSupport_usesScreenshotRecorder()
      throws Exception {
    useDevice(FakeAndroidRealDevice.class);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(17);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setAutoDetect(AndroidVideoDecoratorSpec.AutoDetect.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of());

    decorator.run(testInfo);

    Path genDir = tempFolder.getRoot().toPath();
    verify(screenshotRecorder).start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    verify(screenshotRecorder).stop();
    verify(emulatorConsoleRecorder, never()).start(any(), any(), any(), any(), any());
    verify(screenrecord, never()).start(any(), any(), any(), any(), any());
  }

  @Test
  public void run_autoMode_getSdkVersionFails_usesScreenshotRecorder() throws Exception {
    useDevice(FakeAndroidRealDevice.class);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_SDK_ERROR, "error"));

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setAutoDetect(AndroidVideoDecoratorSpec.AutoDetect.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of());

    decorator.run(testInfo);

    Path genDir = tempFolder.getRoot().toPath();
    verify(screenshotRecorder).start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    verify(screenrecord, never()).start(any(), any(), any(), any(), any());
  }

  @Test
  public void run_explicitMode_usesRequestedRecorder() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setEmulatorConsole(
                AndroidVideoDecoratorSpec.EmulatorConsole
                    .getDefaultInstance()) // User requested emulator console
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    decorator.run(testInfo);

    Path genDir = tempFolder.getRoot().toPath();
    verify(emulatorConsoleRecorder).start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    verify(emulatorConsoleRecorder).stop();
    verify(screenshotRecorder, never()).start(any(), any(), any(), any(), any());
  }

  @Test
  public void run_explicitHdScreenRecord_usesScreenrecordRegardlessOfSdk() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setHdScreenRecord(AndroidVideoDecoratorSpec.HdScreenRecord.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    when(screenrecord.stop()).thenReturn(ImmutableList.of());

    decorator.run(testInfo);

    Path genDir = tempFolder.getRoot().toPath();
    verify(screenrecord).start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    verify(systemSettingUtil, never()).getDeviceSdkVersion(any());
  }

  @Test
  public void run_screenrecord_writesCorrectVideoOutputProto() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setHdScreenRecord(AndroidVideoDecoratorSpec.HdScreenRecord.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    Path mockFile = tempFolder.getRoot().toPath().resolve(Screenrecord.VIDEO_FILE_NAME);
    when(screenrecord.stop()).thenReturn(ImmutableList.of(mockFile));

    decorator.run(testInfo);

    VideoOutput videoOutput = readVideoOutput();
    assertThat(videoOutput.getVideoType()).isEqualTo(VideoOutput.VideoType.SCREENRECORD);
    assertThat(videoOutput.getContainerFormat()).isEqualTo(VideoOutput.ContainerFormat.MP4);
    assertThat(videoOutput.getGeneratedFileNamesList())
        .containsExactly(Screenrecord.VIDEO_FILE_NAME);
  }

  @Test
  public void run_videoOnPass_false_screenrecord_deletesFilesOnPass() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setHdScreenRecord(AndroidVideoDecoratorSpec.HdScreenRecord.getDefaultInstance())
            .setVideoOnPass(false)
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    Path mockFile = tempFolder.getRoot().toPath().resolve(Screenrecord.VIDEO_FILE_NAME);
    when(screenrecord.stop()).thenReturn(ImmutableList.of(mockFile));

    decorator.run(testInfo);

    verify(localFileUtil).removeFileOrDir(mockFile.toAbsolutePath().toString());
  }

  @Test
  public void run_videoOnPass_false_deletesFilesOnPass() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setStitchedScreenshots(
                AndroidVideoDecoratorSpec.StitchedScreenshots.getDefaultInstance())
            .setVideoOnPass(false)
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    Path mockFile = tempFolder.getRoot().toPath().resolve("screenshots/screenshot_00000001.png");
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of(mockFile));

    decorator.run(testInfo);

    String ssDir =
        Path.of(tempFolder.getRoot().getAbsolutePath(), AndroidVideoDecorator.SCREENSHOTS_DIR_NAME)
            .toString();
    verify(localFileUtil).removeFileOrDir(ssDir);
  }

  @Test
  public void run_videoOnPass_false_emulator_deletesFilesOnPass() throws Exception {
    useDevice(FakeAndroidEmulator.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setEmulatorConsole(AndroidVideoDecoratorSpec.EmulatorConsole.getDefaultInstance())
            .setVideoOnPass(false)
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    Path mockFile = tempFolder.getRoot().toPath().resolve("emulator_video_1.webm");
    when(emulatorConsoleRecorder.stop()).thenReturn(ImmutableList.of(mockFile));

    decorator.run(testInfo);

    verify(localFileUtil).removeFileOrDir(mockFile.toAbsolutePath().toString());
  }

  @Test
  public void run_videoOnPass_false_keepsFilesOnFail() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setStitchedScreenshots(
                AndroidVideoDecoratorSpec.StitchedScreenshots.getDefaultInstance())
            .setVideoOnPass(false)
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.FAIL,
                new MobileHarnessException(BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_FAIL, "Failed")));

    Path mockFile = tempFolder.getRoot().toPath().resolve("screenshots/screenshot_00000001.png");
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of(mockFile));

    decorator.run(testInfo);

    verify(localFileUtil, never()).removeFileOrDir(any(Path.class));
    verify(localFileUtil, never()).removeFileOrDir(any(String.class));

    VideoOutput videoOutput = readVideoOutput();
    assertThat(videoOutput.getGeneratedFileNamesList())
        .containsExactly("screenshots/screenshot_00000001.png");
  }

  @Test
  public void run_noOpDevice_skipsRecording() throws Exception {
    useDevice(FakeNoOpDevice.class);

    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    decorator.run(testInfo);

    verify(screenshotRecorder, never()).start(any(), any(), any(), any(), any());
    verify(emulatorConsoleRecorder, never()).start(any(), any(), any(), any(), any());
    verify(screenrecord, never()).start(any(), any(), any(), any(), any());
  }

  @Test
  public void run_emulator_writesCorrectVideoOutputProto() throws Exception {
    useDevice(FakeAndroidEmulator.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setEmulatorConsole(AndroidVideoDecoratorSpec.EmulatorConsole.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    Path mockFile = tempFolder.getRoot().toPath().resolve("emulator_video_1.webm");
    when(emulatorConsoleRecorder.stop()).thenReturn(ImmutableList.of(mockFile));

    decorator.run(testInfo);

    VideoOutput videoOutput = readVideoOutput();
    assertThat(videoOutput.getVideoType()).isEqualTo(VideoOutput.VideoType.EMULATOR_CONSOLE);
    assertThat(videoOutput.getContainerFormat()).isEqualTo(VideoOutput.ContainerFormat.WEBM);
    assertThat(videoOutput.getGeneratedFileNamesList()).containsExactly("emulator_video_1.webm");
  }

  @Test
  public void run_realDevice_writesCorrectVideoOutputProto() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setStitchedScreenshots(
                AndroidVideoDecoratorSpec.StitchedScreenshots.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    Path mockFile = tempFolder.getRoot().toPath().resolve("screenshots/screenshot_00000001.png");
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of(mockFile));

    decorator.run(testInfo);

    VideoOutput videoOutput = readVideoOutput();
    assertThat(videoOutput.getVideoType()).isEqualTo(VideoOutput.VideoType.STITCHED_SCREENSHOTS);
    assertThat(videoOutput.getContainerFormat()).isEqualTo(VideoOutput.ContainerFormat.PNG);
    assertThat(videoOutput.getGeneratedFileNamesList())
        .containsExactly("screenshots/screenshot_00000001.png");
  }

  @Test
  public void run_writeOutputError_throwsMobileHarnessException() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setStitchedScreenshots(
                AndroidVideoDecoratorSpec.StitchedScreenshots.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of());

    // Make getGenFileDir return a path that cannot be written to
    when(testInfo.getGenFileDir()).thenReturn("/non/existent/path");

    assertThrows(MobileHarnessException.class, () -> decorator.run(testInfo));
  }

  @Test
  public void run_videoTypeUnspecified_emulator_usesEmulatorConsoleRecorder() throws Exception {
    useDevice(FakeAndroidEmulator.class);

    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);

    decorator.run(testInfo);

    Path genDir = tempFolder.getRoot().toPath();
    verify(emulatorConsoleRecorder).start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    verify(emulatorConsoleRecorder).stop();
    verify(screenshotRecorder, never()).start(any(), any(), any(), any(), any());
  }

  @Test
  public void run_videoTypeUnspecified_realDeviceSdk34_usesScreenrecord() throws Exception {
    useDevice(FakeAndroidRealDevice.class);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(35);

    AndroidVideoDecoratorSpec spec = AndroidVideoDecoratorSpec.getDefaultInstance();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    when(screenrecord.stop()).thenReturn(ImmutableList.of());

    decorator.run(testInfo);

    Path genDir = tempFolder.getRoot().toPath();
    verify(screenrecord).start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    verify(screenrecord).stop();
    verify(screenshotRecorder, never()).start(any(), any(), any(), any(), any());
    verify(emulatorConsoleRecorder, never()).start(any(), any(), any(), any(), any());
  }

  @Test
  public void run_recorderStartFails_fallsBackToScreenshotRecorder() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setHdScreenRecord(AndroidVideoDecoratorSpec.HdScreenRecord.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    Path genDir = tempFolder.getRoot().toPath();
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_SDK_ERROR, "start error"))
        .when(screenrecord)
        .start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    Path mockFile = genDir.resolve("screenshots/screenshot_00000001.png");
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of(mockFile));

    decorator.run(testInfo);

    verify(screenshotRecorder).start(DEVICE_ID, TEST_ID, genDir, spec, REMAINING_TIME);
    verify(screenshotRecorder).stop();
    verify(screenrecord, never()).stop();
    VideoOutput videoOutput = readVideoOutput();
    assertThat(videoOutput.getVideoType()).isEqualTo(VideoOutput.VideoType.STITCHED_SCREENSHOTS);
    assertThat(videoOutput.getContainerFormat()).isEqualTo(VideoOutput.ContainerFormat.PNG);
  }

  @Test
  public void run_screenshotRecorderStartFails_skipsRecording() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setStitchedScreenshots(
                AndroidVideoDecoratorSpec.StitchedScreenshots.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_SDK_ERROR, "start error"))
        .when(screenshotRecorder)
        .start(any(), any(), any(), any(), any());

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(screenshotRecorder, never()).stop();
    assertThat(
            Files.exists(
                tempFolder
                    .getRoot()
                    .toPath()
                    .resolve(AndroidVideoDecorator.VIDEO_OUTPUT_PB_FILE_NAME)))
        .isFalse();
  }

  @Test
  public void run_recorderStopFails_doesNotFailTestAndSkipsVideoOutput() throws Exception {
    useDevice(FakeAndroidRealDevice.class);

    AndroidVideoDecoratorSpec spec =
        AndroidVideoDecoratorSpec.newBuilder()
            .setHdScreenRecord(AndroidVideoDecoratorSpec.HdScreenRecord.getDefaultInstance())
            .build();
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
    when(screenrecord.stop())
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_VIDEO_DECORATOR_SCREENRECORD_FILE_EMPTY, "empty"));

    decorator.run(testInfo);

    assertThat(
            Files.exists(
                tempFolder
                    .getRoot()
                    .toPath()
                    .resolve(AndroidVideoDecorator.VIDEO_OUTPUT_PB_FILE_NAME)))
        .isFalse();
  }

  private AndroidVideoDecorator createDecorator() {
    return new AndroidVideoDecorator(
        decoratedDriver,
        testInfo,
        screenshotRecorder,
        emulatorConsoleRecorder,
        screenrecord,
        systemSettingUtil,
        localFileUtil);
  }

  private void useDevice(Class<? extends Device> deviceClass) {
    useDevice(mockDevice(deviceClass));
  }

  private void useDevice(Device mockDevice) {
    when(decoratedDriver.getDevice()).thenReturn(mockDevice);
    decorator = createDecorator();
  }

  private static Device mockDevice(Class<? extends Device> deviceClass) {
    Device mockDevice = Mockito.mock(deviceClass);
    when(mockDevice.getDeviceId()).thenReturn(DEVICE_ID);
    return mockDevice;
  }

  private VideoOutput readVideoOutput() throws Exception {
    Path pbFile =
        tempFolder.getRoot().toPath().resolve(AndroidVideoDecorator.VIDEO_OUTPUT_PB_FILE_NAME);
    assertThat(Files.exists(pbFile)).isTrue();
    return VideoOutput.parseFrom(
        Files.readAllBytes(pbFile), ProtoExtensionRegistry.getGeneratedRegistry());
  }

  private abstract static class FakeAndroidRealDevice implements Device {}

  private abstract static class FakeAndroidEmulator implements Device {}

  private abstract static class FakeNoOpDevice implements Device {}
}
