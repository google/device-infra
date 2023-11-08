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

package com.google.devtools.mobileharness.platform.android.systemstate;

import static com.google.common.base.Verify.verify;
import static com.google.common.io.Files.getFileExtension;
import static com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy.exponentialBackoff;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptions;
import com.google.devtools.mobileharness.platform.android.process.AndroidProcessUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DeviceConnectionState;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.IntentArgs;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.RebootMode;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.WaitArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryException;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryingCallable;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Utility class to perform Android system state related operations on Android device. */
public class AndroidSystemStateUtil {

  /** Interval of checking device root access. */
  public static final Duration CHECK_ROOT_INTERVAL = Duration.ofSeconds(5);

  /** Max retry times of checking device root access. */
  public static final int CHECK_ROOT_RETRY_TIMES = 10;

  /** Timeout of becoming root. */
  public static final Duration CHECK_ROOT_TIMEOUT = Duration.ofSeconds(15);

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** ADB arg for rebooting the device. */
  @VisibleForTesting static final String ADB_ARG_REBOOT = "reboot";

  /** ADB arg for rebooting the device into bootloader. */
  @VisibleForTesting static final String ADB_ARG_REBOOT_TO_BOOTLOADER = "reboot-bootloader";

  /** ADB arg for becoming root of the device/emulator. */
  @VisibleForTesting static final String ADB_ARG_ROOT = "root";

  /** ADB arg for waiting for device. */
  @VisibleForTesting static final String ADB_ARG_WAIT_FOR_DEVICE = "wait-for-device";

  /** ADB arg for rebooting the device into recovery. */
  @VisibleForTesting static final String[] ADB_ARGS_REBOOT_TO_RECOVERY = {"reboot", "recovery"};

  private static final String ADB_ARG_GET_STATE = "get-state";

  private static final String ADB_ARG_SIDELOAD = "sideload";

  @VisibleForTesting
  static final String ADB_SHELL_ENABLE_TEST_HARNESS_MODE = "cmd testharness enable";

  /** ADB action for broadcasting factory reset. */
  @VisibleForTesting
  static final String ADB_SHELL_BROADCAST_FACTORY_RESET_ACTION =
      "android.intent.action.MASTER_CLEAR";

  /** ADB component for broadcasting factory reset, required for Android >= O. */
  @VisibleForTesting
  static final String ADB_SHELL_BROADCAST_FACTORY_RESET_COMPONENT =
      "android/com.android.server.MasterClearReceiver";

  /** ADB shell broadcast command argument for checking whether the device is ready. */
  @VisibleForTesting
  static final String ADB_SHELL_BROADCAST_IS_DEVICE_READY = "check.if.device.is.ready";

  /** ADB shell command for printing user IDs. */
  @VisibleForTesting static final String ADB_SHELL_ID = "id";

  /** ADB shell command for starting the zygote process. */
  @VisibleForTesting static final String ADB_SHELL_START = "start";

  /** ADB shell command for stopping the zygote process. */
  @VisibleForTesting static final String ADB_SHELL_STOP = "stop";

  /** The properties name of boot completed. */
  @VisibleForTesting
  static final String[] BOOT_COMPLETED_PROPERTIES = {"sys.boot_completed", "dev.bootcomplete"};

  /** Interval of checking whether the Android device/emulator is ready. */
  @VisibleForTesting static final Duration CHECK_READY_INTERVAL = Duration.ofSeconds(1);

  /** Timeout of probing device/emulator readiness. */
  @VisibleForTesting static final Duration CHECK_READY_TIMEOUT = Duration.ofMinutes(5);

  private static final Duration FACTORY_RESET_WAIT_TIME = Duration.ofSeconds(30);

  // Defaults to 10m: assuming USB 2.0 transfer speed, concurrency and some buffer
  private static final Duration MININAL_SIDELOAD_EXECUTION_TIME = Duration.ofMinutes(10);

  private static final Duration SIDELOAD_WAIT_TIME = Duration.ofSeconds(5);

  /** Output signal of "adb root" command if device becomes rooted. */
  @VisibleForTesting static final String OUTPUT_BECAME_ROOTED = "restarting adbd as root";

  @VisibleForTesting
  static final String OUTPUT_BROKEN_PIPE = "Failure calling service activity: Broken pipe";

  @VisibleForTesting
  static final String OUTPUT_GET_STATE_ERROR = "error: no devices/emulators found";

