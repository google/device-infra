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

package com.google.devtools.mobileharness.platform.android.media;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidContent;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/**
 * Utility methods to manage audio, video, and other media types on Android devices/emulators.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class AndroidMediaUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** ADB argument flag for intent extra int */
  @VisibleForTesting static final String ADB_ARG_INTENT_EXTRA_INT = "--ei %s %d ";

  /** ADB argument flag for intent extra string */
  @VisibleForTesting static final String ADB_ARG_INTENT_EXTRA_STRING = "--es %s %s ";

  /** ADB shell command to launch vrcore's main VR activity to enter VR mode. */
  static final String ADB_SHELL_ENTER_VR_MODE =
      "am start -n com.google.vr.vrcore/.daydream.MetaworldActivity";

  /** ADB shell command for inputting text. Should be followed by the text. */
  @VisibleForTesting static final String ADB_SHELL_INPUT_TEXT = "input text";

  /** ADB shell template to catch screenshot. Should fill with output path. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_SCREEN_SHOT = "screencap -p %s";

  /** ADB shell command to launch vrcore RecorderService intent. */
  @VisibleForTesting
  static final String ADB_SHELL_VR_RECORDER_SERVICE =
      "am startservice -n com.google.vr.vrcore/.capture.record.RecorderService ";

  /** The pattern of screen orientation. */
  private static final Pattern PATTERN_SCREEN_ORIENTATION =
      Pattern.compile("SurfaceOrientation: (\\d+)");

  @VisibleForTesting
  static final String ANDROID_CONTENT_PROVIDER_SETTING = "content://settings/system";

  /** The delay between issuing multiple intents to RecorderService. */
  @VisibleForTesting static final Duration RECORDER_SERVICE_DELAY = Duration.ofSeconds(1);

  /** Android SDK ADB command line tools executor. */
  private final Adb adb;

  /** Adb command wrapper class. */
  private final AndroidAdbUtil adbUtil;

  /** {@code Sleeper} for waiting the device to become ready for use. */
  private final Sleeper sleeper;

  /** {@code Clock} for getting current system time. */
  private final Clock clock;

  private final AndroidProcessUtil androidProcessUtil;

  public AndroidMediaUtil() {
    this(
        new Adb(),
        new AndroidAdbUtil(),
        Sleeper.defaultSleeper(),
        Clock.systemUTC(),
        new AndroidProcessUtil());
  }

  @VisibleForTesting
  AndroidMediaUtil(
      Adb adb,
      AndroidAdbUtil adbUtil,
      Sleeper sleeper,
      Clock clock,
      AndroidProcessUtil androidProcessUtil) {
    this.adb = adb;
    this.adbUtil = adbUtil;
    this.sleeper = sleeper;
    this.clock = clock;
    this.androidProcessUtil = androidProcessUtil;
  }

  /**
   * Enables the underlying Adb object to have its command output logged to the class logger.
   *
   * <p>WARNING: This will log ALL command output for Adb commands from this instance of
   * AndroidMediaUtil. Take caution to make sure this won't unintentionally spam your log.
   */
  public void enableCommandOutputLogging() {
    adb.enableCommandOutputLogging();
  }

  /**
   * Enters VR mode by launching VrCore's MetaworldActivity (a VR activity).
   *
   * <p>API level requirement: 24; System Feature requirement: {@link
   * android.content.pm.PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE} (i.e. all Daydream-ready
   * devices)
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void enterVrMode(String serial) throws MobileHarnessException, InterruptedException {
    try {
      String unused = adb.runShell(serial, ADB_SHELL_ENTER_VR_MODE);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_ENTER_VR_MODE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets the screen orientation of the given device.
   *
   * <p>API level requirement: 10
   *
   * @param serial serial number of the device
   * @return the current screen orientation of the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  @CanIgnoreReturnValue
  public ScreenOrientation getScreenOrientation(String serial)
      throws MobileHarnessException, InterruptedException {
    StringBuilder builder = new StringBuilder();
    // When api level >= 16, the orientation information is in dumpsys input.
    // When api level < 16, the orientation information is in dumpsys window.
    // The output is like:
    // ... ...
    //       SurfaceOrientation: 0
    // ... ...
    try {
      builder.append(adbUtil.dumpSys(serial, DumpSysType.INPUT));
      builder.append(adbUtil.dumpSys(serial, DumpSysType.WINDOW));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_DUMPSYS_ORIENTATION_INFO_ERROR, e.getMessage(), e);
    }
    String output = builder.toString();
    Matcher matcher = PATTERN_SCREEN_ORIENTATION.matcher(output);
    matcher.find();
    try {
      String orientationStr = matcher.group(1);
      int orientation = Integer.parseInt(orientationStr);
      return ScreenOrientation.values()[orientation];
    } catch (IllegalStateException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_GET_SCREEN_ORIENTATION_ERROR,
          "Failed to parse screen orientation for device " + serial + ":\n" + output,
          e);
    }
  }

  /**
   * Inputs a text to the device.
   *
   * <p>API level requirement: 10
   *
   * <p>Note that the text needs to be encoded.
   *
   * @param serial serial number of the device
   * @param text the string to input to the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void inputText(String serial, String text)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    Exception exception = null;
    try {
      output = adb.runShellWithRetry(serial, ADB_SHELL_INPUT_TEXT + " " + text);
    } catch (MobileHarnessException e) {
      exception = e;
    }
    logger.atInfo().log("Input text %s to device %s", text, serial);
    if (exception != null || !StrUtil.isEmptyOrWhitespace(output)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_INPUT_TEXT_ERROR,
          String.format(
              "Failed to input text %s: %s",
              text, exception == null ? output : exception.getMessage()),
          exception);
    }
  }

  /**
   * Records the screen of an Android real device asynchronously.
   *
   * <p>API level requirement: 19; Max recording time: 180s; Bit rate range: [100000, 100000000]
   *
   * <p>Ensure parent dir of {@code outputFileOnDevice} exists before calling this method.
   *
   * @param serial serial number of the device
   * @param screenRecordArgs screenrecord command's arguments.
   * @param timeout the shell command timeout
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   * @see <a href="http://bigflake.com/screenrecord/">Android screenrecord shell command</a>
   */
  @CanIgnoreReturnValue
  @Nonnull
  public CommandProcess recordScreen(
      String serial, ScreenRecordArgs screenRecordArgs, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    String command = screenRecordArgs.toShellCmd();
    try {
      return adb.runShellAsync(serial, command, timeout);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_RECORD_SCREEN_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Records the screen of a device in VR mode using VrCore's RecorderService {@link
   * com.google.vr.vrcore.capture.record.RecorderService}.
   *
   * <p>API level requirement: 26;
   *
   * @param serial serial number of the device
   * @param outputFileOnDevice the output file path on device
   * @param bitRate the video bit rate
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void recordScreenVr(String serial, String outputFileOnDevice, int bitRate)
      throws MobileHarnessException, InterruptedException {
    // Make sure last recording is stopped before starting a new one.
    // If Recorder is not running, this is a No-op.
    stopScreenRecordVr(serial);
    sleeper.sleep(RECORDER_SERVICE_DELAY);
    startScreenRecordVr(serial, outputFileOnDevice, bitRate);
  }

  /**
   * Rotates the screen to a specific orientation. Only works when accelerometer rotation is
   * disabled.
   *
   * <p>Requires API level 16.
   *
   * @param serial serial number of the device
   * @param orientation target orientation to rotate to
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void rotateScreen(String serial, ScreenOrientation orientation)
      throws MobileHarnessException, InterruptedException {
    AndroidContent contentArgs =
        AndroidContent.builder()
            .setCommand(AndroidContent.Command.INSERT)
            .setUri(ANDROID_CONTENT_PROVIDER_SETTING)
            .setOtherArgument(
                String.format(
                    "--bind name:s:user_rotation --bind value:i:%d", orientation.ordinal()))
            .build();
    try {
      adbUtil.content(UtilArgs.builder().setSerial(serial).build(), contentArgs);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_ROTATE_SCREEN_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Enables or disables the accelerometer controlling rotation.
   *
   * <p>Requires API level 16.
   *
   * @param serial serial number of the device
   * @param enable set to true to enable the accelerometer, false to disable
   */
  public void setAccelerometerRotation(String serial, boolean enable)
      throws MobileHarnessException, InterruptedException {
    AndroidContent contentArgs =
        AndroidContent.builder()
            .setCommand(AndroidContent.Command.INSERT)
            .setUri(ANDROID_CONTENT_PROVIDER_SETTING)
            .setOtherArgument(
                String.format(
                    "--bind name:s:accelerometer_rotation --bind value:i:%d", enable ? 1 : 0))
            .build();
    try {
      adbUtil.content(UtilArgs.builder().setSerial(serial).build(), contentArgs);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_SET_ACCELEROMETER_ROTATION_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Launches an intent to start recording using VrCore's RecorderService ({@link
   * com.google.vr.vrcore.capture.record.RecorderService})
   *
   * <p>API level requirement: 26;
   *
   * @param serial serial number of the device
   * @param outputFileOnDevice the output file path on device
   * @param bitRate the video bit rate
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void startScreenRecordVr(String serial, String outputFileOnDevice, int bitRate)
      throws MobileHarnessException, InterruptedException {
    String cmd =
        String.format(
            ADB_SHELL_VR_RECORDER_SERVICE
                + ADB_ARG_INTENT_EXTRA_STRING
                + ADB_ARG_INTENT_EXTRA_STRING
                + ADB_ARG_INTENT_EXTRA_INT,
            "command",
            "START",
            "path",
            outputFileOnDevice,
            "bitRate",
            bitRate);
    String result = "";
    try {
      result = adb.runShellWithRetry(serial, cmd);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_START_SCREEN_RECORD_VR_ERROR, e.getMessage(), e);
    }
    logger.atInfo().log("startScreenRecordVr command: %s, result: %s", cmd, result);
  }

  /**
   * Kill screenrecord process on device by sending SIGINT to the process.
   *
   * @param serial device serial number
   * @param timeout timeout counter to kill process
   * @throws MobileHarnessException if fails to execute the command to kill
   * @throws InterruptedException if the thread execution is interrupted
   */
  public void stopScreenRecord(String serial, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    Set<String> killedProcesses = new HashSet<>();
    String processId = null;
    String processName = ScreenRecordArgs.COMMAND_NAME;
    Instant expireTime = clock.instant().plus(timeout);
    int sdkVersion = adbUtil.getIntProperty(serial, AndroidProperty.SDK_VERSION);
    UtilArgs utilArgs = UtilArgs.builder().setSerial(serial).setSdkVersion(sdkVersion).build();

    while ((processId = androidProcessUtil.getProcessId(utilArgs, processName)) != null) {
      if (clock.instant().isAfter(expireTime)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_MEDIA_UTIL_STOP_SCREEN_RECORD_TIMEOUT,
            "Timeout to kill screenrecord process");
      }

      // ScreenRecord need some delay to finish video encoding after signal sent.
      // Continue sending signal will break video encoder.
      if (killedProcesses.contains(processId)) {
        sleeper.sleep(RECORDER_SERVICE_DELAY);
      } else {
        logger.atInfo().log("Send Ctrl+C to ScreenRecord process %s", processId);
        androidProcessUtil.stopProcess(serial, processId, KillSignal.SIGINT);
        killedProcesses.add(processId);
      }
    }
  }

  /**
   * Launches an intent to stop recording using VrCore's RecorderService ({@link
   * com.google.vr.vrcore.capture.record.RecorderService})
   *
   * <p>API level requirement: 26;
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void stopScreenRecordVr(String serial)
      throws MobileHarnessException, InterruptedException {
    String cmd =
        ADB_SHELL_VR_RECORDER_SERVICE
            + String.format(ADB_ARG_INTENT_EXTRA_STRING, "command", "STOP");
    String result = "";
    try {
      result = adb.runShellWithRetry(serial, cmd);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_STOP_SCREEN_RECORD_VR_ERROR, e.getMessage(), e);
    }
    logger.atInfo().log("stopScreenRecordVr command: %s, result: %s", cmd, result);
  }

  /**
   * Takes screenshot. Only works if the device has root access and API level >= 10.
   *
   * <p>Ensure parent dir of {@code outputFilePathOnDevice} exists before calling this method.
   *
   * @param serial serial number of device
   * @param outputFilePathOnDevice the output file path on device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void takeScreenshot(String serial, String outputFilePathOnDevice)
      throws MobileHarnessException, InterruptedException {
    String output = null;
    Exception exception = null;
    try {
      output =
          adb.runShellWithRetry(
              serial, String.format(ADB_SHELL_TEMPLATE_SCREEN_SHOT, outputFilePathOnDevice));
    } catch (MobileHarnessException e) {
      exception = e;
    }
    // for fold device, the output is:
    // "
    // [Warning] Multiple displays were found, but no display id was specified! Defaulting to the
    // first display found, however this default is not guaranteed to be consistent across captures.
    // A display id should be specified.
    // A display ID can be specified with the [-d display-id] option.
    // See "dumpsys SurfaceFlinger --display-id" for valid display IDs.
    // "
    // The screenshot is still stored in outputFilePathOnDevice, for this case, we should not
    // throw exception for such cases. (b/277879807)
    if (exception != null
        || (!Strings.isNullOrEmpty(output)
            && !output.contains("Defaulting to the first display found"))) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_MEDIA_UTIL_TAKE_SCREEN_SHOT_ERROR,
          String.format(
              "Failed to take screenshot to [%s]:%n%s",
              outputFilePathOnDevice, exception == null ? output : exception.getMessage()),
          exception);
    }
  }
}
