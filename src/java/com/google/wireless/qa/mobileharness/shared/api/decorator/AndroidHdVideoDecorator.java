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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Comparators;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.platform.android.media.ScreenRecordArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidHdVideoSpec;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Driver decorator for recording the Android real device screen with high resolution at the last
 * few minutes of tests.
 */
@DecoratorAnnotation(
    help =
        "For recording screen video with high definition at the last few minutes of "
            + "the test. This decorator must be put in front of AndroidFilePullerDecorator, if "
            + "there is AndroidFilePullerDecorator.")
public class AndroidHdVideoDecorator extends AsyncTimerDecorator implements AndroidHdVideoSpec {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The default number of video clips. */
  @VisibleForTesting static final int DEFAULT_NUM_VIDEO_CLIPS = 3;

  /** The minimum number of video clips. */
  @VisibleForTesting static final int MIN_NUM_VIDEO_CLIPS = 2;

  /** The default value for whether or not to use a prefix for the clip file names. */
  @VisibleForTesting static final boolean DEFAULT_CLIP_FILENAME_USE_PREFIX = true;

  /** The default video bit rate. */
  @VisibleForTesting static final int DEFAULT_BIT_RATE = 4_000_000;

  /** The minimum video bit rate. */
  @VisibleForTesting static final int MIN_BIT_RATE = 100_000;

  /** The maximum video bit rate. */
  @VisibleForTesting static final int MAX_BIT_RATE = 100_000_000;

  /** The maximum recording time. */
  @VisibleForTesting static final long MAX_RECORDING_TIME_MS = Duration.ofMinutes(3L).toMillis();

  /** The expected upper bound of post processing time of screenrecord. */
  @VisibleForTesting static final long POST_PROCESSING_TIME_MS = Duration.ofSeconds(10L).toMillis();

  @VisibleForTesting static final String DATA_LOCAL_TMP_DIR = "/data/local/tmp";

  /** The relative working directory. */
  @VisibleForTesting static final String RELATIVE_WORKING_DIR = "tmp/mh/video";

  /** The default video clip file name. */
  @VisibleForTesting static final String DEFAULT_CLIP_FILE_NAME = "video";

  /** The max number of video clips to take. */
  @VisibleForTesting int numVideoClips;

  /** Whether to upload video when test pass. */
  @VisibleForTesting boolean videoOnPass;

  /** Whether the test has stopped (the {@link #onEnd(TestInfo)} has been invoked). */
  @VisibleForTesting boolean hasStopped;

  /** The current clip ID to record. */
  @VisibleForTesting int currentClipId;

  /** The current clip ID to record. */
  @VisibleForTesting int oldestClipIdToKeep;

  /** The template for video clip path on device. */
  @VisibleForTesting String templateClipPath;

  /** The video bit rate. */
  @VisibleForTesting int videoBitRate;

  /** Whether to record VR video. */
  @VisibleForTesting boolean recordVrVideo;

  /** The video size. */
  @VisibleForTesting String videoSize;

  @VisibleForTesting boolean bugreport;

  @VisibleForTesting Duration screenRecordTimeLimit;

  /**
   * Running recording processes on device.
   *
   * <p>NOTICE: Please get the ownership of its monitor before using it to ensure thread safety.
   */
  @VisibleForTesting final Queue<CommandProcess> runningProcesses;

  private final AndroidSystemSettingUtil systemSettingUtil;

  private final AndroidFileUtil androidFileUtil;

  private final AndroidMediaUtil androidMediaUtil;

  private final Clock clock;

