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

import static com.google.common.base.Strings.nullToEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidLogCatSpec;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Decorator for retrieving Android device log of a test. It can save the log in file and send to
 * client or Sponge, or just save in the {@link TestInfo}.
 */
@DecoratorAnnotation(
    help =
        "For retrieving Android device log of a test. It can save the device log "
            + "in file and send back to client or Sponge, or just merge into the test log.")
public class AndroidLogCatDecorator extends BaseDecorator implements AndroidLogCatSpec {

  public static final boolean DEFAULT_IGNORE_LOG_BUFFER_SIZE_SET_FAILURE = false;

  /** The log cat file name if log_to_file is enabled. */
  public static final String LOG_CAT_FILE_NAME = "logcat_dump.txt";

  /** Logger for this device. */
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Log template header to the device log. */
  @VisibleForTesting
  static final String LOG_TEMPLATE_HEADER =
      "========= Beginning of device log with filter specs [%s]\n";

  /** Log template footer to the device log. */
  @VisibleForTesting static final String LOG_TEMPLATE_FOOTER = "\n========= End of device log";

  @VisibleForTesting
  static final Pattern FAILED_TO_CLEAR_LOG_PATTERN =
      Pattern.compile("failed to clear the '(.*)' (log|logs)");

  /** {@code FileUtil} for writing device log to file. */
  private final LocalFileUtil fileUtil;

  private final AndroidAdbUtil adbUtil;

  private final AndroidSystemSettingUtil systemSettingUtil;

  private final SystemStateManager systemStateManager;

  /**
   * Constructor. Do NOT modify the parameter list. This constructor is required by the lab server
   * framework.
   */
  public AndroidLogCatDecorator(Driver decoratedDriver, TestInfo testInfo) {
    this(
        decoratedDriver,
        testInfo,
        new LocalFileUtil(),
        new AndroidAdbUtil(),
        new AndroidSystemSettingUtil(),
        new SystemStateManager());
  }

