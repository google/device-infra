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
import com.google.common.base.Ascii;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.shared.util.time.Sleeper;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.app.devicedaemon.DeviceDaemonHelper;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserInfo;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserState;
import com.google.devtools.mobileharness.platform.android.user.AndroidUserUtil;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidSwitchUserDecoratorSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/** Driver decorator for starting applications on Android device. */
@DecoratorAnnotation(
    help =
        "For switching and creating users."
            + "\nNote that for satellite labs it is advisable to ensure this decorator is"
            + " always used or set dimension 'recovery' = 'wipe'."
            + "\nThis decorator only supports Android P or higher (sdk>=28).")
public class AndroidSwitchUserDecorator extends BaseDecorator
    implements SpecConfigable<AndroidSwitchUserDecoratorSpec> {

  // TODO: gather data on how long this is supposed to take
  // Manual measurement: android P Pixel2 this took at least 10 second for each switch
  private static final int MAX_DECISECONDS_SWITCH_USER_WAIT = 300;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final DeviceDaemonHelper deviceDaemonHelper;
  private final AndroidUserUtil userUtil;
  private final AndroidSystemSettingUtil systemSettingUtil;
  @VisibleForTesting State state = null;
  private UserType userType = null;
  private AndroidUserState waitState = null;

  private final Sleeper sleeper;

  @Inject
  AndroidSwitchUserDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidSystemSettingUtil systemSettingUtil,
      AndroidUserUtil userUtil,
      DeviceDaemonHelper deviceDaemonHelper) {
    this(
        decoratedDriver,
        testInfo,
        systemSettingUtil,
        userUtil,
        deviceDaemonHelper,
        Sleeper.defaultSleeper());
  }

  @VisibleForTesting
  AndroidSwitchUserDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      AndroidSystemSettingUtil systemSettingUtil,
      AndroidUserUtil userUtil,
      DeviceDaemonHelper deviceDaemonHelper,
      Sleeper sleeper) {
    super(decoratedDriver, testInfo);
    this.systemSettingUtil = systemSettingUtil;
    this.userUtil = userUtil;
    this.deviceDaemonHelper = deviceDaemonHelper;
    this.sleeper = sleeper;
  }

  @Override
  public void run(TestInfo testInfo)
      throws com.google.wireless.qa.mobileharness.shared.MobileHarnessException,
          InterruptedException {
    Clock clock = Clock.systemUTC();
    Instant startTime = clock.instant();

    JobInfo jobInfo = testInfo.jobInfo();
    String deviceId = getDevice().getDeviceId();
    AndroidSwitchUserDecoratorSpec spec = jobInfo.combinedSpec(this, deviceId);
    userType = UserType.fromParam(spec.getSwitchUser());
    waitState = convertWaitState(spec.getSwitchUserWaitState());

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("AndroidSwitchUserDecorator: ensuring user switches to %s", userType.toString());

    state =
        new State(userUtil, deviceId, systemSettingUtil.getDeviceSdkVersion(deviceId), userType);

    Optional<AndroidUserInfo> existingUserMaybe = state.findUser();
    if (existingUserMaybe.isPresent()) {
      AndroidUserInfo existingUser = existingUserMaybe.get();
      // There is a user which matches the type we need. Switch to it.
      if (state.userInfoMatchesUserType(state.currentUserInfo)) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Current user userId=%d matches %s, doing nothing",
                state.currentUserInfo.userId(), userType.toString());
      } else {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Switching to existing %s user userId=%d",
                userType.toString(), existingUser.userId());
        switchUser(testInfo, existingUser.userId());
      }
    } else {
      // Create a new user of the correct type and switch to it.
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Creating user %s to switch to.", userType.toString());
      int userId = state.createUser();
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Switching to created %s user userId=%d.", userType.toString(), userId);
      switchUser(testInfo, userId);
    }

    Instant endTime = clock.instant();
    long runTimeMs = Duration.between(startTime, endTime).toMillis();
    testInfo
        .properties()
        .add(
            PropertyName.Test.PREFIX_DECORATOR_RUN_TIME_MS + getClass().getSimpleName(),
            Long.toString(runTimeMs));

    try {
      getDecorated().run(testInfo);
    } finally {
      if (state != null) {
        performCleanup(testInfo, spec);
      }
    }
  }

  void performCleanup(TestInfo testInfo, AndroidSwitchUserDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    boolean cleanupUsers = spec.getCleanupUsers();

    State endState = new State(userUtil, state.deviceId, state.sdkVersion, userType);

    if (state.currentUserInfo.userId() != endState.currentUserInfo.userId()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "currentUser was changed from %d to %d during the test.%s",
              state.currentUserInfo.userId(),
              endState.currentUserInfo.userId(),
              cleanupUsers ? " Attempting to switch back." : "");

      if (cleanupUsers) {
        int previousUser = state.currentUserInfo.userId();
        // only switch if the user still exists.
        if (endState.usersInfo.containsKey(previousUser)) {
          switchUser(testInfo, previousUser);
        } else {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "The previous current user %d was deleted, so cannot switch back.", previousUser);
        }
      }
    }

    for (AndroidUserInfo userInfo : state.usersInfo.values()) {
      AndroidUserInfo endUserInfo = endState.usersInfo.get(userInfo.userId());

      if (endUserInfo == null) {
        // TODO: possibly raise exception do to this?
        testInfo
            .log()
            .atWarning()
            .alsoTo(logger)
            .log(
                "User %d was deleted during the test. This is poor test behavior but not an error.",
                state.currentUserInfo.userId());
        continue;
      }

      if (userInfo.isRunning() != endUserInfo.isRunning()) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "User %d running state changed %b -> %b. No cleanup necessary.",
                userInfo.userId(), userInfo.isRunning(), endUserInfo.isRunning());
      }
    }

    for (AndroidUserInfo endUserInfo : endState.usersInfo.values()) {
      AndroidUserInfo userInfo = state.usersInfo.get(endUserInfo.userId());
      if (userInfo == null) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "User %d was created during the test.%s",
                endUserInfo.userId(), cleanupUsers ? " Deleting" : "");

        if (cleanupUsers) {
          userUtil.removeUser(state.deviceId, state.sdkVersion, endUserInfo.userId());
        }
      }
    }
  }

  private void switchUser(TestInfo testInfo, int userId)
      throws MobileHarnessException, InterruptedException {
    userUtil.switchUser(state.deviceId, state.sdkVersion, userId);
    int i = 0;
    while (userUtil.getUserState(state.deviceId, state.sdkVersion, userId) != waitState) {
      if (i >= MAX_DECISECONDS_SWITCH_USER_WAIT) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SWITCH_USER_DECORATOR_USER_SWITCH_TIMEOUT,
            String.format(
                "User %d failed to reach %s within %f seconds",
                userId, waitState, MAX_DECISECONDS_SWITCH_USER_WAIT / 10.0));
      }
      sleeper.sleep(Duration.ofMillis(100));
      i++;
    }

    deviceDaemonHelper.installAndStartDaemon(getDevice(), testInfo.log());
  }

  /** Parameters that specify which user to operate on. */
  public enum UserType {
    /** current foreground user of the device */
    CURRENT,
    /** user flagged as primary on the device; most often primary = system user = user 0 */
    PRIMARY,
    /** system user = user 0 */
    SYSTEM,
    /** secondary user, i.e. non-primary and non-system. */
    SECONDARY,
    /** guest user */
    GUEST;

    @CheckReturnValue
    public static UserType fromParam(String value) {
      return UserType.valueOf(Ascii.toUpperCase(value));
    }

    boolean isGuest() {
      return this == GUEST;
    }
  }

  /**
   * Holds the current device/user state as well as the intended userType.
   *
   * <p>Contains helper methods for getting the state to the intended userType.
   */
  @VisibleForTesting
  static class State {
    public Map<Integer, AndroidUserInfo> usersInfo = new HashMap<>();
    public AndroidUserInfo currentUserInfo;

    private final AndroidUserUtil userUtil;
    public final String deviceId;
    public final UserType userType;
    public final int sdkVersion;
    private static final int FLAGS_NOT_SECONDARY =
        AndroidUserInfo.FLAG_PRIMARY
            | AndroidUserInfo.FLAG_GUEST
            | AndroidUserInfo.FLAG_RESTRICTED
            | AndroidUserInfo.FLAG_MANAGED_PROFILE;

    State(AndroidUserUtil userUtil, String deviceId, int sdkVersion, UserType userType)
        throws MobileHarnessException, InterruptedException {
      this.userUtil = userUtil;
      this.deviceId = deviceId;
      this.userType = userType;
      this.sdkVersion = sdkVersion;

      int currentUserId = userUtil.getCurrentUser(deviceId, sdkVersion);
      for (AndroidUserInfo userInfo : userUtil.listUsersInfo(deviceId, sdkVersion)) {
        usersInfo.put(userInfo.userId(), userInfo);
      }

      this.currentUserInfo = usersInfo.get(currentUserId);
      if (this.currentUserInfo == null) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SWITCH_USER_DECORATOR_USER_MISSING,
            "currentUser not found in listUsers");
      }
    }

    /** Find a user which matches the userType, or return null. */
    public Optional<AndroidUserInfo> findUser() throws MobileHarnessException {
      for (AndroidUserInfo userInfo : usersInfo.values()) {
        if (userInfoMatchesUserType(userInfo)) {
          return Optional.of(userInfo);
        }
      }
      return Optional.empty();
    }

    public int createUser() throws MobileHarnessException, InterruptedException {
      return userUtil.createUser(
          /* serial= */ deviceId,
          /* sdkVersion= */ sdkVersion,
          /* userName= */ "usrtype_" + Ascii.toLowerCase(userType.toString()),
          /* ephemeral= */ false,
          /* guest= */ userType.isGuest());
    }

    public boolean userInfoMatchesUserType(AndroidUserInfo userInfo) throws MobileHarnessException {
      switch (userType) {
        case CURRENT:
          return userInfo.userId() == currentUserInfo.userId();
        case PRIMARY:
          return userInfo.isPrimary();
        case SYSTEM:
          return userInfo.isSystem();
        case SECONDARY:
          return !userInfo.isSystem() && (userInfo.flag() & FLAGS_NOT_SECONDARY) == 0;
        case GUEST:
          return userInfo.isGuest();
      }

      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SWITCH_USER_DECORATOR_VARIANT_MISSING,
          "Variant not covered: " + userType);
    }
  }

  /** Convert the param string to AndroidUserState. */
  public static AndroidUserState convertWaitState(String waitStateParam) {
    return AndroidUserState.enumOf(Ascii.toUpperCase(waitStateParam));
  }
}
