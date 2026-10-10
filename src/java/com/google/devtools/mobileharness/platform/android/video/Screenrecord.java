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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.platform.android.media.ScreenRecordArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidVideoDecoratorSpec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * An {@link AndroidVideoRecorder} that uses the native Android {@code screenrecord} command.
 *
 * <p>Intended for real devices with SDK >= {@link #MIN_SDK_VERSION_FOR_UNLIMITED_RECORDING}, where
 * {@code screenrecord} supports recording without the default 180 seconds time limit. The video is
 * recorded to a single MP4 file on the device, which is pulled to the host and removed from the
 * device when the recording is stopped.
 */
public class Screenrecord implements AndroidVideoRecorder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The minimum SDK version where {@code screenrecord --time-limit 0} (no limit) is supported. */
  public static final int MIN_SDK_VERSION_FOR_UNLIMITED_RECORDING = 34;

  /** File name of the video pulled to the host gen file directory. */
  public static final String VIDEO_FILE_NAME = "screenrecord_video.mp4";

  @VisibleForTesting static final int DEFAULT_BIT_RATE = 4_000_000;
  @VisibleForTesting static final int MIN_BIT_RATE = 100_000;
  @VisibleForTesting static final int MAX_BIT_RATE = 100_000_000;

  /** Timeout for the screenrecord process to finish encoding after receiving SIGINT. */
  @VisibleForTesting static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);

  /**
   * Default upper bound of the async screenrecord shell command, used when the caller does not
   * provide a max duration. The recording is normally stopped in stop().
   */
  @VisibleForTesting static final Duration MAX_RECORDING_DURATION = Duration.ofHours(24);

  private static final String DEVICE_DIR = "/data/local/tmp";
  private static final Pattern SIZE_PATTERN = Pattern.compile("\\d+x\\d+");

  private final AndroidMediaUtil androidMediaUtil;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidSystemSettingUtil systemSettingUtil;

  private String deviceId;
  private String deviceVideoPath;
  private Path hostVideoPath;
  private CommandProcess process;

  @Inject
  Screenrecord(
      AndroidMediaUtil androidMediaUtil,
      AndroidFileUtil androidFileUtil,
      AndroidSystemSettingUtil systemSettingUtil) {
    this.androidMediaUtil = androidMediaUtil;
    this.androidFileUtil = androidFileUtil;
    this.systemSettingUtil = systemSettingUtil;
  }

  @Override
  public void start(String deviceId, String testId, Path genFileDir, AndroidVideoDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    start(deviceId, testId, genFileDir, spec, MAX_RECORDING_DURATION);
  }

  @Override
  public void start(
      String deviceId,
      String testId,
      Path genFileDir,
      AndroidVideoDecoratorSpec spec,
      Duration maxDuration)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Starting screenrecord recorder...");
    this.deviceId = deviceId;
    deviceVideoPath = String.format("%s/mh_screenrecord_%s.mp4", DEVICE_DIR, testId);
    hostVideoPath = genFileDir.resolve(VIDEO_FILE_NAME);

    AndroidVideoDecoratorSpec.HdScreenRecord config = spec.getHdScreenRecord();
    int bitRate =
        Math.min(
            MAX_BIT_RATE,
            Math.max(MIN_BIT_RATE, config.hasBitRate() ? config.getBitRate() : DEFAULT_BIT_RATE));
    ScreenRecordArgs.Builder args =
        ScreenRecordArgs.builder(deviceVideoPath).setBitRate(bitRate).setVerbose(true);

    if (config.hasResolution()) {
      if (SIZE_PATTERN.matcher(config.getResolution()).matches()) {
        args.setSize(config.getResolution());
      } else {
        logger.atWarning().log(
            "Ignoring invalid screenrecord resolution [%s], expected format WIDTHxHEIGHT.",
            config.getResolution());
      }
    }
    if (config.hasFps()) {
      logger.atWarning().log(
          "Ignoring screenrecord fps [%d]: Android screenrecord command does not support setting"
              + " frame rate.",
          config.getFps());
    }

    if (getSdkVersion(deviceId) >= MIN_SDK_VERSION_FOR_UNLIMITED_RECORDING) {
      // A time limit of 0 removes the default 180 seconds limit.
      args.setTimeLimit(Optional.of(Duration.ZERO));
    } else {
      logger.atWarning().log(
          "Device %s SDK < %d, screenrecord will stop after its default time limit (180s).",
          deviceId, MIN_SDK_VERSION_FOR_UNLIMITED_RECORDING);
    }

    // Removes the stale video file (if any) left by a previous run.
    removeDeviceVideoFile();
    process = androidMediaUtil.recordScreen(deviceId, args.build(), maxDuration);
  }

  @Override
  public List<Path> stop() throws MobileHarnessException, InterruptedException {
    if (process == null) {
      logger.atInfo().log("Screenrecord recorder is not started, skip stopping.");
      return ImmutableList.of();
    }
    logger.atInfo().log("Stopping screenrecord recorder and pulling the video...");
    try {
      // Sends SIGINT and waits for the screenrecord process to finish writing the video file.
      androidMediaUtil.stopScreenRecord(deviceId, STOP_TIMEOUT);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to gracefully stop screenrecord on device %s, force stop...", deviceId);
    }
    if (process.isAlive()) {
      process.killForcibly();
    }
    process = null;

    try {
      androidFileUtil.pull(deviceId, deviceVideoPath, hostVideoPath.toString());
    } finally {
      removeDeviceVideoFile();
    }

    if (!hostVideoPath.toFile().exists()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_VIDEO_DECORATOR_SCREENRECORD_FILE_ABSENT,
          "Screenrecord video file absent: " + hostVideoPath);
    }
    if (hostVideoPath.toFile().length() == 0L) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_VIDEO_DECORATOR_SCREENRECORD_FILE_EMPTY,
          "Screenrecord video file empty: " + hostVideoPath);
    }
    return ImmutableList.of(hostVideoPath);
  }

  private int getSdkVersion(String deviceId) throws InterruptedException {
    try {
      return systemSettingUtil.getDeviceSdkVersion(deviceId);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to get SDK version of device %s.", deviceId);
      return 0;
    }
  }

  private void removeDeviceVideoFile() throws InterruptedException {
    try {
      androidFileUtil.removeFiles(deviceId, deviceVideoPath);
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to remove screenrecord video %s on device %s.", deviceVideoPath, deviceId);
    }
  }
}
