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


/**
 * All Device Infra flags.
 *
 * <p>Remember to sort all flags by @FlagSpec.name.
 */
@com.beust.jcommander.Parameters(separators = "=")
@SuppressWarnings({"NonPrivateFlag", "UnnecessarilyFullyQualified"})
public class Flags {

  @com.beust.jcommander.Parameter(
      names = "--adb",
      description = "Android adb path, overriding the SDK location.",
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
      names = "--extra_adb_keys",
      description =
          ("Colon-separated list of adb keys (files or directories) to be used (see ADB_VENDOR_KEYS"
              + " in adb --help for formatting details)."),
      converter = Flag.StringConverter.class)
  public Flag<String> adbKeyPathsFromUser = Flag.value("");

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