  /** Output of "adb shell am broadcast" when a device is rebooting after flashed. */
  @VisibleForTesting static final String OUTPUT_DEVICE_BOOTING = "before boot completed";

  /** Output of "adb shell am broadcast" when a device is rebooting after flashed . */
  private static final String OUTPUT_DEVICE_CANT_FIND_SERVICE = "cmd: Can't find service: activity";

  /** Output of "adb shell am broadcast" when a device has not started yet. */
  private static final Pattern OUTPUT_DEVICE_NOT_FOUND_PATTERN =
      Pattern.compile(
          "(?s).*error: device(\\s|"
              + "\\s'(localhost|127.0.0.1):[0-9]+'\\s|"
              + "\\s'[0-9a-zA-Z]+'\\s)not found.*");

  /** Output of "adb shell am broadcast" when a device is rebooting after flashed. */
  @VisibleForTesting
  static final String OUTPUT_DEVICE_SYSTEM_NOT_RUNNING = "is the system running?";

  /** Output with error. */
  @VisibleForTesting static final String OUTPUT_ERROR = "Error";

  /** Output with exception. */
  @VisibleForTesting static final String OUTPUT_EXCEPTION = "Exception";

  /** Output with exit code 139. */
  @VisibleForTesting static final String OUTPUT_EXIT_CODE_139 = "exit code = 139";

  /** Output signal of "adb root" command if device is already rooted. */
  @VisibleForTesting static final String OUTPUT_IS_ROOTED = "adbd is already running as root";

  /** Output message which shows the command is killed. */
  @VisibleForTesting static final String OUTPUT_KILLED = "Killed";

  /** Output signal of "adb root" command if device can not run as root. */
  @VisibleForTesting
  static final String OUTPUT_NOT_ROOTED = "adbd cannot run as root in production builds";

  /**
   * Error output of "adb shell am broadcast" when a device is not ready after rebooting, such as:
   *
   * <ul>
   *   <li>"error: device offline"
   *   <li>"error: exec '/system/bin/sh' failed: No such file or directory"
   *   <li>"error: device unauthorized"
   *   <li>"error: protocol fault (couldn't read status length): Success" (b/65192738)
   *   <li>"error: closed" (b/77698516) *
   *   <li>"error: no devices/emulators found" (b/77698516)
   * </ul>
   */
  private static final String OUTPUT_PREFIX_DEVICE_READINESS_ERROR = "error: ";

  /** Output message which shows the command is timeout. */
  private static final String OUTPUT_TIMEOUT = "Timeout";

  /** Output of a successful broadcasting. */
  @VisibleForTesting static final String OUTPUT_BROADCAST_SUCCESS = "Broadcast completed: result=0";

  /** Short timeout for quick operations. */
  @VisibleForTesting static final Duration SHORT_COMMAND_TIMEOUT = Duration.ofSeconds(5);

  @VisibleForTesting
  static final String RESET_VIA_TEST_HARNESS_SCREEN_LOCKED_ERROR = "there is a lock screen";

  private static final String OTA_PACKAGE_EXTENSION = "zip";
  private static final RetryStrategy RETRY_STRATEGY =
      exponentialBackoff(Duration.ofSeconds(3), /* multiplier= */ 1, /* numAttempts= */ 10);

  private final Adb adb;

  /** {@code Clock} for getting current system time. */
  private final Clock clock;

  /** {@code Sleeper} for waiting the device to become ready for use. */
  private final Sleeper sleeper;

  private final AndroidAdbUtil adbUtil;

  private final AndroidProcessUtil androidProcessUtil;

  public AndroidSystemStateUtil() {
    this(
        new Adb(),
        Sleeper.defaultSleeper(),
        Clock.systemUTC(),
        new AndroidAdbUtil(),
        new AndroidProcessUtil());
  }

  @VisibleForTesting
  AndroidSystemStateUtil(
      Adb adb,
      Sleeper sleeper,
      Clock clock,
      AndroidAdbUtil adbUtil,
      AndroidProcessUtil androidProcessUtil) {
    this.adb = adb;
    this.sleeper = sleeper;
    this.clock = clock;
    this.adbUtil = adbUtil;
    this.androidProcessUtil = androidProcessUtil;
  }

