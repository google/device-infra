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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidUserUtilTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Adb adb;
  @Mock private AndroidAdbUtil adbUtil;
  @Mock private Sleeper sleeper;
  @Mock private Clock clock;

  private static final String SERIAL = "363005dc750400ec";
  private AndroidUserUtil userUtil;

  @Before
  public void setUp() {
    userUtil = new AndroidUserUtil(adb, adbUtil, sleeper, clock);
  }

  @Test
  public void createUser_successApi17() throws Exception {
    mockCreateUserCommandOutput("Success: created user id 11");

    int userId = userUtil.createUser(SERIAL, 17, "test");

    assertThat(userId).isEqualTo(11);
    verify(adb)
        .runShell(eq(SERIAL), contains("test"), any(Duration.class), any(LineCallback.class));
  }

  @Test
  public void createUser_errorApi17() throws Exception {
    mockCreateUserCommandOutput("Error: couldn't create User.");

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> userUtil.createUser(SERIAL, 17, "test"))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_CREATE_USER_ERROR);
  }

  @Test
  public void createUser_successApi28_ephemeralGuest() throws Exception {
    mockCreateUserCommandOutput("Success: created user id 11");

    int userId = userUtil.createUser(SERIAL, 28, "test", /* ephemeral= */ true, /* guest= */ true);

    assertThat(userId).isEqualTo(11);
    verify(adb)
        .runShell(
            eq(SERIAL),
            contains("--ephemeral --guest"),
            any(Duration.class),
            any(LineCallback.class));
  }

  @Test
  public void createUser_successApi28_ephemeralButNotGuest() throws Exception {
    mockCreateUserCommandOutput("Success: created user id 11");

    int userId = userUtil.createUser(SERIAL, 28, "test", /* ephemeral= */ true, /* guest= */ false);

    assertThat(userId).isEqualTo(11);
    verify(adb)
        .runShell(
            eq(SERIAL), contains("--ephemeral test"), any(Duration.class), any(LineCallback.class));
  }

  @Test
  public void createUser_guest_errorBelow24() throws Exception {
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        userUtil.createUser(
                            SERIAL, 23, "test", /* ephemeral= */ false, /* guest= */ true))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT);
  }

  @Test
  public void createUser_ephemeral_errorBelow28() throws Exception {
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        userUtil.createUser(
                            SERIAL, 26, "test", /* ephemeral= */ true, /* guest= */ true))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT);
  }

  @Test
  public void createWorkProfile_success() throws Exception {
    mockCreateUserCommandOutput("Success: created user id 10");

    int userId = userUtil.createWorkProfile(SERIAL, 21);

    assertThat(userId).isEqualTo(10);
    verify(adb)
        .runShell(
            eq(SERIAL), contains("--profileOf 0"), any(Duration.class), any(LineCallback.class));
  }

  @Test
  public void createWorkProfile_error() throws Exception {
    mockCreateUserCommandOutput("Error: couldn't create User.");

    assertThat(
            assertThrows(MobileHarnessException.class, () -> userUtil.createWorkProfile(SERIAL, 21))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_CREATE_WORK_PROFILE_ERROR);
  }

  @Test
  public void createWorkProfile_belowApi21() throws Exception {
    assertThat(
            assertThrows(MobileHarnessException.class, () -> userUtil.createWorkProfile(SERIAL, 20))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT);
  }

  @Test
  public void getCurrentUser_api24() throws Exception {
    when(adb.runShell(SERIAL, AndroidUserUtil.ADB_SHELL_GET_CURRENT_USER))
        .thenReturn("10")
        .thenReturn("NON_DIGITAL")
        .thenThrow(
            new MobileHarnessException(
                AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Error"));

    assertThat(userUtil.getCurrentUser(SERIAL, 24)).isEqualTo(10);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> userUtil.getCurrentUser(SERIAL, 24))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_GET_FOREGROUND_USER_ERROR);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> userUtil.getCurrentUser(SERIAL, 24))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_GET_FOREGROUND_USER_ERROR);
  }

  @Test
  public void getCurrentUser_api23() throws Exception {
    String dumpsysActivityOutputMultiUser =
        "mStartedUserArray: [0, 10, 11]\n"
            + "mUserLru: [10, 0, 11]\n"
            + "mHomeProcess: ProcessRecord{f0a222 12264:com.android.settings/u10s1000}\n";
    String dumpsysActivityOutputSingleUser =
        "mStartedUserArray: [0, 10, 11]\n"
            + "mUserLru: [0]\n"
            + "mHomeProcess: ProcessRecord{f0a222 12264:com.android.settings/u10s1000}\n";
    when(adbUtil.dumpSys(SERIAL, DumpSysType.ACTIVITY))
        .thenReturn(dumpsysActivityOutputMultiUser)
        .thenReturn(dumpsysActivityOutputSingleUser)
        .thenReturn("Some Error Information");

    assertThat(userUtil.getCurrentUser(SERIAL, 23)).isEqualTo(11);
    assertThat(userUtil.getCurrentUser(SERIAL, 23)).isEqualTo(0);
    assertThat(
            assertThrows(MobileHarnessException.class, () -> userUtil.getCurrentUser(SERIAL, 23))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_GET_FOREGROUND_USER_ERROR);
  }

  @Test
  public void getUserState_api24() throws Exception {
    String dumpsysActivityOutputMultiUser =
        "mStartedUsers:\n"
            + " User #0: state=RUNNING_UNLOCKED\n"
            + " User #11: state=BOOTING\n"
            + "mStartedUserArray: [0, 10]\n"
            + "mUserLru: [0, 10]\n"
            + "mHomeProcess: ProcessRecord{f0a222 12264:com.android.settings/u10s1000}\n";

    when(adbUtil.dumpSys(SERIAL, DumpSysType.ACTIVITY)).thenReturn(dumpsysActivityOutputMultiUser);

    assertThat(userUtil.getUserState(SERIAL, 24, 0))
        .isEqualTo(AndroidUserState.STATE_RUNNING_UNLOCKED);
    assertThat(userUtil.getUserState(SERIAL, 24, 10)).isEqualTo(AndroidUserState.STATE_UNKNOWN);
  }

  @Test
  public void getUserState_api23() throws Exception {
    assertThat(
            assertThrows(MobileHarnessException.class, () -> userUtil.getUserState(SERIAL, 16, 10))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT);
  }

  @Test
  public void getMaxNumberOfUsersSupported() throws Exception {
    when(adb.runShellWithRetry(SERIAL, AndroidUserUtil.ADB_SHELL_GET_MAX_USERS))
        .thenReturn("Maximum supported users: 4")
        .thenReturn("Invalid output");

    assertThat(userUtil.getMaxNumberOfUsersSupported(SERIAL)).isEqualTo(4);
    assertThat(userUtil.getMaxNumberOfUsersSupported(SERIAL)).isEqualTo(0);
  }

  @Test
  public void isUserReady() throws Exception {
    String dumpsysActivityOutputMultiUserBooting =
        "mStartedUsers:\n" + " User #0: state=RUNNING_UNLOCKED\n" + " User #11: state=BOOTING\n";
    String dumpsysActivityOutputMultiUserReady =
        "mStartedUsers:\n"
            + " User #0: state=RUNNING_UNLOCKED\n"
            + " User #11: state=RUNNING_UNLOCKED\n";
    String dumpsysActivityOutputMultiUserUnknown =
        "mStartedUsers:\n" + " User #0: state=RUNNING_UNLOCKED\n" + " User #11: state=UNKNOWN\n";

    when(adbUtil.dumpSys(SERIAL, DumpSysType.ACTIVITY))
        .thenReturn(dumpsysActivityOutputMultiUserBooting)
        .thenReturn(dumpsysActivityOutputMultiUserReady)
        .thenReturn(dumpsysActivityOutputMultiUserUnknown);

    assertThat(userUtil.isUserReady(SERIAL, 24, 11)).isFalse();
    assertThat(userUtil.isUserReady(SERIAL, 24, 11)).isTrue();
    assertThat(userUtil.isUserReady(SERIAL, 24, 11)).isFalse();
  }

  @Test
  public void listUsers() throws Exception {
    mockListUser("0", "12");

    assertThrows(MobileHarnessException.class, () -> userUtil.listUsers(SERIAL, 16));
    assertThat(userUtil.listUsers(SERIAL, 17)).containsExactly(0, 12);
  }

  @Test
  public void listUsersInfo() throws Exception {
    mockListUser("0", "10", "11");

    List<AndroidUserInfo> userInfoList = userUtil.listUsersInfo(SERIAL, 17);
    assertThat(userInfoList).hasSize(3);
    assertThat(userInfoList.get(0).userId()).isEqualTo(0);
    assertThat(userInfoList.get(0).userName()).isEqualTo("owner");
    assertThat(userInfoList.get(0).isRunning()).isTrue();
    assertThat(userInfoList.get(1).userName()).isEqualTo("user1");
  }

  @Test
  public void listUsersInfo_numberFormatError() throws Exception {
    mockListUser("x", "y", "z");

    assertThrows(MobileHarnessException.class, () -> userUtil.listUsersInfo(SERIAL, 17));
  }

  @Test
  public void removeUser() throws Exception {
    when(adb.runShellWithRetry(
            SERIAL, String.format(AndroidUserUtil.ADB_SHELL_TEMPLATE_PM_REMOVE_USER, 10)))
        .thenReturn("Success: removed user");

    assertThrows(MobileHarnessException.class, () -> userUtil.removeUser(SERIAL, 16, 10));
    userUtil.removeUser(SERIAL, 17, 10);
  }

  @Test
  public void startUser_success() throws Exception {
    mockListUser("0", "10");
    mockStartUserCommandOutput("am start-user 10", "Success: user started");

    userUtil.startUser(SERIAL, 22, 10);
  }

  @Test
  public void startUser_error() throws Exception {
    mockListUser("0", "10");
    mockStartUserCommandOutput("am start-user 10", "Error: couldn't create User.");

    assertThat(
            assertThrows(MobileHarnessException.class, () -> userUtil.startUser(SERIAL, 22, 10))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_START_USER_ERROR);
  }

  @Test
  public void startUser_belowApi22() throws Exception {
    assertThat(
            assertThrows(MobileHarnessException.class, () -> userUtil.startUser(SERIAL, 16, 10))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT);
  }

  @Test
  public void startUser_withWaitFlag_api29_success() throws Exception {
    mockListUser("0", "10");
    mockStartUserCommandOutput("am start-user -w 10", "Success: user started");
    when(adb.runShellWithRetry(SERIAL, "am get-started-user-state 10"))
        .thenReturn("RUNNING_UNLOCKED");

    userUtil.startUser(SERIAL, 29, 10, true);

    verify(adb).runShellWithRetry(SERIAL, "am get-started-user-state 10");
  }

  @Test
  public void startUser_withWaitFlag_api28_failed() throws Exception {
    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> userUtil.startUser(SERIAL, 28, 10, true))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_SDK_VERSION_NOT_SUPPORT);
  }

  @Test
  public void switchUser_api24_success() throws Exception {
    int targetUserId = 12;
    mockListUser("0", "10", "12");
    when(adb.runShell(
            SERIAL, String.format(AndroidUserUtil.ADB_SHELL_TEMPLATE_AM_SWITCH_USER, targetUserId)))
        .thenReturn("");
    when(adb.runShell(SERIAL, AndroidUserUtil.ADB_SHELL_GET_CURRENT_USER)).thenReturn("12");
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1L));

    userUtil.switchUser(SERIAL, 24, targetUserId);
  }

  @Test
  public void switchUser_api24_failed() throws Exception {
    int targetUserId = 12;
    mockListUser("0", "10", "12");
    when(adb.runShell(
            SERIAL, String.format(AndroidUserUtil.ADB_SHELL_TEMPLATE_AM_SWITCH_USER, targetUserId)))
        .thenReturn("");
    when(adb.runShell(SERIAL, AndroidUserUtil.ADB_SHELL_GET_CURRENT_USER)).thenReturn("10");
    long nowMs = 1;
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(nowMs))
        .thenReturn(Instant.ofEpochMilli(nowMs + Duration.ofSeconds(1).toMillis()))
        .thenReturn(Instant.ofEpochMilli(nowMs + Duration.ofMinutes(2).toMillis()));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> userUtil.switchUser(SERIAL, 24, targetUserId))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_SWITCH_USER_ERROR);
  }

  @Test
  public void switchUser_api23_success() throws Exception {
    int targetUserId = 12;
    mockListUser("0", "10", "12");
    when(adb.runShell(
            SERIAL, String.format(AndroidUserUtil.ADB_SHELL_TEMPLATE_AM_SWITCH_USER, targetUserId)))
        .thenReturn("");
    when(adbUtil.dumpSys(SERIAL, DumpSysType.ACTIVITY)).thenReturn("mUserLru: [10, 0, 12]\n");
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1L));

    userUtil.switchUser(SERIAL, 23, targetUserId);
  }

  @Test
  public void switchUser_api23_failed() throws Exception {
    int targetUserId = 12;
    mockListUser("0", "10", "12");
    when(adb.runShell(
            SERIAL, String.format(AndroidUserUtil.ADB_SHELL_TEMPLATE_AM_SWITCH_USER, targetUserId)))
        .thenReturn("");
    when(adbUtil.dumpSys(SERIAL, DumpSysType.ACTIVITY)).thenReturn("mUserLru: [10, 12, 0]\n");
    long nowMs = 1;
    when(clock.instant())
        .thenReturn(Instant.ofEpochMilli(nowMs))
        .thenReturn(Instant.ofEpochMilli(nowMs + Duration.ofSeconds(1).toMillis()))
        .thenReturn(Instant.ofEpochMilli(nowMs + Duration.ofMinutes(2).toMillis()));

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> userUtil.switchUser(SERIAL, 23, targetUserId))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_SWITCH_USER_ERROR);
  }

  @Test
  public void switchUser_error() throws Exception {
    int targetUserId = 12;
    mockListUser("0", "10");

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> userUtil.switchUser(SERIAL, 23, targetUserId))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_USER_NOT_EXIST);
  }

  @Test
  public void waitForUserReady() throws Exception {
    String dumpsysActivityOutputMultiUserBooting =
        "mStartedUsers:\n" + " User #0: state=RUNNING_UNLOCKED\n" + " User #11: state=BOOTING\n";
    String dumpsysActivityOutputMultiUserUnlocking =
        "mStartedUsers:\n"
            + " User #0: state=RUNNING_UNLOCKED\n"
            + " User #11: state=RUNNING_UNLOCKING\n";
    String dumpsysActivityOutputMultiUserReady =
        "mStartedUsers:\n"
            + " User #0: state=RUNNING_UNLOCKED\n"
            + " User #11: state=RUNNING_UNLOCKED\n";
    long nowMs = 1;
    when(clock.instant())
        .thenReturn(
            Instant.ofEpochMilli(nowMs),
            Instant.ofEpochMilli(nowMs + AndroidUserUtil.CHECK_USER_READY_INTERVAL.toMillis()),
            Instant.ofEpochMilli(nowMs + 2 * AndroidUserUtil.CHECK_USER_READY_INTERVAL.toMillis()));
    when(adbUtil.dumpSys(SERIAL, DumpSysType.ACTIVITY))
        .thenReturn(dumpsysActivityOutputMultiUserBooting)
        .thenReturn(dumpsysActivityOutputMultiUserUnlocking)
        .thenReturn(dumpsysActivityOutputMultiUserReady);

    userUtil.waitForUserReady(SERIAL, 24, 11);
  }

  @Test
  public void waitForUserReady_notReady() throws Exception {
    String dumpsysActivityOutputMultiUserBooting =
        "mStartedUsers:\n" + " User #0: state=RUNNING_UNLOCKED\n" + " User #11: state=BOOTING\n";
    String dumpsysActivityOutputMultiUserUnlocking =
        "mStartedUsers:\n"
            + " User #0: state=RUNNING_UNLOCKED\n"
            + " User #11: state=RUNNING_UNLOCKING\n";
    long nowMs = 1;
    when(clock.instant())
        .thenReturn(
            Instant.ofEpochMilli(nowMs),
            Instant.ofEpochMilli(nowMs + AndroidUserUtil.CHECK_USER_READY_TIMEOUT.toMillis()));
    when(adbUtil.dumpSys(SERIAL, DumpSysType.ACTIVITY))
        .thenReturn(dumpsysActivityOutputMultiUserBooting)
        .thenReturn(dumpsysActivityOutputMultiUserUnlocking);

    assertThat(
            assertThrows(
                    MobileHarnessException.class, () -> userUtil.waitForUserReady(SERIAL, 24, 11))
                .getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_USER_UTIL_WAIT_FOR_USER_NOT_READY);
  }

  private void mockCreateUserCommandOutput(String output) throws Exception {
    when(adb.runShell(
            eq(SERIAL),
            startsWith(AndroidUserUtil.ADB_SHELL_PM_CREATE_USER),
            any(Duration.class),
            any(LineCallback.class)))
        .thenAnswer(
            invocation -> {
              LineCallback callback = (LineCallback) invocation.getArguments()[3];
              callback.onLine(output);
              throw new MobileHarnessException(
                  AndroidErrorId.ANDROID_ADB_SYNC_CMD_EXECUTION_ERROR, "Failed to execute command");
            });
  }

  private void mockListUser(String... userIds) throws Exception {
    String pmListUserOutput = "Users:\n";
    int index = 0;
    for (String userId : userIds) {
      String userName = index == 0 ? "owner" : "user" + index;
      String userState = "";
      if (index == 0) {
        userState = " running";
      }
      pmListUserOutput =
          pmListUserOutput + String.format("UserInfo{%s:%s:30}%s\n", userId, userName, userState);
      index++;
    }

    when(adb.runShellWithRetry(SERIAL, AndroidUserUtil.ADB_SHELL_PM_LIST_USERS))
        .thenReturn(pmListUserOutput);
  }

  private void mockStartUserCommandOutput(String expectedCommand, String output) throws Exception {
    when(adb.runShellWithRetry(eq(SERIAL), eq(expectedCommand))).thenReturn(output);
  }
}
