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

package com.google.devtools.mobileharness.platform.android.systemsetting;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidContent;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidService;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidSettings;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidSvc;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.DumpSysType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.IntentArgs;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.WaitArgs;
import com.google.devtools.mobileharness.platform.android.shared.autovalue.UtilArgs;
import com.google.devtools.mobileharness.platform.android.systemstate.AndroidSystemStateUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.wireless.qa.mobileharness.shared.util.ScreenResolution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Utility class to perform Android system settings and configuration.
 *
 * <p>Please keep all methods in this class sorted in alphabetical order by name.
 *
 * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
 * separator on SDK>23. It's callers' responsibility to parse it correctly.
 */
public class AndroidSystemSettingUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** ADB arg to disable dm-verity checking on userdebug builds. */
  @VisibleForTesting static final String ADB_ARG_DISABLE_VERITY = "disable-verity";

  /** ADB arg to enable dm-verity checking on userdebug builds. */
  @VisibleForTesting static final String ADB_ARG_ENABLE_VERITY = "enable-verity";

  /** ADB shell command for allowing mock location. */
  @VisibleForTesting
  static final String ADB_SHELL_ALLOW_MOCK_LOCATION =
      "--where \"name='mock_location'\" --bind value:i:1";

  /**
   * ADB shell command for setting AppOps permissions. Should be followed by app package and setting
   * name. Requires API >= 21.
   */
  @VisibleForTesting static final String ADB_SHELL_APPOPS = "appops";

  /** ADB shell command for broadcasting airplane mode change. */
  @VisibleForTesting
  static final String ADB_SHELL_BROADCAST_AIRPLANE_MODE = "android.intent.action.AIRPLANE_MODE";

  /** ADB shell command for disabling Setup Wizard. */
  @VisibleForTesting
  static final String ADB_SHELL_DISABLE_SETUP_WIZARD =
      "echo ro.setupwizard.mode=DISABLED >> /data/local.prop && chmod 644 /data/local.prop";

  /** ADB shell command for dismissing keyguard for device with API level >= 23. */
  @VisibleForTesting
  static final String ADB_SHELL_DISMISS_KEYGUARD_API23_AND_ABOVE = "wm dismiss-keyguard";

  /** ADB shell command for dismissing keyguard for device with API level <= 22. */
  @VisibleForTesting
  static final String ADB_SHELL_DISMISS_KEYGUARD_API22_AND_BELOW = "input keyevent 82";

  /** ADB shell command for skip setup wizard with FOUR_CORNER_EXIT. */
  @VisibleForTesting
  static final String ADB_SHELL_EXIT_SETUP_WIZARD =
      "am start -a com.android.setupwizard.FOUR_CORNER_EXIT";

  /** Arguments of the ADB command for showing CPU usage. */
  @VisibleForTesting static final String ADB_SHELL_GET_CPU_USAGE = "top -n 1";

  @VisibleForTesting static final String ADB_SHELL_GET_EPOCH_TIME = "echo $EPOCHREALTIME";

  /**
   * ADB shell command for getting the seconds since 1970-01-01 00:00:00 UTC. Check strftime(3) for
   * more information.
   */
  @VisibleForTesting static final String ADB_SHELL_GET_SECONDS_UTC = "date '+%s'";

  /** ADB shell command for getting the time zone offset. */
  @VisibleForTesting static final String ADB_SHELL_GET_TIME_ZONE_OFFSET = "date '+%z'";

  /** ADB shell svc power command arg for keeping the device stay awake. */
  @VisibleForTesting static final String ADB_SHELL_SVC_KEEP_AWAKE_ARGS = "stayon true";

  /*
   * See http://cs/android/frameworks/base/cmds/locksettings/src/com/android/commands/locksettings/LockSettingsCmd.java
   * for more information.
   */
  @VisibleForTesting
  static final String ADB_SHELL_LOCKSETTINGS_CLEAR_TEMPLATE = "locksettings set-disabled %s true";

  @VisibleForTesting static final String ADB_SHELL_SETTINGS_AIRPLANE_MODE = "airplane_mode_on";

  /** ADB shell command to get current allowed location providers. */
  @VisibleForTesting
  static final String ADB_SHELL_SETTINGS_CHECK_LOCATION_PROVIDERS = "location_providers_allowed";

  /** ADB shell command template for disabling a location provider, like gps, network, etc. */
  @VisibleForTesting
  static final String ADB_SHELL_SETTINGS_DISABLE_LOCATION_PROVIDER_TEMPLATE =
      "location_providers_allowed -%s";

  /** ADB shell command template for enabling a location provider, like gps, network, etc. */
  @VisibleForTesting
  static final String ADB_SHELL_SETTINGS_ENABLE_LOCATION_PROVIDER_TEMPLATE =
      "location_providers_allowed +%s";

  /** ADB shell command to get unknown sources setting. */
  @VisibleForTesting
  static final String ADB_SHELL_SETTINGS_UNKNOWN_SOURCES = "install_non_market_apps";

  /** ADB shell command template for setting unknown sources. */
  @VisibleForTesting
  static final String ADB_SHELL_SETTINGS_SET_UNKNOWN_SOURCES_TEMPLATE =
      ADB_SHELL_SETTINGS_UNKNOWN_SOURCES + " %s";

  /** ADB shell command for checking if setup wizard already been skipped. */
  @VisibleForTesting
  static final String ADB_SHELL_SETTINGS_USER_SETUP_COMPLETE = "user_setup_complete";

  /**
   * ADB shell template to set system time by the device time zone. After execute the command, ADB
   * shell will return current system time by GMT time zone. Should fill the string which of format
   * is 'MMDDhhmm[[CC]YY][.ss]'. For example, if you want to set system time 1999.07.09 15:22:22,
   * you have to fill the string 070915221999.22
   */
  @VisibleForTesting static final String ADB_SHELL_TEMPLATE_SET_SYSTEM_TIME = "date -u %s %s";

  private static final String ANDROID_CONTENT_PROVIDER_PARTNER_SETTING =
      "content://com.google.settings/partner";

  private static final String ANDROID_CONTENT_PROVIDER_SETTING_SECURE = "content://settings/secure";

  /** Database file path for gservices on device. */
  @VisibleForTesting static final String ANDROID_GSERVICE_DB_PATH = "/data/*/*/*/gservices.db";

  /** Database file path for locksettings on device. */
  @VisibleForTesting
  static final String ANDROID_LOCKSETTINGS_DB_PATH = "/data/system/locksettings.db";

  /** Android property for Isolated Storage Mode on Q+. */
  @VisibleForTesting
  static final String ANDROID_PROPERTY_ISOLATED_STORAGE_MODE = "sys.isolated_storage_snapshot";

  /** Android property for Test Harness Mode on Q+. */
  @VisibleForTesting
  static final String ANDROID_PROPERTY_TEST_HARNESS_MODE = "persist.sys.test_harness";

  /** ADB shell command to enable full-wakelock history. */
  @VisibleForTesting
  static final String BATTERY_STATS_FULL_WL_HISTORY = "--enable full-wake-history";

  /** Max retry times for battery stats. */
  @VisibleForTesting static final int BATTERY_STATS_RETRY_TIMES = 3;

  @VisibleForTesting static final Duration BROADCAST_AIRPLANE_MODE_TIMEOUT = Duration.ofMinutes(1);

  /** Interval seconds of checking whether the Android device/emulator is ready. */
  @VisibleForTesting static final Duration CHECK_READY_INTERVAL = Duration.ofSeconds(1);

  /** Timeout seconds of checking whether the Android device/emulator is ready. */
  @VisibleForTesting static final Duration CHECK_READY_TIMEOUT = Duration.ofSeconds(10);

  /**
   * Max acceptable milliseconds difference between lab server system time and device system time.
   */
  @VisibleForTesting
  static final Duration LAB_SERVER_DEVICE_MAX_DIFFERENCE = Duration.ofMinutes(30);

  @VisibleForTesting static final String LEGACY_STORAGE_KEY_NAME = "android:legacy_storage";

  private static final String LEGACY_STORAGE_OUTPUT_PATTERN = "LEGACY_STORAGE:\\s(?<MODE>\\w+)";

  /** Regex pattern that is used for detecting device height for api level < 15. */
  private static final Pattern PATTERN_DISPLAY_HEIGHT_MATCH =
      Pattern.compile("DisplayHeight=([0-9]+)");

  /** Regex pattern that is used for detecting device width for api level < 15. */
  private static final Pattern PATTERN_DISPLAY_WIDTH_MATCH =
      Pattern.compile("DisplayWidth=([0-9]+)");

  /** Regex pattern that is used for detecting device resolution for api level >= 15. */
  private static final Pattern PATTERN_RESOLUTION_MATCH =
      Pattern.compile(
          "init=(?<initWidth>[0-9]+)x(?<initHeight>[0-9]+).*"
              + "cur=(?<curWidth>[0-9]+)x(?<curHeight>[0-9]+)");

  /** The pattern of time zone offset for the device. */
  private static final Pattern PATTERN_TIME_ZONE_OFFSET =
      Pattern.compile("^(\\+|-)((?:[0-1]\\d)|(?:2[0-3]))([0-5]\\d)$");

  /** Property name for setting dex pre-verification.. */
  @VisibleForTesting static final String PROPERTY_DEX_PRE_VERIFICATION = "dalvik.vm.dexopt-flags";

  /** Property value for enabling dex pre-verification. */
  @VisibleForTesting static final String PROP_VALUE_ENABLE_DEX_PRE_VERIFICATION = "m=y";

  /** Property value for disabling dex pre-verification. */
  @VisibleForTesting static final String PROP_VALUE_DISABLE_DEX_PRE_VERIFICATION = "v=n,o=v";

  /** Short duration to run adb command to fail faster. */
  @VisibleForTesting static final Duration SHORT_COMMAND_TIMEOUT = Duration.ofSeconds(5);

  /** SQL string to delete everything from overrides. */
  @VisibleForTesting static final String SQL_CLEAR_GSERVICE_OVERRIDE = "DELETE FROM overrides";

  /** Array of SQL strings to disable screen lock. */
  @VisibleForTesting
  static final String[] SQL_DISABLE_SCREENLOCK =
      new String[] {
        "UPDATE locksettings SET value = \"1\" WHERE name = \"lockscreen.disabled\"",
        "UPDATE locksettings SET value = \"0\" WHERE name = \"lockscreen.password_type\"",
        "UPDATE locksettings SET value = \"0\" WHERE name = \"lockscreen.password_type_alternate\""
      };

  /** SQL string to query GService AndroidId from device. */
  @VisibleForTesting
  static final String SQL_GET_GSERVICE_ANDROID_ID =
      "SELECT value FROM main WHERE name = \"android_id\"";

  /** SQL string to query lockscreen disabled state from device. */
  @VisibleForTesting
  static final String SQL_GET_LOCKSCREEN_DISABLED_VALUE =
      "SELECT value from locksettings WHERE name = \"lockscreen.disabled\"";

  /** The tag for checking user debug build with dev keys. */
  static final String TAG_DEV_KEYS = "dev-keys";

  /** The tag for checking user debug build with test keys. */
  static final String TAG_TEST_KEYS = "test-keys";

  /** The tag used to determine that a build type is userdebug */
  static final String TAG_USERDEBUG = "userdebug";

  /**
   * Regex pattern for the Exception message when protected broadcasts are sent by non-system
   * caller.
   */
  private static final Pattern OUTPUT_PROTECTED_BROADCAST_PERMISSION_DENIAL_PATTERN =
      Pattern.compile(
          "(?s).*\\bPermission Denial: not allowed to send broadcast \\b(.*)\\b from pid=[0-9]*"
              + ", uid=[0-9]*");

  /** UTC time zone. */
  @VisibleForTesting static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");

  /** Date format of set system time. Using UTC time zone. */
  @VisibleForTesting
  static final DateTimeFormatter SET_SYSTEM_TIME_FORMAT =
      DateTimeFormatter.ofPattern("MMddHHmmyyyy.ss").withZone(UTC_TIME_ZONE.toZoneId());

  /** Date format of the return string after set system time. Using UTC time zone. */
  @VisibleForTesting
  static final DateTimeFormatter SET_SYSTEM_TIME_RETURN_FORMAT =
      DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss z yyyy").withZone(UTC_TIME_ZONE.toZoneId());

  /** {@code Adb} for running shell command on device. */
  private final Adb adb;

  /** {@code Clock} for getting current system time. */
  private final Clock clock;

  /** {@code Sleeper} for waiting the device to become ready for use. */
  private final Sleeper sleeper;

  private final AndroidAdbUtil adbUtil;

  private final AndroidSystemStateUtil systemStateUtil;

  public AndroidSystemSettingUtil() {
    this(
        new Adb(),
        Sleeper.defaultSleeper(),
        Clock.systemUTC(),
        new AndroidAdbUtil(),
        new AndroidSystemStateUtil());
  }

  @VisibleForTesting
  AndroidSystemSettingUtil(
      Adb adb,
      Sleeper sleeper,
      Clock clock,
      AndroidAdbUtil adbUtil,
      AndroidSystemStateUtil systemStateUtil) {
    this.adb = adb;
    this.sleeper = sleeper;
    this.clock = clock;
    this.adbUtil = adbUtil;
    this.systemStateUtil = systemStateUtil;
  }

  /**
   * Allows mock location in device.
   *
   * <p>Requires API level 16.
   *
   * @param serial the serial number of the device
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public String allowMockLocation(String serial)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Device %s: Allow mock location.", serial);
    AndroidContent contentArgs =
        AndroidContent.builder()
            .setCommand(AndroidContent.Command.UPDATE)
            .setUri(ANDROID_CONTENT_PROVIDER_SETTING_SECURE)
            .setOtherArgument(ADB_SHELL_ALLOW_MOCK_LOCATION)
            .build();
    try {
      return adbUtil.content(UtilArgs.builder().setSerial(serial).build(), contentArgs);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_ALLOW_MOCK_LOCATION_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Checks the difference between the host system time and the device system time. This method only
   * works with SDK version >= 10, rooted and non-rooted devices.
   *
   * @param serial serial number of the device
   * @return whether is not more than the max difference between host system time and device system
   *     time
   */
  public boolean checkSystemTime(String serial)
      throws MobileHarnessException, InterruptedException {
    return checkSystemTime(serial, LAB_SERVER_DEVICE_MAX_DIFFERENCE);
  }

  /**
   * Checks the difference between the host system time and the device system time. This method only
   * works with SDK version >= 10, rooted and non-rooted devices.
   *
   * @param serial serial number of the device
   * @param maxDifferenceFromHost the max difference the host system time and device system time
   *     should be
   * @return {@code true} if difference between host system time and device system time is less than
   *     {@code maxDifferenceFromHost}, otherwise {@code false}.
   */
  public boolean checkSystemTime(String serial, Duration maxDifferenceFromHost)
      throws MobileHarnessException, InterruptedException {
    Instant hostTime = clock.instant();
    Instant deviceTime = getSystemTime(serial);
    Duration difference = Duration.between(deviceTime, hostTime).abs();
    return difference.compareTo(maxDifferenceFromHost) < 0;
  }

  /**
   * Clears the all of the gservices.db overrides. Note this only works with API level >= 18 rooted
   * devices. Otherwise, there is no effect.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public String clearGServicesOverrides(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      return adbUtil.sqlite(serial, ANDROID_GSERVICE_DB_PATH, SQL_CLEAR_GSERVICE_OVERRIDE);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_CLEAR_GSERVICE_OVERRIDE_ERROR,
          String.format(
              "Failed to clear GService Override for device %s: %s", serial, e.getMessage()),
          e);
    }
  }

  /**
   * Sets airplane mode. Only works with API level >= 17. Supports production build with API level <
   * 24. For production build with API level >= 24, airplane mode may not be broadcasted properly.
   */
  public void setAirplaneMode(String serial, boolean enable)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    String targetAirplaneModeState = enable ? "1" : "0";
    try {
      // Airplane read/write commands are very slow. DOES NOT retry.
      // Sets timeout to 60s to fail fast.
      String airplaneModeState =
          String.format("%s %s", ADB_SHELL_SETTINGS_AIRPLANE_MODE, targetAirplaneModeState);
      AndroidSettings.Spec spec =
          AndroidSettings.Spec.create(
              AndroidSettings.Command.PUT, AndroidSettings.NameSpace.GLOBAL, airplaneModeState);
      adbUtil.settings(UtilArgs.builder().setSerial(serial).build(), spec, Duration.ofSeconds(60));
      output =
          adbUtil.broadcast(
              UtilArgs.builder().setSerial(serial).build(),
              IntentArgs.builder()
                  .setAction(ADB_SHELL_BROADCAST_AIRPLANE_MODE)
                  .setExtrasBoolean(ImmutableMap.of("state", enable))
                  .build(),
              /* checkCmdOutput= */ false,
              BROADCAST_AIRPLANE_MODE_TIMEOUT);
    } catch (MobileHarnessException e) {
      // Check if the airplane mode is off in spite of the broadcast failure. In this case, no
      // exception is thrown.
      Matcher exceptionMatcher =
          OUTPUT_PROTECTED_BROADCAST_PERMISSION_DENIAL_PATTERN.matcher(e.getMessage());
      if (exceptionMatcher.lookingAt()) {
        AndroidSettings.Spec spec =
            AndroidSettings.Spec.create(
                AndroidSettings.Command.GET,
                AndroidSettings.NameSpace.GLOBAL,
                ADB_SHELL_SETTINGS_AIRPLANE_MODE);
        String checkModeOutput =
            adbUtil
                .settings(UtilArgs.builder().setSerial(serial).build(), spec, SHORT_COMMAND_TIMEOUT)
                .trim();
        if (checkModeOutput.equals(targetAirplaneModeState)) {
          logger.atInfo().log(
              "Failed to broadcast %s because the caller is not system."
                  + " But current airplane mode is same as the target one [%s].",
              exceptionMatcher.group(1), targetAirplaneModeState);
          return;
        }
      }
      // handle exception e in the next if block.
      output = e.getMessage();
    }
    // Example output:
    // Broadcasting: Intent { act=android.intent.action.AIRPLANE_MODE }
    // Broadcast completed: result=0
    if (!output.contains(AndroidAdbUtil.OUTPUT_BROADCAST_SUCCESS)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_DISABLE_AIRPLANE_MODE_ERROR,
          String.format("Failed to broadcast airplane change to device %s:\n%s", serial, output));
    }
  }

  /**
   * Tries to disable screen lock. This only works if the device has root access and API >= 22 and
   * has already become root. Be careful, this method will restart the Zygote process on your
   * device. disableScreenLock() disables lock screen feature from Android settings so the device
   * will never be lock in the future.
   *
   * @param serial the serial number of the device
   * @param sdkVersion SDK version of device
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void disableScreenLock(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    disableScreenLock(serial, sdkVersion, /* userId= */ null);
  }

  /**
   * Tries to disable screen lock. This only works if the device has root access and API >= 22 and
   * has already become root. Be careful, this method will restart the Zygote process on your
   * device. disableScreenLock() disables lock screen feature from Android settings so the device
   * will never be lock in the future.
   *
   * @param serial the serial number of the device
   * @param sdkVersion SDK version of device
   * @param userId user id when execute shell command
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void disableScreenLock(String serial, int sdkVersion, @Nullable String userId)
      throws MobileHarnessException, InterruptedException {
    // We only try to take action when API is >=22.
    if (sdkVersion > AndroidVersion.NOUGAT.getEndSdkVersion()) {
      try {
        String unused =
            adb.runShell(
                serial,
                String.format(
                    ADB_SHELL_LOCKSETTINGS_CLEAR_TEMPLATE,
                    userId == null ? "" : "--user " + userId));
        dismissLockScreen(serial, sdkVersion);
        return;
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log("Failed to clear lock setting, fallback to SQLite");
      }
    }

    try {
      if (sdkVersion >= AndroidVersion.LOLLIPOP.getEndSdkVersion()) {
        try {
          // Check if we have already disabled lock in some way, to avoid unnecessary Zygote
          // restart.
          if (!isScreenLockDisabled(serial)) {
            disableScreenLockViaSqlite(serial);
            systemStateUtil.softReboot(serial);
            systemStateUtil.waitUntilReady(serial);
          }
        } catch (MobileHarnessException e) {
          if (e.getMessage().contains("sqlite3: not found")) {
            // According to b/37627999, device may not have sqlite3
            logger.atWarning().log("sqlite3 is missing for device %s%n%s", serial, e.getMessage());
          } else {
            throw e;
          }
        }
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_DISABLE_SCREENLOCK_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Disables Setup Wizard. This method works on Google Experience Rootewd Device with userdebug
   * build only.
   *
   * @param serial serial number of the device
   * @return if need device reboot
   * @throws MobileHarnessException if error occurs when setting the device property
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public PostSettingDeviceOp disableSetupWizard(String serial)
      throws MobileHarnessException, InterruptedException {
    boolean readyAfterFourCornerExit = false;
    Exception exception = null;
    try {
      // Try with formal method "FOUR_CORNER_EXIT" to skip setup wizard first which could avoid
      // device reboot.
      String unused = adb.runShell(serial, ADB_SHELL_EXIT_SETUP_WIZARD);
      readyAfterFourCornerExit =
          AndroidAdbUtil.waitForDeviceReady(
              UtilArgs.builder().setSerial(serial).build(),
              this::isSetupWizardDisabled,
              WaitArgs.builder()
                  .setSleeper(sleeper)
                  .setClock(clock)
                  .setCheckReadyInterval(CHECK_READY_INTERVAL)
                  .setCheckReadyTimeout(CHECK_READY_TIMEOUT)
                  .build());
      if (readyAfterFourCornerExit) {
        logger.atInfo().log("Setup wizard skipped by FOUR_CORNER_EXIT activity");
        // No need to reboot.
        return PostSettingDeviceOp.NONE;
      }
    } catch (MobileHarnessException oe) {
      exception = oe;
    }
    if (exception != null || !readyAfterFourCornerExit) {
      logger.atInfo().log(
          "Failed to perform FOUR_CORNER_EXIT, use local property instead: %s",
          exception == null
              ? "device not ready after trying to skip setup wizard"
              : exception.getMessage());
    }

    // Try again by setting local property.
    String output = "";
    exception = null;
    try {
      output = adb.runShell(serial, ADB_SHELL_DISABLE_SETUP_WIZARD);
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_ERROR,
          String.format(
              "Failed to skip Setup Wizard; command \"%s\" failed:%n%s",
              ADB_SHELL_DISABLE_SETUP_WIZARD, exception == null ? output : exception.getMessage()),
          exception);
    }
    // Need a reboot
    return PostSettingDeviceOp.REBOOT;
  }

  /**
   * Dismisses lockscreen on device. This method will unlock the screen, then device/emulator will
   * be locked again according to device policies.
   *
   * @return {@code true} if discconnected successfully, {@code false} otherwise
   * @throws MobileHarnessException if fails to execute the command or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public boolean dismissLockScreen(String deviceId, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Dismissing Lock screen on device %s", deviceId);
    String cmdOutput;
    try {
      if (sdkVersion >= AndroidVersion.MARSHMALLOW.getStartSdkVersion()) {
        cmdOutput = adb.runShell(deviceId, ADB_SHELL_DISMISS_KEYGUARD_API23_AND_ABOVE);
      } else {
        cmdOutput = adb.runShell(deviceId, ADB_SHELL_DISMISS_KEYGUARD_API22_AND_BELOW);
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_DISMISS_KEYGUARD_ERROR, e.getMessage(), e);
    }
    if (!cmdOutput.isEmpty()) {
      logger.atWarning().log("Dismiss LockScreen failed. Output=%s", cmdOutput);
      return false;
    }
    return true;
  }

  /**
   * Enables full wakelock history.
   *
   * <p>Work on API 21 and above.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if failed to set the flag
   */
  public void enableFullWakelockHistory(String serial)
      throws MobileHarnessException, InterruptedException {
    String expectedOutput = "Enabled: full-wake-history";
    String errMsgPrefix = "Failed to set batterystats full wakelock-history ";
    batteryStatsCommandWithRetry(
        serial, BATTERY_STATS_FULL_WL_HISTORY, expectedOutput, errMsgPrefix);
  }

  /**
   * Enable GPS if {@code enable} is true, otherwise disable GPS.
   *
   * <p>Work on API 17 and above.
   *
   * @param enable true if want to enable GPS, otherwise disable GPS
   */
  public void enableGpsLocation(String serial, boolean enable)
      throws MobileHarnessException, InterruptedException {
    String extraArgs =
        String.format(
            enable
                ? ADB_SHELL_SETTINGS_ENABLE_LOCATION_PROVIDER_TEMPLATE
                : ADB_SHELL_SETTINGS_DISABLE_LOCATION_PROVIDER_TEMPLATE,
            Ascii.toLowerCase(LocationProvider.GPS.name()));
    AndroidSettings.Spec spec =
        AndroidSettings.Spec.create(
            AndroidSettings.Command.PUT, AndroidSettings.NameSpace.SECURE, extraArgs);
    try {
      adbUtil.settings(UtilArgs.builder().setSerial(serial).build(), spec);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_ENABLE_GPS_ERROR,
          String.format(
              "Failed to %s GPS; command \"%s\" failed", enable ? "enable" : "disable", extraArgs),
          e);
    }
  }

  /**
   * Enable network location service if {@code enable} is true, otherwise disable network location
   * service.
   *
   * <p>Work on API 17 and above.
   *
   * @param enable true if want to enable network location, otherwise disable network location
   */
  public void enableNetworkLocation(String serial, boolean enable)
      throws MobileHarnessException, InterruptedException {
    // Enable 'network_location_opt_in' before 'location_providers_allowed +network'. Without this
    // setting, a user prompt window will pop up and asking 'Improve location accuracy?'
    // See b/65843341 for more information.
    String dismissPromptCmd =
        String.format("--bind name:s:network_location_opt_in --bind value:s:%d", enable ? 1 : 0);
    AndroidContent contentArgs =
        AndroidContent.builder()
            .setCommand(AndroidContent.Command.INSERT)
            .setUri(ANDROID_CONTENT_PROVIDER_PARTNER_SETTING)
            .setOtherArgument(dismissPromptCmd)
            .build();
    String networkLocationCmd =
        String.format(
            enable
                ? ADB_SHELL_SETTINGS_ENABLE_LOCATION_PROVIDER_TEMPLATE
                : ADB_SHELL_SETTINGS_DISABLE_LOCATION_PROVIDER_TEMPLATE,
            Ascii.toLowerCase(LocationProvider.NETWORK.name()));
    AndroidSettings.Spec spec =
        AndroidSettings.Spec.create(
            AndroidSettings.Command.PUT, AndroidSettings.NameSpace.SECURE, networkLocationCmd);
    try {
      adbUtil.content(UtilArgs.builder().setSerial(serial).build(), contentArgs);
      adbUtil.settings(UtilArgs.builder().setSerial(serial).build(), spec);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_ENABLE_NETWORK_LOCATION_ERROR,
          String.format("Failed to %s network location", enable ? "enable" : "disable"),
          e);
    }
  }

  /** Enable Unknown sources option. Only works with API level >= 17. Supports production build. */
  public void enableUnknownSources(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    try {
      // We only try to take action when API is >=17.
      if (sdkVersion >= 17) {
        String secureValue =
            adbUtil.settings(
                UtilArgs.builder().setSerial(serial).build(),
                AndroidSettings.Spec.create(
                    AndroidSettings.Command.GET,
                    AndroidSettings.NameSpace.SECURE,
                    ADB_SHELL_SETTINGS_UNKNOWN_SOURCES),
                SHORT_COMMAND_TIMEOUT);
        String globalValue =
            adbUtil.settings(
                UtilArgs.builder().setSerial(serial).build(),
                AndroidSettings.Spec.create(
                    AndroidSettings.Command.GET,
                    AndroidSettings.NameSpace.GLOBAL,
                    ADB_SHELL_SETTINGS_UNKNOWN_SOURCES),
                SHORT_COMMAND_TIMEOUT);
        if (Objects.equals(secureValue, "0")) {
          logger.atInfo().log("Enable secure unknown source");
          adbUtil.settings(
              UtilArgs.builder().setSerial(serial).build(),
              AndroidSettings.Spec.create(
                  AndroidSettings.Command.PUT,
                  AndroidSettings.NameSpace.SECURE,
                  String.format(ADB_SHELL_SETTINGS_SET_UNKNOWN_SOURCES_TEMPLATE, "1")),
              SHORT_COMMAND_TIMEOUT);
        } else if ("1".equals(secureValue)) {
          logger.atInfo().log("Secure unknown source enabled, skipped");
        } else if ("0".equals(globalValue)) {
          logger.atInfo().log("Enable global unknown source");
          adbUtil.settings(
              UtilArgs.builder().setSerial(serial).build(),
              AndroidSettings.Spec.create(
                  AndroidSettings.Command.PUT,
                  AndroidSettings.NameSpace.GLOBAL,
                  String.format(ADB_SHELL_SETTINGS_SET_UNKNOWN_SOURCES_TEMPLATE, "1")),
              SHORT_COMMAND_TIMEOUT);
        } else if ("1".equals(globalValue)) {
          logger.atInfo().log("Global unknown source enabled, skipped");
        } else {
          logger.atWarning().log("Failed to find secure/global unknown source options, aborted");
        }
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_ENABLE_UNKNOWN_SOURCES_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Forces the USB connection to 'adb' mode only. Note this only works on rooted devices.
   * Otherwise, there is no effect.
   *
   * <p>This method will disconnect device for a short while, need to cache device before using it.
   */
  public void forceUsbToAdbMode(String serial) throws MobileHarnessException, InterruptedException {
    try {
      String usbProp = "persist.sys.usb.config";
      String usbMode = adbUtil.getProperty(serial, ImmutableList.<String>of(usbProp));
      if (!usbMode.isEmpty() // for early devices such as Nexus S 2.3, there is no such property
          && !usbMode.equals("adb")) {
        adbUtil.setProperty(serial, usbProp, "adb");
        logger.atInfo().log("Set device %s to adb mode only", serial);
        // After setting the usb mode, immediately adb commands to the device can fail with error
        // "device not found". Needs to wait until the device connection is back.
        systemStateUtil.waitUntilReady(serial);

        // Even worse, the "adb devices" may not be able to detect the device after setting the usb
        // mode. Needs the detector to tolerate such issue.
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_FORCE_USB2ADB_MODE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Reads the airplane mode. Only works with API level >= 17. Supports production build with API
   * level < 24. For production build with API level >= 24, airplane mode may not be broadcasted
   * properly.
   *
   * @return true if airplane mode is enable, false if disabled
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public boolean getAirplaneMode(String serial)
      throws MobileHarnessException, InterruptedException {
    // Airplane read/write commands are very slow. DOES NOT retry.
    // Sets timeout to 5s to fail fast.
    AndroidSettings.Spec spec =
        AndroidSettings.Spec.create(
            AndroidSettings.Command.GET,
            AndroidSettings.NameSpace.GLOBAL,
            ADB_SHELL_SETTINGS_AIRPLANE_MODE);
    String output =
        adbUtil
            .settings(UtilArgs.builder().setSerial(serial).build(), spec, SHORT_COMMAND_TIMEOUT)
            .trim();
    boolean airplaneMode;
    switch (output) {
      case "0":
        airplaneMode = false;
        break;
      case "1":
        airplaneMode = true;
        break;
      default:
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SYSTEM_SETTING_PARSE_AIRPLANE_MODE_ERROR,
            "Failed to read airplane mode: " + output);
    }

    // Makes sure the airplane mode setting has effect.
    try {
      output =
          adbUtil.broadcast(
              UtilArgs.builder().setSerial(serial).build(),
              IntentArgs.builder().setAction(ADB_SHELL_BROADCAST_AIRPLANE_MODE).build(),
              /* checkCmdOutput= */ false,
              BROADCAST_AIRPLANE_MODE_TIMEOUT);
    } catch (MobileHarnessException e) {
      // In case the airplane mode is not broadcasted properly due to permission denial,
      // no exception is thrown.
      Matcher exceptionMatcher =
          OUTPUT_PROTECTED_BROADCAST_PERMISSION_DENIAL_PATTERN.matcher(e.getMessage());
      if (exceptionMatcher.lookingAt()) {
        logger.atInfo().log(
            "Failed to broadcast %s because the caller is not system.", exceptionMatcher.group(1));
        return airplaneMode;
      }
      // handle exception e in the next if block.
      output = e.getMessage();
    }
    // Example output:
    // Broadcasting: Intent { act=android.intent.action.AIRPLANE_MODE }
    // Broadcast completed: result=0
    if (!output.contains(AndroidAdbUtil.OUTPUT_BROADCAST_SUCCESS)) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_BROADCAST_AIRPLANE_MODE_ERROR,
          "Failed to broadcast airplane change to device " + serial);
    }
    return airplaneMode;
  }

  /**
   * Gets the battery level of the device.
   *
   * @return battery level: 0~100
   */
  public int getBatteryLevel(String serial) throws MobileHarnessException, InterruptedException {
    Exception exception = null;
    String errMsg = "";

    try {
      String output = adbUtil.dumpSys(serial, DumpSysType.BATTERY);
      Matcher matcher = Pattern.compile(" level: (\\d+)").matcher(output);
      errMsg = String.format("Failed to parse battery level for device %s:\n%s", serial, output);
      if (matcher.find()) {
        String level = matcher.group(1);
        try {
          return Integer.parseInt(level);
        } catch (NumberFormatException e) {
          exception = e;
        }
      }
    } catch (MobileHarnessException e) {
      exception = e;
      errMsg = e.getMessage();
    }

    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_BATTERY_LEVEL_ERROR, errMsg, exception);
  }

  /**
   * Gets the battery temperature of the device. Tested on unrooted Android 7.1.2 and 4.4.4.
   *
   * @return battery temperature in ℃
   */
  public Optional<Integer> getBatteryTemperature(String serial)
      throws MobileHarnessException, InterruptedException {
    // Run dumpSys adb command to get battery state. A sample output:
    // Current Battery Service state:
    // AC powered: false
    // USB powered: true
    // status: 2
    // health: 2
    // present: true
    // level: 98
    // scale: 100
    // voltage:4083
    // temperature: 360
    // technology: Li-ion

    String output = "";
    try {
      output = adbUtil.dumpSys(serial, DumpSysType.BATTERY);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_BATTERY_TEMP_ERROR, e.getMessage(), e);
    }

    Matcher matcher = Pattern.compile(" temperature: (\\d+)").matcher(output);
    String errMsg =
        String.format("Failed to parse battery temperature for device %s:\n%s", serial, output);
    if (matcher.find()) {
      String temperature = matcher.group(1);
      try {
        return Optional.of(Integer.parseInt(temperature) / 10);
      } catch (NumberFormatException e) {
        logger.atWarning().log("%s", errMsg);
        return Optional.empty();
      }
    } else {
      logger.atWarning().log("%s", errMsg);
      return Optional.empty();
    }
  }

  /**
   * Gets the batterystats in CSV format (Lollipop and above only).
   *
   * <p>Work on API 19 and above.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial the serial number of the device
   * @return batterystats info in CSV format
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public String getBatteryStatsCSV(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      return adbUtil.dumpSys(serial, DumpSysType.BATTERYSTATS, "--checkin");
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_BATTERY_TEMP_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Gets the CPU usage of the device.
   *
   * <p>For command output, Adb uses "\r\n" as line separator on SDK<=23, while uses "\n" as line
   * separator on SDK>23. It's callers' responsibility to parse it correctly.
   *
   * @param serial the serial number of the device
   * @return the cpu usage report of the device.
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public String getCpuUsage(String serial) throws MobileHarnessException, InterruptedException {
    // Sample output:
    //  User 3%, System 8%, IOW 0%, IRQ 0%
    //  User 11 + Nice 0 + Sys 27 + Idle 265 + IOW 0 + IRQ 0 + SIRQ 0 = 303
    //
    //    PID PR CPU% S  #THR     VSS     RSS PCY UID      Name
    //    827  0   4% S    87 988432K  72308K  fg system   system_server
    //  12383  0   2% R     1   1296K    488K     root     top
    //    186  1   2% S    28  31136K   1084K     nobody   /system/bin/sensors.qcom
    //   4362  1   1% S    31 937944K  52760K  bg u0_a7    com.google.android.gms.persistent
    //   1353  0   0% S     7   7220K    492K     root     /system/bin/mpdecision
    //  29971  0   0% S     1      0K      0K     root     kworker/u:1
    //   5924  0   0% S     1      0K      0K     root     kworker/u:0
    //   1046  0   0% S     1   3324K   1776K     wifi     /system/bin/wpa_supplicant
    //   9059  0   0% S     1      0K      0K     root     kworker/0:2
    try {
      return adb.runShellWithRetry(serial, ADB_SHELL_GET_CPU_USAGE);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_CPU_USAGE_ERROR, e.getMessage(), e);
    }
  }

  /** Gets the SDK version of the device. */
  public int getDeviceSdkVersion(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      int baseSdkVersion = adbUtil.getIntProperty(serial, AndroidProperty.SDK_VERSION);
      String previewSdkVersion = adbUtil.getProperty(serial, AndroidProperty.PREVIEW_SDK_VERSION);
      if (!previewSdkVersion.isEmpty() && Integer.parseInt(previewSdkVersion) > 0) {
        return baseSdkVersion + 1;
      } else {
        return baseSdkVersion;
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_SDK_ERROR,
          "Failed to parse sdk version from device property: " + e.getMessage(),
          e);
    }
  }

  /**
   * Gets the version codename of the device, e.g. "Q" for Android Q. {@link #getDeviceSdkVersion}
   * is preferable in all cases except when the sdk version is not yet public, in which case
   * depending on the version codename is necessary.
   *
   * <p>For example, in the case of Android Q, as of Jan 2019, the sdk version of the device will be
   * 28 (indicating Android P), but the codename will be "Q".
   */
  public String getDeviceVersionCodeName(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      return adbUtil.getProperty(serial, AndroidProperty.CODENAME);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_VERSION_CODE_NAME_ERROR,
          "Failed to get version code name from device property: " + e.getMessage(),
          e);
    }
  }

  /**
   * Run GServices Android ID. Only works with rooted devices with API level >= 18.
   *
   * @param serial serial number of the device
   */
  public String getGServicesAndroidID(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      return adbUtil.sqlite(serial, ANDROID_GSERVICE_DB_PATH, SQL_GET_GSERVICE_ANDROID_ID);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_GSERVICE_ANDROID_ID_ERROR,
          String.format(
              "Failed to get GService AndroidId from device %s: %s", serial, e.getMessage()),
          e);
    }
  }

  /** Check if legacy storage mode enabled for a package on device. */
  public boolean getPackageLegacyStorageMode(String serial, String packageName)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    try {
      String[] command = new String[] {"get", packageName};
      output = adbUtil.cmd(serial, AndroidService.APPOPS, command);
      Pattern modePattern = Pattern.compile(LEGACY_STORAGE_OUTPUT_PATTERN);
      Matcher modeMatcher = modePattern.matcher(output);
      if (modeMatcher.find()) {
        return Ascii.equalsIgnoreCase(modeMatcher.group("MODE").trim(), "allow");
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_PACKAGE_STORAGE_MODE_ERROR, e.getMessage(), e);
    }
    return false;
  }

  /**
   * Gets the mode for a particular application and operation via AppOps service command. Returns
   * {@code Optional.empty()} when given operation is unknown.
   *
   * <p>See more details in "adb shell appops help".
   */
  public Optional<AppOperationMode> getPackageOperationMode(
      String serial, String packageName, String operation)
      throws MobileHarnessException, InterruptedException {
    try {
      String[] command = new String[] {"get", packageName};
      String output = adbUtil.cmd(serial, AndroidService.APPOPS, command);
      Pattern modePattern = Pattern.compile(String.format("%s:\\s(?<MODE>\\w+)", operation));
      Matcher modeMatcher = modePattern.matcher(output);
      if (modeMatcher.find()) {
        return Optional.of(
            AppOperationMode.valueOf(Ascii.toUpperCase(modeMatcher.group("MODE").trim())));
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_PACKAGE_OP_MODE_ERROR, e.getMessage(), e);
    }
    return Optional.empty();
  }

  /**
   * Get the screen resolution of the given device. It works on api level >=15 and 10.
   *
   * @param serial the serial number of the device
   * @return the screen resolution
   * @throws MobileHarnessException if window information dumped can't be parsed as expected.
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public ScreenResolution getScreenResolution(String serial)
      throws MobileHarnessException, InterruptedException {
    String windowInfo = "";
    try {
      windowInfo = adbUtil.dumpSys(serial, DumpSysType.WINDOW);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_DUMPSYS_WINDOW_ERROR, e.getMessage(), e);
    }
    /*
     * Sample segment of windowInfo for PATTERN_RESOLUTION_MATCH:
     * WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
     * Display: mDisplayId=0
     * init=1080x1920 420dpi cur=1080x1920 app=1080x1794 rng=1080x1017-1794x1731
     * deferred=false layoutNeeded=false
     * mStacks[1]1
     * mStackId=1
     * mDeferDetach=false
     */
    Matcher matcher = PATTERN_RESOLUTION_MATCH.matcher(windowInfo);
    if (matcher.find()) {
      int width = Integer.parseInt(matcher.group("initWidth"));
      int height = Integer.parseInt(matcher.group("initHeight"));
      int curWidth = Integer.parseInt(matcher.group("curWidth"));
      int curHeight = Integer.parseInt(matcher.group("curHeight"));
      ScreenResolution resolution =
          ScreenResolution.createWithOverride(width, height, curWidth, curHeight);
      logger.atInfo().log("Detect device resolution: %s", resolution);
      return resolution;
    } else {
      /*
       * Sample segment of windowInfo for PATTERN_DISPLAY_WIDTH_MATCH:
       *  mSystemBooted=true mDisplayEnabled=true
       *  mLayoutNeeded=false mBlurShown=false
       *  no DimAnimator
       *  mInputMethodAnimLayerAdjustment=0  mWallpaperAnimLayerAdjustment=0
       *  mLastWallpaperX=0.5 mLastWallpaperY=0.0
       *  mDisplayFrozen=false mWindowsFreezingScreen=false mAppsFreezingScreen=0
       *  mRotation=0, mForcedAppOrientation=5, mRequestedRotation=0
       *  mAnimationPending=false mWindowAnimationScale=1.0 mTransitionWindowAnimationScale=1.0
       *  mNextAppTransition=0xffffffff, mAppTransitionReady=false, mAppTransitionRunning=false
       *  mStartingIconInTransition=false, mSkipAppTransitionAnimation=false
       *  DisplayWidth=480 DisplayHeight=800
       */
      Matcher widthMatcher = PATTERN_DISPLAY_WIDTH_MATCH.matcher(windowInfo);
      Matcher heightMatcher = PATTERN_DISPLAY_HEIGHT_MATCH.matcher(windowInfo);
      if (widthMatcher.find() && heightMatcher.find()) {
        int width = Integer.parseInt(widthMatcher.group(1));
        int height = Integer.parseInt(heightMatcher.group(1));
        ScreenResolution resolution = ScreenResolution.create(width, height);
        logger.atInfo().log("Detect device resolution: %s", resolution);
        return resolution;
      } else {
        logger.atWarning().log("Invalid window information: %s", windowInfo);
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SYSTEM_SETTING_PARSE_RESOLUTION_ERROR,
            "Fail to parse device resolution from sys window information: " + windowInfo);
      }
    }
  }

  /**
   * Gets device system time by coordinated universal time (UTC). It's accurate to the second. This
   * method only works with SDK version >= 10, rooted and non-rooted devices.
   *
   * @param serial serial number of the device
   * @return the device system time
   */
  public Instant getSystemTime(String serial) throws MobileHarnessException, InterruptedException {
    long milliSeconds = 0;
    try {
      String output = adb.runShell(serial, ADB_SHELL_GET_SECONDS_UTC).trim();
      milliSeconds = Duration.ofSeconds(Long.parseLong(output)).toMillis();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_SYSTEM_TIME_ERROR, e.getMessage(), e);
    } catch (NumberFormatException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_PARSE_SYSTEM_TIME_ERROR,
          "Failed to parse the number of second: " + e.getMessage(),
          e);
    }
    return Instant.ofEpochMilli(milliSeconds);
  }

  /**
   * Gets device system time by coordinated universal time (UTC). It's accurate to the milliseconds.
   *
   * @param serial serial number of the device
   * @return the device system time
   */
  @SuppressWarnings("GoodTime") // TODO: legacy API requires a long.
  public long getSystemTimeMillis(String serial)
      throws MobileHarnessException, InterruptedException {
    String output = getSystemEpochTime(serial);
    try {
      return (long) (Double.parseDouble(output) * 1000L);
    } catch (NumberFormatException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_PARSE_SYSTEM_TIME_ERROR,
          "Failed to parse the number of millisecond: " + e.getMessage(),
          e);
    }
  }

  /**
   * Gets device system time by coordinated universal time (UTC). It's accurate to the microseconds.
   *
   * @param serial serial number of the device
   * @return the device system time
   */
  @SuppressWarnings("GoodTime") // TODO: legacy API requires a long.
  public long getSystemTimeMicros(String serial)
      throws MobileHarnessException, InterruptedException {
    String output = getSystemEpochTime(serial);
    try {
      return (long) (Double.parseDouble(output) * 1000000L);
    } catch (NumberFormatException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_PARSE_SYSTEM_TIME_ERROR,
          "Failed to parse the number of microsecond: " + e.getMessage(),
          e);
    }
  }

  /**
   * Check if GPS is enabled.
   *
   * <p>Work on API 17 and above.
   *
   * @return true if GPS is enabled, false otherwise.
   */
  public boolean isGpsEnabled(String serial) throws MobileHarnessException, InterruptedException {
    return isLocationProviderAllowed(serial, LocationProvider.GPS);
  }

  /** Check if system wide isolated-storage feature been enabled. */
  public boolean isIsolatedStorageEnabled(String serial)
      throws MobileHarnessException, InterruptedException {
    String propertyValue = "";
    try {
      propertyValue =
          adbUtil.getProperty(serial, ImmutableList.of(ANDROID_PROPERTY_ISOLATED_STORAGE_MODE));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_ISOLATED_STORAGE_MODE_ERROR, e.getMessage(), e);
    }
    return Boolean.parseBoolean(propertyValue);
  }

  /**
   * Check if location service is disabled.
   *
   * <p>Work on API 17 and above.
   *
   * @return true if location service is disabled, false otherwise
   */
  public boolean isLocationServiceDisabled(String serial)
      throws MobileHarnessException, InterruptedException {
    AndroidSettings.Spec spec =
        AndroidSettings.Spec.create(
            AndroidSettings.Command.GET,
            AndroidSettings.NameSpace.SECURE,
            ADB_SHELL_SETTINGS_CHECK_LOCATION_PROVIDERS);
    try {
      return Strings.isNullOrEmpty(
          adbUtil.settings(UtilArgs.builder().setSerial(serial).build(), spec).trim());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_CHECK_LOCATION_SERVICE_ERROR,
          String.format("Failed to check device %s location service", serial),
          e);
    }
  }

  /**
   * Check if network location is enabled.
   *
   * <p>Work on API 17 and above.
   *
   * @return true if network location is enabled, false otherwise.
   */
  public boolean isNetworkLocationEnabled(String serial)
      throws MobileHarnessException, InterruptedException {
    return isLocationProviderAllowed(serial, LocationProvider.NETWORK);
  }

  /**
   * Determines whether device is currently running with a production build.
   *
   * @param serial serial number of the device
   * @return whether the device is running with a production build
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isProductionBuild(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      // The key info from ro.build.display.id may not be reliable for some OEM devices
      // (b/159424014), use ro.build.tags to decide whether a device is production build or not.
      String deviceBuild = adbUtil.getProperty(serial, AndroidProperty.SIGN);
      return !(deviceBuild.contains(TAG_DEV_KEYS) || deviceBuild.contains(TAG_TEST_KEYS));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_BUILD_PROP_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Check if Android setup wizard has already complete on device. Note that there won't be any
   * exception thrown from this function.
   *
   * @param utilArgs arguments wrapper for device serial, sdk version, etc
   * @return if the setup wizard has been skipped
   */
  public boolean isSetupWizardDisabled(UtilArgs utilArgs) {
    AndroidSettings.Spec spec =
        AndroidSettings.Spec.create(
            AndroidSettings.Command.GET,
            AndroidSettings.NameSpace.SECURE,
            ADB_SHELL_SETTINGS_USER_SETUP_COMPLETE);
    try {
      String output = adbUtil.settings(utilArgs, spec);
      if (output != null && Integer.parseInt(output) == 1) {
        return true;
      }
    } catch (NumberFormatException | MobileHarnessException e) {
      logger.atWarning().log("Failed to parse user_setup_complete with output: %s", e.getMessage());
      return false;
    } catch (InterruptedException ie) {
      logger.atWarning().log(
          "Caught interrupted exception, interrupt current thread: %s", ie.getMessage());
      Thread.currentThread().interrupt();
    }

    return false;
  }

  /** Check if Test Harness Mode on device is enabled. */
  public boolean isTestHarnessModeEnabled(String serial)
      throws MobileHarnessException, InterruptedException {
    String propertyValue = "";
    try {
      propertyValue =
          adbUtil.getProperty(serial, ImmutableList.of(ANDROID_PROPERTY_TEST_HARNESS_MODE));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_DEVICE_PROPERTY_ERROR, e.getMessage(), e);
    }
    return "1".equals(propertyValue);
  }

  /**
   * Determines whether device is currently running with a userdebug build signed with dev-keys.
   * This is used to determine when to run activity controller to suppress crash dialog.
   *
   * @param serial serial number of the device
   * @return whether the device is running with a dev-key signed build.
   * @throws MobileHarnessException if fails to execute the commands or timeout
   * @throws InterruptedException if the thread executing the commands is interrupted
   */
  public boolean isUserDebugDevKeySignedBuild(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      String deviceBuild = adbUtil.getProperty(serial, AndroidProperty.BUILD);
      return deviceBuild.contains(TAG_DEV_KEYS) && deviceBuild.contains(TAG_USERDEBUG);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_BUILD_PROP_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Controls device to keep it awake or not while charging. This only works if the device has root
   * access and has already become root.
   *
   * @param serial the serial number of the device
   * @param alwaysAwake {@code true} to keep the device always awake, otherwise {@code false}
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void keepAwake(String serial, boolean alwaysAwake)
      throws MobileHarnessException, InterruptedException {
    AndroidSvc svcArgs =
        AndroidSvc.builder()
            .setCommand(AndroidSvc.Command.POWER)
            .setOtherArgs("stayon " + (alwaysAwake ? "true" : "false"))
            .build();
    try {
      adbUtil.svc(serial, svcArgs);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_KEEP_AWAKE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Resets batterystats info.
   *
   * <p>Work on API 21 and above.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void resetBatteryStats(String serial) throws MobileHarnessException, InterruptedException {
    String expectedOutput = "Battery stats reset";
    String errMsgPrefix = "Failed to reset batterystats due to ";
    batteryStatsCommandWithRetry(serial, "--reset", expectedOutput, errMsgPrefix);
  }

  /**
   * Sets the logical discharge/charge status of the device (L and above only).
   *
   * @param serial serial number of the device
   * @param discharge set to true for logically discharging the device
   */
  public void setBatteryLogicalDischarge(String serial, boolean discharge)
      throws MobileHarnessException, InterruptedException {
    try {
      if (discharge) {
        adbUtil.dumpSys(serial, DumpSysType.BATTERY, "set ac 0");
        adbUtil.dumpSys(serial, DumpSysType.BATTERY, "set usb 0");
        adbUtil.dumpSys(serial, DumpSysType.BATTERY, "set wireless 0");
      } else {
        adbUtil.dumpSys(serial, DumpSysType.BATTERY, "reset");
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_BATTERY_DISCHARGE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Enable/Disable no-auto-reset flag for "dumpsys batterystats" (L and above only).
   *
   * <p>Work on API 21 and above.
   *
   * @param serial serial number of the device
   * @param enable set to true to enable "no-auto-reset" flag
   * @throws MobileHarnessException if failed to set the flag
   */
  public void setBatteryStatsNoAutoReset(String serial, boolean enable)
      throws MobileHarnessException, InterruptedException {
    String expectedOutput = "no-auto-reset";
    String errMsgPrefix = "Failed to set no-auto-reset status of batterystats due to ";
    batteryStatsCommandWithRetry(
        serial, (enable ? "enable" : "disable") + " no-auto-reset", expectedOutput, errMsgPrefix);
  }

  /**
   * Force write current collected batterystats to disk.
   *
   * <p>Work on API 21 and above.
   *
   * @param serial serial number of the device
   * @throws MobileHarnessException if failed to write
   */
  public void setBatteryStatsWrite(String serial)
      throws MobileHarnessException, InterruptedException {
    String expectedOutput = "Battery stats written";
    String errMsgPrefix = "Failed to write batterystats data to disk due to ";
    batteryStatsCommandWithRetry(serial, "--write", expectedOutput, errMsgPrefix);
  }

  /**
   * Sets the default mock location provider using appops. New setting/behavior for mock location
   * which is set through Setting -> Developer options -> Select mock location app.
   *
   * @param serial serial number of the device
   * @param locationAppPackage mock location app package
   * @throws MobileHarnessException if error occurs with when setting the value using appops
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public void setDefaultMockLocationProvider(String serial, String locationAppPackage)
      throws MobileHarnessException, InterruptedException {
    String output = "";
    Exception exception = null;
    try {
      // Doesn't use AndroidAdbUtil#cmd with AndroidService.APPOPS here as it doesn't support on API
      // 21
      String[] cmd =
          new String[] {
            ADB_SHELL_APPOPS, "set", locationAppPackage, "android:mock_location", "allow"
          };
      output = adb.runShellWithRetry(serial, Joiner.on(' ').join(cmd), Duration.ofSeconds(2));
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || !output.isEmpty()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_MOCK_LOCATION_PROVIDER_ERROR,
          String.format(
              "Failed to set default mock location provider. Error: %s",
              exception == null ? output : exception.getMessage()),
          exception);
    }
  }

  /**
   * Enables/Disables dex pre-verification. Note the device needs to become root before setting
   * this. It has no effect and no harm on device without root access.
   *
   * <p>When dex pre-verification is disabled, verification is still done, but at runtime instead of
   * installation time. We do this to allow for the case where the production and test apks both
   * contain the same class. With preverification turned on, this situation will result in a dalvik
   * failure (because verification was done at installation time and the verified expected the app
   * apk to be completely self contained). Since build system will ensure that app and test apk are
   * using the same dependencies this check is superflous in our case.
   *
   * @param serial serial number of the device
   * @param enable whether to enable or disable dex pre-verification
   */
  public void setDexPreVerification(String serial, boolean enable)
      throws MobileHarnessException, InterruptedException {
    try {
      adbUtil.setProperty(
          serial,
          PROPERTY_DEX_PRE_VERIFICATION,
          enable
              ? PROP_VALUE_ENABLE_DEX_PRE_VERIFICATION
              : PROP_VALUE_DISABLE_DEX_PRE_VERIFICATION);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_DEX_PRE_VERIFICATION_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Sets dm-verity checking. Disabling verity only works on API >=22 and enabling verity only works
   * on API >=23. Both require userdebug builds, and the device must already have become root.
   *
   * @param serial serial number of the device
   * @return operation after set dm-verity
   * @throws MobileHarnessException if the command fails or timeout
   * @throws InterruptedException if the thread executing the command is interrupted
   */
  public PostSetDmVerityDeviceOp setDmVerityChecking(String serial, boolean enabled)
      throws MobileHarnessException, InterruptedException {
    String operation = enabled ? ADB_ARG_ENABLE_VERITY : ADB_ARG_DISABLE_VERITY;
    String output = "";
    Exception exception = null;
    try {
      output = adb.runWithRetry(serial, new String[] {operation});
    } catch (MobileHarnessException e) {
      exception = e;
    }
    if (exception != null || output.contains("Failed")) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_VERITY_ERROR,
          String.format(
              "Failed to set dm-verity checking; command \"%s\" failed.\nOutput: %s",
              operation, exception == null ? output : exception.getMessage()),
          exception);
    }
    if (output.contains("Now reboot your device for settings to take effect")
        || output.contains("Reboot the device")) {
      return PostSetDmVerityDeviceOp.REBOOT;
    }
    return PostSetDmVerityDeviceOp.NONE;
  }

  /**
   * Sets Log Level property for DEBUG and VERBOSE tags based on requested filters.
   *
   * @param serial serial number of the device
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
   * @throws MobileHarnessException if some error occurs in executing system commands
   * @throws InterruptedException if current thread is interrupted during this method
   */
  public void setLogLevelProperty(final String serial, @Nullable String filterSpecs)
      throws MobileHarnessException, InterruptedException {
    if (filterSpecs == null) {
      return;
    }
    try {
      // If the user wants us to run include logging for a given tag at the DEBUG/VERBOSE level,
      // lets make sure it is actually logged.
      for (String filterSpec : Splitter.onPattern("\\s+").split(filterSpecs.trim())) {
        List<String> tagAndPriority = Splitter.on(':').splitToList(filterSpec);
        String tag = tagAndPriority.get(0);
        String priority = tagAndPriority.size() > 1 ? tagAndPriority.get(1) : "Warn";
        if (!tag.equals("*")) {
          // Both "tag:D" and "tag:DEBUG" are valid filterSpecs.
          if (priority.startsWith("D")) {
            adbUtil.setProperty(serial, "log.tag." + tag, "DEBUG");
          }
          if (priority.startsWith("V")) {
            adbUtil.setProperty(serial, "log.tag." + tag, "VERBOSE");
          }
        }
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_LOG_LEVEL_PROP_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Set the package legacy storage mode.
   *
   * @param serial device serial number
   * @param packageName package to be configured
   * @param enable true/false mapping to "allow" and "default"
   */
  public void setPackageLegacyStorageMode(String serial, String packageName, boolean enable)
      throws MobileHarnessException, InterruptedException {
    String modeString = enable ? "allow" : "default";
    try {
      String[] command = new String[] {"set", packageName, LEGACY_STORAGE_KEY_NAME, modeString};
      adbUtil.cmd(serial, AndroidService.APPOPS, command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_PACKAGE_STORAGE_MODE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Set the mode for a particular application and operation via AppOps service commands.
   *
   * <p>See more details in "adb shell appops help".
   *
   * @param serial device serial number
   * @param packageName package to be configured
   * @param operation AppOps operation to be set
   * @param mode application operation mode to be set on the package.
   */
  public void setPackageOperationMode(
      String serial, String packageName, String operation, AppOperationMode mode)
      throws MobileHarnessException, InterruptedException {
    try {
      String[] command = new String[] {"set", packageName, operation, mode.getMode()};
      adbUtil.cmd(serial, AndroidService.APPOPS, command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_PACKAGE_OP_MODE_ERROR, e.getMessage(), e);
    }
  }

  /**
   * Sets the device system time by coordinated universal time (UTC) to host system's time. It's
   * accurate to the second. This method only works with SDK version >= 10 and rooted device.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   */
  public void setSystemTimeToHost(String serial, int sdkVersion)
      throws MobileHarnessException, InterruptedException {
    setSystemTime(serial, sdkVersion, clock.instant());
  }

  /**
   * Sets the device system time by coordinated universal time (UTC). It's accurate to the second.
   * This method only works with SDK version >= 10 and rooted device.
   *
   * @param serial serial number of the device
   * @param sdkVersion SDK version of device
   * @param systemTime the time that will be set to the device system time by UTC, regardless of the
   *     time zone of the device
   */
  public void setSystemTime(String serial, int sdkVersion, Instant systemTime)
      throws MobileHarnessException, InterruptedException {
    // For API < 24, date usage as:
    // usage: date [-u] [-r FILE] [-d DATE] [+DISPLAY_FORMAT] [-s SET_FORMAT] [SET]
    // And we need to convert the pass-in UTC time to match the timezone set on device.
    //
    // For API >= 24, date usage as:
    // usage: date [-u] [-r FILE] [-d DATE] [+DISPLAY_FORMAT] [-D SET_FORMAT] [SET]
    // The adb shell converts pass-in UTC time to match the time zone set on device automatically.
    String newSystemTimeString;
    String setSystemTimeArg = sdkVersion < AndroidVersion.NOUGAT.getStartSdkVersion() ? "-s" : "";
    String systemTimeString =
        sdkVersion < AndroidVersion.NOUGAT.getStartSdkVersion()
            ? convertTimeBasedOnDeviceTimeZoneOffset(serial, systemTime)
            : SET_SYSTEM_TIME_FORMAT.format(systemTime);
    try {
      newSystemTimeString =
          adb.runShell(
              serial,
              String.format(
                  ADB_SHELL_TEMPLATE_SET_SYSTEM_TIME, setSystemTimeArg, systemTimeString));
      logger.atInfo().log(
          "Command output for setting system time [%s] on device %s: [%s]",
          systemTimeString, serial, newSystemTimeString);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_SYSTEM_TIME_ERROR, e.getMessage(), e);
    }

    // Below is to verify whether is successful to set system time.
    try {
      // Some devices may return date as " 8" (extra space before 8) instead of "8", "LMT" instead
      // of "UTC", e.g., "Wed Sep  4 05:40:02 LMT 2019"
      newSystemTimeString = newSystemTimeString.replace("  ", " ").replaceFirst("LMT", "UTC");
      Instant newSystemTime =
          SET_SYSTEM_TIME_RETURN_FORMAT.parse(newSystemTimeString, Instant::from);
      long newSystemTimeInSec = newSystemTime.getEpochSecond();
      long systemTimeInSec = systemTime.getEpochSecond();
      boolean failed;
      if (sdkVersion < AndroidVersion.NOUGAT.getStartSdkVersion()) {
        // It could have minor diff on devices sdk < 24
        failed = Math.abs(systemTimeInSec - newSystemTimeInSec) > 2;
      } else {
        failed = newSystemTimeInSec != systemTimeInSec;
      }
      if (failed) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_SYSTEM_SETTING_SET_SYSTEM_TIME_ERROR,
            String.format(
                "Failed to set system time, newSystemTime is: [%s]. (SystemTimeInSec,"
                    + " NewSystemTimeInSec): (%d, %d)",
                newSystemTimeString, systemTimeInSec, newSystemTimeInSec));
      }
    } catch (DateTimeParseException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_PARSE_SYSTEM_TIME_ERROR,
          "Failed to set system time, error: " + e.getMessage());
    }
  }

  /**
   * Wrapper function for executing battery stats command.
   *
   * <p>Work on API 21 and above.
   */
  private void batteryStatsCommandWithRetry(
      String serial, String command, String expectedOutput, String errMsgPrefix)
      throws MobileHarnessException, InterruptedException {
    StringBuilder errMsg = new StringBuilder();
    for (int i = 0; i < BATTERY_STATS_RETRY_TIMES; i++) {
      String msg = adbUtil.dumpSys(serial, DumpSysType.BATTERYSTATS, command);
      errMsg.append(msg);
      if (msg.contains(expectedOutput)) {
        return;
      }
    }
    throw new MobileHarnessException(
        AndroidErrorId.ANDROID_SYSTEM_SETTING_BATTERY_STATE_CMD_ERROR, errMsgPrefix + errMsg);
  }

  private String convertTimeBasedOnDeviceTimeZoneOffset(String serial, Instant systemTime)
      throws MobileHarnessException, InterruptedException {
    String timeZoneOffsetString = "";
    Exception exception = null;
    try {
      timeZoneOffsetString = adb.runShell(serial, ADB_SHELL_GET_TIME_ZONE_OFFSET);
    } catch (MobileHarnessException e) {
      exception = e;
    }
    //  The format of time zone offset of the Android device is such as "+hhmm" or "-hhmm".
    Matcher matcher = PATTERN_TIME_ZONE_OFFSET.matcher(timeZoneOffsetString);
    if (exception != null || !matcher.find()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_TIME_ZONE_OFFSET_ERROR,
          String.format(
              "Failed to get time zone offset for device %s:%n%s",
              serial, exception == null ? timeZoneOffsetString : exception.getMessage()),
          exception);
    }
    int timeZoneOffsetSign = matcher.group(1).equals("+") ? 1 : -1;
    int timeZoneOffsetHour = timeZoneOffsetSign;
    int timeZoneOffsetMinute = timeZoneOffsetSign;
    timeZoneOffsetHour *= Integer.parseInt(matcher.group(2));
    timeZoneOffsetMinute *= Integer.parseInt(matcher.group(3));
    Calendar calendar = Calendar.getInstance(UTC_TIME_ZONE);
    calendar.setTimeInMillis(systemTime.toEpochMilli());
    calendar.add(Calendar.HOUR_OF_DAY, timeZoneOffsetHour);
    calendar.add(Calendar.MINUTE, timeZoneOffsetMinute);
    return SET_SYSTEM_TIME_FORMAT.format(calendar.getTime().toInstant());
  }

  private void disableScreenLockViaSqlite(String serial)
      throws MobileHarnessException, InterruptedException {
    for (String sql : SQL_DISABLE_SCREENLOCK) {
      adbUtil.sqlite(serial, ANDROID_LOCKSETTINGS_DB_PATH, sql);
    }
  }

  /** String output of system env $EPOCHREALTIME on device. */
  private String getSystemEpochTime(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      return adb.runShell(serial, ADB_SHELL_GET_EPOCH_TIME).trim();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_GET_EPOCH_SYSTEM_TIME_ERROR, e.getMessage(), e);
    }
  }

  /** Helper function to check if location provider is allowed. */
  private boolean isLocationProviderAllowed(String serial, LocationProvider locationProvider)
      throws MobileHarnessException, InterruptedException {
    AndroidSettings.Spec spec =
        AndroidSettings.Spec.create(
            AndroidSettings.Command.GET,
            AndroidSettings.NameSpace.SECURE,
            ADB_SHELL_SETTINGS_CHECK_LOCATION_PROVIDERS);
    try {
      String output = adbUtil.settings(UtilArgs.builder().setSerial(serial).build(), spec);
      return Splitter.on(',')
          .trimResults()
          .omitEmptyStrings()
          .splitToList(output)
          .contains(Ascii.toLowerCase(locationProvider.name()));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_CHECK_LOCATION_SERVICE_ERROR,
          String.format("Failed to check device %s location allowed providers", serial),
          e);
    }
  }

  private boolean isScreenLockDisabled(String serial)
      throws MobileHarnessException, InterruptedException {
    try {
      return "1"
          .equals(
              adbUtil.sqlite(
                  serial, ANDROID_LOCKSETTINGS_DB_PATH, SQL_GET_LOCKSCREEN_DISABLED_VALUE));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_SYSTEM_SETTING_SQLITE_CMD_ERROR, e.getMessage(), e);
    }
  }
}
