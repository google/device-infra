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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private ScheduledFuture<?> rotationFuture;
  private int clipIndex = 0;
  private final List<Path> generatedFiles = new CopyOnWriteArrayList<>();
  private final AtomicBoolean isStarted = new AtomicBoolean(false);

  private String deviceId;
  private String consoleSerial;

  @VisibleForTesting
  EmulatorConsoleRecorder(Adb adb, Sleeper sleeper, ScheduledExecutorService executorService) {
    this.adb = adb;
    this.sleeper = sleeper;
    this.executorService = executorService;
  }

  @Inject
  EmulatorConsoleRecorder(Adb adb, Sleeper sleeper) {
    this.adb = adb;
    this.sleeper = sleeper;
  }

  @Override
  public void start(String deviceId, String testId, Path genFileDir, AndroidVideoDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Starting emulator console recorder...");
    long intervalMs = MAX_EMULATOR_SUPPORTED_VIDEO_DURATION.toMillis();

    this.deviceId = deviceId;
    isStarted.set(false);
    generatedFiles.clear();
    clipIndex = 0;
    consoleSerial = getEmulatorSerialForConsole(deviceId);

    if (executorService == null) {
      executorService = Executors.newSingleThreadScheduledExecutor();
    }
    rotationFuture =
        executorService.scheduleAtFixedRate(
            () -> {
              try {
                if (isStarted.getAndSet(true)) {
                  stopCurrentClip(consoleSerial);
                }
                startNewClip(consoleSerial, genFileDir, spec);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.atWarning().withCause(e).log("Interrupted while rotating emulator clip.");
              } catch (MobileHarnessException e) {
                logger.atWarning().withCause(e).log("Failed to rotate emulator clip.");
              }
            },
            0,
            intervalMs,
            MILLISECONDS);
  }

  @Override
  public List<Path> stop() throws MobileHarnessException, InterruptedException {
    if (isStarted.get()) {
      String serial = consoleSerial != null ? consoleSerial : deviceId;
      stopCurrentClip(serial);
      sleeper.sleep(Duration.ofSeconds(2)); // wait for emulator to complete writing
    }
    if (rotationFuture != null) {
      rotationFuture.cancel(false);
      rotationFuture = null;
    }
    if (executorService != null) {
      executorService.shutdown();
      executorService = null;
    }

    ImmutableList<Path> missingFiles =
        generatedFiles.stream().filter(path -> !path.toFile().exists()).collect(toImmutableList());
    if (!missingFiles.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_VIDEO_DECORATOR_EMULATOR_CONSOLE_FILE_ABSENT,
          "Generated video files absent. Expected: " + missingFiles);
    }

    ImmutableList<Path> emptyFiles =
        generatedFiles.stream()
            .filter(path -> path.toFile().length() == 0L)
            .collect(toImmutableList());
    if (!emptyFiles.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_VIDEO_DECORATOR_EMULATOR_CONSOLE_FILE_EMPTY,
          "Generated video files empty: " + emptyFiles);
    }

    return ImmutableList.copyOf(generatedFiles);
  }

  private void startNewClip(String serial, Path genFileDir, AndroidVideoDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    Path videoOutputPath = genFileDir.resolve(String.format("emulator_video_%d.webm", ++clipIndex));

    int fps = spec.getEmulatorConsole().hasFps() ? spec.getEmulatorConsole().getFps() : DEFAULT_FPS;
    int bitRate =
        spec.getEmulatorConsole().hasBitRate()
            ? spec.getEmulatorConsole().getBitRate()
            : DEFAULT_BIT_RATE;
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

    String output = adb.run(serial, args.toArray(new String[0]));
    generatedFiles.add(videoOutputPath);
    logger.atInfo().log("Started emulator clip: %s", output);
  }

  private void stopCurrentClip(String deviceId)
      throws MobileHarnessException, InterruptedException {
    String output = adb.run(deviceId, new String[] {"emu", "screenrecord", "stop"});
    logger.atInfo().log("Stopped emulator clip: %s", output);
    // The sleep is necessary for the buffer to flush and write the file to disk.
    sleeper.sleep(Duration.ofSeconds(1));
  }

  private String getEmulatorSerialForConsole(String deviceId)
      throws MobileHarnessException, InterruptedException {
    String consolePort = adb.runShell(deviceId, "getprop emulator_port").trim();
    if (!consolePort.isEmpty()) {
      logger.atInfo().log("Found actual emulator console port: %s", consolePort);
      return "emulator-" + consolePort;
    }
    return deviceId;
  }
}
