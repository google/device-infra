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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(TestParameterInjector.class)
public final class AndroidVideoDecoratorTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String DEVICE_ID = "localhost:12345";
  private static final String TEST_ID = "fake_test_id";
  private static final Duration REMAINING_TIME = Duration.ofMinutes(30);
  private static final String SCREENSHOT_FILE = "screenshots/screenshot_00000001.png";
  private static final String EMULATOR_VIDEO_FILE = "emulator_video_1.webm";

  private static final AndroidVideoDecoratorSpec DEFAULT_SPEC =
      AndroidVideoDecoratorSpec.getDefaultInstance();
  private static final AndroidVideoDecoratorSpec AUTO_DETECT_SPEC =
      AndroidVideoDecoratorSpec.newBuilder()
          .setAutoDetect(AndroidVideoDecoratorSpec.AutoDetect.getDefaultInstance())
          .build();
  private static final AndroidVideoDecoratorSpec HD_SCREEN_RECORD_SPEC =
      AndroidVideoDecoratorSpec.newBuilder()
          .setHdScreenRecord(AndroidVideoDecoratorSpec.HdScreenRecord.getDefaultInstance())
          .build();
  private static final AndroidVideoDecoratorSpec STITCHED_SCREENSHOTS_SPEC =
      AndroidVideoDecoratorSpec.newBuilder()
          .setStitchedScreenshots(
              AndroidVideoDecoratorSpec.StitchedScreenshots.getDefaultInstance())
          .build();
  private static final AndroidVideoDecoratorSpec EMULATOR_CONSOLE_SPEC =
      AndroidVideoDecoratorSpec.newBuilder()
          .setEmulatorConsole(AndroidVideoDecoratorSpec.EmulatorConsole.getDefaultInstance())
          .build();

  /** Exceptions a recorder may throw, which should be handled the same way by the decorator. */
  enum RecorderFailure {
    MOBILE_HARNESS_EXCEPTION {
      @Override
      Exception create(AndroidErrorId errorId) {
        return new MobileHarnessException(errorId, "recorder error");
      }
    },
    RUNTIME_EXCEPTION {
      @Override
      Exception create(AndroidErrorId errorId) {
        return new IllegalStateException("recorder error");
      }
    };

    abstract Exception create(AndroidErrorId errorId);
  }

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
  @Mock private Log log;
  @Mock private Api api;
  @Mock private Result result;

  private Path genDir;
  private AndroidVideoDecorator decorator;

  @Before
  public void setUp() throws Exception {
    genDir = tempFolder.getRoot().toPath();
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
    when(testInfo.getGenFileDir()).thenReturn(genDir.toString());
  }

  @Test
  public void run_autoMode_emulator_usesEmulatorConsoleRecorder() throws Exception {
    setUpDecorator(FakeAndroidEmulator.class, AUTO_DETECT_SPEC);

    decorator.run(testInfo);

    verify(emulatorConsoleRecorder)
        .start(DEVICE_ID, TEST_ID, genDir, AUTO_DETECT_SPEC, REMAINING_TIME);
    verify(emulatorConsoleRecorder).stop();
    verifyNoInteractions(screenshotRecorder, screenrecord, systemSettingUtil);
  }

  @Test
  public void run_autoMode_realDeviceSdk34_usesScreenrecord() throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, AUTO_DETECT_SPEC);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID))
        .thenReturn(Screenrecord.MIN_SDK_VERSION_FOR_UNLIMITED_RECORDING);

    decorator.run(testInfo);

    verify(screenrecord).start(DEVICE_ID, TEST_ID, genDir, AUTO_DETECT_SPEC, REMAINING_TIME);
    verify(screenrecord).stop();
    verifyNoInteractions(screenshotRecorder, emulatorConsoleRecorder);
  }

  @Test
  public void run_autoMode_realDeviceWithoutVideoRecorder_usesScreenshotRecorder()
      throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, AUTO_DETECT_SPEC);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(17);

    decorator.run(testInfo);

    verify(screenshotRecorder).start(DEVICE_ID, TEST_ID, genDir, AUTO_DETECT_SPEC, REMAINING_TIME);
    verify(screenshotRecorder).stop();
    verifyNoInteractions(screenrecord, emulatorConsoleRecorder);
  }

  @Test
  public void run_autoMode_getSdkVersionFails_usesScreenshotRecorder() throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, AUTO_DETECT_SPEC);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID))
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_SDK_ERROR, "error"));

    decorator.run(testInfo);

    verify(screenshotRecorder).start(DEVICE_ID, TEST_ID, genDir, AUTO_DETECT_SPEC, REMAINING_TIME);
    verifyNoInteractions(screenrecord, emulatorConsoleRecorder);
  }

  @Test
  public void run_videoTypeUnspecified_emulator_usesEmulatorConsoleRecorder() throws Exception {
    setUpDecorator(FakeAndroidEmulator.class, DEFAULT_SPEC);

    decorator.run(testInfo);

    verify(emulatorConsoleRecorder).start(DEVICE_ID, TEST_ID, genDir, DEFAULT_SPEC, REMAINING_TIME);
    verify(emulatorConsoleRecorder).stop();
    verifyNoInteractions(screenshotRecorder, screenrecord);
  }

  @Test
  public void run_videoTypeUnspecified_realDeviceSdk35_usesScreenrecord() throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, DEFAULT_SPEC);
    when(systemSettingUtil.getDeviceSdkVersion(DEVICE_ID)).thenReturn(35);

    decorator.run(testInfo);

    verify(screenrecord).start(DEVICE_ID, TEST_ID, genDir, DEFAULT_SPEC, REMAINING_TIME);
    verify(screenrecord).stop();
    verifyNoInteractions(screenshotRecorder, emulatorConsoleRecorder);
  }

  @Test
  public void run_explicitEmulatorConsoleOnRealDevice_usesEmulatorConsoleRecorder()
      throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, EMULATOR_CONSOLE_SPEC);

    decorator.run(testInfo);

    verify(emulatorConsoleRecorder)
        .start(DEVICE_ID, TEST_ID, genDir, EMULATOR_CONSOLE_SPEC, REMAINING_TIME);
    verify(emulatorConsoleRecorder).stop();
    verifyNoInteractions(screenshotRecorder, screenrecord);
  }

  @Test
  public void run_explicitHdScreenRecord_usesScreenrecordRegardlessOfSdk() throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, HD_SCREEN_RECORD_SPEC);

    decorator.run(testInfo);

    verify(screenrecord).start(DEVICE_ID, TEST_ID, genDir, HD_SCREEN_RECORD_SPEC, REMAINING_TIME);
    verify(screenrecord).stop();
    verifyNoInteractions(screenshotRecorder, emulatorConsoleRecorder, systemSettingUtil);
  }

  @Test
  public void run_noOpDevice_skipsRecording() throws Exception {
    setUpDecorator(FakeNoOpDevice.class, DEFAULT_SPEC);

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verifyNoInteractions(screenshotRecorder, emulatorConsoleRecorder, screenrecord);
  }

  @Test
  public void run_screenrecord_writesCorrectVideoOutputProto() throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, HD_SCREEN_RECORD_SPEC);
    when(screenrecord.stop())
        .thenReturn(ImmutableList.of(genDir.resolve(Screenrecord.VIDEO_FILE_NAME)));

    decorator.run(testInfo);

    VideoOutput videoOutput = readVideoOutput();
    assertThat(videoOutput.getVideoType()).isEqualTo(VideoOutput.VideoType.SCREENRECORD);
    assertThat(videoOutput.getContainerFormat()).isEqualTo(VideoOutput.ContainerFormat.MP4);
    assertThat(videoOutput.hasStartTime()).isTrue();
    assertThat(videoOutput.hasEndTime()).isTrue();
    assertThat(videoOutput.getStartTime().getSeconds())
        .isAtMost(videoOutput.getEndTime().getSeconds());
    assertThat(videoOutput.getGeneratedFileNamesList())
        .containsExactly(Screenrecord.VIDEO_FILE_NAME);
  }

  @Test
  public void run_emulatorConsole_writesCorrectVideoOutputProto() throws Exception {
    setUpDecorator(FakeAndroidEmulator.class, EMULATOR_CONSOLE_SPEC);
    when(emulatorConsoleRecorder.stop())
        .thenReturn(ImmutableList.of(genDir.resolve(EMULATOR_VIDEO_FILE)));

    decorator.run(testInfo);

    VideoOutput videoOutput = readVideoOutput();
    assertThat(videoOutput.getVideoType()).isEqualTo(VideoOutput.VideoType.EMULATOR_CONSOLE);
    assertThat(videoOutput.getContainerFormat()).isEqualTo(VideoOutput.ContainerFormat.WEBM);
    assertThat(videoOutput.getGeneratedFileNamesList()).containsExactly(EMULATOR_VIDEO_FILE);
  }

  @Test
  public void run_stitchedScreenshots_writesCorrectVideoOutputProto() throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, STITCHED_SCREENSHOTS_SPEC);
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of(genDir.resolve(SCREENSHOT_FILE)));

    decorator.run(testInfo);

    VideoOutput videoOutput = readVideoOutput();
    assertThat(videoOutput.getVideoType()).isEqualTo(VideoOutput.VideoType.STITCHED_SCREENSHOTS);
    assertThat(videoOutput.getContainerFormat()).isEqualTo(VideoOutput.ContainerFormat.PNG);
    assertThat(videoOutput.getGeneratedFileNamesList()).containsExactly(SCREENSHOT_FILE);
  }

  @Test
  public void run_videoOnPassFalse_stitchedScreenshots_deletesScreenshotsDirOnPass()
      throws Exception {
    setUpDecorator(
        FakeAndroidRealDevice.class,
        STITCHED_SCREENSHOTS_SPEC.toBuilder().setVideoOnPass(false).build());
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of(genDir.resolve(SCREENSHOT_FILE)));

    decorator.run(testInfo);

    verify(localFileUtil)
        .removeFileOrDir(genDir.resolve(AndroidVideoDecorator.SCREENSHOTS_DIR_NAME).toString());
    assertThat(videoOutputExists()).isFalse();
  }

  @Test
  public void run_videoOnPassFalse_videoRecorder_deletesGeneratedFilesOnPass() throws Exception {
    setUpDecorator(
        FakeAndroidRealDevice.class,
        HD_SCREEN_RECORD_SPEC.toBuilder().setVideoOnPass(false).build());
    Path videoFile = genDir.resolve(Screenrecord.VIDEO_FILE_NAME);
    when(screenrecord.stop()).thenReturn(ImmutableList.of(videoFile));

    decorator.run(testInfo);

    verify(localFileUtil).removeFileOrDir(videoFile.toAbsolutePath().toString());
    assertThat(videoOutputExists()).isFalse();
  }

  @Test
  public void run_videoOnPassFalse_deleteScreenshotsFails_doesNotFailTest() throws Exception {
    setUpDecorator(
        FakeAndroidRealDevice.class,
        STITCHED_SCREENSHOTS_SPEC.toBuilder().setVideoOnPass(false).build());
    doThrow(new MobileHarnessException(BasicErrorId.LOCAL_FILE_OR_DIR_REMOVE_ERROR, "error"))
        .when(localFileUtil)
        .removeFileOrDir(any(String.class));

    // Does not throw.
    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    assertThat(videoOutputExists()).isFalse();
  }

  @Test
  public void run_videoOnPassFalse_deleteVideoFails_doesNotFailTest() throws Exception {
    setUpDecorator(
        FakeAndroidRealDevice.class,
        HD_SCREEN_RECORD_SPEC.toBuilder().setVideoOnPass(false).build());
    when(screenrecord.stop())
        .thenReturn(ImmutableList.of(genDir.resolve(Screenrecord.VIDEO_FILE_NAME)));
    doThrow(new MobileHarnessException(BasicErrorId.LOCAL_FILE_OR_DIR_REMOVE_ERROR, "error"))
        .when(localFileUtil)
        .removeFileOrDir(any(String.class));

    // Does not throw.
    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    assertThat(videoOutputExists()).isFalse();
  }

  @Test
  public void run_videoOnPassFalse_keepsFilesOnFail() throws Exception {
    setUpDecorator(
        FakeAndroidRealDevice.class,
        STITCHED_SCREENSHOTS_SPEC.toBuilder().setVideoOnPass(false).build());
    when(result.get())
        .thenReturn(
            ResultTypeWithCause.create(
                TestResult.FAIL,
                new MobileHarnessException(BasicErrorId.JOB_OR_TEST_RESULT_LEGACY_FAIL, "Failed")));
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of(genDir.resolve(SCREENSHOT_FILE)));

    decorator.run(testInfo);

    verifyNoInteractions(localFileUtil);
    assertThat(readVideoOutput().getGeneratedFileNamesList()).containsExactly(SCREENSHOT_FILE);
  }

  @Test
  public void run_writeOutputError_doesNotFailTest() throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, STITCHED_SCREENSHOTS_SPEC);
    // Makes writing video_output.pb fail.
    when(testInfo.getGenFileDir()).thenReturn("/non/existent/path");

    // Does not throw.
    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
  }

  @Test
  public void run_recorderStartFails_fallsBackToScreenshotRecorder(
      @TestParameter RecorderFailure failure) throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, HD_SCREEN_RECORD_SPEC);
    doThrow(failure.create(AndroidErrorId.ANDROID_MEDIA_UTIL_RECORD_SCREEN_ERROR))
        .when(screenrecord)
        .start(DEVICE_ID, TEST_ID, genDir, HD_SCREEN_RECORD_SPEC, REMAINING_TIME);
    when(screenshotRecorder.stop()).thenReturn(ImmutableList.of(genDir.resolve(SCREENSHOT_FILE)));

    decorator.run(testInfo);

    verify(screenshotRecorder)
        .start(DEVICE_ID, TEST_ID, genDir, HD_SCREEN_RECORD_SPEC, REMAINING_TIME);
    verify(screenshotRecorder).stop();
    verify(screenrecord, never()).stop();
    VideoOutput videoOutput = readVideoOutput();
    assertThat(videoOutput.getVideoType()).isEqualTo(VideoOutput.VideoType.STITCHED_SCREENSHOTS);
    assertThat(videoOutput.getContainerFormat()).isEqualTo(VideoOutput.ContainerFormat.PNG);
  }

  @Test
  public void run_screenshotRecorderStartFails_skipsRecording() throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, STITCHED_SCREENSHOTS_SPEC);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_MEDIA_UTIL_TAKE_SCREEN_SHOT_ERROR, "start error"))
        .when(screenshotRecorder)
        .start(any(), any(), any(), any(), any());

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(screenshotRecorder, never()).stop();
    assertThat(videoOutputExists()).isFalse();
  }

  @Test
  public void run_primaryAndFallbackStartFail_skipsRecording() throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, HD_SCREEN_RECORD_SPEC);
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_MEDIA_UTIL_RECORD_SCREEN_ERROR, "start error"))
        .when(screenrecord)
        .start(any(), any(), any(), any(), any());
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_MEDIA_UTIL_TAKE_SCREEN_SHOT_ERROR, "start error"))
        .when(screenshotRecorder)
        .start(any(), any(), any(), any(), any());

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    verify(screenrecord, never()).stop();
    verify(screenshotRecorder, never()).stop();
    assertThat(videoOutputExists()).isFalse();
  }

  @Test
  public void run_recorderStopFails_doesNotFailTestAndSkipsVideoOutput(
      @TestParameter RecorderFailure failure) throws Exception {
    setUpDecorator(FakeAndroidRealDevice.class, HD_SCREEN_RECORD_SPEC);
    doThrow(failure.create(AndroidErrorId.ANDROID_VIDEO_DECORATOR_SCREENRECORD_FILE_EMPTY))
        .when(screenrecord)
        .stop();

    decorator.run(testInfo);

    verify(decoratedDriver).run(testInfo);
    assertThat(videoOutputExists()).isFalse();
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

  /**
   * Creates {@link #decorator} for a device of {@code deviceClass} and makes the job return {@code
   * spec} for it.
   */
  private void setUpDecorator(Class<? extends Device> deviceClass, AndroidVideoDecoratorSpec spec)
      throws Exception {
    setUpDecorator(mockDevice(deviceClass), spec);
  }

  private void setUpDecorator(Device device, AndroidVideoDecoratorSpec spec) throws Exception {
    useDevice(device);
    when(jobInfo.combinedSpec(decorator, DEVICE_ID)).thenReturn(spec);
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

  private boolean videoOutputExists() {
    return Files.exists(genDir.resolve(AndroidVideoDecorator.VIDEO_OUTPUT_PB_FILE_NAME));
  }

  private VideoOutput readVideoOutput() throws Exception {
    assertThat(videoOutputExists()).isTrue();
    return VideoOutput.parseFrom(
        Files.readAllBytes(genDir.resolve(AndroidVideoDecorator.VIDEO_OUTPUT_PB_FILE_NAME)),
        ProtoExtensionRegistry.getGeneratedRegistry());
  }

  private abstract static class FakeAndroidRealDevice implements Device {}

  private abstract static class FakeAndroidEmulator implements Device {}

  private abstract static class FakeNoOpDevice implements Device {}
}
