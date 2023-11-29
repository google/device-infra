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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static com.google.devtools.mobileharness.shared.util.shell.ShellUtils.shellEscape;
import static java.util.Comparator.naturalOrder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.file.AndroidFileUtil;
import com.google.devtools.mobileharness.platform.android.lightning.systemsetting.SystemSettingManager;
import com.google.devtools.mobileharness.platform.android.lightning.systemstate.SystemStateManager;
import com.google.devtools.mobileharness.platform.android.packagemanager.AndroidPackageManagerUtil;
import com.google.devtools.mobileharness.platform.android.packagemanager.PackageType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidProperty;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidVersion;
import com.google.devtools.mobileharness.platform.android.systemsetting.AndroidSystemSettingUtil;
import com.google.devtools.mobileharness.platform.android.systemsetting.PostSetDmVerityDeviceOp;
import com.google.devtools.mobileharness.platform.android.systemspec.AndroidSystemSpecUtil;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryException;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryStrategy;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryingCallable;
import com.google.devtools.mobileharness.shared.util.concurrent.retry.RetryingCallable.ThrowStrategy;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.api.validator.AndroidDeviceSettingsDecoratorValidator;
import com.google.wireless.qa.mobileharness.shared.constant.ErrorCode;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDeviceSettingsDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidDeviceSettingsDecoratorSpec.Reboot;
import com.google.wireless.qa.mobileharness.shared.util.DeviceUtil;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Driver decorator for setting android device by following in the setting in {@code
 * mobileharness.shared.spec.AndroidDeviceSettingsDecoratorSpec}
 */
@DecoratorAnnotation(
    help = "For setting device specs. See AndroidDeviceSettingsDecoratorSpec for more details.")
