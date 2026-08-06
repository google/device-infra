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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/** An {@link AndroidVideoRecorder} that uses emulator console commands to capture video. */
public class EmulatorConsoleRecorder implements AndroidVideoRecorder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration MAX_EMULATOR_SUPPORTED_VIDEO_DURATION = Duration.ofMinutes(15);
  private static final int DEFAULT_FPS = 5;
  private static final int DEFAULT_BIT_RATE = 100000;

  private final Adb adb;
  private final Sleeper sleeper;
  private ScheduledExecutorService executorService;
  private int clipIndex = 0;
  private final Set<Path> generatedFiles = ConcurrentHashMap.newKeySet();

  @Inject
  EmulatorConsoleRecorder(Adb adb, Sleeper sleeper) {
    this.adb = adb;
    this.sleeper = sleeper;
  }

  @Override
  public void start(TestInfo testInfo, Device device, AndroidVideoDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Starting emulator console recorder...");
    long intervalMs = MAX_EMULATOR_SUPPORTED_VIDEO_DURATION.toMillis();

    startNewClip(device.getDeviceId(), testInfo, spec);

    executorService = Executors.newSingleThreadScheduledExecutor();
    var unused =
        executorService.scheduleAtFixedRate(
            () -> {
              try {
                stopCurrentClip(device.getDeviceId());
                startNewClip(device.getDeviceId(), testInfo, spec);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.atWarning().withCause(e).log("Interrupted while rotating emulator clip.");
              } catch (MobileHarnessException e) {
                logger.atWarning().withCause(e).log("Failed to rotate emulator clip.");
              }
            },
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop(TestInfo testInfo, Device device)
      throws MobileHarnessException, InterruptedException {
    if (executorService != null) {
      executorService.shutdownNow();
      executorService = null;
    }
    stopCurrentClip(device.getDeviceId());
    sleeper.sleep(Duration.ofSeconds(2)); // wait for emulator to complete writing

    ImmutableList<Path> missingFiles =
        generatedFiles.stream()
            .filter(path -> !path.toFile().exists())
            .collect(ImmutableList.toImmutableList());
    if (!missingFiles.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_EMULATOR_VIDEO_DECORATOR_VIDEO_FILE_ABSENT,
          "Generated video files absent. Expected: " + missingFiles);
    }

    ImmutableList<Path> emptyFiles =
        generatedFiles.stream()
            .filter(path -> path.toFile().length() == 0L)
            .collect(ImmutableList.toImmutableList());
    if (!emptyFiles.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_EMULATOR_VIDEO_DECORATOR_VIDEO_FILE_EMPTY,
          "Generated video files empty: " + emptyFiles);
    }
  }

  private void startNewClip(String deviceId, TestInfo testInfo, AndroidVideoDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    Path videoOutputPath =
        Path.of(testInfo.getGenFileDir())
            .resolve(String.format("emulator_video_%d.webm", ++clipIndex));
    generatedFiles.add(videoOutputPath);

    int fps = spec.hasFps() ? spec.getFps() : DEFAULT_FPS;
    int bitRate = spec.hasBitRate() ? spec.getBitRate() : DEFAULT_BIT_RATE;
    long timeLimitSecs = MAX_EMULATOR_SUPPORTED_VIDEO_DURATION.toSeconds();

    ImmutableList<String> args =
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

    var unused = adb.run(deviceId, args.toArray(new String[0]));
  }

  private void stopCurrentClip(String deviceId)
      throws MobileHarnessException, InterruptedException {
    var unused = adb.run(deviceId, new String[] {"emu", "screenrecord", "stop"});
    sleeper.sleep(Duration.ofSeconds(1));
  }
}
