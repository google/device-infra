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
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.media.AndroidMediaUtil;
import com.google.devtools.mobileharness.platform.android.media.ScreenRecordArgs;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.annotation.ParamAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
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
public class AndroidHdVideoDecorator extends AsyncTimerDecorator {

  @ParamAnnotation(
      required = false,
      help = "Whether to upload video when test pass. By default, it is true.")
  public static final String PARAM_VIDEO_ON_PASS = "video_on_pass";

  @ParamAnnotation(
      required = false,
      help =
          "Whether one full video of the whole test should be recorded, instead of multiple video"
              + " clips. Default is false. If set to true the param video_clip_num has no meaning."
              + " Only supported on API 34+ and non-VR mode. On API <34 this param is disregarded.")
  public static final String PARAM_FULL_VIDEO = "full_video";

  @ParamAnnotation(
      required = false,
      help =
          "The number of video clips. If it equals x, records screen at the last (x*3-3, x*3] "
              + "minutes. By default, it is 3 (recording at the last 6-9 minutes). At least it's "
              + "2.")
  public static final String PARAM_NUM_VIDEO_CLIPS = "video_clip_num";

  @ParamAnnotation(
      required = false,
      help =
          "The video bit rate. The acceptable range is [100000, 100000000]. "
              + "By default, it is 4000000 (4Mbps).")
  public static final String PARAM_VIDEO_BIT_RATE = "video_bit_rate";

  @ParamAnnotation(required = false, help = "Custom prefix for video clip file names.")
  public static final String PARAM_CLIP_FILE_NAME = "clip_filename";

  @ParamAnnotation(
      required = false,
      help =
          "Whether or not to use the device ID as prefix for the clip file names. True by default.")
  public static final String PARAM_CLIP_FILENAME_USE_PREFIX = "clip_filename_use_prefix";

  @ParamAnnotation(
      required = false,
      help =
          "The video size: 1280x720. "
              + "The default value is the device's native display resolution "
              + "if supported, 1280x720 if not. "
              + "For best results, "
              + "use a size supported by your device's Advanced Video Coding (AVC) encoder.")
  public static final String PARAM_VIDEO_SIZE = "video_size";

  @ParamAnnotation(
      required = false,
      help = "Whether to add additional information on the video, such as timestamp overlay.")
  public static final String PARAM_BUGREPORT = "enable_screenrecord_bugreport";

  @ParamAnnotation(
      required = false,
      help =
          "Whether to use VrCore RecorderService to record video. "
              + "Defaults to false; "
              + "set this to true if test runs in VR mode with VrCore as the compositor, "
              + "since the Android default screen recording does not work in this situation.")
  public static final String PARAM_RECORD_VR_VIDEO = "record_vr_video";

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
  @VisibleForTesting static final Duration MAX_RECORDING_TIME = Duration.ofMinutes(3L);

  /** The overlap recording time between two video clips. */
  @VisibleForTesting static final Duration OVERLAP_RECORDING_TIME = Duration.ofSeconds(5L);

  /** The expected upper bound of post processing time of screenrecord. */
  @VisibleForTesting static final Duration POST_PROCESSING_TIME = Duration.ofSeconds(10L);

  @VisibleForTesting static final String DATA_LOCAL_TMP_DIR = "/data/local/tmp";

  /** The relative working directory. */
  @VisibleForTesting static final String RELATIVE_WORKING_DIR = "tmp/mh/video";

  /** The default video clip file name. */
  @VisibleForTesting static final String DEFAULT_CLIP_FILE_NAME = "video";

  /** Whether one full video should be recorded, instead of video clips. */
  @VisibleForTesting boolean fullVideo;

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

  /**
   * Running recording processes on device.
   *
   * <p>NOTICE: Please get the ownership of its monitor before using it to ensure thread safety.
   */
  @VisibleForTesting final Queue<CommandProcess> runningProcesses;

  private final AndroidSystemSettingUtil systemSettingUtil;

  private final AndroidFileUtil androidFileUtil;

  private final AndroidMediaUtil androidMediaUtil;

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
        new AndroidMediaUtil());
  }

  /** Constructor for testing only. */
  @VisibleForTesting
  @SuppressWarnings("JdkObsolete")
  public AndroidHdVideoDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidSystemSettingUtil systemSettingUtil,
      AndroidFileUtil androidFileUtil,
      AndroidMediaUtil androidMediaUtil) {
    super(decoratedDriver, testInfo);
    this.systemSettingUtil = systemSettingUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidMediaUtil = androidMediaUtil;
    this.stopwatch = Stopwatch.createUnstarted();
    runningProcesses = new LinkedList<>();
  }

  @Override
  protected long getIntervalMs(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (recordVrVideo) {
      // Recording using VrCore RecorderService doesn't support overlap, as only one instance of the
      // recorder runs at any time
      return MAX_RECORDING_TIME.toMillis();
    } else if (fullVideo) {
      return testInfo.timer().remainingTimeJava().toMillis();
    } else {
      return MAX_RECORDING_TIME.minus(OVERLAP_RECORDING_TIME).toMillis();
    }
  }

  @Override
  void onStart(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    stopwatch.start();
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    recordVrVideo = jobInfo.params().getBool(PARAM_RECORD_VR_VIDEO, false);
    fullVideo =
        jobInfo.params().getBool(PARAM_FULL_VIDEO, false)
            && systemSettingUtil.getDeviceSdkVersion(getDevice().getDeviceId()) >= 34
            && !recordVrVideo;
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
                + "bugreport=%s",
            numVideoClips, videoOnPass, videoBitRate, bugreport);
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
                      .setTimeLimit(fullVideo ? Optional.of(Duration.ZERO) : Optional.absent())
                      .build(),
                  Comparators.min(
                      testInfo.timer().remainingTimeJava(),
                      fullVideo
                          ? testInfo.timer().remainingTimeJava()
                          : MAX_RECORDING_TIME.plus(OVERLAP_RECORDING_TIME)));
          runningProcesses.add(process);
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
        androidMediaUtil.stopScreenRecord(deviceId, POST_PROCESSING_TIME);
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
      Sleeper.defaultSleeper().sleep(POST_PROCESSING_TIME);
      // TODO: Uploads the video to test result even if the test result is TIMEOUT.
      if (testInfo.result().get() == TestResult.TIMEOUT) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Skip Pulling video files, because test has been timeout.");
      } else {
        if (videoOnPass || testInfo.result().get() != TestResult.PASS) {
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
      logger.atInfo().log("Remove video files");
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