  /**
   * Becomes non-root if possible.
   *
   * <p>This method will disconnect device for a short while, need to cache device before using it.
   *
   * @param serial serial number of the device
   * @return whether the device is unrooted
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean becomeNonRoot(String serial) throws MobileHarnessException, InterruptedException {
    StringBuilder output = new StringBuilder();
    for (int i = 1; i <= CHECK_ROOT_RETRY_TIMES; i++) {
      try {
        // First check if device is already running as non-root.
        if (isDeviceNonRoot(serial, output)) {
          return true;
        }

        // Restarting adbd without root permissions via setting properties
        adbUtil.setProperty(
            serial, "service.adb.root", "0", /* ignoreError= */ false, CHECK_ROOT_TIMEOUT);
        adbUtil.setProperty(
            serial, "ctl.restart", "adbd", /* ignoreError= */ false, CHECK_ROOT_TIMEOUT);
        waitForState(serial, DeviceConnectionState.DEVICE, CHECK_ROOT_TIMEOUT);

        // Check again if device is non-root now.
        if (isDeviceNonRoot(serial, output)) {
          return true;
        }
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Device %s failed to become non-root(attempt-%d)", serial, i);
        if (i >= CHECK_ROOT_RETRY_TIMES) {
          logger.atWarning().log(
              "Failed to check the root access of device %s: %s", serial, output);
          return false;
        }
      }
      sleeper.sleep(CHECK_ROOT_INTERVAL);
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_SYSTEM_STATE_UNROOT_DEVICE_ERROR,
        "Failed to check the root access of device " + serial + ": " + output);
  }

  /**
   * Becomes root if possible.
   *
   * <p>This method will disconnect device for a short while, need to cache device before using it.
   *
   * @param serial serial number of the device
   * @return whether the device is rooted
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean becomeRoot(String serial) throws MobileHarnessException, InterruptedException {
    // After rebooting, the first time running "adb root" command with Galaxy Nexus may return empty
    // output. Retries until we get the expected output.
    String output = null;
    for (int i = 1; i <= CHECK_ROOT_RETRY_TIMES; i++) {
      try {
        // Sets a small timeout for this command because Galaxy Note 2 and HTC M8 can hang this
        // command.
        output =
            adb.run(
                    serial,
                    new String[] {ADB_ARG_ROOT},
                    CHECK_ROOT_TIMEOUT,
                    // Galaxy Note 2 and HTC M8 can hang here even we've gotten such output.
                    // So stops the command once we got what we want.
                    LineCallback.stopWhen(
                        line ->
                            line.contains(OUTPUT_NOT_ROOTED)
                                || line.contains(OUTPUT_BECAME_ROOTED)
                                || line.contains(OUTPUT_IS_ROOTED)))
                .trim();
      } catch (MobileHarnessException e) {
        // If timed out(e.g. Galaxy Note 2) or failed, we consider this device has no root access.
        logger.atWarning().withCause(e).log(
            "Device %s failed to become root(attempt-%d)", serial, i);
        if (i >= CHECK_ROOT_RETRY_TIMES) {
          return false;
        }
        continue;
      } finally {
        // Always sleeps for a while after running the "adb root" command. Because Galaxy Nexus may
        // be disconnected for a short while after running the command, no matter it returns the
        // result immediately or not.
        sleeper.sleep(CHECK_ROOT_INTERVAL);
      }
      logger.atInfo().log("Device %s: %s", serial, output);
      if (!output.isEmpty()) {
        if (output.contains(OUTPUT_BECAME_ROOTED) || output.contains(OUTPUT_IS_ROOTED)) {
          return true;
        } else if (output.contains(OUTPUT_NOT_ROOTED)) {
          return false;
        }
      }
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_SYSTEM_STATE_ROOT_DEVICE_ERROR,
        "Failed to check the root access of device " + serial + ": " + output);
  }

  /**
   * Factory reset. Verified working with 17 <= API level <= 31 (later SDKs may need to re-verify).
   * Note this only works on rooted devices. Otherwise, there is no effect.
   *
   * @param serial device serial number
   * @param waitTime wait time (seconds) for device to be disconnected after calling factory reset,
   *     or null to use default wait time.
   */
  public void factoryReset(String serial, @Nullable Duration waitTime)
      throws MobileHarnessException, InterruptedException {
    try {
      adbUtil.broadcast(
          UtilArgs.builder().setSerial(serial).build(),
          IntentArgs.builder()
              .setAction(ADB_SHELL_BROADCAST_FACTORY_RESET_ACTION)
              .setComponent(ADB_SHELL_BROADCAST_FACTORY_RESET_COMPONENT)
              .build(),
          /* checkCmdOutput= */ false,
          SHORT_COMMAND_TIMEOUT);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_STATE_FACTORY_RESET_VIA_BROADCAST_ERROR, e.getMessage(), e);
    }
    // Sleep for command propagation.
    sleeper.sleep(waitTime == null ? FACTORY_RESET_WAIT_TIME : waitTime);
  }

  /**
   * Factory reset Android Q+ devices via Test Harness Mode, which will also retain adb key before
   * factory reset and apply some pre-defined settings to device.
   *
   * <p>Settings in Test Harness Mode include skipping setup wizard, staying device awake when
   * charging, etc. This mode works on user/userdebug builds on Q+, see go/adb-test-harness for more
   * details.
   *
   * @param serial device serial number
   * @param waitTime wait time (seconds) for device to be disconnected after calling factory reset
   *     via test harness command, or null to use default wait time.
   */
  public void factoryResetViaTestHarness(String serial, @Nullable Duration waitTime)
      throws MobileHarnessException, InterruptedException {
    try {
      String unused = adb.runShellWithRetry(serial, ADB_SHELL_ENABLE_TEST_HARNESS_MODE);
    } catch (MobileHarnessException e) {
      if (e.getMessage().contains(RESET_VIA_TEST_HARNESS_SCREEN_LOCKED_ERROR)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SYSTEM_STATE_FACTORY_RESET_VIA_TEST_HARNESS_SCREEN_LOCKED_ERROR,
            format(
                "Failed to factory reset device %s via Test Harness Mode as device screen seems"
                    + " locked.",
                serial),
            e);
      }

      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_STATE_FACTORY_RESET_VIA_TEST_HARNESS_ERROR,
          format(
              "Failed to factory reset device %s via Test Harness Mode; command \"%s\" failed",
              serial, ADB_SHELL_ENABLE_TEST_HARNESS_MODE),
          e);
    }
    // Sleep for command propagation.
    sleeper.sleep(waitTime == null ? FACTORY_RESET_WAIT_TIME : waitTime);
  }

  /**
   * Checks whether the device is ready after rebooting.
   *
   * @param serial serial number of the device
   * @return whether the device is ready
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isOnline(String serial) throws MobileHarnessException, InterruptedException {
    return isOnline(serial, /* silent= */ false, /* rateLimitLog= */ true);
  }

  /**
   * Checks whether the device is ready after rebooting.
   *
   * @param serial serial number of the device
   * @param silent whether log broadcast exception detailed messages
   * @param rateLimitLog whether to rate limit the logging of "device not online", which is every 2
   *     seconds at most.
   * @return whether the device is ready
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  private boolean isOnline(String serial, boolean silent, boolean rateLimitLog)
      throws MobileHarnessException, InterruptedException {
    String broadcastReadyOutput = null;
    String bootCompletedOutput = null;
    try {
      // The total wait time here will be aligned to CHECK_READY_TIMEOUT in waitUntilReady,
      // b/69908518
      broadcastReadyOutput =
          adbUtil.broadcast(
              UtilArgs.builder().setSerial(serial).build(),
              IntentArgs.builder().setOtherArgument(ADB_SHELL_BROADCAST_IS_DEVICE_READY).build(),
              /* checkCmdOutput= */ false,
              CHECK_READY_TIMEOUT);
      bootCompletedOutput =
          adbUtil.getProperty(serial, ImmutableList.copyOf(BOOT_COMPLETED_PROPERTIES));
    } catch (MobileHarnessException e) {
      String message = e.getMessage();
      // The command may have a non-zero exit code when a device is not fully booted. Returns false
      // in this situation.
      // TODO: The error message is not exactly the command output. So here we are using
      // contains(OUTPUT_PREFIX_DEVICE_READINESS_ERROR) instead of
      // startsWith(OUTPUT_PREFIX_DEVICE_READINESS_ERROR). Should be changed to verify pure command
      // output with startsWith(OUTPUT_PREFIX_DEVICE_READINESS_ERROR)
      if (message.contains(OUTPUT_PREFIX_DEVICE_READINESS_ERROR)
          || OUTPUT_DEVICE_NOT_FOUND_PATTERN.matcher(message).matches()
          || message.contains(OUTPUT_DEVICE_BOOTING)
          || message.contains(OUTPUT_DEVICE_CANT_FIND_SERVICE)
          || message.contains(OUTPUT_DEVICE_SYSTEM_NOT_RUNNING)
          || message.contains(OUTPUT_TIMEOUT)
          // Android N with adb 1.0.36 may exit with exit code 139: b/32169418.
          || message.contains(OUTPUT_EXIT_CODE_139)) {
        if (rateLimitLog) {
          logger.atInfo().atMostEvery(2, SECONDS).log(
              "Device %s is not online yet%s", serial, silent ? "." : format(": [%s]", message));
        } else {
          logger.atInfo().log(
              "Device %s is not online yet%s", serial, silent ? "." : format(": [%s]", message));
        }
        return false;
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SYSTEM_STATE_BROADCAST_DEVICE_IS_READY_ERROR, message, e);
      }
    }
    if (broadcastReadyOutput.isEmpty()) {
      // The output of Galaxy Y and Galaxy Mini is empty when they are ready.
      logger.atInfo().log("Broadcast check.if.device.is.ready return empty for device %s", serial);
    } else if (broadcastReadyOutput.contains(OUTPUT_ERROR)
        || broadcastReadyOutput.contains(OUTPUT_KILLED)
        || broadcastReadyOutput.contains(OUTPUT_EXCEPTION)) {
      return false;
    } else if (broadcastReadyOutput.contains(OUTPUT_BROADCAST_SUCCESS)) {
      logger.atInfo().log(
          "Broadcast check.if.device.is.ready completed: result=0 for device %s", serial);
    } else {
      logger.atWarning().log(
          "Broadcast check.if.device.is.ready unexpected: %s for device %s",
          broadcastReadyOutput, serial);
      return false;
    }
    logger.atInfo().log(
        "Device property sys.boot_completed=%s for device %s", bootCompletedOutput, serial);

    // Also check if Android Package Manager is up and running. See b/37321987
    return bootCompletedOutput.equals("1")
        && androidProcessUtil.checkServiceAvailable(
            serial, Ascii.toLowerCase(DumpSysType.PACKAGE.name()));
  }

  /**
   * Checks whether the oxygen device is online by finding its sdk version.
   *
   * @return true if online and false otherwise
   */
  public boolean isOxygenDeviceOnline(String serial) throws InterruptedException {
    String sdkVersion = "";
    try {
      sdkVersion = adbUtil.getProperty(serial, AndroidProperty.SDK_VERSION);
      logger.atInfo().log("Oxygen device with found with sdkVersion %s", sdkVersion);
    } catch (MobileHarnessException e) {
      return false;
    }
    return !sdkVersion.isEmpty();
  }

  /**
   * Reboots the device, and returns immediately.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void reboot(String serial) throws MobileHarnessException, InterruptedException {
    reboot(serial, RebootMode.SYSTEM_IMAGE);
  }

  /**
   * Reboots the device into specified mode.
   *
   * <p>Executes the adb reboot command and returns immediately. No check of the completeness.
   *
   * @param serial id of the device.
   * @param mode of reboot.
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void reboot(String serial, RebootMode mode)
      throws InterruptedException, MobileHarnessException {
    try {
      // No output expected.
      String unused = adb.run(serial, mode.getRebootArgs());
    } catch (MobileHarnessException e) {
      AndroidErrorId id;
      switch (mode) {
        case BOOTLOADER:
          id = AndroidErrorId.ANDROID_SYSTEM_STATE_REBOOT_TO_BOOTLOADER_ERROR;
          break;
        case RECOVERY:
          id = AndroidErrorId.ANDROID_SYSTEM_STATE_REBOOT_TO_RECOVERY_ERROR;
          break;
        default:
          id = AndroidErrorId.ANDROID_SYSTEM_STATE_REBOOT_ERROR;
      }
      throw new MobileHarnessException(id, e.getMessage(), e);
    }
  }

  /**
   * Reboots the device into bootloader.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   * @deprecated Prefer {@code reboot(serial, RebootMode.BOOTLOADER)}.
   */
  // TODO: b/307912207 - Inline the method.
  @Deprecated
  public void rebootToBootloader(String serial)
      throws MobileHarnessException, InterruptedException {
    reboot(serial, RebootMode.BOOTLOADER);
  }

  /**
   * Reboots the device into recovery.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   * @deprecated Prefer {@code reboot(serial, RebootMode.RECOVERY)}.
   */
  // TODO: b/307912207 - Inline the method.
  @Deprecated
  public void rebootToRecovery(String serial) throws MobileHarnessException, InterruptedException {
    reboot(serial, RebootMode.RECOVERY);
  }

  /**
   * Restarts the Zygote process.
   *
   * <p>Should only be called when device is managed by Mobile Harness.
   */
  public void softReboot(String serial) throws MobileHarnessException, InterruptedException {
    String output = "";
    Exception exception = null;
    try {
      output = adb.runShell(serial, ADB_SHELL_STOP);
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_STATE_STOP_ZYGOTE_PROCESS_ERROR,
          exception == null ? output : exception.getMessage(),
          exception);
    }

    try {
      output = adb.runShell(serial, ADB_SHELL_START);
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_STATE_START_ZYGOTE_PROCESS_ERROR,
          exception == null ? output : exception.getMessage(),
          exception);
    }
  }

  /**
   * Gets the state of the device.
   *
   * <p>Executes the adb get-state. Will retry until a valid state is obtained or timeout in 30s.
   *
   * @param serial id of the device.
   * @return a valid {@link DeviceConnectionState}.
   * @throws MobileHarnessException if fails to execute adb command, getting unexpected output, or
   *     timeout.
   */
  public DeviceConnectionState getState(String serial) throws MobileHarnessException {
    RetryingCallable<String> getStateCallable =
        RetryingCallable.newBuilder(
                () -> {
                  String output = adb.run(serial, new String[] {ADB_ARG_GET_STATE}).trim();
                  verify(
                      !output.equals(OUTPUT_GET_STATE_ERROR),
                      "Device %s is not ready. Retry.",
                      serial);
                  return output;
                },
                RETRY_STRATEGY)
            .setPredicate(e -> e instanceof VerifyException)
            .build();
    try {
      String output = getStateCallable.call();
      return DeviceConnectionState.of(output)
          .orElseThrow(
              () ->
                  new MobileHarnessException(
                      AndroidErrorId.ANDROID_SYSTEM_STATE_GET_STATE_ERROR,
                      format(
                          "Unexpected output of adb get-state for device %s: %s", serial, output)));
    } catch (RetryException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_STATE_GET_STATE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Waits for the device to get into a specific state.
   *
   * <p>Executes adb wait-for-STATE commands until return and then verify the state is expected.
   *
   * @param serial id of the device.
   * @param state the expected state to wait for.
   * @param timeout max wait time.
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void waitForState(String serial, DeviceConnectionState state, Duration timeout)
      throws InterruptedException, MobileHarnessException {
    try {
      // No output expected.
      String unused = adb.run(serial, new String[] {state.getWaitForArg()}, timeout);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_STATE_WAIT_FOR_STATE_ERROR, e.getMessage(), e);
    }
    if (!state.equals(DeviceConnectionState.DISCONNECT)) {
      DeviceConnectionState finalState = getState(serial);
      MobileHarnessExceptions.check(
          Objects.equals(state, finalState),
          AndroidErrorId.ANDROID_SYSTEM_STATE_WAIT_FOR_STATE_ERROR,
          () -> "Get unexpected state " + finalState + " after " + state.getWaitForArg());
    }
  }

  /**
   * Sideloads the OTA package to device.
   *
   * @param serial id of the device
   * @param otaPackage to sideload
   * @param timeout of the whole command
   * @param waitTime extra wait for the state transition
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void sideload(
      String serial, File otaPackage, Duration timeout, @Nullable Duration waitTime)
      throws MobileHarnessException, InterruptedException {
    MobileHarnessExceptions.check(
        otaPackage.isFile()
            && Objects.equals(getFileExtension(otaPackage.getName()), OTA_PACKAGE_EXTENSION),
        AndroidErrorId.ANDROID_SYSTEM_STATE_SIDELOAD_INVALID_OTA_PACKAGE,
        () -> "Invalid OTA package: " + otaPackage.getAbsolutePath());
    Duration wait = Optional.ofNullable(waitTime).orElse(SIDELOAD_WAIT_TIME);
    Duration cmdTimeout = timeout.minus(wait);
    MobileHarnessExceptions.check(
        cmdTimeout.compareTo(MININAL_SIDELOAD_EXECUTION_TIME) > 0,
        AndroidErrorId.ANDROID_SYSTEM_STATE_SIDELOAD_INVALID_TIMEOUT,
        () ->
            format(
                "Timeout %s should be larger than waitTime %s + minimal sideload execution time %s",
                timeout, wait, MININAL_SIDELOAD_EXECUTION_TIME));
    try {
      String output =
          adb.run(serial, new String[] {ADB_ARG_SIDELOAD, otaPackage.getAbsolutePath()}, cmdTimeout)
              .trim();
      logger.atInfo().log("Sideloading ...\n%s", output);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_STATE_SIDELOAD_ERROR,
          format("Failed to sideload %s to %s.", otaPackage, serial),
          e);
    }
    // Sleep for command propagation.
    sleeper.sleep(wait);
  }

  /**
   * Waits until adb connects to android device.
   *
   * @param serial serial number of the device
   * @param timeout max wait and retry time
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   * @deprecated Prefer {@code waitForState(serial, DeviceConnectionState.DEVICE, timeout)}.
   */
  // TODO: b/307912207 - Inline the method.
  @Deprecated
  public void waitForDevice(String serial, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    waitForState(serial, DeviceConnectionState.DEVICE, timeout);
  }

  /**
   * Waits until the device/emulator is online.
   *
   * @param serial the serial number of the device
   * @throws MobileHarnessException if device is not ready after waiting for {@link
   *     #CHECK_READY_TIMEOUT}
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void waitUntilReady(String serial) throws MobileHarnessException, InterruptedException {
    waitUntilReady(serial, CHECK_READY_TIMEOUT);
  }

  /**
   * Waits until the device/emulator is online.
   *
   * @param serial the serial number of the device
   * @param checkReadyTimeout max wait time for checking the readiness of the device
   * @throws MobileHarnessException if device is not ready after waiting for {@link
   *     #CHECK_READY_TIMEOUT}
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void waitUntilReady(String serial, Duration checkReadyTimeout)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Waiting for device %s online...", serial);
    boolean isDeviceOnline =
        AndroidAdbUtil.waitForDeviceReady(
            UtilArgs.builder().setSerial(serial).build(),
            utilArgs -> isDeviceOnline(utilArgs.serial()),
            WaitArgs.builder()
                .setSleeper(sleeper)
                .setClock(clock)
                .setCheckReadyInterval(CHECK_READY_INTERVAL)
                .setCheckReadyTimeout(checkReadyTimeout)
                .build());
    if (!isDeviceOnline) {
      Exception ex = null;
      // Try one more isOnline check which will throw an error with the reason of failure if there
      // was any.
      try {
        if (isOnline(serial, /* silent= */ false, /* rateLimitLog= */ false)) {
          logger.atInfo().log("Device %s is detected as online ready at the last check", serial);
          return;
        }
      } catch (MobileHarnessException e) {
        if (e.getMessage().contains(OUTPUT_BROKEN_PIPE)) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_SYSTEM_STATE_CHECK_DEVICE_ONLINE_BROKEN_PIPE_ERROR,
              format(
                  "Error when checking device %s online status. Device system services may not be"
                      + " ready yet.",
                  serial),
              e);
        }
        ex = e;
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_STATE_DEVICE_NOT_ONLINE_READY,
          format(
              "Device/emulator %s is still not online ready %d milliseconds!",
              serial, checkReadyTimeout.toMillis()),
          ex);
    }

    // Does not try to become root and send key event at this point. Otherwise, may put the device
    // into a bad state. And the device will hang some adb commands such as input keyevent or
    // enabling WI-FI.
  }

  private boolean isDeviceNonRoot(String serial, StringBuilder sb)
      throws MobileHarnessException, InterruptedException {
    // Clear the StringBuilder first.
    if (sb.length() > 0) {
      sb.delete(0, sb.length());
    }

    String output = adb.runShell(serial, ADB_SHELL_ID, CHECK_ROOT_TIMEOUT);
    sb.append(output);
    return output.matches("uid=\\d+\\(shell\\).*");
  }

  /** Helper method for {@link #waitUntilReady(String, Duration)} only. */
  private boolean isDeviceOnline(String serial) {
    try {
      return isOnline(serial, /* silent= */ true, /* rateLimitLog= */ true);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Error caught when checking if device %s is online:%n%s", serial, e.getMessage());
      return false;
    } catch (InterruptedException ie) {
      logger.atWarning().log(
          "Caught interrupted exception when checking if device %s is online, interrupt current "
              + "thread:%n%s",
          serial, ie.getMessage());
      Thread.currentThread().interrupt();
    }
    return false;
  }
}
