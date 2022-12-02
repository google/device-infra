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

package com.google.devtools.mobileharness.platform.android.process;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.IntentArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil.KillSignal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import javax.annotation.Nullable;

/**
 * Utility class to manage applications/processes/services on Android devices/emulators.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class AndroidProcessUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** ADB shell command for am force-stop */
  @VisibleForTesting static final String ADB_SHELL_AM_FORCE_STOP = "am force-stop ";

  /** ADB shell command for showing process status. */
  @VisibleForTesting static final String ADB_SHELL_GET_PROCESS_STATUS = "ps";

  /** ADB arg for killing a process on a device. Should be followed with the process ID. */
  @VisibleForTesting static final String ADB_SHELL_KILL_PROCESS = "kill";

  /** ADB shell command to start an app. Should be followed by the "package/runner" params. */
  @VisibleForTesting
  static final String ADB_SHELL_START_APPLICATION = "am start -a android.intent.action.MAIN -n";

  /** ADB shell command to start a service. Should be followed by the "package/runner" params. */
  @VisibleForTesting static final String ADB_SHELL_START_SERVICE = "am startservice";

  /** ADB shell command to stop a service. Should be followed by the "package/runner" params. */
  @VisibleForTesting static final String ADB_SHELL_STOP_SERVICE = "am stopservice";

  /** ADB shell command to start an app. Should be followed by the intent param. */
  @VisibleForTesting static final String ADB_SHELL_START_APPLICATION_BY_INTENT = "am start ";

  /** ADB shell template for dumpheap. Should fill with: process id, output file. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_DUMP_HEAP = "am dumpheap %s %s";

  /** ADB shell dumpsys command for dumping service info. Should fill with: package name. */
  @VisibleForTesting
  static final String ADB_SHELL_TEMPLATE_DUMP_SYS_SERVICE = "dumpsys activity services -p %s";

  /** ADB shell command for checking state of a specific service */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_SERVICE_CHECK = "service check %s";

  /** Output signal when process does not exist. */
  @VisibleForTesting static final String OUTPUT_NO_PROCESS = "No such process";

  /** Output of a successful installation/uninstallation. */
  @VisibleForTesting static final String OUTPUT_SERVICE_FOUND = "Service %s: found";

  /** Output of a failed start application. */
  public static final String OUTPUT_START_APP_FAILED = "Error: Activity";

  /** Output of a failed service application. */
  @VisibleForTesting static final String OUTPUT_START_SERVICE_FAILED = "no service started";

  /** Timeout for stopping application on a device. */
  private static final Duration DEFAULT_STOP_APPLICATION_TIMEOUT = Duration.ofMinutes(1);

  private final Adb adb;

  private final AndroidSystemSpecUtil systemSpecUtil;

  private final Clock clock;

  /** Creates a util for Android device operations. */
  public AndroidProcessUtil() {
    this(new Adb(), new AndroidSystemSpecUtil(), Clock.systemUTC());
  }

  @VisibleForTesting
  AndroidProcessUtil(Adb adb, AndroidSystemSpecUtil systemSpecUtil, Clock clock) {
    this.adb = adb;
    this.systemSpecUtil = systemSpecUtil;
    this.clock = clock;
  }

  /**
   * Check the status of a service on device. Command "adb shell service check" is available from
   * API 10 to 29 which is also workable for both ged/non-ged, root/non-root devices.
   *
   * @param serial serial number of the device
   * @param serviceName service name to query
   * @return whether the service is found
   * @throws InterruptedException if command execution is interrupted
   */
  public boolean checkServiceAvailable(String serial, String serviceName)
      throws InterruptedException {
    try {
      String output =
          adb.runShellWithRetry(
              serial, String.format(ADB_SHELL_TEMPLATE_SERVICE_CHECK, serviceName));
      logger.atInfo().log(
          "checkService on device: %s for %s return %s", serial, serviceName, output);
      return output.trim().equals(String.format(OUTPUT_SERVICE_FOUND, serviceName));
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to check service availability on device: %s", serial);
      return false;
    }
  }

  /**
   * Dumps heap information to a file.
   *
   * <p>Ensure parent dir of {@code outputFile} exists before calling this method.
   *
   * @param args specifying the serial number of the device and sdk version
   * @param packageName the package name of the application.
   * @param outputFile the output file name.
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void dumpHeap(UtilArgs args, String packageName, String outputFile)
      throws MobileHarnessException, InterruptedException {
    String processId = getProcessId(args, packageName);
    if (processId == null) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PROCESS_GET_PROCESS_ID_ERROR,
          "Failed to get process id of package: " + packageName);
    }
    String output = "";
    Exception exception = null;
    try {
      output =
          adb.runShellWithRetry(
              args.serial(), String.format(ADB_SHELL_TEMPLATE_DUMP_HEAP, processId, outputFile));
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PROCESS_DUMP_HEAP_ERROR,
          String.format(
              "Failed to dumpheap for package %s, output: %s",
              packageName, (exception == null ? output : exception.getMessage())),
          exception);
    }
  }

  /**
   * Enables the underlying Adb object to have its command output logged to the class logger.
   *
   * <p>WARNING: This will log ALL command output for Adb commands from this instance of
   * AndroidProcessUtil. Take caution to make sure this won't unintentionally spam your log.
   */
  public void enableCommandOutputLogging() {
    adb.enableCommandOutputLogging();
  }

  /**
   * Gets all process ids associated with the process name on an android device.
   *
   * @param args specifying the serial number of the device and sdk version
   * @param processName the name of the process to match
   * @return a list of the process ids; returns empty list if no associated process exist
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public List<String> getAllProcessId(UtilArgs args, String processName)
      throws MobileHarnessException, InterruptedException {
    String psOutput = "";
    String psCommand = ADB_SHELL_GET_PROCESS_STATUS;

    // Android gets a toybox since Android 6.0 "Marshmallow" and all later Android versions.
    // However, "ps" command switch from "toolbox" to "toybox" in Android 8.0 "Oreo".
    // https://chromium.googlesource.com/aosp/platform/system/core/+/refs/heads/main/shell_and_utilities/README.md#android-8_0-oreo
    if (args.sdkVersion().orElse(0) >= AndroidVersion.OREO.getStartSdkVersion()) {
      psCommand += " -A";
    }

    try {
      psOutput = adb.runShellWithRetry(args.serial(), psCommand);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PROCESS_GET_PROCESS_STATUS_ERROR, e.getMessage(), e);
    }

    // Sample output:
    // USER     PID   PPID  VSIZE  RSS     WCHAN    PC         NAME
    // root      1     0     280    188   c009a694 0000c93c S /init
    // app_23    181   29    131828 20608 ffffffff afe0da04 S com.android.email
    return Splitters.LINE_SPLITTER
        .splitToStream(psOutput)
        .filter(processInfoLine -> matchProcessName(args.serial(), processName, processInfoLine))
        .map(processInfoLine -> Splitter.onPattern("[ \t]+").splitToList(processInfoLine))
        // The second column in this line is the process id.
        .filter(processInfos -> processInfos.size() >= 2)
        .map(processInfos -> processInfos.get(1))
        .collect(toImmutableList());
  }

  /**
   * Gets the process id of a process on an android device.
   *
   * @param args specifying the serial number of the device and sdk version
   * @param processName the name of the process to match
   * @return the process id of the process if it exists; returns null if the process does not exist
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  @Nullable
  public String getProcessId(UtilArgs args, String processName)
      throws MobileHarnessException, InterruptedException {
    return Iterables.getFirst(getAllProcessId(args, processName), null);
  }

  /** Uses dumpsys to check if service package is running. */
  public boolean isServiceRunning(String serial, String packageName)
      throws MobileHarnessException, InterruptedException {
    String shell = String.format(ADB_SHELL_TEMPLATE_DUMP_SYS_SERVICE, packageName);
    String output;
    try {
      output = adb.runShellWithRetry(serial, shell);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PROCESS_DUMPSYS_SERVICE_ERROR, e.getMessage(), e);
    }
    return output.contains("packageName=" + packageName);
  }

  /**
   * Starts an application on an android device.
   *
   * @param serial the serial number of the device
   * @param packageName the package name of the application
   * @param activityName the main activity name of the application
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void startApplication(String serial, String packageName, String activityName)
      throws MobileHarnessException, InterruptedException {
    startApplication(serial, packageName, activityName, /* extras= */ null);
  }

  /**
   * Starts an application on an android device.
   *
   * @param serial the serial number of the device
   * @param packageName the package name of the application
   * @param activityName the main activity name of the application
   * @param extras the extras of the intent passed to the application
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void startApplication(
      String serial, String packageName, String activityName, @Nullable Map<String, String> extras)
      throws MobileHarnessException, InterruptedException {
    startApplication(serial, packageName, activityName, extras, /* clearTop= */ false);
  }

  /**
   * Starts an application on an android device.
   *
   * @param serial the serial number of the device
   * @param packageName the package name of the application
   * @param activityName the main activity name of the application
   * @param extras the extras of the intent passed to the application
   * @param clearTop if set, and the activity being launched is already running in the current task,
   *     then instead of launching a new instance of that activity, all of the other activities on
   *     top of it will be closed and this Intent will be delivered to the (now on top) old activity
   *     as a new Intent
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void startApplication(
      String serial,
      String packageName,
      String activityName,
      @Nullable Map<String, String> extras,
      boolean clearTop)
      throws MobileHarnessException, InterruptedException {
    StringBuilder extraStringBuilder = new StringBuilder();
    if (extras != null) {
      for (Map.Entry<String, String> entry : extras.entrySet()) {
        extraStringBuilder
            .append(" -e '")
            .append(entry.getKey())
            .append("' '")
            .append(entry.getValue())
            .append("'");
      }
    }
    String shell =
        ADB_SHELL_START_APPLICATION + " " + packageName + '/' + activityName + extraStringBuilder;
    if (clearTop) {
      // --activity-clear-task doesn't work with 2.2.1(API level: 8). Use the "clear-top" instead.
      shell += " --activity-clear-top";
    }

    String output;
    try {
      logger.atInfo().log("Start Application on device %s as: %s", shell, serial);
      output = adb.runShellWithRetry(serial, shell);
    } catch (MobileHarnessException e) {
      logger.atInfo().log("Start Application exception: %s", e.getMessage());
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PROCESS_START_APP_ERROR, e.getMessage(), e);
    }

    logger.atInfo().log("Start Application output: %s", output);
    // ActivityManager always return 0 even start activity failed.
    // According to code:
    // //frameworks/base/services/core/java/com/android/server/am/ActivityManagerShellCommand.java
    // Check the output to tell if activity has been started successfully.
    if (output.contains(OUTPUT_START_APP_FAILED)) {
      throw new MobileHarnessException(AndroidErrorId.ANDROID_PROCESS_START_APP_ERROR, output);
    }
  }

  /**
   * Starts an application on an android device.
   *
   * @param serial the serial number of the device
   * @param intent the intent which specifies the application
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   * @see <a href="https://developer.android.com/studio/command-line/shell.html#IntentSpec">ADB
   *     Shell Activity Manager: Specification for INTENT Arguments</a>
   */
  public void startApplication(String serial, String intent)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    Exception exception = null;
    try {
      output = adb.runShellWithRetry(serial, ADB_SHELL_START_APPLICATION_BY_INTENT + intent);
    } catch (MobileHarnessException e) {
      exception = e;
    }

    // ActivityManager always return 0 even start activity failed.
    // According to code:
    // //frameworks/base/services/core/java/com/android/server/am/ActivityManagerShellCommand.java
    // Check the output to tell if activity has been started successfully.
    if (exception != null || output.contains(OUTPUT_START_APP_FAILED)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PROCESS_START_APP_BY_INTENT_ERROR,
          (exception == null ? output : exception.getMessage()),
          exception);
    }
  }

  /**
   * Starts an android service via adb commands.
   *
   * <p>Basic usage: start a service with package name {@code packageName}, serviceName {@code
   * serviceName} on a device of serial {@code serial}.
   *
   * <pre>{@code
   * AndroidProcessUtil androidProcessUtil = new AndroidProcessUtil();
   * androidProcessUtil.startService(
   *     UtilArgs.builder().setSerial(serial).build(),
   *     IntentArgs.builder().setComponent(packageName + '/' + serviceName).build());
   * }</pre>
   *
   * <p>All flags and arguments of the adb commands are set via {@link UtilArgs} and {@link
   * IntentArgs}.
   *
   * @param args args specifying serial, sdk version and user id.
   * @param intent intent specific
   */
  public void startService(UtilArgs args, IntentArgs intent)
      throws InterruptedException, MobileHarnessException {
    StringJoiner command = new StringJoiner(" ");
    command.add(ADB_SHELL_START_SERVICE);
    args.userId().ifPresent(s -> command.add("--user " + s));
    updateService(args.serial(), command.toString(), intent);
  }

  /**
   * Stops an application on an android device given its package name. If more than one process is
   * found, will kill them all. Returns the killed process ids, or an empty list if the application
   * has not started.
   *
   * @param args specifying the serial number of the device and sdk version
   * @param packageName the package name of the application
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public List<String> stopApplication(UtilArgs args, String packageName)
      throws MobileHarnessException, InterruptedException {
    return stopApplication(args, packageName, /* kill= */ false);
  }

  /**
   * Stops an application on an android device given its package name. Try to use am force-stop
   * first and then run kill process_id. If more than one process is found, will kill them all.
   * Returns the killed process ids, or an empty list if the application has not started.
   *
   * @param args specifying the serial number of the device and sdk version
   * @param packageName the package name of the application
   * @param kill whether to kill the application (send SIGKILL to it)
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public List<String> stopApplication(UtilArgs args, String packageName, boolean kill)
      throws MobileHarnessException, InterruptedException {
    return stopApplication(args, packageName, kill, DEFAULT_STOP_APPLICATION_TIMEOUT);
  }

  /**
   * Stops an application on an android device given its package name within given timeout. Try to
   * use am force-stop first and then run kill process_id. If more than one process is found, will
   * kill them all. Returns the killed process ids, or an empty list if the application has not
   * started.
   *
   * @param args specifying the serial number of the device and sdk version
   * @param packageName the package name of the application
   * @param kill whether to kill the application (send SIGKILL to it)
   * @param timeOutDuration the timeout to wait application stopped
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public List<String> stopApplication(
      UtilArgs args, String packageName, boolean kill, Duration timeOutDuration)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet.Builder<String> processIdsBeenKilled = new ImmutableSet.Builder<>();
    List<String> processIdsRemaining = getAllProcessId(args, packageName);
    Instant timeOut = clock.instant().plus(timeOutDuration);

    while (!processIdsRemaining.isEmpty()) {
      /*
       For non-rooted device, adb shell kill process_id may got "not permitted" error. Do "am
       force-stop" to kill the process before hand
      */
      try {
        String unused = adb.runShellWithRetry(args.serial(), ADB_SHELL_AM_FORCE_STOP + packageName);
      } catch (MobileHarnessException e) {
        /*
         For API < 15, am force-stop has not been introduced. Ignore the exception and fall back to
         use kill process_id
        */
        logger.atInfo().withCause(e).log("Failed to run am force-stop, try kill process");

        for (String processId : processIdsRemaining) {
          if (kill) {
            stopProcess(args.serial(), processId, KillSignal.SIGKILL);
          } else {
            // The  default  signal  for  kill is TERM.
            stopProcess(args.serial(), processId, KillSignal.SIGTERM);
          }
        }
      }

      processIdsBeenKilled.addAll(processIdsRemaining);
      processIdsRemaining = getAllProcessId(args, packageName);
      if (clock.instant().isAfter(timeOut)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_PROCESS_STOP_PROCESS_TIMEOUT,
            String.format(
                "Timeout to stop process id of package: %s in %s, remaining PID: %s",
                packageName, timeOutDuration, processIdsRemaining));
      }
    }

    return processIdsBeenKilled.build().asList();
  }

  /**
   * Stops a process on an android device given its process id.
   *
   * @param serial the serial number of the device
   * @param processId the process id of the application to be killed
   * @param signal kill signals to kill application
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void stopProcess(String serial, String processId, KillSignal signal)
      throws MobileHarnessException, InterruptedException {
    try {
      String unused =
          adb.runShellWithRetry(
              serial, ADB_SHELL_KILL_PROCESS + " -" + signal.value() + " " + processId);
    } catch (MobileHarnessException e) {
      if (e.getMessage().contains(OUTPUT_NO_PROCESS)) {
        return;
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PROCESS_STOP_PROCESS_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Stops an android service via adb commands.
   *
   * <p>Basic usage: stop a service with package name {@code packageName}, serviceName {@code
   * serviceName} on a device of serial {@code serial}.
   *
   * <pre>{@code
   * AndroidProcessUtil androidProcessUtil = new AndroidProcessUtil();
   * androidProcessUtil.stopService(
   *     UtilArgs.builder().setSerial(serial).build(),
   *     IntentArgs.builder().setComponent(packageName + '/' + serviceName).build());
   * }</pre>
   *
   * <p>All flags and arguments of the adb commands are set via {@link UtilArgs} and {@link
   * IntentArgs}.
   *
   * @param args args specifying serial, sdk version and user id.
   * @param intent intent specific
   */
  public void stopService(UtilArgs args, IntentArgs intent)
      throws InterruptedException, MobileHarnessException {
    StringJoiner command = new StringJoiner(" ");
    command.add(ADB_SHELL_STOP_SERVICE);
    args.userId().ifPresent(s -> command.add("--user " + s));
    updateService(args.serial(), command.toString(), intent);
  }

  private void updateService(String serial, String adbPrefix, IntentArgs intent)
      throws InterruptedException, MobileHarnessException {
    String shell =
        Joiner.on(' ').join(new String[] {adbPrefix.trim(), intent.getIntentArgsString()});
    logger.atInfo().log("Update service on device %s as: %s", serial, shell);

    String output = null;
    Exception exception = null;
    try {
      output = adb.runShellWithRetry(serial, shell);
    } catch (MobileHarnessException e) {
      exception = e;
    }
    logger.atInfo().log("Update service output: %s", output);

    // NOTE: An {@link MobileHarnessException} is thrown if service failed to start. It is also
    // possible to fail to stop a service, i.e. if the service was killed by the OS to shed load.
    // Currently, we don't throw an exception when failed to stop a service, because there is no
    // deterministic way to ensure that service was running prior stopping it.
    if (exception != null || output.contains(OUTPUT_START_SERVICE_FAILED)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_PROCESS_UPDATE_SERVICE_ERROR,
          (exception == null ? output : exception.getMessage()),
          exception);
    }
  }

  private boolean matchProcessName(String serial, String processName, String processInfoLine) {
    String matchProcessName;

    // Usually process name is same as package name, but emulators shorten package name as
    // its process name if package name length is larger than 75.
    if (systemSpecUtil.isEmulator(serial) && processName.length() > 75) {
      matchProcessName = processName.substring(0, 75);
    } else {
      matchProcessName = processName;
    }

    // Note that some existing tests rely on this being a suffix match (e.g.,
    // "quicksearchbox:search" matching "com.google.android.googlequicksearchbox:search")
    return processInfoLine.endsWith(matchProcessName);
  }
}
