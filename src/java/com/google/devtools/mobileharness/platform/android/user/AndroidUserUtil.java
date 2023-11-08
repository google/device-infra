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

package com.google.devtools.mobileharness.platform.android.user;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.WaitArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.shared.constant.Splitters;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to manage user on Android device.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 */
public class AndroidUserUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** ADB shell command to get current foreground user on device. */
  @VisibleForTesting static final String ADB_SHELL_GET_CURRENT_USER = "am get-current-user";

  /** ADB shell command to create a user on device. */
  @VisibleForTesting static final String ADB_SHELL_PM_CREATE_USER = "pm create-user";

  /** ADB shell command for listing users. */
  @VisibleForTesting static final String ADB_SHELL_PM_LIST_USERS = "pm list users";

  /** ADB shell command to get the maximum number of supported users. */
  @VisibleForTesting static final String ADB_SHELL_GET_MAX_USERS = "pm get-max-users";

  /** ADB shell command template for start a user and switch to that user in foreground. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_AM_SWITCH_USER = "am switch-user %s";

  /** ADB shell command template for remove a user. */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_PM_REMOVE_USER = "pm remove-user %d";

  @VisibleForTesting static final Duration CHECK_USER_READY_INTERVAL = Duration.ofSeconds(1);

  /** Default timeout for waiting user to become ready. */
  @VisibleForTesting static final Duration CHECK_USER_READY_TIMEOUT = Duration.ofMinutes(1);

  /** Default timeout for regular command. */
  private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofMinutes(5);

  /**
   * Default user ready state. This is the most safe state that all services on device is ready for
   * newly started user.
   */
  private static final AndroidUserState DEFAULT_USER_READY_STATE =
      AndroidUserState.STATE_RUNNING_UNLOCKED;

  private static final Pattern STARTED_USER_REGEX =
      Pattern.compile("\\s+User\\s#(?<ID>\\d+):\\s+state=(?<STATE>.*)");

  private static final Pattern USER_LRU_REGEX = Pattern.compile("mUserLru:\\s\\[(?<LRU>.*)\\]");

  /** Output of a successful installation/uninstallation. */
  private static final String OUTPUT_SUCCESS = "Success";

  /** Android SDK ADB command line tools executor. */
  private final Adb adb;

  private final AndroidAdbUtil adbUtil;

  private final Sleeper sleeper;

  /** {@code Clock} for getting current system time. */
  private final Clock clock;

  public AndroidUserUtil() {
    this(new Adb(), new AndroidAdbUtil(), Sleeper.defaultSleeper(), Clock.systemUTC());
  }

  @VisibleForTesting
  AndroidUserUtil(Adb adb, AndroidAdbUtil adbUtil, Sleeper sleeper, Clock clock) {
    this.adb = adb;
    this.adbUtil = adbUtil;
    this.sleeper = sleeper;
    this.clock = clock;
  }

  /**
   * Creates a new user with the given USER_NAME, printing the new user identifier of the user.
   * Minimal API requires 17 for both root and non-root device.
   *
   * @param serial serial number of the device
   * @param sdkVersion device sdk version
   * @param userName user name to be created
   */
  public int createUser(String serial, int sdkVersion, String userName)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion <= AndroidVersion.JELLY_BEAN.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Multi user support require min api 17.");
    }

    return createUser(
        serial, sdkVersion, AndroidCreateUserArgs.builder().setUserName(userName).build());
  }

  /**
   * Creates a new user with the given USER_NAME, printing the new user identifier of the user.
   * Minimal API requires 24 for both root and non-root device.
   *
   * <p>Supports both root and non-root device, minimal API 24 for guest option and minimal API 28
   * for ephemeral option.
   *
   * @param serial serial number of the device
   * @param sdkVersion device sdk version
   * @param userName user name to be created
   * @param ephemeral if this user is ephemeral
   * @param guest if this user is guest
   */
  public int createUser(
      String serial, int sdkVersion, String userName, boolean ephemeral, boolean guest)
      throws MobileHarnessException, InterruptedException {
    if (guest && sdkVersion < AndroidVersion.NOUGAT.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Create guest user require min api 24.");
    }
    if (ephemeral && sdkVersion < AndroidVersion.PI.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Create ephemeral user require min api 28.");
    }

    return createUser(
        serial,
        sdkVersion,
        AndroidCreateUserArgs.builder()
            .setEphemeral(ephemeral)
            .setGuest(guest)
            .setUserName(userName)
            .build());
  }

  /**
   * Creates a new user with the given USER_NAME, printing the new user identifier of the user.
   * Minimal API requires 17 for both root and non-root device.
   *
   * @param serial serial number of the device
   * @param sdkVersion device sdk version
   * @param userArgs arguments to command line for "pm create-user"
   */
  public int createUser(String serial, int sdkVersion, AndroidCreateUserArgs userArgs)
      throws MobileHarnessException, InterruptedException {
    // There won't be extra check in this level of function to make sure args are
    // available in different sdk version.
    if (sdkVersion <= AndroidVersion.JELLY_BEAN.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Multi user support require min api 17.");
    }

    final StringBuilder sb = new StringBuilder();
    String[] cmd =
        new String[] {
          ADB_SHELL_PM_CREATE_USER,
          userArgs.profileOf().isPresent()
              ? String.format("--profileOf %s", userArgs.profileOf().getAsInt())
              : null,
          isTrue(userArgs.managed()) ? "--managed" : null,
          isTrue(userArgs.restricted()) ? "--restricted" : null,
          isTrue(userArgs.ephemeral()) ? "--ephemeral" : null,
          isTrue(userArgs.guest()) ? "--guest" : null,
          userArgs.userName(),
        };
    try {
      String unused =
          adb.runShell(
              serial,
              Joiner.on(' ').skipNulls().join(cmd),
              DEFAULT_COMMAND_TIMEOUT,
              LineCallback.does(line -> sb.append(line).append("\n")));
    } catch (MobileHarnessException ex) {
      // To workaround a framework issue, that is the command return exit code 1 even it succeed.
    }

    String output = sb.toString().trim();
    if (!output.startsWith(OUTPUT_SUCCESS)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_CREATE_USER_ERROR,
          String.format("Failed to create new user %s: %s", userArgs.userName(), output));
    }

    try {
      // Expected output:
      // Success: created user id 11
      return Integer.parseInt(
          Iterables.getLast(Splitter.onPattern("\\s+").trimResults().splitToList(output)));
    } catch (NumberFormatException | NoSuchElementException ex) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_CREATE_USER_ERROR,
          String.format("Failed to create new user %s: %s", userArgs.userName(), output),
          ex);
    }
  }

  /**
   * Creates work profile and return user id if success.
   *
   * <p>Minimal API requires 21 for both root and non-root device. Root is not required.
   */
  public int createWorkProfile(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion < AndroidVersion.LOLLIPOP.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "pm create-user --profileOf requires API 21 or above");
    }

    try {
      return createUser(
          serial,
          sdkVersion,
          AndroidCreateUserArgs.builder()
              .setProfileOf(0)
              .setManaged(true)
              .setUserName("TestProfile")
              .build());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_CREATE_WORK_PROFILE_ERROR,
          "Failed to run create work profile: " + e.getMessage(),
          e);
    }
  }

  /**
   * Enables the underlying Adb object to have its command output logged to the class logger.
   *
   * <p>WARNING: This will log ALL command output for Adb commands from this instance of
   * AndroidAccountManagerUtil. Take caution to make sure this won't unintentionally spam your log.
   */
  public void enableCommandOutputLogging() {
    adb.enableCommandOutputLogging();
  }

  /**
   * Returns id of the current foreground user.
   *
   * <p>Minimal API requires 17 for both root and non-root device. For API < 17, always return uid =
   * 0.
   */
  public int getCurrentUser(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion <= AndroidVersion.JELLY_BEAN.getStartSdkVersion()) {
      logger.atInfo().log("Multi user support require min api 17.");
      return 0;
    }

    try {
      if (sdkVersion >= AndroidVersion.NOUGAT.getStartSdkVersion()) {
        // Command "am get-current-user" is only available from API 24.
        String currentUserId = adb.runShell(serial, ADB_SHELL_GET_CURRENT_USER);
        return Integer.parseInt(currentUserId);
      } else {
        return getCurrentUsersHistory(serial, sdkVersion).get(0);
      }
    } catch (MobileHarnessException | NumberFormatException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_GET_FOREGROUND_USER_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets user state from "dumpsys activity" by parsing mStartedUsers.
   *
   * <p>Only works with API level >=24. Supports production build.
   *
   * @param serial serial number of the device
   * @param sdkVersion device sdk version
   * @param userId user id to query
   * @return AndroidUserState. If user not started, STATE_UNKNOWN will be returned
   * @throws MobileHarnessException if failed to run dumpsys activity
   */
  public AndroidUserState getUserState(String serial, int sdkVersion, int userId)
      throws MobileHarnessException, InterruptedException {
    Map<Integer, String> startedUserStates = listStartedUserStates(serial, sdkVersion);
    return AndroidUserState.enumOf(startedUserStates.getOrDefault(userId, "UNKNOWN"));
  }

  /**
   * Gets the maximum number of supported users. Defaults to 0.
   *
   * @return an integer indicating the number of supported users
   */
  public int getMaxNumberOfUsersSupported(String serial)
      throws MobileHarnessException, InterruptedException {
    String cmdOutput;
    try {
      cmdOutput = adb.runShellWithRetry(serial, ADB_SHELL_GET_MAX_USERS);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_GET_MAX_USERS_ERROR,
          "Failed to get supported max users",
          e);
    }

    try {
      return Integer.parseInt(cmdOutput.substring(cmdOutput.lastIndexOf(" ")).trim());
    } catch (NumberFormatException e) {
      logger.atWarning().log(
          "Failed to parse result [%s]: %s", cmdOutput, MoreThrowables.shortDebugString(e, 0));
    }
    return 0;
  }

  /**
   * Checks if user is ready in state {@code AndroidUserState.STATE_RUNNING_UNLOCKED}.
   *
   * <p>Support from API 24 and production build.
   *
   * @param serial device serial id
   * @param sdkVersion device sdk version
   * @param userId user id to wait for
   */
  public boolean isUserReady(String serial, int sdkVersion, int userId)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion < AndroidVersion.NOUGAT.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Check user state require min api 24.");
    }
    return isUserInExpectedState(serial, sdkVersion, userId, DEFAULT_USER_READY_STATE);
  }

  /**
   * Lists all existing users. Only works with API level >= 17. Supports production build.
   *
   * @param serial serial number of the device
   * @return list of user ids. There should be at least one, user 0, the primary user.
   * @throws MobileHarnessException if failed to list user or the device API level is < 17.
   */
  public List<Integer> listUsers(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    List<AndroidUserInfo> userInfoList = listUsersInfo(serial, sdkVersion);
    List<Integer> results = new ArrayList<>();
    userInfoList.forEach(userInfo -> results.add(userInfo.userId()));
    return results;
  }

  /**
   * Lists all existing users' info. Only works with API level >= 17. Supports production build.
   *
   * @param serial serial number of the device
   * @return list of user ids. There should be at least one, user 0, the primary user.
   * @throws MobileHarnessException if failed to list user or the device API level is < 17.
   */
  public List<AndroidUserInfo> listUsersInfo(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion <= AndroidVersion.JELLY_BEAN.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Multi user support require min api 17.");
    }
    // Output of "pm list users" command is expected to be:
    // Users:
    //         UserInfo{0:Owner:13} running
    //         UserInfo{14:Work profile:30}
    // Running or for earlier Android platform:
    // Users:
    //         UserInfo{0:Owner:13}
    //         UserInfo{14:Work profile:30}
    List<AndroidUserInfo> results = new ArrayList<>();
    String cmdOutput = null;
    try {
      cmdOutput = adb.runShellWithRetry(serial, ADB_SHELL_PM_LIST_USERS);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_LIST_USERS_ERROR, "Failed to list users", e);
    }

    Exception exception = null;
    for (String line : Splitters.LINE_SPLITTER.split(cmdOutput)) {
      Matcher m = USER_PROFILE_REGEX.matcher(line);
      if (m.matches() && m.groupCount() >= 3) {
        try {
          AndroidUserInfo userInfo =
              AndroidUserInfo.builder()
                  .setUserId(Integer.parseInt(m.group("UID")))
                  .setUserName(m.group("NAME"))
                  // Flag is printed as Hex string, so set radix to 16.
                  .setFlag(Integer.parseInt(m.group("FLAG"), 16))
                  .setIsRunning(m.group("STATE").contains("running"))
                  .build();
          results.add(userInfo);
        } catch (NumberFormatException e) {
          exception = e;
        }
      }
    }

    if (exception != null || results.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_LIST_USERS_ERROR,
          String.format("Failed to parse user info from: %s", cmdOutput),
          exception);
    }

    return results;
  }

  private static final Pattern USER_PROFILE_REGEX =
      Pattern.compile("^.*UserInfo\\{(?<UID>\\d+):(?<NAME>.*):(?<FLAG>\\w+)\\}(?<STATE>.*)$");

  /**
   * Removes a user. Only works with API level >= 17. Supports production build.
   *
   * @param serial serial number of the device
   * @param userId id of the user to remove
   * @throws MobileHarnessException if failed to remove user or API level is < 17.
   */
  public void removeUser(String serial, int sdkVersion, int userId)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion <= AndroidVersion.JELLY_BEAN.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Multi user support require min api 17.");
    }
    String output = "";
    Exception exception = null;
    try {
      output =
          adb.runShellWithRetry(serial, String.format(ADB_SHELL_TEMPLATE_PM_REMOVE_USER, userId));
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.startsWith("Success")) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_REMOVE_USER_ERROR,
          String.format(
              "Failed to remove user %d: %s",
              userId,
              (exception != null
                  ? exception.getMessage()
                  : String.format(
                      "Command output: %s\nCurrent foreground user: %s\nSystem users info: %s",
                      output,
                      getCurrentUser(serial, sdkVersion),
                      listUsersInfo(serial, sdkVersion)))),
          exception);
    }
  }

  /**
   * Starts a user in background. Root is not required. Support from API 21.
   *
   * @param serial serial number of the device
   * @param sdkVersion device sdk version
   * @param userId id of the user to start
   */
  public void startUser(String serial, int sdkVersion, int userId)
      throws MobileHarnessException, InterruptedException {
    startUser(serial, sdkVersion, userId, /* waitFlag= */ false);
  }

  /**
   * Starts a user in background. Root is not required. Support from API 21.
   *
   * @param serial serial number of the device
   * @param sdkVersion device sdk version
   * @param userId id of the user to start
   * @param waitFlag whether to specify the wait flag "-w" when starting the user, it only supports
   *     from API 29
   */
  public void startUser(String serial, int sdkVersion, int userId, boolean waitFlag)
      throws MobileHarnessException, InterruptedException {
    // "am start-user" only available from API 21.
    if (sdkVersion < AndroidVersion.LOLLIPOP.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "am start-user support require min api 21.");
    }

    if (waitFlag && sdkVersion < AndroidVersion.ANDROID_10.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "am start-user -w support require min api 29.");
    }

    checkUserExistOnDevice(serial, sdkVersion, userId);

    String output = "";
    Exception exception = null;
    try {
      output =
          adb.runShellWithRetry(
              serial, String.format("am start-user %s%d", waitFlag ? "-w " : "", userId));
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.startsWith("Success")) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_START_USER_ERROR,
          String.format(
              "Failed to start user %d: %s",
              userId, (exception == null ? output : exception.getMessage())),
          exception);
    }

    if (waitFlag) {
      String state = adb.runShellWithRetry(serial, "am get-started-user-state " + userId);
      if (!state.contains(DEFAULT_USER_READY_STATE.getUserState())) {
        logger.atWarning().log(
            "User %s on device %s is not %s after start-user -w: %s",
            userId, serial, DEFAULT_USER_READY_STATE.getUserState(), state);
      }
    }
  }

  /**
   * Switches to put userId in the foreground, starting execution of that user if it is currently
   * stopped. Support from API 17 and production build.
   *
   * @param serial serial number of the device
   * @param sdkVersion device sdk version
   * @param userId id of the user to switch
   */
  public void switchUser(String serial, int sdkVersion, int userId)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion <= AndroidVersion.JELLY_BEAN.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Multi user support require min api 17.");
    }

    checkUserExistOnDevice(serial, sdkVersion, userId);

    Exception exception = null;
    String switchCommand =
        String.format(
            ADB_SHELL_TEMPLATE_AM_SWITCH_USER,
            (sdkVersion >= AndroidVersion.ANDROID_11.getStartSdkVersion() ? "-w " : "") + userId);
    boolean success = false;
    Duration checkUserInterval = Duration.ofSeconds(1);
    try {
      Instant startTime = clock.instant();
      // Instead of check if userId is already in foreground, we simply switch and check.
      String unused = adb.runShell(serial, switchCommand);
      // Give it short time to update current user after switching user
      sleeper.sleep(checkUserInterval);
      success = userId == getCurrentUser(serial, sdkVersion);
      Instant expireTime = startTime.plus(Duration.ofMinutes(1));
      while (!success && clock.instant().isBefore(expireTime)) {
        unused = adb.runShell(serial, switchCommand);
        sleeper.sleep(checkUserInterval);
        success = userId == getCurrentUser(serial, sdkVersion);
      }
    } catch (MobileHarnessException e) {
      exception = e;
    }

    if (exception != null || !success) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SWITCH_USER_ERROR,
          String.format(
              "Failed to switch current user to %s, %s",
              userId,
              (exception != null
                  ? exception.getMessage()
                  : String.format(
                      "expect current id: %s%nsystem users info: %s",
                      userId, listUsersInfo(serial, sdkVersion)))),
          exception);
    }
  }

  /**
   * Waits for user to be in state of {@code AndroidUserState.STATE_USER_UNLOCKED}.
   *
   * <p>Support from API 24 and production build.
   *
   * @param serial device serial id
   * @param sdkVersion device sdk version
   * @param userId user id to wait for
   */
  public void waitForUserReady(String serial, int sdkVersion, int userId)
      throws MobileHarnessException, InterruptedException {
    waitForUserReady(serial, sdkVersion, userId, CHECK_USER_READY_TIMEOUT);
  }

  /**
   * Waits for user to be in state of {@code AndroidUserState.STATE_USER_UNLOCKED}.
   *
   * <p>Support from API 24 and production build.
   *
   * @param serial device serial id
   * @param sdkVersion device sdk version
   * @param userId user id to wait for
   */
  public void waitForUserReady(
      String serial, int sdkVersion, int userId, Duration checkUserReadyTimeout)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Waiting for user %s to state %s", userId, DEFAULT_USER_READY_STATE.name());
    boolean isUserReady =
        AndroidAdbUtil.waitForDeviceReady(
            UtilArgs.builder()
                .setSerial(serial)
                .setSdkVersion(sdkVersion)
                .setUserId(String.valueOf(userId))
                .build(),
            utilArgs ->
                isUserInExpectedState(
                    utilArgs.serial(),
                    utilArgs.sdkVersion().getAsInt(),
                    Integer.parseInt(utilArgs.userId().get()),
                    DEFAULT_USER_READY_STATE),
            WaitArgs.builder()
                .setSleeper(sleeper)
                .setClock(clock)
                .setCheckReadyInterval(CHECK_USER_READY_INTERVAL)
                .setCheckReadyTimeout(checkUserReadyTimeout)
                .build());

    if (!isUserReady) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_WAIT_FOR_USER_NOT_READY,
          String.format(
              "After wait for %s seconds, user is still not in state %s",
              checkUserReadyTimeout.getSeconds(), DEFAULT_USER_READY_STATE.name()));
    }
  }

  /** Helper function to check if a userId exist on device. */
  private void checkUserExistOnDevice(String serial, int sdkVersion, int userId)
      throws MobileHarnessException, InterruptedException {
    List<Integer> userList = listUsers(serial, sdkVersion);
    if (!userList.contains(userId)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_USER_NOT_EXIST,
          String.format(
              "User %s not exist on device (%s), create the user first.", userId, userList));
    }
  }

  /**
   * Get LRU list of history of current users by parsing "mUserLru" in "dumpsys activity". Most
   * recently current is at the head. See detail definition of mUserLru in:
   * frameworks/base/services/core/java/com/android/server/am/UserController.java
   *
   * <p>Use mUserLru but not mCurrentUserId in "dumpsys activity", because mCurrentUserId is not
   * available for API below 24.
   *
   * <p>Running user IDs are listed in order of latest login time.
   */
  private List<Integer> getCurrentUsersHistory(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion <= AndroidVersion.JELLY_BEAN.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Multi user support require min api 17.");
    }

    List<Integer> results = new ArrayList<>();
    Exception exception = null;
    String cmdOutput = "";
    try {
      // Output of dumpsys activity could be:
      //  mStartedUsers:
      //  User #0: state=RUNNING_UNLOCKED
      //  User #10: state=RUNNING_UNLOCKED
      //  User #11: state=BOOTING
      //  mStartedUserArray: [0, 10, 11]
      //  mUserLru: [0, 10, 11]
      //  mLastActiveUsers:[]
      //  mHomeProcess: ProcessRecord{9d61466 21454:com.google.android.setupwizard/u11a111}
      cmdOutput = adbUtil.dumpSys(serial, DumpSysType.ACTIVITY);
      Matcher lruMatcher = USER_LRU_REGEX.matcher(cmdOutput);
      if (lruMatcher.find()) {
        List<String> userLruString =
            Splitter.on(",").trimResults().splitToList(lruMatcher.group("LRU"));
        userLruString.forEach(s -> results.add(Integer.parseInt(s)));
      }
    } catch (MobileHarnessException | NumberFormatException e) {
      exception = e;
    }

    if (exception != null || results.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_GET_RUNNING_USER_ERROR,
          String.format(
              "Failed to get running user list from \"dumpsys activity\": %s",
              exception != null ? exception.getMessage() : cmdOutput),
          exception);
    }
    // Reverse the order so that latest user is in the head.
    return Lists.reverse(results);
  }

  /** Helper function to check if Optional exist and value is true. */
  private boolean isTrue(Optional<Boolean> opt) {
    return opt.isPresent() && opt.get();
  }

  /**
   * Checks if user is in target user state. Since this function will be used as predicate, no
   * exception will be thrown.
   */
  private boolean isUserInExpectedState(
      String serial, int sdkVersion, int userId, AndroidUserState targetUserState) {
    try {
      return targetUserState == getUserState(serial, sdkVersion, userId);
    } catch (MobileHarnessException e) {
      logger.atWarning().log("Failed to get user state with output: %s", e.getMessage());
      return false;
    } catch (InterruptedException ie) {
      logger.atWarning().log(
          "Caught interrupted exception, interrupt current thread: %s", ie.getMessage());
      Thread.currentThread().interrupt();
    }
    return false;
  }

  /**
   * Gets user state from "dumpsys activity" by parsing mStartedUsers.
   *
   * <p>Only works with API level >=24. Supports production build.
   */
  private Map<Integer, String> listStartedUserStates(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    if (sdkVersion < AndroidVersion.NOUGAT.getStartSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT,
          "Check user state require min api 24.");
    }

    Map<Integer, String> startedUserStates = null;
    Exception exception = null;
    String cmdOutput = "";
    try {
      // Output of dumpsys activity could be:
      //  mStartedUsers:
      //   User #0: state=RUNNING_UNLOCKED
      //   User #10: state=RUNNING_UNLOCKED
      //   User #11: state=BOOTING
      //  mStartedUserArray: [0, 10, 11]
      //  mUserLru: [0, 10, 11]
      //  mLastActiveUsers:[]
      //  mHomeProcess: ProcessRecord{9d61466 21454:com.google.android.setupwizard/u11a111}
      cmdOutput = adbUtil.dumpSys(serial, DumpSysType.ACTIVITY);
      Matcher startedUserMatcher = STARTED_USER_REGEX.matcher(cmdOutput);
      startedUserStates = new HashMap<>();
      while (startedUserMatcher.find()) {
        List<String> userState =
            Splitter.onPattern("\\s+").trimResults().splitToList(startedUserMatcher.group("STATE"));
        startedUserStates.put(Integer.parseInt(startedUserMatcher.group("ID")), userState.get(0));
      }
    } catch (MobileHarnessException | NumberFormatException e) {
      exception = e;
    }

    if (exception != null || startedUserStates.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_USER_UTIL_GET_RUNNING_USER_ERROR,
          String.format(
              "Failed to get user state list from \"dumpsys activity\": %s",
              exception != null ? exception.getMessage() : cmdOutput),
          exception);
    }
    return startedUserStates;
  }
}
