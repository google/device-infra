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

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.video.AndroidVideoRecorder;
import com.google.devtools.mobileharness.platform.android.video.EmulatorConsoleRecorder;
import com.google.devtools.mobileharness.platform.android.video.Screenrecord;
import com.google.devtools.mobileharness.platform.android.video.ScreenshotRecorder;
import com.google.devtools.mobileharness.platform.android.video.proto.VideoOutput;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** Decorator for capturing video or screenshots of Android devices during tests. */
@DecoratorAnnotation(
    help = "Decorator for capturing video or screenshots of Android devices during tests.")
public class AndroidVideoDecorator extends LifecycleDecorator
    implements SpecConfigable<AndroidVideoDecoratorSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** File name of the video output PB file. */
  public static final String VIDEO_OUTPUT_PB_FILE_NAME = "video_output.pb";

  /** Prefix of the emulator video files. */
  public static final String EMULATOR_VIDEO_PREFIX = "emulator_video_";

  /** Directory name of the screenshots. */
  public static final String SCREENSHOTS_DIR_NAME = "screenshots";

  private AndroidVideoDecoratorSpec spec;
  private AndroidVideoRecorder recorder;
  private final ScreenshotRecorder screenshotRecorder;
  private final EmulatorConsoleRecorder emulatorConsoleRecorder;
  private final Screenrecord screenrecord;
  private final AndroidSystemSettingUtil systemSettingUtil;
  private final LocalFileUtil localFileUtil;

  private VideoOutput.VideoType usedVideoType;
  private Instant startTime;

  @Inject
  AndroidVideoDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      ScreenshotRecorder screenshotRecorder,
      EmulatorConsoleRecorder emulatorConsoleRecorder,
      Screenrecord screenrecord,
      AndroidSystemSettingUtil systemSettingUtil,
      LocalFileUtil localFileUtil) {
    super(decoratedDriver, testInfo);
    this.screenshotRecorder = screenshotRecorder;
    this.emulatorConsoleRecorder = emulatorConsoleRecorder;
    this.screenrecord = screenrecord;
    this.systemSettingUtil = systemSettingUtil;
    this.localFileUtil = localFileUtil;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    Device device = getDevice();
    spec = testInfo.jobInfo().combinedSpec(this, device.getDeviceId());
    String deviceType = device.getClass().getSimpleName();

    if (deviceType.contains("NoOp")) {
      logger.atInfo().log("Skipping AndroidVideoDecorator for NoOp device.");
      return SetupResult.continueDecorated();
    }

    AndroidVideoDecoratorSpec.VideoTypeSpecCase requestedType = spec.getVideoTypeSpecCase();

    if (requestedType == AndroidVideoDecoratorSpec.VideoTypeSpecCase.AUTO_DETECT
        || requestedType == AndroidVideoDecoratorSpec.VideoTypeSpecCase.VIDEOTYPESPEC_NOT_SET) {
      requestedType = autoDetectVideoType(testInfo, device.getDeviceId(), deviceType);
    }

    switch (requestedType) {
      case EMULATOR_CONSOLE -> {
        recorder = emulatorConsoleRecorder;
        usedVideoType = VideoOutput.VideoType.EMULATOR_CONSOLE;
      }
      case STITCHED_SCREENSHOTS -> {
        recorder = screenshotRecorder;
        usedVideoType = VideoOutput.VideoType.STITCHED_SCREENSHOTS;
      }
      case HD_SCREEN_RECORD -> {
        recorder = screenrecord;
        usedVideoType = VideoOutput.VideoType.SCREENRECORD;
      }
      default -> {
        logger.atWarning().log(
            "Unsupported video type: %s, using STITCHED_SCREENSHOTS instead.", requestedType);
        recorder = screenshotRecorder;
        usedVideoType = VideoOutput.VideoType.STITCHED_SCREENSHOTS;
      }
    }
    startTime = Instant.now();
    startRecorder(testInfo, device.getDeviceId());
    if (recorder != null) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "AndroidVideoDecorator uses video type %s for device %s (%s)",
              usedVideoType, device.getDeviceId(), deviceType);
    }
    return SetupResult.continueDecorated();
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    List<Path> generatedFiles = new ArrayList<>();
    if (recorder != null) {
      try {
        generatedFiles = recorder.stop();
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to stop %s recorder, the video may be missing.", usedVideoType);
        return;
      }
      if (!spec.getVideoOnPass() && testInfo.resultWithCause().get().type() == TestResult.PASS) {
        cleanupVideoFiles(testInfo, generatedFiles);
        return;
      }

      Instant endTime = Instant.now();

      VideoOutput.Builder outputBuilder =
          VideoOutput.newBuilder()
              .setVideoType(usedVideoType)
              .setStartTime(TimeUtils.toProtoTimestamp(startTime))
              .setEndTime(TimeUtils.toProtoTimestamp(endTime))
              .setContainerFormat(getContainerFormat(usedVideoType));

      Path genFileDir = Path.of(testInfo.getGenFileDir());
      for (Path p : generatedFiles) {
        outputBuilder.addGeneratedFileNames(genFileDir.relativize(p).toString());
      }
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "AndroidVideoDecorator generated %s video files: %s",
              usedVideoType, outputBuilder.getGeneratedFileNamesList());

      try {
        Path pbFile = Path.of(testInfo.getGenFileDir(), VIDEO_OUTPUT_PB_FILE_NAME);
        Files.write(pbFile, outputBuilder.build().toByteArray());
      } catch (IOException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_VIDEO_DECORATOR_WRITE_OUTPUT_ERROR,
            "Failed to write " + VIDEO_OUTPUT_PB_FILE_NAME,
            e);
      }
    }
  }

  /**
   * Starts {@link #recorder}. Falls back to {@link #screenshotRecorder} if it fails to start, and
   * disables recording if the screenshot recorder fails too, so recording problems never fail the
   * test.
   */
  private void startRecorder(TestInfo testInfo, String deviceId)
      throws MobileHarnessException, InterruptedException {
    Path genFileDir = Path.of(testInfo.getGenFileDir());
    String testId = testInfo.locator().getId();
    try {
      recorder.start(deviceId, testId, genFileDir, spec, testInfo.timer().remainingTimeJava());
      return;
    } catch (MobileHarnessException e) {
      if (usedVideoType == VideoOutput.VideoType.STITCHED_SCREENSHOTS) {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .withCause(e)
            .log("Failed to start %s recorder, skip recording.", usedVideoType);
        recorder = null;
        return;
      }
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log(
              "Failed to start %s recorder, falling back to %s.",
              usedVideoType, VideoOutput.VideoType.STITCHED_SCREENSHOTS);
    }

    recorder = screenshotRecorder;
    usedVideoType = VideoOutput.VideoType.STITCHED_SCREENSHOTS;
    try {
      recorder.start(deviceId, testId, genFileDir, spec, testInfo.timer().remainingTimeJava());
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to start %s recorder, skip recording.", usedVideoType);
      recorder = null;
    }
  }

  /**
   * Selects the best supported video type for the device.
   *
   * <ul>
   *   <li>Emulator: EMULATOR_CONSOLE.
   *   <li>Physical device with SDK >= 34: HD_SCREEN_RECORD (native screenrecord without time
   *       limit).
   *   <li>Physical device with a supported 32-bit ARM SDK in [{@code
   *       Videocat.MIN_SUPPORTED_SDK_VERSION}, {@code Videocat.MAX_SUPPORTED_SDK_VERSION}]:
   *       VIDEOCAT.
   *   <li>Otherwise: STITCHED_SCREENSHOTS.
   * </ul>
   */
  private AndroidVideoDecoratorSpec.VideoTypeSpecCase autoDetectVideoType(
      TestInfo testInfo, String deviceId, String deviceType) throws InterruptedException {
    if (deviceType.contains("Emulator")) {
      return AndroidVideoDecoratorSpec.VideoTypeSpecCase.EMULATOR_CONSOLE;
    }
    // TODO: Fall back to STITCHED_SCREENSHOTS if the device reports (via device
    // property) that screen recording is not supported.
    int sdkVersion;
    try {
      sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to get SDK version of device %s, using STITCHED_SCREENSHOTS.", deviceId);
      return AndroidVideoDecoratorSpec.VideoTypeSpecCase.STITCHED_SCREENSHOTS;
    }
    if (sdkVersion >= Screenrecord.MIN_SDK_VERSION_FOR_UNLIMITED_RECORDING) {
      return AndroidVideoDecoratorSpec.VideoTypeSpecCase.HD_SCREEN_RECORD;
    }
    return AndroidVideoDecoratorSpec.VideoTypeSpecCase.STITCHED_SCREENSHOTS;
  }

  private void cleanupVideoFiles(TestInfo testInfo, List<Path> generatedFiles)
      throws InterruptedException {
    String deviceId = getDevice().getDeviceId();
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Test passed and video_on_pass is false, deleting video files on Lab server.");
    try {
      if (usedVideoType == VideoOutput.VideoType.STITCHED_SCREENSHOTS) {
        String ssDir = Path.of(testInfo.getGenFileDir(), SCREENSHOTS_DIR_NAME).toString();
        localFileUtil.removeFileOrDir(ssDir);
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Removed screenshots directory on host for device %s", deviceId);
      } else {
        try {
          for (Path p : generatedFiles) {
            localFileUtil.removeFileOrDir(p.toAbsolutePath().toString());
          }
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log("Failed to clean up generated video files.");
        }
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Removed %s video files on host for device %s", usedVideoType, deviceId);
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Failed to remove video file on host for device %s when test pass:%n%s",
              deviceId, e.getMessage());
    }
  }

  private static VideoOutput.ContainerFormat getContainerFormat(VideoOutput.VideoType videoType) {
    return switch (videoType) {
      case EMULATOR_CONSOLE -> VideoOutput.ContainerFormat.WEBM;
      case STITCHED_SCREENSHOTS -> VideoOutput.ContainerFormat.PNG;
      case VIDEOCAT -> VideoOutput.ContainerFormat.MPEGTS;
      case SCREENRECORD -> VideoOutput.ContainerFormat.MP4;
      default -> VideoOutput.ContainerFormat.CONTAINER_FORMAT_UNSPECIFIED;
    };
  }
}
