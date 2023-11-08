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

package com.google.devtools.mobileharness.platform.android.sdktool.adb;

import static com.google.common.base.Predicates.not;
import static com.google.devtools.mobileharness.shared.util.shell.ShellUtils.tokenize;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.shared.util.base.StrUtil;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.command.linecallback.ScanSignalOutputCallback;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.util.ArrayUtil;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Util class for Android commonly used adb commands.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 *
 * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
 * separator on SDK>23. It's callers' responsibility to parse it correctly.
 */
public class AndroidAdbUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** ADB arg to generate bugreport. */
  @VisibleForTesting static final String ADB_ARG_BUGREPORT = "bugreport";

  /** ADB shell template for sending broadcast. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_AM_BROADCAST = "am broadcast %s %s";

  @VisibleForTesting static final Duration BROADCAST_DEFAULT_TIMEOUT = Duration.ofMinutes(5);

  /** ADB args for port forward. Should be followed by the "tcp:host_port" and "tcp:device_port". */
  @VisibleForTesting static final String ADB_ARG_FORWARD_TCP = "forward";

  /** ADB arg to view device log. */
  @VisibleForTesting static final String ADB_ARG_LOGCAT = "logcat";

  /** ADB args for port reverse. Should be followed by the "tcp:device_port" and "tcp:host_port". */
  @VisibleForTesting static final String ADB_ARG_REVERSE_TCP = "reverse";

  /** ADB args for clearing the entire log of a device. */
  @VisibleForTesting static final String[] ADB_ARGS_CLEAR_LOG = new String[] {"logcat", "-c"};

  /** ADB Shell command for "cmd". */
  @VisibleForTesting static final String ADB_SHELL_CMD = "cmd";

  /** ADB shell command to get bugreport generated directory. */
  @VisibleForTesting
  static final String ADB_SHELL_FIND_BUGREPORT_DIRECTORY = "find /data -name \"*bugreports\"";

  /** ADB shell command for getting device property. Could be followed by the property name. */
  @VisibleForTesting static final String ADB_SHELL_GET_PROPERTY = "getprop";

  /** ADB shell command for sending key event. Should be followed by the event number. */
  @VisibleForTesting static final String ADB_SHELL_SEND_KEY = "input keyevent";

  /** ADB shell command for settings. */
  @VisibleForTesting static final String ADB_SHELL_SETTINGS = "settings";

  /** ADB shell command for setting device property. Should be followed by the property name. */
  @VisibleForTesting static final String ADB_SHELL_SET_PROPERTY = "setprop";

  /** ADB shell command for becoming root. */
  @VisibleForTesting static final String ADB_SHELL_SU = "su";

  /** Adb shell template of sqlite sql command. Should be filled with the db name and sql. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_SQLITE_SQL = "sqlite3 %s '%s'";

  /** Default user id for adb shell commands. */
  @VisibleForTesting static final String ADB_SHELL_USER_ID_ALL = "all";

  /** ADB shell command for finding command in $PATH */
  @VisibleForTesting static final String ADB_SHELL_WHICH = "which";

  /** Default timeout for regular command. */
  @VisibleForTesting static final Duration BUGREPORT_TIMEOUT = Duration.ofMinutes(10);

  @VisibleForTesting static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofMinutes(5);

  @VisibleForTesting static final Duration DUMPSYS_ALL_TIMEOUT = BUGREPORT_TIMEOUT;

  private static final Splitter LINE_SPLITTER = Splitters.LINE_SPLITTER.omitEmptyStrings();

  /** Output of a successful broadcasting. */
  public static final String OUTPUT_BROADCAST_SUCCESS = "Broadcast completed: result=0";

  /** Output of the "{@code adb shell getprop <KEY>}" command if {@code <KEY>} not found. */
  @VisibleForTesting static final String OUTPUT_KEY_NOT_FOUND = "not found";

  /** Short timeout for quick operations. */
  @VisibleForTesting static final Duration SHORT_COMMAND_TIMEOUT = Duration.ofSeconds(5);

  private static final Splitter TOKEN_SPLITTER =
      Splitter.on(CharMatcher.whitespace()).omitEmptyStrings();

  private final Adb adb;

  private final AndroidAdbInternalUtil androidAdbInternalUtil;

  public AndroidAdbUtil() {
    this(new Adb());
  }

  @VisibleForTesting
  AndroidAdbUtil(Adb adb) {
    this.adb = adb;
    this.androidAdbInternalUtil = new AndroidAdbInternalUtil(adb);
  }

  /**
   * Sends broadcast via am on device. Multi user support requires min api 17. Specify a user ID
   * also requires min api 17.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param utilArgs arguments wrapper for device serial, sdk version, userId, etc
   * @param intent intent arguments for the broadcast command
   * @return broadcast command output
   * @throws MobileHarnessException if fails to execute the commands or timeout, or command output
   *     doesn't show successful info
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String broadcast(UtilArgs utilArgs, IntentArgs intent)
      throws MobileHarnessException, InterruptedException {
    return broadcast(utilArgs, intent, /* checkCmdOutput= */ true, BROADCAST_DEFAULT_TIMEOUT);
  }

  /**
   * Sends broadcast via am on device. Multi user support requires min api 17. Specify a user ID
   * also requires min api 17.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param utilArgs arguments wrapper for device serial, sdk version, userId, etc
   * @param intent intent arguments for the broadcast command
   * @param timeout command timeout for broadcast command
   * @return broadcast command output
   * @throws MobileHarnessException if fails to execute the commands or timeout, or command output
   *     doesn't show successful info
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String broadcast(UtilArgs utilArgs, IntentArgs intent, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    return broadcast(utilArgs, intent, /* checkCmdOutput= */ true, timeout);
  }

  /**
   * Sends broadcast via am on device. Multi user support requires min api 17. Specify a user ID
   * also requires min api 17.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param utilArgs arguments wrapper for device serial, sdk version, userId, etc
   * @param intent intent arguments for the broadcast command
   * @param checkCmdOutput set to {@code true} if want to throw a {@link MobileHarnessException}
   *     when command output doesn't show successful info
   * @param timeout command timeout for broadcast command
   * @return broadcast command output
   * @throws MobileHarnessException if fails to execute the commands or timeout, or command output
   *     doesn't show successful info
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String broadcast(
      UtilArgs utilArgs, IntentArgs intent, boolean checkCmdOutput, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    String serial = utilArgs.serial();
    String intentStr = intent.getIntentArgsString();
    if (intentStr.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_BROADCAST_EMPTY_INTENT_ARG,
          String.format("Missing intent arguments for broadcast command on device %s.", serial));
    }
    String output = null;
    String user = "";
    if (utilArgs.userId().isPresent()) {
      user = String.format("--user %s", utilArgs.userId().get());
    }
    String cmd = String.format(ADB_SHELL_TEMPLATE_AM_BROADCAST, user, intentStr);
    try {
      output = adb.runShell(serial, cmd, timeout);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_BROADCAST_ERROR, e.getMessage(), e);
    }
    if (checkCmdOutput && !output.contains(OUTPUT_BROADCAST_SUCCESS)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_BROADCAST_ERROR,
          "Broadcast command output doesn't show successful info: " + output);
    }
    return output;
  }

  /**
   * Dumps the bugreport from the device using adb. Will block the current thread when dumping.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param bugreportFilePath path of the zip file to generate the bugreport (only SDK >= 24)
   * @return the bugreport command output
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public String bugreport(String serial, @Nullable String bugreportFilePath)
      throws MobileHarnessException, InterruptedException {
    try {
      if (bugreportFilePath != null) {
        return adb.runWithRetry(
            serial, new String[] {ADB_ARG_BUGREPORT, bugreportFilePath}, BUGREPORT_TIMEOUT);
      } else {
        return adb.runWithRetry(serial, new String[] {ADB_ARG_BUGREPORT}, BUGREPORT_TIMEOUT);
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_BUGREPORT_ERROR,
          String.format("Failed to generate bugreport from device %s.", serial),
          e);
    }
  }

  /**
   * Gets the directory of bugreport zip/txt/tmp saved on the device under /data. Support rooted
   * devices with SDK>=23.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @return the bugreport files directory on the device.
   */
  public String bugreportDirectory(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      return adb.runShellWithRetry(serial, ADB_SHELL_FIND_BUGREPORT_DIRECTORY);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_FIND_BUGREPORT_DIR_ERROR,
          String.format("Failed to find bugreport directory from device %s.", serial),
          e);
    }
  }

  /**
   * Clears the entire log of the device.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void clearLog(String serial) throws MobileHarnessException, InterruptedException {
    try {
      String unused = adb.runWithRetry(serial, ADB_ARGS_CLEAR_LOG);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_CLEAR_LOG_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Call system service thru adb shell cmd.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param service service name to run
   * @param args arguments differ from each service
   * @return the cmd output
   * @throws MobileHarnessException if error occurs when executing commands
   * @throws InterruptedException if current thread is interrupted during execution
   */
  public String cmd(String serial, AndroidService service, String[] args)
      throws MobileHarnessException, InterruptedException {
    try {
      String[] command = ArrayUtil.join(ADB_SHELL_CMD, service.getServiceName(), args);
      return adb.runShellWithRetry(serial, Joiner.on(' ').skipNulls().join(command));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_CMD_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Connects to {@code deviceIp} with the default timeout.
   *
   * <p>Should only be called when device is managed by Mobile Harness.
   *
   * @param deviceIp ip address of the device to connect to
   * @throws MobileHarnessException if failed to connect to the IP or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void connect(String deviceIp) throws MobileHarnessException, InterruptedException {
    connect(deviceIp, /* timeout= */ null);
  }

  /**
   * Connects to {@code deviceIp} with a given timeout.
   *
   * <p>Should only be called when device is managed by Mobile Harness.
   *
   * @param deviceIp ip address of the device to connect to
   * @param timeout time to wait for connection attempt to complete, or null to use default timeout
   * @throws MobileHarnessException if failed to connect to the IP or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void connect(String deviceIp, @Nullable Duration timeout)
      throws MobileHarnessException, InterruptedException {
    androidAdbInternalUtil.connect(deviceIp, timeout);
  }

  /**
   * Function for "adb shell content".
   *
   * <pre>
   * Note the subcommand support in different SDK version:
   * 16: insert/update/delete/query - no multi-user support
   * 17: insert/update/delete/query - start to have multi-user support
   * 18: insert/update/delete/query/call
   * 21: insert/update/delete/query/call/read
   * 26: insert/update/delete/query/call/read/gettype
   * 28: insert/update/delete/query/call/read/write/gettype
   * </pre>
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param utilArgs arguments wrapper for device serial, sdk version, userId, etc
   * @param contentArgs arguments for content command
   * @throws MobileHarnessException if fails to execute the commands or timeout, or command output
   *     doesn't show successful info
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String content(UtilArgs utilArgs, AndroidContent contentArgs)
      throws MobileHarnessException, InterruptedException {
    return content(utilArgs, contentArgs, DEFAULT_COMMAND_TIMEOUT);
  }

  /**
   * Function for "adb shell content". By default without --user specified, command run as {@code
   * USER_SYSTEM}. See Android source code for content command:
   * android/frameworks/base/cmds/content/src/com/android/commands/content/Content.java
   *
   * <pre>
   * Note the subcommand support in different SDK version:
   * 16: insert/update/delete/query
   * 17: insert/update/delete/query - start to have multi-user support
   * 18: insert/update/delete/query/call
   * 21: insert/update/delete/query/call/read
   * 26: insert/update/delete/query/call/read/gettype
   * 28: insert/update/delete/query/call/read/write/gettype
   * </pre>
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param utilArgs arguments wrapper for device serial, sdk version, userId, etc
   * @param contentArgs arguments for content command
   * @param timeout timeout for command execution
   * @throws MobileHarnessException if fails to execute the commands or timeout, or command output
   *     doesn't show successful info
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String content(UtilArgs utilArgs, AndroidContent contentArgs, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    if (utilArgs.sdkVersion().isPresent()
        && (utilArgs.sdkVersion().getAsInt() < AndroidVersion.JELLY_BEAN.getStartSdkVersion())) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_SDK_NOT_SUPPORT,
          String.format("Command \"adb shell content\" request minimal API 16"));
    }

    String serial = utilArgs.serial();
    String user =
        utilArgs.userId().isPresent() ? String.format("--user %s", utilArgs.userId().get()) : null;
    String[] baseCommand =
        new String[] {
          "content",
          Ascii.toLowerCase(contentArgs.command().name()),
          "--uri",
          contentArgs.uri(),
          user,
          contentArgs.otherArgument().orElse(null)
        };

    try {
      return adb.runShellWithRetry(serial, Joiner.on(' ').skipNulls().join(baseCommand), timeout);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_CONTENT_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Disconnects from {@code deviceIp}.
   *
   * <p>Should only be called when device is managed by Mobile Harness.
   *
   * @throws MobileHarnessException if failed to disconnect from the IP or timeout
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void disconnect(String deviceIp) throws MobileHarnessException, InterruptedException {
    androidAdbInternalUtil.disconnect(deviceIp);
  }

  /**
   * Dumps device all system information.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @return the dumpsys log
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public String dumpSys(String serial) throws MobileHarnessException, InterruptedException {
    return dumpSys(serial, DumpSysType.NONE, DUMPSYS_ALL_TIMEOUT);
  }

  /**
   * Dumps device system information.
   *
   * <pre>
   * DumpSysType supported in different API levels:
   *   SDK 21: ACCOUNT/ACTIVITY/BATTERY/BATTERYSTATS/CONNECTIVITY/CPUINFO/DISPLAY/GFXINFO/MEMINFO
   *           PACKAGE/PROCSTATS/POWER/WIFI/WIFISCANNER/WINDOW/INPUT
   *   SDK 19: ACCOUNT/ACTIVITY/BATTERY/BATTERYSTATS/CONNECTIVITY/CPUINFO/DISPLAY/GFXINFO/MEMINFO
   *           PACKAGE/PROCSTATS/POWER/WIFI/WINDOW/INPUT
   *   SDK 17: ACCOUNT/ACTIVITY/BATTERY/CONNECTIVITY/CPUINFO/DISPLAY/GFXINFO/MEMINFO
   *           PACKAGE/POWER/WIFI/WINDOW/INPUT
   *   SDK 16: ACCOUNT/ACTIVITY/BATTERY/CONNECTIVITY/CPUINFO/GFXINFO/MEMINFO
   *           PACKAGE/POWER/WIFI/WINDOW/INPUT
   *   SDK 15: ACCOUNT/ACTIVITY/BATTERY/CONNECTIVITY/CPUINFO/GFXINFO/MEMINFO
   *           PACKAGE/POWER/WIFI/WINDOW
   * </pre>
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param dumpSysType what info to dumpsys, DumpSysType variable.
   * @param extraArgs extra arguments to dumpsys, could be package name or "all" or other commands
   * @return the dumpsys log
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public String dumpSys(String serial, DumpSysType dumpSysType, String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    return dumpSys(serial, dumpSysType, DEFAULT_COMMAND_TIMEOUT, extraArgs);
  }

  /**
   * Dumps device system information.
   *
   * <pre>
   * DumpSysType supported in different API levels:
   *   SDK 21: ACCOUNT/ACTIVITY/BATTERY/BATTERYSTATS/CONNECTIVITY/CPUINFO/DISPLAY/GFXINFO/MEMINFO
   *           PACKAGE/PROCSTATS/POWER/WIFI/WIFISCANNER/WINDOW/INPUT
   *   SDK 19: ACCOUNT/ACTIVITY/BATTERY/BATTERYSTATS/CONNECTIVITY/CPUINFO/DISPLAY/GFXINFO/MEMINFO
   *           PACKAGE/PROCSTATS/POWER/WIFI/WINDOW/INPUT
   *   SDK 17: ACCOUNT/ACTIVITY/BATTERY/CONNECTIVITY/CPUINFO/DISPLAY/GFXINFO/MEMINFO
   *           PACKAGE/POWER/WIFI/WINDOW/INPUT
   *   SDK 16: ACCOUNT/ACTIVITY/BATTERY/CONNECTIVITY/CPUINFO/GFXINFO/MEMINFO
   *           PACKAGE/POWER/WIFI/WINDOW/INPUT
   *   SDK 15: ACCOUNT/ACTIVITY/BATTERY/CONNECTIVITY/CPUINFO/GFXINFO/MEMINFO
   *           PACKAGE/POWER/WIFI/WINDOW
   * </pre>
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param dumpSysType what info to dumpsys, DumpSysType variable.
   * @param timeout adb command timeout and dumpsys service timeout.
   * @param extraArgs extra arguments to dumpsys, could be package name or "all" or other commands
   * @return the dumpsys log
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public String dumpSys(
      String serial, DumpSysType dumpSysType, @Nullable Duration timeout, String... extraArgs)
      throws MobileHarnessException, InterruptedException {
    String[] baseCommand;
    int sdkVersion = getIntProperty(serial, AndroidProperty.SDK_VERSION);
    timeout = timeout == null ? DEFAULT_COMMAND_TIMEOUT : timeout;
    if (sdkVersion >= AndroidVersion.NOUGAT.getStartSdkVersion()) {
      baseCommand =
          new String[] {
            "dumpsys", "-t", String.valueOf(timeout.getSeconds()), dumpSysType.getTypeValue()
          };
    } else {
      baseCommand = new String[] {"dumpsys", dumpSysType.getTypeValue()};
    }
    ImmutableList<String> fullCommand =
        ImmutableList.<String>builder()
            .addAll(Arrays.asList(baseCommand))
            .addAll(Arrays.asList(extraArgs))
            .build()
            .stream()
            .filter(not(Strings::isNullOrEmpty))
            .collect(ImmutableList.toImmutableList());
    try {
      return adb.runShellWithRetry(serial, Joiner.on(' ').join(fullCommand), timeout);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_DUMPSYS_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Sets tcp port forwarding on an android device.
   *
   * @param serial serial number of the device
   * @param hostPort the port on local machine
   * @param devicePort the port on the android device
   * @throws MobileHarnessException if fails to set tcp forward
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void forwardTcpPort(String serial, int hostPort, int devicePort)
      throws MobileHarnessException, InterruptedException {
    forwardTcpPort(serial, hostPort, "tcp:" + devicePort);
  }

  /**
   * Sets tcp port forwarding on an android device with specific port description on device.
   *
   * @param serial serial number of the device
   * @param hostPort the port on local machine
   * @param devicePortDescription the description of port on the android device. See <a
   *     href="https://developer.android.com/studio/command-line/adb.html#forwardports" /> for more
   *     details.
   * @throws MobileHarnessException if fails to set tcp forward
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void forwardTcpPort(String serial, int hostPort, String devicePortDescription)
      throws MobileHarnessException, InterruptedException {
    try {
      String unused =
          adb.runWithRetry(
              serial, new String[] {ADB_ARG_FORWARD_TCP, "tcp:" + hostPort, devicePortDescription});
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_FORWARD_TCP_PORT_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets device current time which can be used for logcat -T option. Support rooted/non-rooted
   * devices with SDK>=23.
   *
   * <p>The returned time format is "YYYY-MM-DD hh:mm:ss.mmm", example: "2019-02-26 17:09:14.000".
   * It only gets the time with second granularity, millisecond part set to 0 by default. Note:
   * logcat -T option works on rooted/non-rooted devices with SDK>=23.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   */
  public String getDeviceCurrentTimeForLogcat(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      return String.format(
          "%s.%s", adb.runShellWithRetry(serial, "date +%Y-%m-%d\\ %H:%M:%S").trim(), "000");
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_GET_TIME_FOR_LOGCAT_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets the property value of a device. If the device has no value for any keys of the property,
   * will return empty.
   *
   * @param serial serial number of the device
   * @param property device property
   * @return the value of the property, or empty if the system property is not found; will never
   *     return null
   * @throws MobileHarnessException if error occurs when reading the device property
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String getProperty(String serial, AndroidProperty property)
      throws MobileHarnessException, InterruptedException {
    return getProperty(serial, property.getPropertyKeys());
  }

  /**
   * Gets the property value of a device, given one or more property keys. If the device has no
   * value for any keys of the property, will return the empty string.
   *
   * @param serial serial number of the device
   * @param propertyKeys an array of the device property keys
   * @return the value of the property, or empty if the property is not found; will never return
   *     null
   * @throws MobileHarnessException if error occurs when reading the device property
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String getProperty(String serial, ImmutableList<String> propertyKeys)
      throws MobileHarnessException, InterruptedException {
    try {
      for (String key : propertyKeys) {
        String output =
            adb.runShellWithRetry(serial, ADB_SHELL_GET_PROPERTY + " " + key, SHORT_COMMAND_TIMEOUT)
                .trim();
        if (output.isEmpty() || output.contains(OUTPUT_KEY_NOT_FOUND)) {
          continue;
        } else {
          return output;
        }
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_GET_DEVICE_PROPERTY_ERROR, e.getMessage(), e);
    }
    return "";
  }

  /**
   * Gets and parses the integer property value of a device. If the device has no value for any keys
   * of the property or fail to parse into integer, will throw MobileHarnessException.
   *
   * @param serial serial number of the device
   * @param property device property
   * @return the integer value of the property
   * @throws MobileHarnessException if error occurs when reading/parsing the device property
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public int getIntProperty(String serial, AndroidProperty property)
      throws MobileHarnessException, InterruptedException {
    String intPropertyValue = getProperty(serial, property);
    try {
      return Integer.parseInt(intPropertyValue);
    } catch (NumberFormatException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_INT_PROPERTY_ERROR,
          "Failed to parse property["
              + property
              + "]: '"
              + intPropertyValue
              + "' to integer from device property: "
              + e.getMessage(),
          e);
    }
  }

  /**
   * Whether device is rooted.
   *
   * @param serial serial number of device.
   * @throws InterruptedException
   * @throws MobileHarnessException
   */
  public boolean isRooted(String serial) throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      output = adb.runShell(serial, ADB_SHELL_WHICH + " " + ADB_SHELL_SU).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_CHECK_ROOT_ERROR, e.getMessage(), e);
    }
    // Unrooted device doesn't generate any message.
    return !output.isEmpty();
  }

  /**
   * Dumps the log from the device using logcat. Will block the current thread when dumping.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of the device
   * @param options options of the logcat command, for example "-b main -v time". This string will
   *     also be split by unquoted whitespace to separate arguments. For example, passing "-d -T
   *     '01-15 01:02:03.456'" will safely result in the arguments being split as "-d" "-T" "01-15
   *     01:02:03.456".
   * @param filterSpecs a series of tag[:priority], where tag is a log component tag (or "*" for
   *     all), and priority is:
   *     <ul>
   *       <li>V Verbose
   *       <li>D Debug
   *       <li>I Info
   *       <li>W Warn
   *       <li>E Error
   *       <li>F Fatal
   *       <li>S Silent (suppress all output)
   *     </ul>
   *     null means "*:Warn", "*" means "*:Debug", and tag by itself means "tag:Verbose"
   * @return the device log
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   * @see <a href="http://developer.android.com/tools/help/logcat.html">logcat help</a>
   */
  public String logCat(String serial, @Nullable String options, @Nullable String filterSpecs)
      throws MobileHarnessException, InterruptedException {
    if (filterSpecs == null) {
      filterSpecs = "*:" + LogCatPriority.WARN.name();
    }
    String[] command =
        ArrayUtil.join(
            new String[] {ADB_ARG_LOGCAT, "-d"},
            tokenizeOptions(Optional.ofNullable(options)),
            Splitter.onPattern("\\s+").splitToList(filterSpecs.trim()).toArray(new String[0]));
    try {
      return adb.runWithRetry(serial, command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_DUMP_LOG_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Removes all reverse tcp ports setup between host machine and device. Only works on API level >=
   * 21.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if fails to remove reverse tcp setup
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void removeAllReverseTcpPorts(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      String unused = adb.runWithRetry(serial, new String[] {ADB_ARG_REVERSE_TCP, "--remove-all"});
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_REMOVE_ALL_REVERSE_TCP_PORTS_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Remove ADB forwarding by remote port.
   *
   * <p>For example, use this method with {@code ("abcd", "localabstract:minicap")} to remove all
   * ADB forwardings to the port {@code "localabstract:minicap"} of the device {@code "abcd"} if
   * any.
   *
   * <p>Requires API level: 18
   *
   * @return all local ports of the matched ADB forwardings
   */
  public List<String> removeForwardByRemotePort(String serial, String remotePort)
      throws MobileHarnessException, InterruptedException {
    try {
      String output = adb.runWithRetry(new String[] {ADB_ARG_FORWARD_TCP, "--list"});
      // Example output of "adb forward --list":
      // 00c621732e6115c3 tcp:33771 localabstract:minicap
      // 00c621732e6115c3 tcp:44012 localabstract:minitouch
      ImmutableList<String> localPorts =
          LINE_SPLITTER.splitToList(output).stream()
              .map(TOKEN_SPLITTER::splitToList)
              .filter(tokens -> tokens.size() == 3)
              .filter(tokens -> serial.equals(tokens.get(0)))
              .filter(tokens -> remotePort.equals(tokens.get(2)))
              .map(tokens -> tokens.get(1))
              .collect(ImmutableList.toImmutableList());
      for (String localPort : localPorts) {
        String unused =
            adb.runWithRetry(serial, new String[] {ADB_ARG_FORWARD_TCP, "--remove", localPort});
      }
      return localPorts;
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_REMOVE_FORWARD_PORT_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Removes tcp forwarding on specific port on host. Requires api level >= 18.
   *
   * @param serial serial number of the device
   * @param hostPort the port on local machine
   * @throws MobileHarnessException if fails to remove tcp forwarding
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void removeTcpPortForward(String serial, int hostPort)
      throws MobileHarnessException, InterruptedException {
    try {
      String unused =
          adb.runWithRetry(
              serial, new String[] {ADB_ARG_FORWARD_TCP, "--remove", "tcp:" + hostPort});
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_REMOVE_FORWARD_PORT_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Sets tcp port reversing on an android device. Only works with API level >= 21.
   *
   * @param serial serial number of the device
   * @param devicePort the port on the android device
   * @param hostPort the port on local machine
   * @throws MobileHarnessException if fails to set tcp reverse
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void reverseTcpPort(String serial, int devicePort, int hostPort)
      throws MobileHarnessException, InterruptedException {
    try {
      String unused =
          adb.runWithRetry(
              serial, new String[] {ADB_ARG_REVERSE_TCP, "tcp:" + devicePort, "tcp:" + hostPort});
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_REVERSE_TCP_PORT_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Forks a new thread to dump the log from the device using logcat asynchronously.
   *
   * <p>It explicitly skips saving logs in command result in case logcat is extremely large,
   * especially for longevity tests. You'll need to save logs locally via the line callback if the
   * logs are needed later.
   *
   * @param serial serial number of the device
   * @param options options of the logcat command, for example "-b main -v time". This string will
   *     also be split by unquoted whitespace to separate arguments. For example, passing "-d -T
   *     '01-15 01:02:03.456'" will safely result in the arguments being split as "-d" "-T" "01-15
   *     01:02:03.456".
   * @param filterSpecs a series of tag[:priority], where tag is a log component tag (or "*" for
   *     all), and priority is:
   *     <ul>
   *       <li>V Verbose
   *       <li>D Debug
   *       <li>I Info
   *       <li>W Warn
   *       <li>E Error
   *       <li>F Fatal
   *       <li>S Silent (suppress all output)
   *     </ul>
   *     null means "*:Warn", "*" means "*:Debug", and tag by itself means "tag:Verbose"
   * @param lineCallback callback to handle each log line
   * @return the command process for stopping the async logcat command
   * @throws MobileHarnessException if some errors occur in executing the command
   * @see <a href="http://developer.android.com/tools/help/logcat.html">logcat help</a>
   */
  public CommandProcess runLogCatAsync(
      final String serial,
      @Nullable String options,
      @Nullable String filterSpecs,
      Duration timeout,
      LineCallback lineCallback)
      throws MobileHarnessException {
    if (filterSpecs == null) {
      filterSpecs = "*:" + LogCatPriority.WARN.name();
    }
    String[] logcatCommand =
        ArrayUtil.join(
            new String[] {ADB_ARG_LOGCAT},
            tokenizeOptions(Optional.ofNullable(options)),
            Splitter.onPattern("\\s+").splitToList(filterSpecs.trim()).toArray(new String[0]));
    Command cmd = adb.getAdbCommand();

    ImmutableSet<Integer> successExitCodes = cmd.getSuccessExitCodes();
    Consumer<CommandResult> exitCallback =
        (commandResult) -> {
          int exitCode = commandResult.exitCode();
          if (successExitCodes.contains(exitCode)) {
            logger.atInfo().log("Finished and stopped async logcat with device %s", serial);
          } else if (exitCode == 143) {
            // The logcat command is killed.
            logger.atInfo().log("Stopped async logcat with device %s", serial);
          } else {
            logger.atWarning().log(
                "Error when async logcat with device %s: %s", serial, commandResult);
          }
        };

    Runnable timeoutCallback =
        () -> logger.atWarning().log("Timeout when async logcat with device %s", serial);

    // Explicitly skip saving logs in command result in case logcat is extremely large, especially
    // for longevity tests.
    cmd =
        cmd.args(ArrayUtil.join("-s", serial, logcatCommand))
            .redirectStderr(false)
            .timeout(timeout)
            .onTimeout(timeoutCallback)
            .onStdout(lineCallback)
            .needStdoutInResult(false)
            .needStderrInResult(false)
            .onExit(exitCallback);
    try {
      return adb.runAsync(cmd);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_DUMP_LOG_ASYNC_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Sends key event to the device.
   *
   * @param serial serial number of the device
   * @param key key value
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void sendKey(String serial, int key) throws MobileHarnessException, InterruptedException {
    String output = null;
    Exception exception = null;

    try {
      logger.atInfo().log("Input keyevent %d to device %s", key, serial);
      output = adb.runShellWithRetry(serial, ADB_SHELL_SEND_KEY + " " + key);
    } catch (MobileHarnessException e) {
      output = e.getMessage();
      exception = e;
    }

    if (!StrUtil.isEmptyOrWhitespace(output)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_KEY_EVENT_ERROR,
          "Failed to hit the key: " + output,
          exception);
    }
  }

  /**
   * Sends key event to the device.
   *
   * @param serial serial number of the device
   * @param keyEvent key event to be sent to the device
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void sendKey(String serial, KeyEvent keyEvent)
      throws MobileHarnessException, InterruptedException {
    sendKey(serial, keyEvent.getKey());
  }

  /**
   * Sets the buffer size of logcat. Requires API>=21. Supports non-rooted devices.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial the serial number of the device
   * @param sdkVersion the SDK version of the device
   * @param sizeKb the size of the buffer in KB
   * @return std/err output
   */
  public String setLogCatBufferSize(String serial, int sdkVersion, int sizeKb)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion < AndroidVersion.LOLLIPOP.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_SDK_NOT_SUPPORT,
          "Setting Logcat buffer size requires min api 21.");
    }
    String[] command = new String[] {ADB_ARG_LOGCAT, "-G", String.format("%dK", sizeKb)};
    try {
      return adb.runWithRetry(serial, command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_SET_LOGCAT_BUFFER_SIZE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Sets device property. This implementation handles gracefully empty strings as property value.
   *
   * @param serial serial number of the device
   * @param propertyKey name of the device property
   * @param propertyValue device property value
   * @throws MobileHarnessException if error occurs when setting the device property
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void setProperty(String serial, String propertyKey, String propertyValue)
      throws MobileHarnessException, InterruptedException {
    setProperty(
        serial, propertyKey, propertyValue, /* ignoreError= */ false, SHORT_COMMAND_TIMEOUT);
  }

  /**
   * Sets device property. This implementation handles gracefully empty strings as property value.
   *
   * @param serial serial number of the device
   * @param propertyKey name of the device property
   * @param propertyValue device property value
   * @param ignoreError true if throw error when property failed to be set.
   * @throws MobileHarnessException if error occurs when setting the device property
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void setProperty(
      String serial, String propertyKey, String propertyValue, boolean ignoreError)
      throws InterruptedException, MobileHarnessException {
    setProperty(serial, propertyKey, propertyValue, ignoreError, SHORT_COMMAND_TIMEOUT);
  }

  /**
   * Sets device property. This implementation handles gracefully empty strings as property value.
   *
   * @param serial serial number of the device
   * @param propertyKey name of the device property
   * @param propertyValue device property value
   * @param ignoreError true if throw error when property failed to be set.
   * @param timeout command timeout
   * @throws MobileHarnessException if error occurs when setting the device property
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public void setProperty(
      String serial,
      String propertyKey,
      String propertyValue,
      boolean ignoreError,
      Duration timeout)
      throws InterruptedException, MobileHarnessException {
    // Bug: b/34828942
    // In some cases MH cannot reinitialize device after the test,
    // because values of ro properties are not discarded after reboot.
    // In this case, instead of failing just continue gracefully
    // if the desired value is already set.

    // If the property is read only...
    if (propertyKey.startsWith("ro.")) {
      // ... check its previous value.
      String previousValue = getProperty(serial, ImmutableList.<String>of(propertyKey));
      // Do nothing, if previous value is the desired one.
      if (previousValue.equals(propertyValue)) {
        logger.atWarning().log(
            "Trying to set ro property (%s) and its previous value is the desired one (%s). "
                + "Do nothing.",
            propertyKey, propertyValue);
        return;
      }
    }

    String output = null;
    if (StrUtil.isEmptyOrWhitespace(propertyValue)) {
      propertyValue = "\"\"";
    }
    try {
      output =
          adb.runShellWithRetry(
              serial, ADB_SHELL_SET_PROPERTY + " " + propertyKey + " " + propertyValue, timeout);
    } catch (MobileHarnessException e) {
      output = e.getMessage();
    }

    if (!Strings.isNullOrEmpty(output)) {
      if (ignoreError) {
        logger.atWarning().log("%s", output);
      } else {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ADB_UTIL_SET_DEVICE_PROPERTY_ERROR,
            String.format(
                "Failed to set property. Key=[%s]. Value=[%s]. Error:%s",
                propertyKey, propertyValue, output));
      }
    }
  }

  /**
   * Settings command on Android device. Requires API >= 17. Supports non-rooted device.
   *
   * <pre>
   * Commands for settings in different API level:
   *   SDK 17: get/put
   *   SDK 21: get/put/delete
   *   SDK 23: get/put/delete/list
   *   SDK 26: get/put/delete/reset/list
   * </pre>
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param utilArgs arguments wrapper for device serial, sdk version, etc
   * @param spec settings command spec
   * @return std/err output
   */
  public String settings(UtilArgs utilArgs, AndroidSettings.Spec spec)
      throws MobileHarnessException, InterruptedException {
    return settings(utilArgs, spec, DEFAULT_COMMAND_TIMEOUT);
  }

  /**
   * Settings command on Android device. Requires API >= 17. Supports non-rooted device.
   *
   * <pre>
   * Commands for settings in different API level:
   *   SDK 17: get/put
   *   SDK 21: get/put/delete
   *   SDK 23: get/put/delete/list
   *   SDK 26: get/put/delete/reset/list
   * </pre>
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param utilArgs arguments wrapper for device serial, sdk version, etc
   * @param spec settings command spec
   * @param timeout command timeout
   * @return std/err output
   */
  public String settings(UtilArgs utilArgs, AndroidSettings.Spec spec, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    String serial = utilArgs.serial();
    String userId =
        utilArgs.userId().isPresent() ? String.format("--user %s", utilArgs.userId().get()) : null;
    String[] baseCommand =
        new String[] {
          ADB_SHELL_SETTINGS, spec.commandName(), userId, spec.nameSpace(), spec.extraArgs()
        };
    String commandStr = Joiner.on(' ').skipNulls().join(baseCommand);

    try {
      return adb.runShell(serial, commandStr, timeout);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_SETTINGS_CMD_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Run SQL command thru sqlite3.
   *
   * <p>Only works with rooted devices with API level >= 18.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial the serial number of the device
   * @param dbPath database path on device
   * @param sql SQL string to specified database
   * @return std/err output
   */
  public String sqlite(String serial, String dbPath, String sql)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    Exception exception = null;

    try {
      output = adb.runShell(serial, String.format(ADB_SHELL_TEMPLATE_SQLITE_SQL, dbPath, sql));
    } catch (MobileHarnessException e) {
      exception = e;
    }

    if (exception != null || output.startsWith("Error:")) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_SQLITE_ERROR,
          String.format(
              "Failed to run [%s] with db [%s]:%n%s",
              sql, dbPath, (exception != null ? exception.getMessage() : output)),
          exception);
    }

    return output;
  }

  /**
   * Function for "adb shell svc".
   *
   * <p>It affects all users, no support for particular user only.
   *
   * <pre>
   * Note the subcommand support in different SDK version:
   * 15: power/data/wifi
   * 16 - 23: power/data/wifi/usb
   * 24 - 26: power/data/wifi/usb/nfc
   * 27 - 28: power/data/wifi/usb/nfc/bluetooth
   * API less than 15 hasn't been tested, use at your own risk.
   * </pre>
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of device
   * @param svcArgs arguments for svc command
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String svc(String serial, AndroidSvc svcArgs)
      throws MobileHarnessException, InterruptedException {
    return svc(serial, svcArgs, DEFAULT_COMMAND_TIMEOUT);
  }

  /**
   * Function for "adb shell svc".
   *
   * <p>It affects all users, no support for particular user only.
   *
   * <pre>
   * Note the subcommand support in different SDK version:
   * 15: power/data/wifi
   * 16 - 23: power/data/wifi/usb
   * 24 - 26: power/data/wifi/usb/nfc
   * 27 - 28: power/data/wifi/usb/nfc/bluetooth
   * API less than 15 hasn't been tested, use at your own risk.
   * </pre>
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial serial number of device
   * @param svcArgs arguments for svc command
   * @param timeout timeout for command execution
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public String svc(String serial, AndroidSvc svcArgs, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    String[] baseCommand =
        new String[] {
          "svc", Ascii.toLowerCase(svcArgs.command().name()), svcArgs.otherArgs().orElse(null)
        };

    try {
      return adb.runShellWithRetry(serial, Joiner.on(' ').skipNulls().join(baseCommand), timeout);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_SVC_CMD_ERROR, e.getMessage(), e);
    }
  }

  /**
   * A wait-until-condition-ready function with condition pointer passed in.
   *
   * <p>{@code UtilArgs} will be passed to {@code predicate} as its single argument.
   *
   * <p>Caller must handle exceptions explicitly that occurred within {@code predicate}. If {@code
   * InterruptedException} occurs within {@code predicate}, caller should interrupt thread within
   * {@code predicate} and return {@code false}.
   *
   * @param utilArgs arguments wrapper for device serial, sdk version, etc
   * @param predicate lambda expression for condition function
   * @param waitArgs arguments to managing waiting
   * @return {@code true} if given condition is satisfied within timeout, otherwise {@code false}
   * @throws InterruptedException if the thead executing commands got interrupted
   */
  public static boolean waitForDeviceReady(
      UtilArgs utilArgs, Predicate<UtilArgs> predicate, WaitArgs waitArgs)
      throws InterruptedException {
    Clock clock = waitArgs.clock();
    Sleeper sleeper = waitArgs.sleeper();
    Duration checkReadyInterval = waitArgs.checkReadyInterval();
    Duration checkReadyTimeout = waitArgs.checkReadyTimeout();
    String serial = utilArgs.serial();

    logger.atInfo().log("Waiting for device %s...", serial);
    Instant expireTime = clock.instant().plus(checkReadyTimeout);
    while (clock.instant().isBefore(expireTime)) {
      sleeper.sleep(checkReadyInterval);
      if (predicate.test(utilArgs)) {
        return true;
      }
      // Rethrow the interrupted exception in case it was fired in predicate.
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException(
            "Waiting for setting ready interrupted, check corresponding log for detail");
      }
    }

    logger.atWarning().log(
        "Device/emulator %s doesn't satisfy given condition yet after waiting for %s!",
        serial, checkReadyTimeout);
    return false;
  }

  /**
   * Runs "adb logcat" to scan the device log. Waits until a given signal message appears in the
   * log, or timeout.
   *
   * @param serial the serial number of the device
   * @param signal target signal to search in the device log
   * @param timeout max wait and search time
   * @throws MobileHarnessException if some errors occur in executing system commands or cannot find
   *     signal in device log before timeout.
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void waitForSignalInLog(String serial, String signal, Duration timeout)
      throws MobileHarnessException, InterruptedException {
    ScanSignalOutputCallback outputCallback =
        new ScanSignalOutputCallback(signal, /* stopOnSignal= */ true);
    try {
      String unused = adb.run(serial, new String[] {ADB_ARG_LOGCAT}, timeout, outputCallback);
    } catch (MobileHarnessException e) {
      if (!AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_TIMEOUT.equals(e.getErrorId())) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_ADB_UTIL_DUMP_LOG_ERROR, e.getMessage(), e);
      }
    }
    if (!outputCallback.isSignalCaught()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_ADB_UTIL_WAIT_FOR_SIGNAL_IN_LOG_ERROR,
          String.format(
              "Can not catch signal [%s] in the log of device [%s] within %d milliseconds",
              signal, serial, timeout.toMillis()));
    }
  }

  /**
   * A thin wrapper around {@link
   * com.gogole.devtools.deviceinfra.shared.util.shell.ShellUtils#tokenize} to split options by
   * whitespace to shell arguments.
   *
   * @param options Shell options
   * @throws MobileHarnessException if there were errors tokenizing the options, such as when there
   *     are unterminated quotations.
   */
  private static String[] tokenizeOptions(Optional<String> options) throws MobileHarnessException {
    List<String> tokenizedOptions = new ArrayList<>();
    try {
      tokenize(tokenizedOptions, options.orElse(""));
    } catch (TokenizationException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ADB_SHELL_COMMAND_INVALID_ARGS, e.getMessage(), e);
    }
    return tokenizedOptions.toArray(new String[0]);
  }
}
