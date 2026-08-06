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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.protobuf.Timestamp;
import com.google.wireless.qa.mobileharness.shared.api.decorator.base.LifecycleDecorator;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec.VideoType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.VideoOutput;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import javax.inject.Inject;

/** Decorator for capturing video or screenshots of Android devices during tests. */
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
  private final LocalFileUtil localFileUtil;

  private VideoOutput.VideoType usedVideoType;
  private Instant startTime;

  @Inject
  AndroidVideoDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      ScreenshotRecorder screenshotRecorder,
      EmulatorConsoleRecorder emulatorConsoleRecorder,
      LocalFileUtil localFileUtil) {
    super(decoratedDriver, testInfo);
    this.screenshotRecorder = screenshotRecorder;
    this.emulatorConsoleRecorder = emulatorConsoleRecorder;
    this.localFileUtil = localFileUtil;
  }

  @Override
  protected SetupResult setUp(SetupContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    spec = testInfo.jobInfo().combinedSpec(this);
    Device device = getDevice();
    String deviceType = device.getClass().getSimpleName();

    VideoType requestedType = spec.hasVideoType() ? spec.getVideoType() : VideoType.AUTO;

    if (deviceType.contains("NoOp")) {
      logger.atInfo().log("Skipping AndroidVideoDecorator for NoOp device.");
      return SetupResult.continueDecorated();
    }

    if (requestedType == VideoType.AUTO || requestedType == VideoType.VIDEO_TYPE_UNSPECIFIED) {
      if (deviceType.contains("Emulator")) {
        requestedType = VideoType.EMULATOR_CONSOLE;
      } else {
        requestedType = VideoType.SCREENSHOTS;
      }
    }

    switch (requestedType) {
      case EMULATOR_CONSOLE -> {
        recorder = emulatorConsoleRecorder;
        usedVideoType = VideoOutput.VideoType.EMULATOR_CONSOLE;
      }
      case SCREENSHOTS -> {
        recorder = screenshotRecorder;
        usedVideoType = VideoOutput.VideoType.SCREENSHOTS;
      }
      // TODO: implement VIDEOCAT and HD_SCREEN_RECORD
      default -> {
        logger.atWarning().log(
            "Unsupported video type: %s, using SCREENSHOTS instead.", requestedType);
        recorder = screenshotRecorder;
        usedVideoType = VideoOutput.VideoType.SCREENSHOTS;
      }
    }

    startTime = Instant.now();
    recorder.start(testInfo, device, spec);
    return SetupResult.continueDecorated();
  }

  @Override
  protected void tearDown(TeardownContext context)
      throws MobileHarnessException, InterruptedException {
    TestInfo testInfo = context.testInfo();
    if (recorder != null) {
      try {
        recorder.stop(testInfo, getDevice());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Error during recorder.stop()");
      }
      if (!spec.getVideoOnPass() && testInfo.resultWithCause().get().type() == TestResult.PASS) {
        String deviceId = getDevice().getDeviceId();
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Test passed and video_on_pass is false, deleting video files on Lab server.");
        try {
          if (usedVideoType == VideoOutput.VideoType.SCREENSHOTS) {
            String ssDir = Path.of(testInfo.getGenFileDir(), SCREENSHOTS_DIR_NAME).toString();
            localFileUtil.removeFileOrDir(ssDir);
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Removed screenshots directory on host for device %s", deviceId);
          } else if (usedVideoType == VideoOutput.VideoType.EMULATOR_CONSOLE) {
            try {
              File[] files = localFileUtil.listFilesOrDirs(testInfo.getGenFileDir());
              if (files != null) {
                for (File f : files) {
                  if (f.getName().startsWith(EMULATOR_VIDEO_PREFIX)
                      && f.getName().endsWith(".webm")) {
                    localFileUtil.removeFileOrDir(f.getAbsolutePath());
                  }
                }
              }
            } catch (MobileHarnessException e) {
              logger.atWarning().withCause(e).log(
                  "Failed to list emulator video files for deletion");
            }
            testInfo
                .log()
                .atInfo()
                .alsoTo(logger)
                .log("Removed emulator video files on host for device %s", deviceId);
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
        return;
      }

      Instant endTime = Instant.now();

      VideoOutput.Builder outputBuilder = VideoOutput.newBuilder();
      outputBuilder
          .setVideoType(usedVideoType)
          .setStartTime(
              Timestamp.newBuilder()
                  .setSeconds(startTime.getEpochSecond())
                  .setNanos(startTime.getNano())
                  .build())
          .setEndTime(
              Timestamp.newBuilder()
                  .setSeconds(endTime.getEpochSecond())
                  .setNanos(endTime.getNano())
                  .build());

      if (usedVideoType == VideoOutput.VideoType.EMULATOR_CONSOLE) {
        outputBuilder.setContainerFormat(VideoOutput.ContainerFormat.WEBM);
      } else if (usedVideoType == VideoOutput.VideoType.SCREENSHOTS) {
        outputBuilder.setContainerFormat(VideoOutput.ContainerFormat.PNG);
      }

      if (usedVideoType == VideoOutput.VideoType.SCREENSHOTS) {
        String ssDir = Path.of(testInfo.getGenFileDir(), SCREENSHOTS_DIR_NAME).toString();
        try {
          File[] files = localFileUtil.listFilesOrDirs(ssDir);
          if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
              outputBuilder.addGeneratedFileNames(SCREENSHOTS_DIR_NAME + "/" + f.getName());
            }
          }
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log("Failed to list screenshot files for PB output");
        }
      } else if (usedVideoType == VideoOutput.VideoType.EMULATOR_CONSOLE) {
        try {
          File[] files = localFileUtil.listFilesOrDirs(testInfo.getGenFileDir());
          if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
              if (f.getName().startsWith(EMULATOR_VIDEO_PREFIX) && f.getName().endsWith(".webm")) {
                outputBuilder.addGeneratedFileNames(f.getName());
              }
            }
          }
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log("Failed to list emulator video files for PB output");
        }
      } else {
        logger.atWarning().log("Unsupported video type: %s", usedVideoType);
      }

      try {
        String pbFile = Path.of(testInfo.getGenFileDir(), VIDEO_OUTPUT_PB_FILE_NAME).toString();
        try (FileOutputStream out = new FileOutputStream(pbFile)) {
          outputBuilder.build().writeTo(out);
        }
      } catch (IOException e) {
        logger.atWarning().withCause(e).log("Failed to write %s", VIDEO_OUTPUT_PB_FILE_NAME);
      }
    }
  }
}
