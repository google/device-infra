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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidEmulatorVideoDecoratorSpec;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

/** Decorator for recording video on Android Emulators using the console recording */
public class AndroidEmulatorVideoDecorator extends AsyncTimerDecorator
    implements SpecConfigable<AndroidEmulatorVideoDecoratorSpec> {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration MAX_EMULATOR_SUPPORTED_VIDEO_DURATION = Duration.ofMinutes(15);
  private static final int DEFAULT_FPS = 5;
  private static final int DEFAULT_BIT_RATE = 100000;

  private final Adb adb;
  private final Sleeper sleeper;
  private final Clock clock;
  private final AtomicInteger videoCount = new AtomicInteger(0);

  private final AtomicBoolean running = new AtomicBoolean(false);

  private final Set<Path> generatedFiles = Sets.newConcurrentHashSet();

  private AndroidEmulatorVideoDecoratorSpec emulatorVideoDecoratorSpec;

  @Inject
  AndroidEmulatorVideoDecorator(
      Driver decorated, TestInfo testInfo, Adb adb, Sleeper sleeper, Clock clock) {
    super(decorated, testInfo);
    this.adb = adb;
    this.sleeper = sleeper;
    this.clock = clock;
  }

  @Override
  public void onStart(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    emulatorVideoDecoratorSpec = testInfo.jobInfo().combinedSpec(this);
  }

  @Override
  long getIntervalMs(TestInfo testInfo) {
    return emulatorVideoDecoratorSpec.hasTimeLimitSecs()
        ? Duration.ofSeconds(emulatorVideoDecoratorSpec.getTimeLimitSecs()).toMillis()
        : MAX_EMULATOR_SUPPORTED_VIDEO_DURATION.toMillis();
  }

  @Override
  void runTimerTask(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    // If a recording is already running, stop it before starting a new one. The video file is only
    // written to when the recording is explicitly stopped. Allow a small delay before starting the
    // next recording to let the emulator complete writing the previous video file. If consecutive
    // video recordings are started too quickly, the emulator may not have finished writing the
    // previous video file, resulting in a corrupt or empty video file.
    if (running.get()) {
      stopVideoRecording(testInfo);
    }
    var device = getDevice();
    var fps =
        emulatorVideoDecoratorSpec.hasFps() ? emulatorVideoDecoratorSpec.getFps() : DEFAULT_FPS;
    var bitRate =
        emulatorVideoDecoratorSpec.hasBitRate()
            ? emulatorVideoDecoratorSpec.getBitRate()
            : DEFAULT_BIT_RATE;
    var timeLimitSecs =
        emulatorVideoDecoratorSpec.hasTimeLimitSecs()
            ? emulatorVideoDecoratorSpec.getTimeLimitSecs()
            : MAX_EMULATOR_SUPPORTED_VIDEO_DURATION.toSeconds();
    var videoOutputPath =
        Path.of(testInfo.getGenFileDir())
            .resolve(String.format("video-%d.webm", videoCount.incrementAndGet()));
    var args =
        ImmutableList.of(
            "emu",
            "screenrecord",
            "start",
            "--bit-rate",
            String.valueOf(bitRate),
            "--fps",
            String.valueOf(fps),
            "--time-limit",
            String.valueOf(timeLimitSecs),
            videoOutputPath.toString());

    var output = adb.run(device.getDeviceId(), args.toArray(String[]::new));
    if (videoCount.get() == 1) {
      testInfo
          .properties()
          .add(
              Test.ANDROID_EMULATOR_VIDEO_DECORATOR_VIDEO_START_EPOCH_MS,
              Long.toString(clock.instant().toEpochMilli()));
    }
    running.set(true);
    generatedFiles.add(videoOutputPath);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("------- Emulator screenrecord start --------\n Output: %s", output);
  }

  private void stopVideoRecording(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    var videoOutputPath =
        Path.of(testInfo.getGenFileDir()).resolve(String.format("video-%d.webm", videoCount.get()));
    var args = ImmutableList.of("emu", "screenrecord", "stop", videoOutputPath.toString());
    var unused = adb.run(getDevice().getDeviceId(), args.toArray(String[]::new));
    // Sleep for 1 second to let the emulator complete writing the video file.
    sleeper.sleep(Duration.ofSeconds(1));
    running.set(false);
  }

  @Override
  void onEnd(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    stopVideoRecording(testInfo);
    testInfo
        .properties()
        .add(
            Test.ANDROID_EMULATOR_VIDEO_DECORATOR_VIDEO_STOP_EPOCH_MS,
            Long.toString(clock.instant().toEpochMilli()));
    // Sleep for 2 seconds to let the emulator complete writing the video file.
    sleeper.sleep(Duration.ofSeconds(2));
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("------- Stopped Android Emulator Video Decorator --------");

    var missingFiles =
        generatedFiles.stream()
            .filter(videoFile -> !videoFile.toFile().exists())
            .collect(toImmutableList());
    if (!missingFiles.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_EMULATOR_VIDEO_DECORATOR_VIDEO_FILE_ABSENT,
          "Generated video files absent. Expected: " + Joiner.on(",").join(missingFiles));
    }
    var emptyFiles =
        generatedFiles.stream()
            .filter(videoFile -> videoFile.toFile().length() == 0L)
            .collect(toImmutableList());
    if (!emptyFiles.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_EMULATOR_VIDEO_DECORATOR_VIDEO_FILE_EMPTY,
          "Generated video files empty. Empty files: " + Joiner.on(",").join(emptyFiles));
    }
  }
}
