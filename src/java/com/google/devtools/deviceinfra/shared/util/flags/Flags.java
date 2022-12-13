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

import java.time.Duration;

/**
 * All Device Infra flags.
 *
 * <p>Remember to sort all flags by @FlagSpec.name.
 */
@com.beust.jcommander.Parameters(separators = "=")
@SuppressWarnings({"NonPrivateFlag", "UnnecessarilyFullyQualified"})
public class Flags {

  @com.beust.jcommander.Parameter(
      names = "--aapt",
      description = "Android AAPT path.",
      converter = Flag.StringConverter.class)
  public Flag<String> aaptPath = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--adb",
      description = "Android ADB path.",
      converter = Flag.StringConverter.class)
  public Flag<String> adbPathFromUser = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--adb_dont_kill_server",
      description = "Don't ever kill the adb server.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> adbDontKillServer = Flag.value(false);

  /**
   * Force the reboot of adb server regardless of other flags conditions. e.g. If both this flag and
   * {@code adb_dont_kill_server} are set, this flag will override {@code adb_dont_kill_server}.
   */
  @com.beust.jcommander.Parameter(
      names = "--adb_kill_server",
      description = "Force to kill the adb server.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> adbForceKillServer = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--adb_libusb",
      description = "Start the adb server with flag ADB_LIBUSB=1.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> adbLibusb = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--android_device_daemon",
      description = "Whether to install Mobile Harness Android daemon app on the device.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDaemon = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--cache_installed_apks",
      description = "Cache installed apk in device property to avoid installing again.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> cacheInstalledApks = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_ate_dual_stack",
      description = "Whether to enable ATE dual stack mode, which runs tests from both MH and TFC.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableAteDualStack = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_failed_device_creation",
      description =
          "Whether the lab server should create FailedDevice when devices constantly fail to"
              + " initialize. In some rare use cases devices might not finish initialization but"
              + " still be able to work sometimes. This flag does not work in shared lab, the"
              + " default value of this flag is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> createFailedDevice = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--extra_adb_keys",
      description =
          ("Colon-separated list of adb keys (files or directories) to be used (see ADB_VENDOR_KEYS"
              + " in adb --help for formatting details)."),
      converter = Flag.StringConverter.class)
  public Flag<String> adbKeyPathsFromUser = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--fastboot",
      description = "File path of the fastboot tool",
      converter = Flag.StringConverter.class)
  public Flag<String> fastbootPathFromUser = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--max_concurrent_adb_push_large_file",
      description = "Maximum number of concurrent ADB push commands for large files",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> maxConcurrentAdbPushLargeFile = Flag.value(4);

  @com.beust.jcommander.Parameter(
      names = "--max_concurrent_flash_device",
      description =
          "Maximum number of concurrent device flashing. "
              + "Do not set this flag too larger than max_concurrent_adb_push_large_file, "
              + "because flashing img to different partitions is controlled by that flag. "
              + "Setting this flag too larger may cause cache device timeout if there are "
              + "many devices on the lab to be flashed at the same time.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> maxConcurrentFlashDevice = Flag.value(2);

  @com.beust.jcommander.Parameter(
      names = "--max_concurrent_unzip_large_file",
      description = "Maximum number of concurrent large file unzipping",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> maxConcurrentUnzipLargeFile = Flag.value(2);

  @com.beust.jcommander.Parameter(
      names = "--mh_adb_command_default_redirect_stderr",
      description =
          "Default redirect_stderr setting for each Device Infra(DI) ADB command executed by DI"
              + " Adb library. Default is true (stderr will be redirected to stdout).",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> defaultAdbCommandRedirectStderr = Flag.value(true);

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
  public Flag<Duration> extraAdbCommandTimeout = DurationFlag.value(Duration.ZERO);

  @com.beust.jcommander.Parameter(
      names = "--no_op_device_num",
      description =
          "The number of NoOpDevice to be started. If set all other devices will be disabled.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> noOpDeviceNum = Flag.value(0);

  @com.beust.jcommander.Parameter(
      names = "--no_op_device_type",
      description =
          "Device type string supported, e.g. AndroidRealDevice, only for debug/test purpose.",
      converter = Flag.StringConverter.class)
  public Flag<String> noOpDeviceType = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--public_dir",
      description = "The public directory of the Apache/GSE.",
      converter = Flag.StringConverter.class)
  public Flag<String> publicDir = Flag.value("/var/www");

  @com.beust.jcommander.Parameter(
      names = "--real_time_job",
      description = "If this flag is true, all submitted jobs will run as real-time jobs.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> realTimeJob = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--remove_job_gen_files_when_finished",
      description =
          "If this flag is true, all job generated files are removed after the job is done.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> removeJobGenFilesWhenFinished = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--resource_dir_name",
      description = "Name of resource directory.",
      converter = Flag.StringConverter.class)
  public Flag<String> resDirName = Flag.value("mh_res_files");

  @com.beust.jcommander.Parameter(
      names = "--should_manage_devices",
      description =
          "Whether the lab server should actively manage and recover devices from bad state, or"
              + " just let a test fail. True for traditional deployments, false for labs where some"
              + " other component manages and recovers the devices. By default, it is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> shouldManageDevices = Flag.value(true);

  // The flag for dynamically loading resource files from the supplemental directory instead of
  // unpacking from the JAR binary. It allows updating resource files without rebuilding the JAR
  // binary. Please only use it for local development and do not use it in production.
  // See b/255255107.
  @com.beust.jcommander.Parameter(
      names = "--supplemental_res_dir",
      description =
          "Absolute path to the supplemental resource directory. If a resource exists in the"
              + " supplemental dir, this util won't extract it from the jar package. Please do not"
              + " use it in production environment.",
      converter = Flag.StringConverter.class)
  public Flag<String> supplementalResDir = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--tmp_dir_root",
      description = "The tmp Dir Root.",
      converter = Flag.StringConverter.class)
  public Flag<String> tmpDirRoot = Flag.value("/usr/local/google/mobileharness");

  private static final Flags INSTANCE = new Flags();

  public static Flags instance() {
    return INSTANCE;
  }

  /** It is necessary to call this method in main() in OSS. */
  public static void parse(String[] args) {
    new com.beust.jcommander.JCommander(instance()).parse(args);
  }

  private Flags() {}
}