  /** Constructor for testing only. */
  @VisibleForTesting
  AndroidLogCatDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      LocalFileUtil fileUtil,
      AndroidAdbUtil adbUtil,
      AndroidSystemSettingUtil systemSettingUtil,
      SystemStateManager systemStateManager) {
    super(decoratedDriver, testInfo);
    this.fileUtil = fileUtil;
    this.adbUtil = adbUtil;
    this.systemSettingUtil = systemSettingUtil;
    this.systemStateManager = systemStateManager;
  }

  @Override
  public void run(final TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    JobInfo jobInfo = testInfo.jobInfo();
    String logcatSinceTime = null;

    // Only invokes the logcat command when the device is detected, otherwise the command will
    // wait for the device.
    if (isDeviceOnline(deviceId)) {
      if (jobInfo.params().getBool(AndroidLogCatSpec.PARAM_CLEAR_LOG, true)) {
        testInfo.log().atInfo().alsoTo(logger).log("Start to clean logcat on device %s", deviceId);
        try {
          adbUtil.clearLog(deviceId);
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Finish cleaning logcat on device %s", deviceId);
        } catch (MobileHarnessException e) {
          int sdkVersion = getDeviceSdkVersion(deviceId);
          Matcher matcher = FAILED_TO_CLEAR_LOG_PATTERN.matcher(e.getMessage());
          boolean temp = matcher.find();
          logger.atInfo().log(
              "Finding pattern %s in %s: %b", FAILED_TO_CLEAR_LOG_PATTERN, e.getMessage(), temp);
          if (temp && sdkVersion >= AndroidVersion.MARSHMALLOW.getEndSdkVersion()) {
            // Based on b/66166385 and b/287468614, sometimes it fails clearing logs due to race
            // condition in shared
            // resource. In this case, we add logcat -T option to capture logcat since specified
            // time.
            logcatSinceTime = getDeviceCurrentTimeForLogcat(deviceId);
          } else {
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_LOGCAT_DECORATOR_CLEAR_LOGCAT_ERROR,
                String.format("Failed to clear logcat on device %s", deviceId),
                e);
          }
        }
      }
    } else {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_LOGCAT_DECORATOR_DEVICE_NOT_FOUND,
          String.format("Can not clean device log because device %s is disconnected", deviceId));
    }

    // Filter specs and format could be null.
    String filterSpecs = jobInfo.params().get(AndroidLogCatSpec.PARAM_LOG_FILTER_SPECS);
    String options = jobInfo.params().get(AndroidLogCatSpec.PARAM_LOG_OPTIONS);
    if (!Strings.isNullOrEmpty(logcatSinceTime)) {
      options = String.format("%s -T '%s'", nullToEmpty(options), logcatSinceTime).trim();
    }
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("logcat options: [%s]. logcat filter specs [%s]", options, filterSpecs);

    try {
      systemSettingUtil.setLogLevelProperty(deviceId, filterSpecs);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_LOGCAT_DECORATOR_SET_LOG_LEVEL_PROPERTY_ERROR,
          String.format("Failed to set log level property on device %s", deviceId),
          e);
    }

    String logFilePath = null;
    if (jobInfo.params().isTrue(AndroidLogCatSpec.PARAM_LOG_TO_FILE)) {
      logFilePath = PathUtil.join(testInfo.getGenFileDir(), LOG_CAT_FILE_NAME);
    }
    int sdkVersion = getDeviceSdkVersion(deviceId);

    if (jobInfo.params().getBool(AndroidLogCatSpec.PARAM_SET_LOG_BUFFER_SIZE, true)) {
      int bufferSize = AndroidLogCatSpec.LOG_BUFFER_SIZE_MAX_KB;
      boolean isLogBufferSizeSpecified =
          jobInfo.params().has(AndroidLogCatSpec.PARAM_LOG_BUFFER_SIZE_KB);
      if (isLogBufferSizeSpecified) {
        bufferSize = jobInfo.params().getInt(AndroidLogCatSpec.PARAM_LOG_BUFFER_SIZE_KB, 0);
      }
      try {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Output setting log buffer size = %d KB: %s",
                bufferSize, adbUtil.setLogCatBufferSize(deviceId, sdkVersion, bufferSize));
      } catch (MobileHarnessException e) {
        if (isLogBufferSizeSpecified
            && !jobInfo
                .params()
                .getBool(
                    AndroidLogCatSpec.PARAM_IGNORE_LOG_BUFFER_SIZE_SET_FAILURE,
                    DEFAULT_IGNORE_LOG_BUFFER_SIZE_SET_FAILURE)) {
          if (DeviceUtil.inSharedLab()) {
            throw new MobileHarnessException(
                AndroidErrorId.ANDROID_LOGCAT_DECORATOR_SET_LOGCAT_BUFFER_SIZE_ERROR_IN_SHARED_LAB,
                String.format("Failed to set logcat buffer size on device %s", deviceId),
                e);
          } else {
            throw new MobileHarnessException(
                AndroidErrorId
                    .ANDROID_LOGCAT_DECORATOR_SET_LOGCAT_BUFFER_SIZE_ERROR_IN_SATELLITE_LAB,
                String.format(
                    "Failed to set logcat buffer size on device %s. Please consider 1. not setting"
                        + " log_buffer_size_kb, and we'll use a default size; or 2. set"
                        + " ignore_log_buffer_size_set_failure true, the default size will also be"
                        + " used",
                    deviceId),
                e);
          }
        } else {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log("Failed to set logcat buffer size on device %s. Ignoring.", deviceId);
        }
      }
    }

    if (jobInfo.params().isTrue(AndroidLogCatSpec.PARAM_ASYNC_LOG)) {
      asyncRun(testInfo, sdkVersion, filterSpecs, options, logFilePath);
    } else {
      Stopwatch stopwatch = Stopwatch.createStarted();
      syncRun(testInfo, filterSpecs, options, logFilePath);
      stopwatch.stop();
      long runTimeMs = stopwatch.elapsed().toMillis();
      testInfo
          .properties()
          .add(
              Test.PREFIX_DECORATOR_RUN_TIME_MS + getClass().getSimpleName(),
              Long.toString(runTimeMs));
    }
  }

  /** Runs logcat after the test is finished. Will block the current thread when dumping. */
  private void syncRun(
      final TestInfo testInfo, String filterSpecs, String options, @Nullable String logFilePath)
      throws MobileHarnessException, InterruptedException {
    try {
      getDecorated().run(testInfo);
    } finally {
      postSyncRun(testInfo, filterSpecs, options, logFilePath);
    }
  }

  /**
   * Actually run the logcat after the decorated driver is executed.
   *
   * <p>DO NOT throw out any other exception except {@link InterruptedException}, to avoid
   * overwriting the exception throw out by the decorated driver.
   */
  private void postSyncRun(
      TestInfo testInfo, String filterSpecs, String options, @Nullable String logFilePath)
      throws InterruptedException {
    // Skips logcat when test pass and doesn't require log to file.
    if (logFilePath == null && testInfo.resultWithCause().get().type() == TestResult.PASS) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Skip logcat when test passed and parameter "
                  + AndroidLogCatSpec.PARAM_LOG_TO_FILE
                  + " is false");
      return;
    }

    boolean isDeviceOnline;
    String deviceId = getDevice().getDeviceId();
    try {
      isDeviceOnline = isDeviceOnline(deviceId);
    } catch (MobileHarnessException e) {
      testInfo.warnings().addAndLog(e, logger);
      return;
    }

    if (!isDeviceOnline) {
      // Only invokes the logcat command when the device is detected, otherwise the command will
      // wait for the device.
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_LOGCAT_DECORATOR_DEVICE_NOT_FOUND,
                  String.format("Skip logcat because the device %s is disconnected", deviceId)),
              logger);
      return;
    }

    // Block the current thread to dump the device log.
    testInfo.log().atInfo().alsoTo(logger).log("Start logcat for device %s", deviceId);
    String rawLog = null;
    try {
      rawLog = adbUtil.logCat(deviceId, options, filterSpecs);
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_LOGCAT_DECORATOR_LOGCAT_EXEC_ERROR,
                  String.format("Failed to dump logs via logcat from device %s", deviceId),
                  e),
              logger);
      return;
    }

    if (logFilePath != null) {
      // Logs to file only.
      testInfo.log().atInfo().alsoTo(logger).log("Writing log file for device %s", deviceId);
      try {
        // Since the rawLog can be large and if we try to make local copy again, it may cause OOM
        // (b/157514128)
        fileUtil.writeToFile(logFilePath, String.format(LOG_TEMPLATE_HEADER, filterSpecs));
        fileUtil.writeToFile(logFilePath, rawLog, /* append= */ true);
        fileUtil.writeToFile(logFilePath, LOG_TEMPLATE_FOOTER, /* append= */ true);
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Device %s logcat saved to %s", deviceId, logFilePath);
      } catch (MobileHarnessException writeToFileException) {
        testInfo
            .warnings()
            .addAndLog(
                new MobileHarnessException(
                    AndroidErrorId.ANDROID_LOGCAT_DECORATOR_SAVE_LOG_FILE_TO_HOST_ERROR,
                    String.format(
                        "Failed to save device %s log file to host: %s", deviceId, logFilePath),
                    writeToFileException),
                logger);
      }
    } else if (testInfo.resultWithCause().get().type() != TestResult.PASS) {
      // Logs in TestInfo only.
      testInfo.log().atInfo().log(LOG_TEMPLATE_HEADER, filterSpecs);
      testInfo.log().atInfo().log("%s", rawLog);
      testInfo.log().atInfo().log(LOG_TEMPLATE_FOOTER);
      logger.atInfo().log(
          "\n%s",
          String.format(LOG_TEMPLATE_HEADER, filterSpecs)
              + StrUtil.tail(rawLog)
              + LOG_TEMPLATE_FOOTER);
    }
  }

  /** Runs logcat asynchronously during the test is running. */
  private void asyncRun(
      final TestInfo testInfo,
      final int sdkVersion,
      String filterSpecs,
      String options,
      @Nullable String logFilePath)
      throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();

    CommandProcess commandProcess = null;
    Writer logFileWriter = null;
    if (logFilePath != null) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Start async logcat to file %s for device %s", logFilePath, deviceId);
      // Creates the parent folder if not exist.
      String logFileParentPath = new File(logFilePath).getParent();
      if (logFileParentPath != null) {
        fileUtil.prepareDir(logFileParentPath);
      }
      // Opens the log file.
      try {
        logFileWriter =
            Files.newBufferedWriter(
                Path.of(logFilePath), UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_LOGCAT_DECORATOR_OPEN_FILE_WRITER_ERROR, e.getMessage(), e);
      }
      // Async dumps to file.
      final Writer finalLogFileWriter = logFileWriter;
      commandProcess =
          adbUtil.runLogCatAsync(
              deviceId,
              options,
              filterSpecs,
              testInfo.timer().remainingTimeJava(),
              LineCallback.stopWhen(
                  line -> {
                    try {
                      // Keep line separator consistent with actual adb command output.
                      String lineSeparator =
                          sdkVersion < AndroidVersion.NOUGAT.getStartSdkVersion() ? "\r\n" : "\n";
                      finalLogFileWriter.write(line + lineSeparator);
                    } catch (IOException e) {
                      testInfo
                          .warnings()
                          .addAndLog(
                              new MobileHarnessException(
                                  AndroidErrorId.ANDROID_LOGCAT_DECORATOR_FILE_WRITER_WRITE_ERROR,
                                  "Failed to write logcat to file",
                                  e),
                              logger);
                      return true;
                    }
                    return false;
                  }));
    } else {
      testInfo.log().atInfo().alsoTo(logger).log("Start async logcat for device %s", deviceId);
      commandProcess =
          adbUtil.runLogCatAsync(
              deviceId,
              options,
              filterSpecs,
              testInfo.timer().remainingTimeJava(),
              LineCallback.does(
                  line -> testInfo.log().atInfo().alsoTo(logger).log("[logcat] %s", line)));
    }

    try {
      getDecorated().run(testInfo);
    } finally {
      if (commandProcess != null) {
        testInfo.log().atInfo().alsoTo(logger).log("Stopping logcat for device %s...", deviceId);
        commandProcess.stop();
        commandProcess.await();
        testInfo.log().atInfo().alsoTo(logger).log("Logcat stopped for device %s", deviceId);
      }
      // Closes file only after the command is stopped. Otherwise, the running command may fail to
      // write to file. See b/17269603.
      if (logFileWriter != null) {
        try {
          logFileWriter.close();
        } catch (IOException e) {
          testInfo
              .warnings()
              .addAndLog(
                  new MobileHarnessException(
                      AndroidErrorId.ANDROID_LOGCAT_DECORATOR_CLOSE_FILE_WRITER_ERROR,
                      "Failed to close logcat file",
                      e),
                  logger);
        }
      }
    }
  }

  private boolean isDeviceOnline(String deviceId)
      throws MobileHarnessException, InterruptedException {
    try {
      return systemStateManager.isOnline(deviceId);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_LOGCAT_DECORATOR_GET_ONLINE_DEVICES_ERROR, e.getMessage(), e);
    }
  }

  private String getDeviceCurrentTimeForLogcat(String deviceId)
      throws MobileHarnessException, InterruptedException {
    String deviceTime = "";
    try {
      deviceTime = adbUtil.getDeviceCurrentTimeForLogcat(deviceId);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_LOGCAT_DECORATOR_GET_DEVICE_TIME_ERROR, e.getMessage(), e);
    }
    return deviceTime;
  }

  private int getDeviceSdkVersion(String deviceId)
      throws MobileHarnessException, InterruptedException {
    try {
      return systemSettingUtil.getDeviceSdkVersion(deviceId);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_LOGCAT_DECORATOR_GET_DEVICE_SDK_ERROR, e.getMessage(), e);
    }
  }
}