public class AndroidDeviceSettingsDecorator extends BaseDecorator
    implements SpecConfigable<AndroidDeviceSettingsDecoratorSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Prefix of all persist properties on Android devices. */
  private static final String PERSIST_PROP_PREFIX = "persist.";

  /** Wake lock file path. */
  private static final String WAKE_LOCK_FILE_ON_DEVICE = "/sys/power/wake_lock";

  /** Drop cache file path. */
  private static final String DROP_CACHE_FILE_ON_DEVICE = "/proc/sys/vm/drop_caches";

  private static final String GENERAL_CPU_TEMP_FILE_ON_DEVICE =
      "/sys/class/thermal/thermal_zone0/temp";

  /** GPU configuration files. */
  private static final String GPU_CLOCK_SPEED_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies";

  private static final String GPU_CLOCK_CONFIG_FILE_ON_DEVICE = "/sys/class/kgsl/kgsl-3d0/gpuclk";

  private static final String GPU_MAX_FREQ_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";

  private static final String GPU_MIN_FREQ_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq";

  private static final String GPU_MAX_PWRLEVEL_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/max_pwrlevel";

  private static final String GPU_MIN_PWRLEVEL_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/min_pwrlevel";

  private static final String GPU_BUS_IDLE_TIMER_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/idle_timer";

  private static final String GPU_BUS_SPLIT_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/bus_split";

  private static final String GPU_BUS_FORCE_CLK_ON_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/force_clk_on";

  private static final String GPU_BUS_FORCE_BUS_ON_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/force_bus_on";

  private static final String GPU_BUS_FORCE_RAIL_ON_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/force_rail_on";

  private static final String[] GPU_BUS_CONFIG_FILES_ON_DEVICE = {
    GPU_BUS_FORCE_BUS_ON_CONFIG_FILE_ON_DEVICE,
    GPU_BUS_FORCE_RAIL_ON_CONFIG_FILE_ON_DEVICE,
    GPU_BUS_FORCE_CLK_ON_CONFIG_FILE_ON_DEVICE
  };

  private static final String GPU_FREQ_GOVERNOR_CONFIG_FILE_ON_DEVICE =
      "/sys/class/kgsl/kgsl-3d0/devfreq/governor";

  /** List of known performance interfering vendor services. */
  private static final String[] PERFORMANCE_INTERFERING_SERVICES = {
    "thermal-engine", "thermald", "perfd", "mpdecision"
  };

  /** List of device types that don't support interactive CPU scaling governor mode. */
  private static final ImmutableList<String> NON_INTERACTIVE_CPU_GOVERNOR =
      ImmutableList.of("beast", "atom", "deadpool");

  /** Timeout for waiting for CPU folders and files to be created after reboot. */
  private static final long WAIT_FOR_CPU_FILES_TIMEOUT_SEC = Duration.ofMinutes(2).getSeconds();

  /** Duration of waiting for reboot. */
  private static final Duration WAIT_FOR_REBOOT = Duration.ofMinutes(3);

  /**
   * Max offset allowed between the host and the device after attempting to synchronize their
   * clocks.
   */
  private static final Duration HOST_DEVICE_MAX_OFFSET = Duration.ofSeconds(5);

  /** Temperature checking, value is in celsius. */
  private double maxAllowedTemperature = 37.0;

  private static final Duration TEMPERATURE_CHECK_RETRY_DELAY = Duration.ofMinutes(1);
  private Duration acceptableTemperatureWaitTimeout;

  private static final Splitter SPLITTER_ON_SPACES = Splitter.on(' ').omitEmptyStrings();

  /** ADB shell command to clean the file local.prop. */
  private static final String ADB_SHELL_CLEAN_LOCAL_PROP = "echo > /data/local.prop";

  /** ADB shell command to chmod the file local.prop. */
  private static final String ADB_SHELL_CHMOD_LOCAL_PROP = "chmod 644 /data/local.prop";

  /** ADB shell command output when file doesn't exist. */
  private static final String NO_FILE_ERR_MSG = "No such file or directory";

  /** ADB shell command output when package doesn't exist. */
  private static final String UNKNOWN_PACKAGE_ERR_MSG = "Unknown package";

  /** ADB shell command output when stop service failed. */
  private static final Pattern UNABLE_TO_STOP_SERVICE_MSG_PATTERN =
      Pattern.compile("Unable to stop service (?<service>\\S+)\\s+See dmesg for error reason");

  /** A List of commands. */
  private static class Commands {

    private static final char COMMAND_TOKEN_SPLITER = ' ';

    private static final Joiner JOINER = Joiner.on(COMMAND_TOKEN_SPLITER).skipNulls();

    protected final List<String> commands;

    private final String prefix;

    /** Creates a command list, whose items all start with {@code prefix}. */
    public Commands(Object... prefix) {
      this(new ArrayList<>(), prefix);
    }

    public Commands(List<String> commands, Object... prefix) {
      this.commands = commands;
      this.prefix = buildPrefix(prefix);
    }

    private String buildPrefix(Object... prefix) {
      String prefixStr = buildCommand(prefix);
      if (prefixStr.isEmpty()) {
        return prefixStr;
      }
      return prefixStr + COMMAND_TOKEN_SPLITER;
    }

    /**
     * Add a new command to command list.
     *
     * @param tokens Tokens of the added command
     * @return this object so callers could add multiple commands easily
     */
    @CanIgnoreReturnValue
    public Commands add(Object... tokens) {
      commands.add(this.prefix + buildCommand(tokens));
      return this;
    }

    @CanIgnoreReturnValue
    public Commands add(List<String> tokens) {
      commands.add(this.prefix + buildCommand(tokens));
      return this;
    }

    /**
     * Takes varargs of command strings as input to build the shell command.
     *
     * <p>This method would not produce correct shell command if input is a {@link List}.
     */
    public static String buildCommand(Object... tokens) {
      return JOINER.join(tokens).trim();
    }

    /** Takes list of command strings as input to build the shell command. */
    public static String buildCommand(List<String> tokens) {
      return JOINER.join(tokens).trim();
    }

    /** Returns a list of all added commands. */
    public List<String> get() {
      return commands;
    }

    /** Returns true if no command is added. */
    public boolean isEmpty() {
      return commands.isEmpty();
    }
  }

  /**
   * Android device shell settings commands which starts with "settings put namespace args". See
   * more details by "adb shell settings --help".
   */
  private static class Settings {

    final Commands system = new Commands("settings", "put", "system");
    final Commands secure = new Commands("settings", "put", "secure");
    final Commands global = new Commands("settings", "put", "global");

    public boolean isEmpty() {
      return system.isEmpty() && secure.isEmpty() && global.isEmpty();
    }
  }

  /** Commands with a set of helping fields for easier build. */
  private static class CommonCommands extends Commands {

    final Commands gservice;

    final Commands svc;

    public CommonCommands() {
      super();
      gservice =
          new Commands(
              commands,
              "am",
              "broadcast",
              "-a",
              "com.google.gservices.intent.action.GSERVICES_OVERRIDE",
              "-e");

      svc = new Commands(commands, "svc");
    }
  }

  /** Reads the contents of the device config file. */
  @VisibleForTesting
  String readConfigFileContents(TestInfo testInfo, String filePath)
      throws MobileHarnessException, InterruptedException {
    return runCommand(testInfo, "cat " + shellEscape(filePath));
  }

  /** Reads and logs the contents of the device config file. */
  String readAndLogConfigFileContents(TestInfo testInfo, String filePath)
      throws MobileHarnessException, InterruptedException {
    String fileContents = readConfigFileContents(testInfo, filePath);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Read device config file: %s = '%s'", filePath, fileContents);
    return fileContents;
  }

  /**
   * Reads a config file on the device with contents in the form of a space-delimited list of
   * integral numbers, and returns the largest one.
   */
  @VisibleForTesting
  long readMaxValueFromConfigFile(TestInfo testInfo, String filePath)
      throws MobileHarnessException, InterruptedException {
    return stream(SPLITTER_ON_SPACES.split(readAndLogConfigFileContents(testInfo, filePath)))
        .map(Long::parseLong)
        .max(naturalOrder())
        .orElseThrow();
  }

  @VisibleForTesting
  double getMaxAllowedTemperature() {
    return maxAllowedTemperature;
  }

  /** Gets the number of CPUs on the device. */
  @VisibleForTesting
  int getNumberOfCpus() throws MobileHarnessException, InterruptedException {
    try {
      return androidSystemSpecUtil.getNumberOfCpus(getDevice().getDeviceId());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_GET_CPU_NUM_ERROR, e.getMessage());
    }
  }

  /** All settings commands. */
  private final Settings settings = new Settings();

  /** All setprop commands. Key is the name of prop, value is the prop value. */
  private final Map<String, String> props = new HashMap<>();

  /** Commands that are needed to run after {@link #settings}. */
  private final CommonCommands commandsAfterSettings = new CommonCommands();

  /** Commands that are needed to run before {@link #settings}. */
  private final CommonCommands commandsBeforeSettings = new CommonCommands();

  /** Commands that are needed to run after the test finishes */
  private final CommonCommands commandsOnTestEnd = new CommonCommands();

  /**
   * The device needs reboot to make some settings into effect or before making some settings. If
   * this is {@code true}, reboots the device in {@link #run}.
   *
   * <p>This variable may be set to {@code true} in {@link #parseSpec} and/or {@link #changeProps}.
   *
   * <p>Use {@link #setNeedRebootForSetting(TestInfo, String)} to set to true.
   */
  private boolean needRebootForSetting;

  /**
   * The device needs to reboot at then end of test run to restore those modified CPU/GPU locking
   * settings to avoid damaging target devices.
   *
   * <p>Use {@link #setNeedRebootAfterTest(TestInfo, String)} to set to true.
   */
  private boolean needRebootAfterTest;

  private boolean dataLocalPropertyFileSet;

  private final Adb adb;
  private final AndroidAdbUtil androidAdbUtil;
  private final AndroidFileUtil androidFileUtil;
  private final AndroidPackageManagerUtil androidPackageManagerUtil;
  private final AndroidSystemSettingUtil androidSystemSettingUtil;
  private final AndroidSystemSpecUtil androidSystemSpecUtil;
  private final SystemSettingManager systemSettingManager;
  private final SystemStateManager systemStateManager;

  @Inject
  AndroidDeviceSettingsDecorator(
      Driver decoratedDriver,
      TestInfo testInfo,
      Adb adb,
      AndroidAdbUtil androidAdbUtil,
      AndroidFileUtil androidFileUtil,
      AndroidPackageManagerUtil androidPackageManagerUtil,
      AndroidSystemSettingUtil androidSystemSettingUtil,
      AndroidSystemSpecUtil androidSystemSpecUtil,
      SystemSettingManager systemSettingManager,
      SystemStateManager systemStateManager) {
    super(decoratedDriver, testInfo);
    this.adb = adb;
    this.androidAdbUtil = androidAdbUtil;
    this.androidFileUtil = androidFileUtil;
    this.androidPackageManagerUtil = androidPackageManagerUtil;
    this.androidSystemSettingUtil = androidSystemSettingUtil;
    this.androidSystemSpecUtil = androidSystemSpecUtil;
    this.systemSettingManager = systemSettingManager;
    this.systemStateManager = systemStateManager;
  }

  /** Converts boolean {@code v} to a 0/1 value. */
  private static int toInt(boolean v) {
    return v ? 1 : 0;
  }

  @VisibleForTesting
  protected static String cpuDeviceConfigPath(long cpuId, String filePath) {
    return Path.of("/sys/devices/system/cpu", "cpu" + cpuId, filePath).toString();
  }

  /**
   * Parses {@code spec} into a list of adb commands. For example: For {@code spec}:
   *
   * <pre>
   * <code>
   *   screen_brightness: 150
   *   screen_adaptive_brightness: false
   *   screen_timeout_sec: 30
   *   enable_nfc: false
   * </code>
   * </pre>
   *
   * <p>The generated commands list is:
   *
   * <pre>
   * <code>
   *   // settings commands.
   *   settings put system screen_brightness 150
   *   settings put system screen_brightness_mode 0
   *   settings put system screen_off_timeout 30000
   *   // commands afters settings.
   *   svc nfc disable
   *   am start -a
   *   com.google.android.gsf.action.SET_USE_LOCATION_FOR_SERVICES --ez disable true
   * </code>
   * </pre>
   */
  @VisibleForTesting
  protected void parseSpec(AndroidDeviceSettingsDecoratorSpec spec, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    AndroidDeviceSettingsDecoratorValidator.validateSpec(spec);
    // screen_brightness
    if (spec.hasScreenBrightness()) {
      settings.system.add("screen_brightness", spec.getScreenBrightness());
    }

    // screen_adaptive_brightness
    if (spec.hasScreenAdaptiveBrightness()) {
      settings.system.add("screen_brightness_mode", toInt(spec.getScreenAdaptiveBrightness()));
    }

    // screen_timeout_sec
    if (spec.hasScreenTimeoutSec()) {
      settings.system.add(
          "screen_off_timeout", Duration.ofSeconds(spec.getScreenTimeoutSec()).toMillis());
    }

    // notification_led
    if (spec.hasNotificationLed()) {
      settings.system.add("notification_light_pulse", toInt(spec.getNotificationLed()));
    }

    // enable_auto_rotate
    if (spec.hasEnableAutoRotate()) {
      settings.system.add("accelerometer_rotation", toInt(spec.getEnableAutoRotate()));
    }

    // enable_sound_effects
    if (spec.hasEnableSoundEffects()) {
      settings.system.add("sound_effects_enabled", toInt(spec.getEnableSoundEffects()));
    }

    // enable_volta
    if (spec.hasEnableVolta()) {
      commandsAfterSettings.add(
          "pm", spec.getEnableVolta() ? "enable" : "disable-user", "com.google.android.volta");
    }

    // enable_doze
    if (spec.hasEnableDoze()) {
      commandsAfterSettings.add(
          "dumpsys", "deviceidle", spec.getEnableDoze() ? "enable" : "disable");
    }

    // enable_nfc
    if (spec.hasEnableNfc()) {
      commandsAfterSettings.svc.add("nfc", spec.getEnableNfc() ? "enable" : "disable");
    }

    // enable_screen_ambient_mode
    if (spec.hasEnableScreenAmbientMode()) {
      settings.secure.add("doze_enabled", toInt(spec.getEnableScreenAmbientMode()));
    }

    // enable_location_services, reuse command from use_location_for_services
    // Old setting would trigger a pop-up window for user prompt.
    //   am start -a com.google.android.gsf.action.SET_USE_LOCATION_FOR_SERVICE --ez disable 1/0
    //
    // TODO Find another way to enable location services without user prompt
    if (spec.hasEnableLocationServices()) {
      if (getDeviceSdkVersion() >= AndroidVersion.ANDROID_10.getStartSdkVersion()) {
        commandsBeforeSettings.add(
            "settings",
            "put",
            "secure",
            "location_mode",
            String.format("%d", spec.getEnableLocationServices() ? 1 : 0));
      } else {
        commandsBeforeSettings.add(
            "content",
            "insert",
            "--uri",
            "content://com.google.settings/partner",
            "--bind",
            "name:s:use_location_for_services",
            "--bind",
            String.format("value:s:%d", spec.getEnableLocationServices() ? 1 : 0));
      }
    }

    // enable_cast_broadcast
    if (spec.hasEnableCastBroadcast()) {
      commandsAfterSettings.add(
          "am broadcast -a com.google.android.gms.phenotype.FLAG_OVERRIDE",
          "--es package com.google.android.gms.cast",
          "--es user \\*",
          "--esa flags mdns_device_scanner:is_enabled",
          "--esa values " + (spec.getEnableCastBroadcast() ? "true" : "false"),
          "--esa types boolean",
          "com.google.android.gms");
    }

    // enable_icing_download
    if (spec.hasEnableIcingDownload()) {
      commandsAfterSettings.gservice.add(
          "gms_icing_mdd_migrate_from_idd", toInt(spec.getEnableIcingDownload()));
    }

    // enable_location_collection
    if (spec.hasEnableLocationCollection()) {
      commandsAfterSettings.gservice.add(
          "location:collection_enabled", toInt(spec.getEnableLocationCollection()));
    }

    // enable_clock_dump_info
    // This setting is reset after reboot. So make sure it is set after rebooting.
    if (spec.hasEnableClockDumpInfo()) {
      commandsAfterSettings.add(
          "echo", spec.getEnableClockDumpInfo() ? "1" : "0", ">", "/d/clk/debug_suspend");
    }

    // enable_wifi.
    if (spec.hasEnableWifi()) {
      commandsAfterSettings.svc.add("wifi", spec.getEnableWifi() ? "enable" : "disable");
    }

    // enable_location_gps
    if (spec.hasEnableLocationGps()) {
      settings.secure.add(
          "location_providers_allowed", spec.getEnableLocationGps() ? "+gps" : "-gps");
    }

    // enable_location_network
    if (spec.hasEnableLocationNetwork()) {
      // Enable 'network_location_opt_in' before 'location_providers_allowed +network'. Without this
      // setting, a user prompt window will pop up and asking 'Improve location accuracy?'
      // See b/65843341 for more information.
      commandsBeforeSettings.add(
          "content",
          "insert",
          "--uri",
          "content://com.google.settings/partner",
          "--bind",
          "name:s:network_location_opt_in",
          "--bind",
          String.format("value:s:%d", spec.getEnableLocationNetwork() ? 1 : 0));
      settings.secure.add(
          "location_providers_allowed", spec.getEnableLocationNetwork() ? "+network" : "-network");
    }

    // enable_heads_up_notifications
    if (spec.hasEnableHeadsUpNotifications()) {
      settings.global.add(
          "heads_up_notifications_enabled", toInt(spec.getEnableHeadsUpNotifications()));
    }

    // enable_screen_saver
    if (spec.hasEnableScreenSaver()) {
      settings.secure.add("screensaver_enabled", toInt(spec.getEnableScreenSaver()));
    }

    // enable_playstore
    if (spec.hasEnablePlaystore()) {
      commandsAfterSettings.add(
          "pm", spec.getEnablePlaystore() ? "enable" : "disable-user", "com.android.vending");
    }

    // enable_audio
    if (spec.hasEnableAudio()) {
      props.put("ro.audio.silent", spec.getEnableAudio() ? "0" : "1");
    }

    // disable_calling
    if (spec.hasDisableCalling()) {
      props.put("ro.telephony.disable-call", spec.getDisableCalling() ? "true" : "false");
    }

    // disable_package_in_test
    for (String packageName : spec.getDisablePackageInTestList()) {
      commandsAfterSettings.add("pm", "disable-user", packageName);
      commandsOnTestEnd.add("pm", "enable", packageName);
    }

    // enable_package
    for (String packageName : spec.getEnablePackageList()) {
      commandsAfterSettings.add("pm", "enable", packageName);
    }

    // clear_package_cache
    for (String packageName : spec.getClearPackageCacheList()) {
      commandsAfterSettings.add("pm", "clear", packageName);
    }

    // gtalk_wifi_max_heartbeat_ping_interval_sec
    if (spec.hasGtalkWifiMaxHeartbeatPingIntervalSec()) {
      commandsAfterSettings.gservice.add(
          "gtalk_wifi_max_heartbeat_ping_interval_ms",
          Duration.ofSeconds(spec.getGtalkWifiMaxHeartbeatPingIntervalSec()).toMillis());
    }

    // gtalk_nosync_heartbeat_ping_interval_sec
    if (spec.hasGtalkNosyncHeartbeatPingIntervalSec()) {
      commandsAfterSettings.gservice.add(
          "gtalk_nosync_heartbeat_ping_interval_ms",
          Duration.ofSeconds(spec.getGtalkNosyncHeartbeatPingIntervalSec()).toMillis());
    }

    // enable_adaptive_wifi_heartbeat
    if (spec.hasEnableAdaptiveWifiHeartbeat()) {
      commandsAfterSettings.gservice.add(
          "enable_adaptive_wifi_heartbeat", spec.getEnableAdaptiveWifiHeartbeat());
    }

    // animator_duration_scale
    if (spec.hasAnimatorDurationScale()) {
      settings.global.add("animator_duration_scale", spec.getAnimatorDurationScale());
    }

    // transition_animation_scale
    if (spec.hasTransitionAnimationScale()) {
      settings.global.add("transition_animation_scale", spec.getTransitionAnimationScale());
    }

    // window_animation_scale
    if (spec.hasWindowAnimationScale()) {
      settings.global.add("window_animation_scale", spec.getWindowAnimationScale());
    }

    // bluetooth_on
    if (spec.hasEnableBluetooth()) {
      if (getDeviceSdkVersion() >= AndroidVersion.MARSHMALLOW.getStartSdkVersion()) {
        commandsAfterSettings.svc.add(
            "bluetooth", spec.getEnableBluetooth() ? "enable" : "disable");
      } else {
        settings.global.add("bluetooth_on", toInt(spec.getEnableBluetooth()));
      }
    }

    // auto_time
    if (spec.hasEnableAutoTime()) {
      // Changing the value will trigger revertToNitzTime.
      // If the date time on device is incorrect,
      // before setting auto_time to be 1, set it to be 0 first. (b/37777891)
      if (spec.getEnableAutoTime()) {
        settings.global.add("auto_time", 0);
      }
      settings.global.add("auto_time", toInt(spec.getEnableAutoTime()));
    }

    // auto timezone
    if (spec.hasEnableAutoTimezone()) {
      settings.global.add("auto_time_zone", toInt(spec.getEnableAutoTimezone()));
    }

    // 12-24h time format
    if (spec.hasEnable24HTimeFormat()) {
      settings.system.add("time_12_24", spec.getEnable24HTimeFormat() ? "24" : "12");
    }

    if (spec.hasTimezone()) {
      props.put("persist.sys.timezone", spec.getTimezone());
    }

    // Prevent system suspend
    if (spec.hasPreventSystemSuspend() && spec.getPreventSystemSuspend()) {
      commandsAfterSettings.add("echo", "temporary", ">", WAKE_LOCK_FILE_ON_DEVICE);
    }

    // Drop kernel caches
    if (spec.hasDropKernelCaches() && spec.getDropKernelCaches()) {
      commandsAfterSettings.add("sync");
      commandsAfterSettings.add("echo", "3", ">", DROP_CACHE_FILE_ON_DEVICE);
    }

    // Wait for temperature become acceptable
    if (spec.hasMaxAllowedTemperature() && spec.getMaxAllowedTemperature() > 0.0f) {
      maxAllowedTemperature = spec.getMaxAllowedTemperature();
    }
    acceptableTemperatureWaitTimeout =
        Duration.ofMinutes(spec.getAcceptableTemperatureWaitTimeoutMinute());

    // enable_auto_update
    if (spec.hasEnableAutoUpdate()) {
      commandsAfterSettings.gservice.add(
          "finsky.play_services_auto_update_enabled", spec.getEnableAutoUpdate());
      commandsAfterSettings.gservice.add(
          "finsky.setup_wizard_additional_account_vpa_enable", spec.getEnableAutoUpdate());
      commandsAfterSettings.gservice.add(
          "finsky.daily_hygiene_schedule_unauthenticated", spec.getEnableAutoUpdate());
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Set the finsky.play_services_auto_update_enabled,"
                  + " finsky.setup_wizard_additional_account_vpa_enable, and"
                  + " finsky.daily_hygiene_schedule_unauthenticated to %s",
              spec.getEnableAutoUpdate());
    }

    // enable_gcm_service
    if (spec.hasEnableGcmService()) {
      commandsAfterSettings.gservice.add("gcm_service_enable", spec.getEnableGcmService() ? 1 : -1);
    }

    // gms_max_reconnect_delay_sec
    if (spec.hasGmsMaxReconnectDelaySec()) {
      commandsAfterSettings.gservice.add(
          "gms_max_reconnect_delay",
          Duration.ofSeconds(spec.getGmsMaxReconnectDelaySec()).toMillis());
    }

    // gms_max_reconnect_delay_long_sec
    if (spec.hasGmsMinReconnectDelayLongSec()) {
      commandsAfterSettings.gservice.add(
          "gms_min_reconnect_delay_long",
          Duration.ofSeconds(spec.getGmsMinReconnectDelayLongSec()).toMillis());
    }

    // gms_max_reconnect_delay_short_sec
    if (spec.hasGmsMinReconnectDelayShortSec()) {
      commandsAfterSettings.gservice.add(
          "gms_min_reconnect_delay_short",
          Duration.ofSeconds(spec.getGmsMinReconnectDelayShortSec()).toMillis());
    }

    // enable_gestures
    if (spec.hasEnableGestures()) {
      if (!spec.getEnableGestures()) {
        settings.secure.add("doze_pulse_on_pick_up", 0);
        settings.secure.add("doze_pulse_on_double_tap", 0);
        settings.secure.add("camera_double_tap_power_gesture_disabled", 1);
        settings.secure.add("camera_double_twist_to_flip_enabled", 0);
        settings.secure.add("assist_gesture_enabled", 0);
        settings.secure.add("assist_gesture_silence_alerts_enabled", 0);
        settings.secure.add("assist_gesture_wake_enabled", 0);
        settings.secure.add("system_navigation_keys_enabled", 0);
        settings.secure.add("camera_lift_trigger_enabled", 0);
      } else {
        settings.secure.add("doze_pulse_on_pick_up", 1);
        settings.secure.add("doze_pulse_on_double_tap", 1);
        settings.secure.add("camera_double_tap_power_gesture_disabled", 0);
        settings.secure.add("camera_double_twist_to_flip_enabled", 1);
        settings.secure.add("assist_gesture_enabled", 1);
        settings.secure.add("assist_gesture_silence_alerts_enabled", 1);
        settings.secure.add("assist_gesture_wake_enabled", 1);
        settings.secure.add("system_navigation_keys_enabled", 1);
        settings.secure.add("camera_lift_trigger_enabled", 1);
      }
    }

    // enable_net_scheduler
    if (spec.hasEnableNetScheduler()) {
      commandsAfterSettings.gservice.add("nts.scheduler_active", spec.getEnableNetScheduler());
    }

    // use_location_for_services
    if (spec.hasUseLocationForServices()) {
      commandsBeforeSettings.add(
          "content",
          "insert",
          "--uri",
          "content://com.google.settings/partner",
          "--bind",
          "name:s:use_location_for_services",
          "--bind",
          String.format("value:s:%d", spec.getUseLocationForServices() ? 1 : 0));
    }

    // location_denylist
    if (spec.hasLocationDenylist()) {
      commandsAfterSettings.gservice.add(
          "secure:masterLocationPackagePrefixBlacklist", spec.getLocationDenylist());
    }

    // location_allowlist
    if (spec.hasLocationAllowlist()) {
      commandsAfterSettings.gservice.add(
          "secure:masterLocationPackagePrefixWhitelist", spec.getLocationAllowlist());
    }

    // enable_instant_app
    if (spec.hasEnableInstantApp()) {
      commandsAfterSettings.gservice.add(
          "gms:wh:enable_westinghouse_support", spec.getEnableInstantApp());
    }

    // wtf_is_fatal
    if (spec.hasWtfIsFatal()) {
      settings.global.add("wtf_is_fatal", spec.getWtfIsFatal());
    }

    // anr_show_background
    if (spec.hasAnrShowBackground()) {
      settings.global.add("anr_show_background", spec.getAnrShowBackground());
    }

    // chimera_denylist
    if (spec.hasChimeraDenylist()) {
      commandsAfterSettings.gservice.add(
          "gms:chimera:dev_module_denylist", spec.getChimeraDenylist());
    }

    // enable_full_battery_stats_history
    if (spec.hasEnableFullBatteryStatsHistory()) {
      if (spec.getEnableFullBatteryStatsHistory()) {
        commandsAfterSettings.add("dumpsys", "batterystats", "--enable", "full-history");
      }
    }

    // enable_wake_gesture
    if (spec.hasEnableWakeGesture()) {
      settings.secure.add("wake_gesture_enabled", spec.getEnableWakeGesture() ? 1 : 0);
    }

    // enable_always_on_display
    if (spec.hasEnableAlwaysOnDisplay()) {
      settings.secure.add("doze_always_on", spec.getEnableAlwaysOnDisplay() ? 1 : 0);
    }

    // enable_location_compact_logging
    if (spec.hasEnableLocationCompactLogging()) {
      commandsAfterSettings.gservice.add(
          "location:compact_log_enabled", spec.getEnableLocationCompactLogging());
      commandsAfterSettings.add(
          "am broadcast -a com.google.android.gms.phenotype.FLAG_OVERRIDE",
          " --es package com.google.android.location",
          " --es user \\*",
          " --esa flags compact_log_enabled",
          " --esa values true",
          "--esa types boolean",
          " com.google.android.gms");
    }

    // enable_camera_hdr
    if (spec.hasEnableCameraHdr()) {
      props.put("camera.optbar.hdr", spec.getEnableCameraHdr() ? "true" : "false");
    }

    // enable_magic_tether
    if (spec.hasEnableMagicTether()) {
      commandsAfterSettings.gservice.add("gms:magictether:enable", spec.getEnableMagicTether());
    }

    // enable_bypass_gms_phenotype_experiments
    if (spec.hasEnableBypassGmsPhenotypeExperiments()) {
      commandsAfterSettings.gservice.add(
          "gms:phenotype:phenotype_flag:debug_bypass_phenotype",
          spec.getEnableBypassGmsPhenotypeExperiments());
    }

    if (spec.hasEnableVerboseAdbd()) {
      props.put("persist.adb.trace_mask", "1");
      setNeedRebootForSetting(testInfo, "Device needs reboot before enabling verbose adbd.");
    }

    if (spec.hasEnableHiddenApi()) {
      settings.global.add(
          "hidden_api_denylist_exemptions", spec.getEnableHiddenApi() ? "\\*" : "null");
    }

    if (spec.hasEnableFuse()) {
      props.put(
          "persist.sys.fflag.override.settings_fuse", spec.getEnableFuse() ? "true" : "false");
    }

    // This line MUST remains as the LAST ONE in this method.
    parsePerformanceSpec(spec, testInfo);
  }

  private void parsePerformanceSpec(AndroidDeviceSettingsDecoratorSpec spec, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    // DO NOT CHANGE THE ORDER OF THE 3 IF BLOCKS BELOW.
    // b/69559815: Performance interfering services need to be killed before locking CPU/GPU.

    boolean stopInterferingServices = false;
    if (spec.hasStopInterferingServices() && spec.getStopInterferingServices()) {
      stopInterferingServices = true;
    } else if (spec.hasEnforceCpuStatus() && spec.getEnforceCpuStatus()) {
      stopInterferingServices = true;
    }

    if (stopInterferingServices) {
      if (DeviceUtil.inSharedLab()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_SETTING_NOT_SUPPORT_IN_SHARED_LAB,
            "stop_interfering_services is not supported in shared lab. See "
                + "go/m&m-performance-testing.");
      }
      List<String> serviceToStop = ImmutableList.copyOf(PERFORMANCE_INTERFERING_SERVICES);
      if (!spec.getInterferingServiceToStopAfterSettingsList().isEmpty()) {
        serviceToStop = spec.getInterferingServiceToStopAfterSettingsList();
      }
      for (String service : serviceToStop) {
        commandsAfterSettings.add("stop", service);
      }
      setNeedRebootAfterTest(
          testInfo, "Device needs reboot after test to restore interfering services");
    }

    if (spec.hasEnforceCpuStatus() && spec.getEnforceCpuStatus()) {
      if (DeviceUtil.inSharedLab()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_SETTING_NOT_SUPPORT_IN_SHARED_LAB,
            "enforce_cpu_status is not supported in shared lab. See go/m&m-performance-testing.");
      }
      int numCpus = getNumberOfCpus();
      Map<Long, Long> freqs = getAllCpuRuntimeFreqsSpecified(spec);
      for (int i = 0; i < numCpus; i++) {
        addCommandsForCpuSettings(freqs.getOrDefault((long) i, 0L), i, testInfo);
      }
      setNeedRebootForSetting(testInfo, "Device needs reboot before locking CPU frequencies.");
    }

    if (spec.hasLockGpuSpeedToMax() && spec.getLockGpuSpeedToMax()) {
      if (DeviceUtil.inSharedLab()) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_SETTING_NOT_SUPPORT_IN_SHARED_LAB,
            "lock_gpu_speed_to_max is not supported in shared lab. See "
                + "go/m&m-performance-testing.");
      }
      addCommandsForGpuSettings(testInfo);
      setNeedRebootForSetting(testInfo, "Device needs reboot before locking GPU frequencies.");
    }
  }

  private static Map<Long, Long> getAllCpuRuntimeFreqsSpecified(
      AndroidDeviceSettingsDecoratorSpec spec) {
    Map<Long, Long> freqs = new HashMap<>();
    for (AndroidDeviceSettingsDecoratorSpec.CpuRuntimeFreq cpuRuntimeFreq :
        spec.getCpuRuntimeFreqList()) {
      freqs.put(cpuRuntimeFreq.getCpuId(), cpuRuntimeFreq.getFreq());
    }
    return freqs;
  }

  private void addCommandsForCpuSettings(long freq, long cpuId, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    // For some Android device and version, e.g. 7.0 on Nexus 6P, there is error when trying to set
    // the CPU to the online state it is already in. We read its current state here. In later phase,
    // we won't modify the online config file if the state isn't changed.
    String onlineConfigPath = cpuDeviceConfigPath(cpuId, "online");

    setNeedRebootAfterTest(testInfo, "Device needs reboot after test to restore CPU settings");

    String deviceType =
        Ascii.toLowerCase(
            getProperty(getDevice().getDeviceId(), AndroidProperty.TYPE.getPropertyKeys()));

    // Set the CPU freq settings
    if (freq == 0) {
      // Fugu does not have hot-pluggable CPU, online file does not exist.
      if (!deviceType.equals("fugu")) {
        addCommandsForCpu(shellEscape(onlineConfigPath), "0");
      }
    } else {
      String freqString;
      // Fugu does not have hot-pluggable CPU, online file does not exist.
      if (!deviceType.equals("fugu")) {
        // freq is not 0, it could be a positive value or -1;
        // either case, set CPU online first. Otherwise, certain CPU(s) could be temporarily
        // offline, which may cause timeout error while waiting for that CPU file to be created.
        addCommandsForCpu(shellEscape(onlineConfigPath), "1");
      }
      if (freq > 0) {
        freqString = Long.toString(freq);
      } else {
        String freqFile = cpuDeviceConfigPath(cpuId, "cpufreq/scaling_available_frequencies");
        // b/69799149: It's possible that this file is not created if the CPU is temporarily
        // offline or recently booted up. Run shell commands to wait for this file to be created.
        runCommands(
            testInfo,
            new Commands().add(getWaitForFileCommand(freqFile, WAIT_FOR_CPU_FILES_TIMEOUT_SEC)));
        freqString = Long.toString(readMaxValueFromConfigFile(testInfo, freqFile));
      }
      // NON_INTERACTIVE_CPU_GOVERNOR devices don't support manually setting CPU frequency, but
      // writing "performance" into scaling_governor will set CPU to max frequency.
      if (NON_INTERACTIVE_CPU_GOVERNOR.contains(deviceType)) {
        addCommandsForCpu(
            shellEscape(cpuDeviceConfigPath(cpuId, "cpufreq/scaling_governor")), "performance");
      } else {
        addCommandsForCpu(
            shellEscape(cpuDeviceConfigPath(cpuId, "cpufreq/scaling_governor")), "userspace");
        addCommandsForCpu(
            shellEscape(cpuDeviceConfigPath(cpuId, "cpufreq/scaling_min_freq")), freqString);
        addCommandsForCpu(
            shellEscape(cpuDeviceConfigPath(cpuId, "cpufreq/scaling_setspeed")), freqString);
        addCommandsForCpu(
            shellEscape(cpuDeviceConfigPath(cpuId, "cpufreq/scaling_max_freq")), freqString);
      }
    }
  }

  private void addCommandsForGpuSettings(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    boolean gpuClockConfigFileExist;
    try {
      gpuClockConfigFileExist =
          androidFileUtil.isFileOrDirExisted(deviceId, GPU_CLOCK_SPEED_CONFIG_FILE_ON_DEVICE);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_CHECK_DIR_OR_FILE_ON_DEVICE_ERROR,
          e.getMessage());
    }
    if (gpuClockConfigFileExist) {
      setNeedRebootAfterTest(testInfo, "Device needs reboot after test to restore GPU settings");
      String maxFrequency =
          Long.toString(
              readMaxValueFromConfigFile(testInfo, GPU_CLOCK_SPEED_CONFIG_FILE_ON_DEVICE));
      commandsAfterSettings.add("echo", "0", ">", GPU_BUS_SPLIT_CONFIG_FILE_ON_DEVICE);
      for (String gpuBusConfigFile : GPU_BUS_CONFIG_FILES_ON_DEVICE) {
        commandsAfterSettings.add("echo", "1", ">", gpuBusConfigFile);
      }
      commandsAfterSettings.add("echo", "10000", ">", GPU_BUS_IDLE_TIMER_CONFIG_FILE_ON_DEVICE);
      commandsAfterSettings.add(
          "echo", "performance", ">", GPU_FREQ_GOVERNOR_CONFIG_FILE_ON_DEVICE);
      commandsAfterSettings.add("echo", maxFrequency, ">", GPU_CLOCK_CONFIG_FILE_ON_DEVICE);
      commandsAfterSettings.add("echo", maxFrequency, ">", GPU_MAX_FREQ_CONFIG_FILE_ON_DEVICE);
      commandsAfterSettings.add("echo", maxFrequency, ">", GPU_MIN_FREQ_CONFIG_FILE_ON_DEVICE);
      // Commands below are from http://cs/android/platform_testing/scripts/perf-setup/ as for
      // different device models, commands will be different.
      // PwrLevel "0" means run at maximum power level.
      String minPwrLevel = "0";
      String maxPwrLevel = "0";
      String deviceType = getProperty(deviceId, AndroidProperty.TYPE.getPropertyKeys());
      if (deviceType != null) {
        switch (Ascii.toLowerCase(deviceType)) {
          case "angler":
            minPwrLevel = "4";
            maxPwrLevel = "4";
            commandsAfterSettings.add("echo 11863 > /sys/class/devfreq/qcom,gpubw.70/min_freq");
            break;
          case "bullhead":
            minPwrLevel = "4";
            maxPwrLevel = "4";
            commandsAfterSettings.add("echo 7102 > /sys/class/devfreq/qcom,gpubw.19/min_freq");
            break;
          case "dragon":
            minPwrLevel = "0";
            maxPwrLevel = "0";
            commandsAfterSettings.add("echo 0a > /sys/class/drm/card0/device/pstate");
            break;
          case "fugu":
            commandsAfterSettings.add("echo -n 533000 > /sys/class/devfreq/dfrgx/max_freq");
            commandsAfterSettings.add("echo -n 533000 > /sys/class/devfreq/dfrgx/min_freq");
            break;
          case "taimen":
            // fall through
          case "walleye":
            minPwrLevel = "0";
            maxPwrLevel = "0";
            commandsAfterSettings.add("echo 13763 > /sys/class/devfreq/soc:qcom,gpubw/min_freq");
            break;
          default: // fall out
        }
      }
      commandsAfterSettings.add("echo", maxPwrLevel, ">", GPU_MAX_PWRLEVEL_CONFIG_FILE_ON_DEVICE);
      commandsAfterSettings.add("echo", minPwrLevel, ">", GPU_MIN_PWRLEVEL_CONFIG_FILE_ON_DEVICE);
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Failed to lock GPU frequency, which might be caused by the fact that there is no "
                  + "GPU on target device. This may introduce noise in performance tests.");
    }
  }

  /**
   * Waits until the device temperature doesn't exceed {@link #maxAllowedTemperature} degrees in
   * Celsius.
   *
   * <p>During waiting, this method will check device temperature every {@link
   * #TEMPERATURE_CHECK_RETRY_DELAY} duration, and stop waiting if the temperature is acceptable.
   * The total waiting time won't exceed {@link #acceptableTemperatureWaitTimeout} duration.
   *
   * @throws MobileHarnessException if the device temperature is not acceptable after {@link
   *     #acceptableTemperatureWaitTimeout} duration
   */
  @VisibleForTesting
  void waitUtilTemperatureAcceptable(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Waiting util device temperature <=%.2f Celsius.", maxAllowedTemperature);

    try {
      RetryingCallable.newBuilder(
              (Callable<Void>)
                  () -> {
                    checkAndLogDeviceTemperature(testInfo, maxAllowedTemperature);
                    return null;
                  },
              RetryStrategy.timed(TEMPERATURE_CHECK_RETRY_DELAY, acceptableTemperatureWaitTimeout))
          .setThrowStrategy(ThrowStrategy.THROW_LAST)
          .build()
          .call();
    } catch (Throwable t) {
      Throwable cause = t.getCause();
      if (t instanceof RetryException) {
        if (cause instanceof TemperatureTooHighException) {
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_DEVICE_TEMPERATURE_TOO_HIGH,
              String.format(
                  "Device temperature is not acceptable after %d readings",
                  ((RetryException) t).getTries()),
              cause);
        }
        t = cause;
      } else if (cause instanceof InterruptedException) {
        throw new InterruptedException(Throwables.getStackTraceAsString(cause));
      }
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_DEVICE_TEMPERATURE_TOO_HIGH,
          "Failed to wait for acceptable device temperature",
          t);
    }
  }

  private void checkAndLogDeviceTemperature(TestInfo testInfo, double maxTemp)
      throws TemperatureTooHighException, MobileHarnessException, InterruptedException {
    // Temperatures are in millidegrees Celsius.
    double deviceTemperature =
        Double.parseDouble(readConfigFileContents(testInfo, GENERAL_CPU_TEMP_FILE_ON_DEVICE))
            / 1000;

    if (deviceTemperature > maxTemp) {
      throw new TemperatureTooHighException(deviceTemperature);
    }

    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Device temperature OK: %.2f Celsius", deviceTemperature);
  }

  /** Exception thrown if the temperature exceeds safe levels for running tests. */
  static final class TemperatureTooHighException extends Exception {
    private TemperatureTooHighException(double temperature) {
      super(String.format("Device temperature too high: %.2f Celsius", temperature));
    }
  }

  private void synchronizeDateWithHost(TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Attempting to manually synchronize clocks for the device and host");
    try {
      int sdkVersion = getDeviceSdkVersion();
      androidSystemSettingUtil.setSystemTimeToHost(deviceId, sdkVersion);
      if (!androidSystemSettingUtil.checkSystemTime(deviceId, HOST_DEVICE_MAX_OFFSET)) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_SYNC_DEVICE_SYSTEM_TIME_ERROR,
            "Failed to synchronize times between host and device.");
      }
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_SYNC_DEVICE_SYSTEM_TIME_ERROR,
          e.getMessage());
    }
  }

  private void runCommands(TestInfo testInfo, Commands commands)
      throws MobileHarnessException, InterruptedException {
    Set<String> availablePackages =
        androidPackageManagerUtil.listPackages(getDevice().getDeviceId(), PackageType.ALL);
    ImmutableList<String> filteredCommands =
        commands.get().stream()
            .filter(
                command -> {
                  Matcher matcher = COMMAND_PATTERN.matcher(command);
                  if (matcher.find()) {
                    String packageName = matcher.group(2);
                    if (availablePackages.contains(packageName)) {
                      return true;
                    } else {
                      testInfo
                          .log()
                          .atInfo()
                          .alsoTo(logger)
                          .log(
                              "Skipping command [%s], since the target package [%s] is not"
                                  + " available anyway.",
                              command, packageName);
                      return false;
                    }
                  }
                  return true;
                })
            .collect(toImmutableList());
    for (String command : filteredCommands) {
      try {
        runCommand(testInfo, command);
      } catch (MobileHarnessException e) {
        String exceptionMessage = e.getMessage();
        Matcher matcher = UNABLE_TO_STOP_SERVICE_MSG_PATTERN.matcher(exceptionMessage);
        if (exceptionMessage.contains(UNKNOWN_PACKAGE_ERR_MSG)
            || exceptionMessage.contains(NO_FILE_ERR_MSG)) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Ignored failed command \"%s\", error details for information:%n%s",
                  command, exceptionMessage);
        } else if (matcher.find()) {
          String msg =
              String.format(
                  "Please check if the service %s is running on your platform",
                  matcher.group("service"));
          throw new MobileHarnessException(
              AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_STOP_INVALID_SERVICE_ERROR, msg, e);
        } else {
          throw e;
        }
      }
    }
  }

  private static final Pattern COMMAND_PATTERN = Pattern.compile("pm (enable|disable-user) (.*)$");

  @CanIgnoreReturnValue
  private String runCommand(TestInfo testInfo, String command)
      throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log("Run adb shell command on device %s: %s", deviceId, command);
    try {
      return adb.runShellWithRetry(deviceId, command);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_COMMAND_EXEC_ERROR, e.getMessage());
    }
  }

  /**
   * @throws MobileHarnessException if the device cannot reboot
   */
  private void checkCanReboot(TestInfo testInfo, String message)
      throws MobileHarnessException, InterruptedException {
    testInfo.log().atInfo().alsoTo(logger).log("%s", message);
    if (!getDevice().canReboot()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_DEVICE_NOT_SUPPORT_REBOOT,
          String.format(
              "Device %s doesn't support reboot. But reboot is required: %s",
              getDevice().getDeviceId(), message));
    }
  }

  private void setNeedRebootForSetting(TestInfo testInfo, String message)
      throws MobileHarnessException, InterruptedException {
    checkCanReboot(testInfo, message);
    needRebootForSetting = true;
  }

  private void setNeedRebootAfterTest(TestInfo testInfo, String message)
      throws MobileHarnessException, InterruptedException {
    checkCanReboot(testInfo, message);
    needRebootAfterTest = true;
  }

  /**
   * Disable verity if requested in {@code spec}. Reboot is required if verity was enabled on the
   * device, and is now disabled. Note that we cannot easily implement the corresponding verity
   * enabling because there is a high chance that the phone will no longer boot (since image is
   * likely modified and verity will prevent booting).
   */
  private void maybeDisableVerity(AndroidDeviceSettingsDecoratorSpec spec, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (spec.hasDisableVerity() && spec.getDisableVerity()) {
      String deviceId = getDevice().getDeviceId();
      int sdkVersion = getDeviceSdkVersion();
      PostSetDmVerityDeviceOp op;
      try {
        op = androidSystemSettingUtil.setDmVerityChecking(deviceId, /* enabled= */ false);
      } catch (MobileHarnessException e) {
        throw new MobileHarnessException(
            AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_SET_DM_VERITY_ERROR, e.getMessage());
      }
      if (sdkVersion >= 22 && op == PostSetDmVerityDeviceOp.REBOOT) {
        testInfo.log().atInfo().alsoTo(logger).log("Disabled dm-verity.");
        setNeedRebootForSetting(testInfo, "Changed dm-verity checking");
      } else if (sdkVersion < 22) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Failed to set dm-verity, SdkVersion too low (required 22 or above, found %d).",
                sdkVersion);
      } else {
        testInfo.log().atInfo().alsoTo(logger).log("dm-verity already disabled on device.");
      }
    }
  }

  /**
   * Changes props that is parsed to {@link #props}. Reboot is required if {@link #props} contains
   * any temporary settings. Does nothing if previous value is the desired one. The property key is
   * not found and the property value is empty are treated as the same.
   */
  private void changeProps(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    List<String> tempProps = new ArrayList<>();
    for (Map.Entry<String, String> pair : props.entrySet()) {
      if (!isPropertyValueChanged(getDevice().getDeviceId(), pair.getKey(), pair.getValue())) {
        continue;
      }
      if (pair.getKey().startsWith(PERSIST_PROP_PREFIX)) {
        runCommand(testInfo, Commands.buildCommand("setprop", pair.getKey(), pair.getValue()));
      } else {
        tempProps.add(String.format("%s=%s", pair.getKey(), pair.getValue()));
      }
    }
    if (tempProps.isEmpty()) {
      return;
    }

    // It is not reliable to run "setprop" on current devices. because it only works for the first
    // time. For example:
    //
    //   <reboot device>
    //   adb shell setprop ro.audio.silent 1  # it works
    //   adb shell setprop ro.audio.silent 0  # ERROR! the prop is still 1.
    //
    // so we could store the temporary settings in device file "/data/local.prop" and reboot it.
    // The settings in the file will be loaded automatically after rebooting.
    StringBuilder command = new StringBuilder();
    command.append(ADB_SHELL_CLEAN_LOCAL_PROP + " && ");
    for (String prop : tempProps) {
      command.append(String.format("echo %s >> /data/local.prop && ", prop));
    }
    command.append(ADB_SHELL_CHMOD_LOCAL_PROP);
    setNeedRebootForSetting(
        testInfo,
        String.format("Device needs reboot to make the setting into effect:\n %s.", command));
    if (!DeviceUtil.inSharedLab()) {
      dataLocalPropertyFileSet = true;
      setNeedRebootAfterTest(
          testInfo, "Device needs reboot after test to clear settings in /data/local.prop");
    }

    runCommand(testInfo, Commands.buildCommand(command.toString()));
  }

  @VisibleForTesting
  boolean isPropertyValueChanged(String serial, String propertyKey, String expectedPropertyValue)
      throws MobileHarnessException, InterruptedException {
    String previousValue = getProperty(serial, ImmutableList.of(propertyKey));
    if (previousValue.equals(expectedPropertyValue)) {
      logger.atInfo().log(
          "Trying to set ro property (%s) and its previous value is the desired one (%s). "
              + "Do nothing.",
          propertyKey, expectedPropertyValue);
      return false;
    }
    return true;
  }

  /** Reboots devices, and blocked current thread until it is finished. */
  @VisibleForTesting
  void rebootDevice(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    String deviceId = getDevice().getDeviceId();
    testInfo.log().atInfo().alsoTo(logger).log("Waiting for device %s rebooting... ", deviceId);

    // The only supported device for this decorator is {@link AndroidRealDevice}.
    try {
      systemStateManager.reboot(getDevice(), testInfo.log(), WAIT_FOR_REBOOT);
    } catch (MobileHarnessException e) {
      testInfo
          .warnings()
          .addAndLog(
              new MobileHarnessException(
                  AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_DEVICE_INIT_AFTER_REBOOT_ERROR,
                  String.format(
                      "Reboot not finished successfully: %s %s",
                      e.getMessage(), Arrays.toString(e.getStackTrace()))));
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_DEVICE_INIT_AFTER_REBOOT_ERROR,
          "Exception during reboot: " + e.getMessage(),
          e);
    }

    testInfo.log().atInfo().alsoTo(logger).log("device %s is rebooted.", deviceId);
  }

  /** Gets the shell command to wait for a file to be created. */
  private static List<String> getWaitForFileCommand(String filePath, long timeoutSec) {
    return Arrays.asList(
        "sh",
        "-c",
        String.format(
            "'i=0; while [ $i -lt %s -a ! -e %s ]; do i=$((i+1)); sleep 1; done; test -e %s'",
            timeoutSec, filePath, filePath));
  }

  /** Gets the shell command to set certain value to given file. */
  private static List<String> getSetValueForFileCommand(String filePath, String value) {
    return Arrays.asList(
        "sh",
        "-c",
        String.format("'[ %s == $(cat %s) ] || echo %s > %s'", value, filePath, value, filePath));
  }

  /** Generates and adds the commands to lock a given CPU. */
  private void addCommandsForCpu(String filePath, String value) {
    // First to wait for this file to be created in case device just booted up.
    commandsAfterSettings.add(getWaitForFileCommand(filePath, WAIT_FOR_CPU_FILES_TIMEOUT_SEC));
    // Cat value to file only if the value is different
    commandsAfterSettings.add(getSetValueForFileCommand(filePath, value));
  }

  /** Changes device settings that is parsed from {@code spec}. Only support device with API 22+. */
  private void changeSettings(TestInfo testInfo, AndroidDeviceSettingsDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    // enable_airplane_mode. It must be set before any other connectivity settings.
    if (spec.hasEnableAirplaneMode()) {
      // change the property to airplane mode.
      runCommands(
          testInfo,
          new Settings().global.add("airplane_mode_on", toInt(spec.getEnableAirplaneMode())));
      // A gservices broadcast to make the property change effective.
      runCommands(
          testInfo,
          new Commands()
              .add(
                  "am",
                  "broadcast",
                  "-a",
                  "android.intent.action.AIRPLANE_MODE",
                  "--ez",
                  "state",
                  Boolean.toString(spec.getEnableAirplaneMode())));
    }

    if (spec.hasSynchronizeDateWithHost() && spec.getSynchronizeDateWithHost()) {
      synchronizeDateWithHost(testInfo);
    }

    runCommands(testInfo, commandsBeforeSettings);

    if (settings.isEmpty()) {
      testInfo.log().atInfo().alsoTo(logger).log("Doesn't need to change settings.");
      return;
    }

    if (getDeviceSdkVersion() < AndroidVersion.LOLLIPOP.getEndSdkVersion()) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_SETTING_NOT_SUPPORT_ERROR,
          String.format(
              "Changing setting not supported on %s, must be API 22+", getDevice().getDeviceId()));
    }

    runCommands(testInfo, settings.system);
    runCommands(testInfo, settings.secure);
    runCommands(testInfo, settings.global);
  }

  private String getProperty(String serial, ImmutableList<String> propertyKeys)
      throws MobileHarnessException, InterruptedException {
    try {
      return androidAdbUtil.getProperty(serial, propertyKeys);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_GET_DEVICE_PROPERTY_ERROR,
          e.getMessage(),
          e);
    }
  }

  private int getDeviceSdkVersion() throws MobileHarnessException, InterruptedException {
    try {
      return systemSettingManager.getDeviceSdkVersion(getDevice());
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.ANDROID_DEVICE_SETTINGS_DECORATOR_GET_DEVICE_SDK_VERSION_ERROR,
          e.getMessage(),
          e);
    }
  }

  /**
   * Handles screen always on settings.
   *
   * <p>This is done in a dedicated method because special handling is required in case of setting
   * screen to always on.
   */
  private void handleScreenAlwaysOnSetting(
      AndroidDeviceSettingsDecoratorSpec spec, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    if (!spec.hasScreenAlwaysOn()) {
      return;
    }
    String deviceId = getDevice().getDeviceId();

    if (spec.getScreenAlwaysOn()) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Setting screen always on to true on device %s", deviceId);
      androidSystemSettingUtil.keepAwake(deviceId, /* alwaysAwake= */ true);
      // Send MENU press in case keyguard needs to be dismissed again
      String unused = adb.runShellWithRetry(deviceId, "input keyevent 82");
      // Send HOME press in case keyguard was already dismissed, so we bring device back to home
      // screen. No need for this on Wear OS, since that causes the launcher to show instead of the
      // home screen
      if (!androidSystemSpecUtil.isWearableDevice(deviceId)) {
        unused = adb.runShellWithRetry(deviceId, "input keyevent 3");
      }
    } else {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Setting screen always on to false on device %s", deviceId);
      androidSystemSettingUtil.keepAwake(deviceId, /* alwaysAwake= */ false);
    }
  }

  @Override
  public void run(TestInfo testInfo)
      throws com.google.wireless.qa.mobileharness.shared.MobileHarnessException,
          InterruptedException {
    if (!"AndroidRealDevice".equals(getDevice().getClass().getSimpleName())) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .log(
              "Device is not an AndroidRealDevice; some settings may not be supported. In"
                  + " particular, hardware-based settings (like changing CPU/GPU frequencies,"
                  + " adjusting screen brightness, or checking temperature) are not supported"
                  + " and some emulator environments do not support rebooting, which prevents"
                  + " settings like disabling audio or telephony.");
    }

    AndroidDeviceSettingsDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, getDevice().getDeviceId());
    testInfo.log().atInfo().alsoTo(logger).log("Setting device with spec:\n%s", spec);

    maybeDisableVerity(spec, testInfo);

    MobileHarnessException.checkNotNull(
        spec, ErrorCode.ILLEGAL_STATE, "settings spec cannot be null");
    if (spec.hasForceReboot() && spec.getForceReboot() == Reboot.BEFORE_SETTING) {
      checkCanReboot(testInfo, "Force to reboot before setting.");
      rebootDevice(testInfo);
    }
    parseSpec(spec, testInfo);
    changeProps(testInfo);

    if (needRebootForSetting) {
      if (spec.hasForceReboot()) {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "Device needs reboot before and/or after some settings. But force_reboot is "
                    + "set to %s, so the settings may not be set correctly.",
                spec.getForceReboot());
      } else {
        rebootDevice(testInfo);
      }
    }

    changeSettings(testInfo, spec);
    runCommands(testInfo, commandsAfterSettings);

    handleScreenAlwaysOnSetting(spec, testInfo);

    if (spec.hasForceReboot() && spec.getForceReboot() == Reboot.AFTER_SETTING) {
      checkCanReboot(testInfo, "Force to reboot after setting.");
      rebootDevice(testInfo);
    }

    // Wait for temperature to be ready should be done as the last step.
    if (spec.hasMaxAllowedTemperature() && spec.getMaxAllowedTemperature() > 0.0f) {
      if (getDeviceSdkVersion() < AndroidVersion.PI.getEndSdkVersion()) {
        waitUtilTemperatureAcceptable(testInfo);
      } else {
        testInfo
            .log()
            .atInfo()
            .alsoTo(logger)
            .log("Temperature check only enforced with API level lower than 28");
      }
    }

    try {
      getDecorated().run(testInfo);
    } finally {
      // After the test we need to enable the apps that were disabled, otherwise there could be a
      // situation when the device can't be properly started after reboot because crucial
      // first-party apps were disabled (for example, com.google.android.googlequicksearchbox).
      try {
        runCommands(testInfo, commandsOnTestEnd);
      } catch (MobileHarnessException e) {
        testInfo.warnings().addAndLog(e, logger);
      }
      if (dataLocalPropertyFileSet) {
        androidFileUtil.removeFiles(getDevice().getDeviceId(), "/data/local.prop");
      }
      if (needRebootAfterTest) {
        try {
          // CPU/GPU settings have been changed. Reboot device to restore.
          rebootDevice(testInfo);
        } catch (MobileHarnessException e) {
          testInfo.warnings().addAndLog(e, logger);
        }
      }
    }
  }
}