  private final Stopwatch stopwatch;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the
   * driver/decorator framework.
   */
  public AndroidHdVideoDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new AndroidSystemSettingUtil(),
        new AndroidFileUtil(),
        new AndroidMediaUtil(),
        Clock.systemUTC());
  }

  /** Constructor for testing only. */
  @VisibleForTesting
  @SuppressWarnings("JdkObsolete")
  public AndroidHdVideoDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidSystemSettingUtil systemSettingUtil,
      AndroidFileUtil androidFileUtil,
      AndroidMediaUtil androidMediaUtil,
      Clock clock) {
    super(decoratedDriver, testInfo);
    this.systemSettingUtil = systemSettingUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidMediaUtil = androidMediaUtil;
    this.clock = clock;
    this.stopwatch = Stopwatch.createUnstarted();
    runningProcesses = new LinkedList<>();
  }

  // Overwrite the interval according to the param.
  private long overwriteMaxRecordingTimeMs() {
    if (screenRecordTimeLimit != null && screenRecordTimeLimit.isZero()) {
      return JobSetting.MAX_TEST_TIMEOUT.toMillis();
    }
    return screenRecordTimeLimit == null ? MAX_RECORDING_TIME_MS : screenRecordTimeLimit.toMillis();
  }

  @Override
  protected long getIntervalMs(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    long maxRecordingTimeMs = overwriteMaxRecordingTimeMs();
    if (recordVrVideo) {
      // Recording using VrCore RecorderService doesn't support overlap, as only one instance of the
      // recorder runs at any time
      return maxRecordingTimeMs;
    } else {
      return maxRecordingTimeMs - OVERLAP_RECORDING_TIME_MS;
    }
  }

  @Override
  void onStart(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    stopwatch.start();
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    numVideoClips =
        Math.max(
            jobInfo.params().getInt(PARAM_NUM_VIDEO_CLIPS, DEFAULT_NUM_VIDEO_CLIPS),
            MIN_NUM_VIDEO_CLIPS);
    videoOnPass = jobInfo.params().getBool(PARAM_VIDEO_ON_PASS, /* defaultValue= */ true);
    videoBitRate =
        Math.min(
            MAX_BIT_RATE,
            Math.max(
                MIN_BIT_RATE, jobInfo.params().getInt(PARAM_VIDEO_BIT_RATE, DEFAULT_BIT_RATE)));
    currentClipId = 0;
    oldestClipIdToKeep = currentClipId;
    videoSize = jobInfo.params().get(PARAM_VIDEO_SIZE, null);
    bugreport = jobInfo.params().getBool(PARAM_BUGREPORT, /* defaultValue= */ false);
    screenRecordTimeLimit = null;
    if (jobInfo.params().has(PARAM_SCREENRECORD_TIME_LIMIT_SECONDS)) {
      int sdkVersion = 0;
      try {
        sdkVersion = systemSettingUtil.getDeviceSdkVersion(deviceId);
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atWarning()
            .withCause(e)
            .alsoTo(logger)
            .log("Failed to get device sdk version, skip setting time limit.");
      }
      if (sdkVersion >= 34) {
        screenRecordTimeLimit =
            Duration.ofSeconds(jobInfo.params().getLong(PARAM_SCREENRECORD_TIME_LIMIT_SECONDS, 0L));
      } else {
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log(
                "Device sdk version is lower than 34, doesn't support %s, will ignore this param.",
                PARAM_SCREENRECORD_TIME_LIMIT_SECONDS);
      }
    }

    prepareWorkingDir(testInfo, systemSettingUtil.getDeviceSdkVersion(deviceId));

    // Removes previous video files.
    logger.atInfo().log("Remove previous video files");
    androidFileUtil.removeFiles(deviceId, getClipPathOnDevice("*"));
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Start AndroidHdVideoDecorator, numVideoClips=%s, videoOnPass=%s, bitRate=%s, "
                + "bugreport=%s, timeLimit=%s",
            numVideoClips,
            videoOnPass,
            videoBitRate,
            bugreport,
            screenRecordTimeLimit == null ? "null" : screenRecordTimeLimit.toSeconds());
    recordVrVideo = jobInfo.params().getBool(PARAM_RECORD_VR_VIDEO, /* defaultValue= */ false);
    if (recordVrVideo) {
      androidMediaUtil.enterVrMode(deviceId);
    }
  }

  @Override
  void runTimerTask(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();

    // Holds the lock while running commands to prevent starting to record after the test finishes.
    synchronized (runningProcesses) {
      if (!hasStopped) {
        // Starts to record a new clip.
        logger.atInfo().log("Start to record clip %d", currentClipId);
        // Throws out the exception if fails to record screen or timeout.
        if (recordVrVideo) {
          androidMediaUtil.recordScreenVr(
              deviceId, getClipPathOnDevice(String.valueOf(currentClipId)), videoBitRate);
        } else {
          // TODO: Check if a videoSize is supported by device.
          // If videoSize is valid but unsupported by device,
          // screenrecord command will return soon and the video file will be 0 bytes.
          // Timeout should be larger than MAX_RECORDING_TIME_MS to avoid process be terminated.
          CommandProcess process =
              androidMediaUtil.recordScreen(
                  deviceId,
                  ScreenRecordArgs.builder(getClipPathOnDevice(String.valueOf(currentClipId)))
                      .setBitRate(videoBitRate)
                      .setSize(Optional.fromNullable(videoSize))
                      .setVerbose(true)
                      .setBugreport(bugreport)
                      .setTimeLimit(Optional.fromNullable(screenRecordTimeLimit))
                      .build(),
                  Comparators.min(
                      testInfo.timer().remainingTimeJava(),
                      Duration.ofMillis(
                          overwriteMaxRecordingTimeMs() + OVERLAP_RECORDING_TIME_MS)));
          runningProcesses.add(process);
        }
        if (currentClipId == 0) {
          testInfo
              .properties()
              .add(
                  Test.AndroidHdVideoDecorator.ANDROID_HD_VIDEO_DECORATOR_VIDEO_START_EPOCH_MS,
                  Long.toString(clock.instant().toEpochMilli()));
        }
        currentClipId++;
      }
    }

    // Removes extra clip.
    if (currentClipId - oldestClipIdToKeep > numVideoClips) {
      int clipIdToRemove = oldestClipIdToKeep;
      try {
        androidFileUtil.removeFiles(deviceId, getClipPathOnDevice(String.valueOf(clipIdToRemove)));
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Failed to remove clip %s: %s", clipIdToRemove, e);
      }
      oldestClipIdToKeep++;
    }

    // Kill extra recording process if necessary
    if (!recordVrVideo && runningProcesses.size() > numVideoClips) {
      runningProcesses.remove().killForcibly();
    }
  }

  private void killRecordingProcesses(String deviceId, TestInfo testInfo)
      throws InterruptedException {
    logger.atInfo().log("Kill recording processes");
    if (recordVrVideo) {
      try {
        androidMediaUtil.stopScreenRecordVr(deviceId);
      } catch (MobileHarnessException e) {
        testInfo.log().atInfo().alsoTo(logger).log("%s", e.getMessage());
      }
    } else {
      // First to stop the screen record program on device.
      try {
        androidMediaUtil.stopScreenRecord(deviceId, Duration.ofMillis(POST_PROCESSING_TIME_MS));
      } catch (MobileHarnessException e) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Failed to stop screenrecord by sending CTRL+C, force stop...");
      }

      for (CommandProcess process : runningProcesses) {
        process.killForcibly();
      }
      runningProcesses.clear();
    }
  }

  @Override
  void onEnd(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();

    try {
      // Stops screen recording.
      testInfo.log().atInfo().alsoTo(logger).log("Stop screen recording");
      synchronized (runningProcesses) {
        hasStopped = true;
        killRecordingProcesses(deviceId, testInfo);
      }
      // Waits for post processing completed, or the video files will be incomplete.
      Thread.sleep(POST_PROCESSING_TIME_MS);
      // TODO: Uploads the video to test result even if the test result is TIMEOUT.
      if (testInfo.resultWithCause().get().type() == TestResult.TIMEOUT) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Skip Pulling video files, because test has been timeout.");
      } else {
        if (videoOnPass || testInfo.resultWithCause().get().type() != TestResult.PASS) {
          // Pulls video files.
          testInfo.log().atInfo().alsoTo(logger).log("Pulling video files");
          for (int clipId = currentClipId - 1;
              clipId >= Math.max(currentClipId - numVideoClips, 0);
              clipId--) {
            try {
              String result =
                  androidFileUtil.pull(
                      deviceId,
                      getClipPathOnDevice(String.valueOf(clipId)),
                      testInfo.getGenFileDir());
              testInfo
                  .log()
                  .atInfo()
                  .alsoTo(logger)
                  .log("Successfully pulled video %s: %s", clipId, result);
            } catch (MobileHarnessException e) {
              testInfo.log().atInfo().alsoTo(logger).log("Failed to pull video %s: %s", clipId, e);
            }
          }
        }
      }
    } finally {
      // Removes video files.
      logger.atInfo().log("Try to remove video files");
      for (int clipId = currentClipId - 1;
          clipId >= Math.max(currentClipId - numVideoClips, 0);
          clipId--) {
        try {
          androidFileUtil.removeFiles(deviceId, getClipPathOnDevice(String.valueOf(clipId)));
        } catch (MobileHarnessException e) {
          logger.atInfo().log("Failed to remove video file: %s", e);
        }
      }
    }

    stopwatch.stop();
    long executionTimeMs = stopwatch.elapsed().toMillis();
    testInfo
        .properties()
        .add(
            PropertyName.Test.PREFIX_DECORATOR_RUN_TIME_MS + getClass().getSimpleName(),
            Long.toString(executionTimeMs));

    stopwatch.reset();
  }

  /** Get the video clip path with the given ID on device. */
  private String getClipPathOnDevice(String clipId) {
    return String.format(templateClipPath, clipId);
  }

  private void prepareWorkingDir(TestInfo testInfo, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    String videoStorageRootDir = androidFileUtil.getExternalStoragePath(deviceId, sdkVersion);
    try {
      if (!androidFileUtil.isDirExist(deviceId, sdkVersion, videoStorageRootDir)) {
        videoStorageRootDir = DATA_LOCAL_TMP_DIR;
      }
    } catch (MobileHarnessException e) {
      testInfo
          .log()
          .atWarning()
          .withCause(e)
          .alsoTo(logger)
          .log("%s check fails, use %s instead.", videoStorageRootDir, DATA_LOCAL_TMP_DIR);
      videoStorageRootDir = DATA_LOCAL_TMP_DIR;
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Root dir for video storage on device %s is: %s", deviceId, videoStorageRootDir);

    // Creates the working directory.
    String workingDir = PathUtil.join(videoStorageRootDir, RELATIVE_WORKING_DIR);
    logger.atInfo().log("Create working directory on device %s: %s", deviceId, workingDir);
    androidFileUtil.makeDirectory(deviceId, workingDir);

    String prefix =
        jobInfo.params().getBool(PARAM_CLIP_FILENAME_USE_PREFIX, DEFAULT_CLIP_FILENAME_USE_PREFIX)
            ? String.format("%s_", deviceId.replace(':', '_'))
            : "";
    String clipFilenameTemplate =
        String.format(
            "%s%s_%%s.mp4",
            prefix, jobInfo.params().get(PARAM_CLIP_FILE_NAME, DEFAULT_CLIP_FILE_NAME));
    templateClipPath = PathUtil.join(workingDir, clipFilenameTemplate);
  }
}
