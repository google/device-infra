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

package com.google.devtools.deviceinfra.shared.util.flags;

import static java.util.Arrays.stream;

import java.lang.reflect.Modifier;
import java.time.Duration;

/**
 * All Device Infra flags.
 *
 * <p>Remember to sort all flags by @FlagSpec.name.
 */
@com.beust.jcommander.Parameters(separators = "=")
@SuppressWarnings({"NonPrivateFlag", "UnnecessarilyFullyQualified"})
public class Flags {

  private static final Flag<String> aaptPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--aapt",
      description = "Android AAPT path.",
      converter = Flag.StringConverter.class)
  public Flag<String> aaptPath = aaptPathDefault;

  private static final Flag<String> adbPathFromUserDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--adb",
      description = "Android ADB path.",
      converter = Flag.StringConverter.class)
  public Flag<String> adbPathFromUser = adbPathFromUserDefault;

  private static final Flag<Boolean> adbDontKillServerDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--adb_dont_kill_server",
      description = "Don't ever kill the adb server.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> adbDontKillServer = adbDontKillServerDefault;

  /**
   * Force the reboot of adb server regardless of other flags conditions. e.g. If both this flag and
   * {@code adb_dont_kill_server} are set, this flag will override {@code adb_dont_kill_server}.
   */
  private static final Flag<Boolean> adbForceKillServerDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--adb_kill_server",
      description = "Force to kill the adb server.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> adbForceKillServer = adbForceKillServerDefault;

  private static final Flag<Boolean> adbLibusbDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--adb_libusb",
      description = "Start the adb server with flag ADB_LIBUSB=1.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> adbLibusb = adbLibusbDefault;

  private static final Flag<Boolean> enableDaemonDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--android_device_daemon",
      description = "Whether to install Mobile Harness Android daemon app on the device.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDaemon = enableDaemonDefault;

  private static final Flag<Boolean> cacheInstalledApksDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--cache_installed_apks",
      description = "Cache installed apk in device property to avoid installing again.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> cacheInstalledApks = cacheInstalledApksDefault;

  private static final Flag<Boolean> clearAndroidDeviceMultiUsersDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--clear_android_device_multi_users",
      description = "Whether to clear multi users in device setup and post-test. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> clearAndroidDeviceMultiUsers = clearAndroidDeviceMultiUsersDefault;

  private static final Flag<Boolean> detectAdbDeviceDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--detect_adb_device",
      description = "Whether to enable ADB detector. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> detectAdbDevice = detectAdbDeviceDefault;

  private static final Flag<Boolean> pingGoogleDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--device_ping_google",
      description = "Whether to enable dimension ping_google_stability. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> pingGoogle = pingGoogleDefault;

  private static final Flag<Boolean> disableCallingDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--disable_calling",
      description =
          "Whether to disable outbound calling. "
              + "By default it is TRUE. After calling is disabled, only reboot can re-enable it.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> disableCalling = disableCallingDefault;

  private static final Flag<Boolean> disableCellBroadcastReceiverDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--disable_cellbroadcastreceiver",
      description =
          "Whether to disable cellbroadcast receiver. It stops the device to receive any "
              + "message sent by cellbroadcast, e.g., emergency alert. Test runner is in charge to "
              + "enable cellbroadcast receiver if the test wants the function.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> disableCellBroadcastReceiver = disableCellBroadcastReceiverDefault;

  private static final Flag<Boolean> enableAteDualStackDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_ate_dual_stack",
      description = "Whether to enable ATE dual stack mode, which runs tests from both MH and TFC.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableAteDualStack = enableAteDualStackDefault;

  private static final Flag<Boolean> enableDebugModeDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_debug_mode",
      description = "Whether enable debug mode to print more detailed logs.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDebugMode = enableDebugModeDefault;

  private static final Flag<Boolean> enableDeviceAirplaneModeDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_device_airplane_mode",
      description =
          "Turn device airplane mode on or off. True is on, false is off. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDeviceAirplaneMode = enableDeviceAirplaneModeDefault;

  private static final Flag<Boolean> createFailedDeviceDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_failed_device_creation",
      description =
          "Whether the lab server should create FailedDevice when devices constantly fail to"
              + " initialize. In some rare use cases devices might not finish initialization but"
              + " still be able to work sometimes. This flag does not work in shared lab, the"
              + " default value of this flag is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> createFailedDevice = createFailedDeviceDefault;

  private static final Flag<Boolean> enforceSafeDischargeDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enforce_safe_discharge",
      description =
          "Enable enforcing safe discharge mode for supported devices. For supported devices this "
              + "will try to keep battery level at safe_charge_level. For devices which do not "
              + "support safe_charge_level, this will try to turn charge off and on when reached "
              + "stop_charge_level and start_charge_level respectively.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enforceSafeDischarge = enforceSafeDischargeDefault;

  private static final Flag<String> adbKeyPathsFromUserDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--extra_adb_keys",
      description =
          ("Colon-separated list of adb keys (files or directories) to be used (see ADB_VENDOR_KEYS"
              + " in adb --help for formatting details)."),
      converter = Flag.StringConverter.class)
  public Flag<String> adbKeyPathsFromUser = adbKeyPathsFromUserDefault;

  private static final Flag<String> fastbootPathFromUserDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--fastboot",
      description = "File path of the fastboot tool",
      converter = Flag.StringConverter.class)
  public Flag<String> fastbootPathFromUser = fastbootPathFromUserDefault;

  private static final Flag<Integer> internalStorageAlertDefault = Flag.value(200);

  @com.beust.jcommander.Parameter(
      names = "--internal_storage_alert_mb",
      description =
          "The threshold for insufficient internal storage alert. If the internal storage is lower "
              + "than the threshold, device will go to prepping state that cannot run tests and "
              + "the dimension 'internal_storage_status' will go from 'ok' to 'low'. Unit is MB. "
              + "Default is 200 MB.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> internalStorageAlert = internalStorageAlertDefault;

  private static final Flag<String> javaCommandPathDefault = Flag.value("java");

  @com.beust.jcommander.Parameter(
      names = "--java_command_path",
      description = "The path of Java",
      converter = Flag.StringConverter.class)
  public Flag<String> javaCommandPath = javaCommandPathDefault;

  private static final Flag<Integer> logFileNumDefault = Flag.value(100);

  @com.beust.jcommander.Parameter(
      names = "--log_file_num",
      description = "Max number of the rotated log files. Max size of each file is 10 MB.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> logFileNum = logFileNumDefault;

  private static final Flag<Integer> maxConcurrentAdbPushLargeFileDefault = Flag.value(4);

  @com.beust.jcommander.Parameter(
      names = "--max_concurrent_adb_push_large_file",
      description = "Maximum number of concurrent ADB push commands for large files",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> maxConcurrentAdbPushLargeFile = maxConcurrentAdbPushLargeFileDefault;

  private static final Flag<Integer> maxConcurrentFlashDeviceDefault = Flag.value(2);

  @com.beust.jcommander.Parameter(
      names = "--max_concurrent_flash_device",
      description =
          "Maximum number of concurrent device flashing. "
              + "Do not set this flag too larger than max_concurrent_adb_push_large_file, "
              + "because flashing img to different partitions is controlled by that flag. "
              + "Setting this flag too larger may cause cache device timeout if there are "
              + "many devices on the lab to be flashed at the same time.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> maxConcurrentFlashDevice = maxConcurrentFlashDeviceDefault;

  private static final Flag<Integer> maxConcurrentUnzipLargeFileDefault = Flag.value(2);

  @com.beust.jcommander.Parameter(
      names = "--max_concurrent_unzip_large_file",
      description = "Maximum number of concurrent large file unzipping",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> maxConcurrentUnzipLargeFile = maxConcurrentUnzipLargeFileDefault;

  private static final Flag<Boolean> defaultAdbCommandRedirectStderrDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--mh_adb_command_default_redirect_stderr",
      description =
          "Default redirect_stderr setting for each Device Infra(DI) ADB command executed by DI"
              + " Adb library. Default is true (stderr will be redirected to stdout).",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> defaultAdbCommandRedirectStderr = defaultAdbCommandRedirectStderrDefault;

  private static final Flag<Duration> extraAdbCommandTimeoutDefault =
      DurationFlag.value(Duration.ZERO);

  @com.beust.jcommander.Parameter(
      names = "--mh_adb_command_extra_timeout",
      description =
          "Extra timeout for each Device Infra(DI) ADB command executed by DI Adb library. Default"
              + " is 0. Example: '4m'. When DI Adb library (used by most of DI Android utilities)"
              + " executes an ADB command, the timeout of the command will be the original timeout"
              + " plus this extra timeout. For example, when the extra timeout is 4 minutes, if an"
              + " ADB command does not specify timeout (uses the default 5 minutes timeout), then"
              + " the timeout will be 9 minutes, if an ADB command specifies 10 seconds timeout,"
              + " then the timeout will be 4 minutes and 10 seconds.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> extraAdbCommandTimeout = extraAdbCommandTimeoutDefault;

  private static final Flag<Boolean> muteAndroidDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--mute_android",
      description =
          "Whether to mute Android rooted devices. "
              + "By default it is TRUE. After a device is muted, only reboot can re-enable sounds.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> muteAndroid = muteAndroidDefault;

  private static final Flag<Integer> noOpDeviceNumDefault = Flag.value(0);

  @com.beust.jcommander.Parameter(
      names = "--no_op_device_num",
      description =
          "The number of NoOpDevice to be started. If set all other devices will be disabled.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> noOpDeviceNum = noOpDeviceNumDefault;

  private static final Flag<String> noOpDeviceTypeDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--no_op_device_type",
      description =
          "Device type string supported, e.g. AndroidRealDevice, only for debug/test purpose.",
      converter = Flag.StringConverter.class)
  public Flag<String> noOpDeviceType = noOpDeviceTypeDefault;

  private static final Flag<Integer> olcServerPortDefault = Flag.value(7030);

  @com.beust.jcommander.Parameter(
      names = "--olc_server_port",
      description = "OLC server port. By default, it is 7030.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> olcServerPort = olcServerPortDefault;

  private static final Flag<String> publicDirDefault = Flag.value("/var/www");

  @com.beust.jcommander.Parameter(
      names = "--public_dir",
      description = "The public directory of the Apache/GSE.",
      converter = Flag.StringConverter.class)
  public Flag<String> publicDir = publicDirDefault;

  private static final Flag<Boolean> realTimeJobDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--real_time_job",
      description = "If this flag is true, all submitted jobs will run as real-time jobs.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> realTimeJob = realTimeJobDefault;

  private static final Flag<Boolean> removeJobGenFilesWhenFinishedDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--remove_job_gen_files_when_finished",
      description =
          "If this flag is true, all job generated files are removed after the job is done.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> removeJobGenFilesWhenFinished = removeJobGenFilesWhenFinishedDefault;

  private static final Flag<String> resDirNameDefault = Flag.value("mh_res_files");

  @com.beust.jcommander.Parameter(
      names = "--resource_dir_name",
      description = "Name of resource directory.",
      converter = Flag.StringConverter.class)
  public Flag<String> resDirName = resDirNameDefault;

  private static final Flag<Integer> safeChargeLevelDefault = Flag.value(50);

  @com.beust.jcommander.Parameter(
      names = "--safe_charge_level",
      description =
          "Battery level devices should be kept at. Devices will be charged at most to this level."
              + "Only works for devices which support this (i.e., marlin, sailfish).",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> safeChargeLevel = safeChargeLevelDefault;

  private static final Flag<Boolean> setTestHarnessPropertyDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--set_test_harness_property",
      description =
          "Whether to set ro.test_harness property on Android devices. If set restarting Zygote"
              + " will skip setup wizard. By default, it is TRUE.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> setTestHarnessProperty = setTestHarnessPropertyDefault;

  private static final Flag<Boolean> shouldManageDevicesDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--should_manage_devices",
      description =
          "Whether the lab server should actively manage and recover devices from bad state, or"
              + " just let a test fail. True for traditional deployments, false for labs where some"
              + " other component manages and recovers the devices. By default, it is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> shouldManageDevices = shouldManageDevicesDefault;

  private static final Flag<Boolean> skipConnectDeviceToWifiDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--skip_connect_device_to_wifi",
      description =
          "Whether to skip connecting device to their default wifi network. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> skipConnectDeviceToWifi = skipConnectDeviceToWifiDefault;

  private static final Flag<Boolean> skipNetworkDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--skip_network",
      description =
          "Whether to skip network connection when set up and periodically check the device."
              + " Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> skipNetwork = skipNetworkDefault;

  private static final Flag<Boolean> skipRecoverDeviceNetworkDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--skip_recover_device_network",
      description =
          "Whether to skip recovering device network by connecting device to saved ssid. Default "
              + "is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> skipRecoverDeviceNetwork = skipRecoverDeviceNetworkDefault;

  private static final Flag<Integer> startChargeLevelDefault = Flag.value(40);

  @com.beust.jcommander.Parameter(
      names = "--start_charge_level",
      description =
          "Battery level at which charging should start. Only works for devices which support this "
              + "(i.e., angler, bullhead)",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> startChargeLevel = startChargeLevelDefault;

  private static final Flag<Integer> stopChargeLevelDefault = Flag.value(80);

  @com.beust.jcommander.Parameter(
      names = "--stop_charge_level",
      description =
          "Battery level at which charging should stop. Only works for devices which support this "
              + "(i.e., angler, bullhead)",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> stopChargeLevel = stopChargeLevelDefault;

  // The flag for dynamically loading resource files from the supplemental directory instead of
  // unpacking from the JAR binary. It allows updating resource files without rebuilding the JAR
  // binary. Please only use it for local development and do not use it in production.
  private static final Flag<String> supplementalResDirDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--supplemental_res_dir",
      description =
          "Absolute path to the supplemental resource directory. If a resource exists in the"
              + " supplemental dir, this util won't extract it from the jar package. Please do not"
              + " use it in production environment.",
      converter = Flag.StringConverter.class)
  public Flag<String> supplementalResDir = supplementalResDirDefault;

  private static final Flag<String> tmpDirRootDefault =
      Flag.value("/usr/local/google/mobileharness");

  @com.beust.jcommander.Parameter(
      names = "--tmp_dir_root",
      description = "The tmp Dir Root.",
      converter = Flag.StringConverter.class)
  public Flag<String> tmpDirRoot = tmpDirRootDefault;

  private static final Flags INSTANCE = new Flags();

  public static Flags instance() {
    return INSTANCE;
  }

  /** It is necessary to call this method in main() in OSS. */
  public static void parse(String[] args) {
    new com.beust.jcommander.JCommander(instance()).parse(args);
  }

  /**
   * Resets all flags to their default values.
   *
   * <p>Only available in OSS. Does nothing in non-OSS.
   *
   * <p>Should be called in @After in UT.
   */
  public static void resetToDefault() {
    doResetToDefault();
  }

  private static void doResetToDefault() {
    stream(Flags.class.getFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .filter(
            field ->
                stream(field.getAnnotations())
                    .anyMatch(
                        annotation ->
                            annotation
                                .annotationType()
                                .getName()
                                .equals("com.beust.jcommander.Parameter")))
        // For all public non-static @com.beust.jcommander.Parameter fields:
        .forEach(
            field -> {
              String defaultValueFieldName = field.getName() + "Default";
              try {
                field.set(
                    instance(), Flags.class.getDeclaredField(defaultValueFieldName).get(null));
              } catch (ReflectiveOperationException e) {
                throw new LinkageError(
                    String.format(
                        "Class Flags should define a private static final field \"%s\" as the"
                            + " default value of the field \"%s\"",
                        defaultValueFieldName, field.getName()),
                    e);
              }
            });
  }

  private Flags() {}
}
