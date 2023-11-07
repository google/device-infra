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

package com.google.devtools.deviceaction.common.utils;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.common.schemas.CommandHelp;
import com.google.devtools.deviceaction.common.schemas.CommandHelp.OptionDescription;
import java.io.PrintStream;

/** A utility class to print help info. */
public final class HelpUtil {
  private static final OptionDescription BUNDLETOOL_FLAG =
      OptionDescription.builder()
          .setFlag("da_bundletool")
          .setDescription("Path to the bundletool jar file.")
          .addExampleValues("bundletool.jar")
          .setIsOptional(false)
          .build();
  private static final OptionDescription CREDENTIAL_FLAG =
      OptionDescription.builder()
          .setFlag("da_cred_file")
          .setDescription("Path to GCS credential file.")
          .addExampleValues("key_file.json")
          .setIsOptional(false)
          .build();
  private static final OptionDescription ADB_FLAG =
      OptionDescription.builder()
          .setFlag("adb")
          .setDescription("Path to the adb binary.")
          .addExampleValues("adb")
          .build();
  private static final OptionDescription AAPT_FLAG =
      OptionDescription.builder()
          .setFlag("aapt")
          .setDescription("Path to the aapt binary.")
          .addExampleValues("aapt")
          .build();
  private static final OptionDescription JAVA_FLAG =
      OptionDescription.builder()
          .setFlag("java_command_path")
          .setDescription("Path to the java jdk.")
          .addExampleValues("/java/jdk/bin/java")
          .build();
  private static final OptionDescription GEN_DIR_FLAG =
      OptionDescription.builder()
          .setFlag("da_gen_file_dir")
          .setDescription("Directory for generated files.")
          .addExampleValues("/gen/file/dir")
          .build();
  private static final OptionDescription TMP_DIR_FLAG =
      OptionDescription.builder()
          .setFlag("tmp_dir_root")
          .setDescription("Directory for tmp files.")
          .addExampleValues("/tmp/file/dir")
          .build();
  private static final OptionDescription SERIAL_FLAG =
      OptionDescription.builder()
          .setFlag("device1")
          .setKey("serial")
          .setDescription("Serial id of the device.")
          .addExampleValues("id")
          .setIsOptional(false)
          .build();
  private static final OptionDescription DEVICE_CONFIG_FLAG =
      OptionDescription.builder()
          .setFlag("device1")
          .setKey("device_config")
          .setDescription("Device config file.")
          .addExampleValues("device_config.textproto")
          .build();
  private static final OptionDescription ENABLE_ROLLBACK_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("enable_rollback")
          .setDescription("Optional flag to enable rollback if install fails.")
          .build();
  private static final OptionDescription CLEAN_UP_SESSIONS_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("clean_up_sessions")
          .setDescription("Optional flag to clean up the sessions before install.")
          .build();
  private static final OptionDescription DEV_KEY_SIGNED_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("dev_key_signed")
          .setDescription("If the packages are signed by dev keys. It is false by default.")
          .build();
  private static final OptionDescription SKIP_CHECK_VERSION_AFTER_PUSH_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("skip_check_version_after_push")
          .setDescription("Optional flag to skip checking version after push.")
          .build();
  private static final OptionDescription CHECK_ROLLBACK_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("check_rollback")
          .setDescription(
              "Optional flag to check if the installation is rolled back by package watchdog.")
          .build();
  private static final OptionDescription SOFT_REBOOT_AFTER_PUSH_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("soft_reboot_after_push")
          .setDescription("Optional flag to do soft reboot after pushing modules.")
          .build();
  private static final OptionDescription TRAIN_FOLDER_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("file_train_folder")
          .setDescription("Path to the folder of mainline modules")
          .addExampleValues("folder")
          .setIsOptional(false)
          .build();
  private static final OptionDescription MAINLINE_MODULES_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("file_mainline_modules")
          .setDescription("Repeated flags of mainline modules")
          .addExampleValues("module1.apex", "module2.apk")
          .setIsOptional(false)
          .build();
  private static final OptionDescription ZIPS_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("file_apks_zips")
          .setDescription("Repeated flags of zipped train files.")
          .addExampleValues("train1.zip", "train2.zip")
          .setIsOptional(false)
          .build();
  private static final OptionDescription RECOVERY_MODULES_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("file_recovery_modules")
          .setDescription("Preloaded modules to recover the device.")
          .addExampleValues("gcs:project id&gs://bucket/uri")
          .build();
  private static final OptionDescription OTA_PACKAGE_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("file_ota_package")
          .setDescription("OTA package to sideload.")
          .addExampleValues("gcs:project id&gs://bucket/uri/ota.zip")
          .build();
  private static final OptionDescription IMAGE_ZIP_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("file_image_zip")
          .setDescription("Zip file of partition images to flash.")
          .addExampleValues("gcs:project id&gs://bucket/uri/image.zip")
          .build();
  private static final OptionDescription RESET_OPTION_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("reset_option")
          .setDescription("Specifies the option to reset a device, e.g., TEST_HARNESS.")
          .addExampleValues("TEST_HARNESS")
          .setIsOptional(false)
          .build();
  private static final OptionDescription NEED_PRELOAD_MODULES_RECOVERY_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("need_preload_modules_recovery")
          .setDescription("Optional flag to push recovery modules.")
          .build();
  private static final OptionDescription FLASH_SCRIPT_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("flash_script")
          .setDescription("Script to flash partition images.")
          .build();
  private static final OptionDescription FLASH_TIMEOUT_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("flash_timeout")
          .setDescription("Timeout to flash partition images.")
          .build();
  private static final OptionDescription SIDELOAD_TIMEOUT_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("sideload_timeout")
          .setDescription("Timeout to sideload an ota package.")
          .build();
  private static final OptionDescription USE_AUTO_REBOOT_FLAG =
      OptionDescription.builder()
          .setFlag("action")
          .setKey("use_auto_reboot")
          .setDescription("Auto reboot after sideload.")
          .build();
  private static final CommandHelp HELP =
      CommandHelp.builder()
          .setCommandName("<command> ...")
          .setCommandDescription(
              "Executes actions on devices.\n"
                  + "Assume the devices already have device config files in the database. Or you"
                  + " can pass a device config file by --device1"
                  + " device_config=<device_config.textproto>\n"
                  + "Use 'DeviceActionMain help <command>' to learn more about the given"
                  + " command.\n"
                  + "Use 'DeviceActionMain version' to check the version.\n")
          .addFlag("bundletool", BUNDLETOOL_FLAG)
          .addFlag("credential", CREDENTIAL_FLAG)
          .addFlag("java", JAVA_FLAG)
          .addFlag("adb", ADB_FLAG)
          .addFlag("aapt", AAPT_FLAG)
          .addFlag("gen", GEN_DIR_FLAG)
          .addFlag("tmp", TMP_DIR_FLAG)
          .build();
  private static final CommandHelp INSTALL_MAINLINE_HELP =
      CommandHelp.builder()
          .setCommandName("install_mainline")
          .setCommandDescription(
              "Installs mainline modules on one device.\n"
                  + "The mainline module files are specified by one of the three flags.\n"
                  + "\"mainline_modules\" for multiple module packages.\n"
                  + "\"train_folder\" for a folder containing module packages.\n"
                  + "\"apks_zips\" for multiple zipped trains\n")
          .addFlag("bundletool", BUNDLETOOL_FLAG)
          .addFlag("credential", CREDENTIAL_FLAG)
          .addFlag("java", JAVA_FLAG)
          .addFlag("adb", ADB_FLAG)
          .addFlag("aapt", AAPT_FLAG)
          .addFlag("gen", GEN_DIR_FLAG)
          .addFlag("tmp", TMP_DIR_FLAG)
          .addFlag("serial", SERIAL_FLAG)
          .addFlag("device-config", DEVICE_CONFIG_FLAG)
          .addFlag("enable_rollback", ENABLE_ROLLBACK_FLAG)
          .addFlag("clean_up_sessions", CLEAN_UP_SESSIONS_FLAG)
          .addFlag("dev_key_signed", DEV_KEY_SIGNED_FLAG)
          .addFlag("skip_check_version_after_push", SKIP_CHECK_VERSION_AFTER_PUSH_FLAG)
          .addFlag("check_rollback", CHECK_ROLLBACK_FLAG)
          .addFlag("soft_reboot_after_push", SOFT_REBOOT_AFTER_PUSH_FLAG)
          .addFlag("files", TRAIN_FOLDER_FLAG)
          .addFlag("files", MAINLINE_MODULES_FLAG)
          .addFlag("files", ZIPS_FLAG)
          .build();
  private static final CommandHelp RESET_HELP =
      CommandHelp.builder()
          .setCommandName("reset")
          .setCommandDescription("Resets a device to prepare for a test.\n")
          .addFlag("bundletool", BUNDLETOOL_FLAG)
          .addFlag("credential", CREDENTIAL_FLAG)
          .addFlag("java", JAVA_FLAG)
          .addFlag("adb", ADB_FLAG)
          .addFlag("aapt", AAPT_FLAG)
          .addFlag("gen", GEN_DIR_FLAG)
          .addFlag("tmp", TMP_DIR_FLAG)
          .addFlag("serial", SERIAL_FLAG)
          .addFlag("device-config", DEVICE_CONFIG_FLAG)
          .addFlag("files", RECOVERY_MODULES_FLAG)
          .addFlag("flash_files", OTA_PACKAGE_FLAG)
          .addFlag("flash_files", IMAGE_ZIP_FLAG)
          .addFlag("reset_option", RESET_OPTION_FLAG)
          .addFlag("need_preload_modules_recovery", NEED_PRELOAD_MODULES_RECOVERY_FLAG)
          .addFlag("flash_script", FLASH_SCRIPT_FLAG)
          .addFlag("flash_timeout", FLASH_TIMEOUT_FLAG)
          .addFlag("sideload_timeout", SIDELOAD_TIMEOUT_FLAG)
          .addFlag("use_auto_reboot", USE_AUTO_REBOOT_FLAG)
          .build();
  private static final ImmutableList<CommandHelp> COMMAND_HELPS =
      ImmutableList.of(INSTALL_MAINLINE_HELP, RESET_HELP);
  private final PrintStream out;

  public HelpUtil(PrintStream out) {
    this.out = out;
  }

  /** Prints the help info for the tool. */
  public void help() {
    HELP.printDetails(out);
    out.println();
    for (CommandHelp help : COMMAND_HELPS) {
      help.printSummary(out);
    }
  }

  /** Prints the help info for the {@code command}. */
  public void help(String command) throws DeviceActionException {
    switch (Command.of(command)) {
      case RESET:
        RESET_HELP.printDetails(out);
        break;
      case INSTALL_MAINLINE:
        INSTALL_MAINLINE_HELP.printDetails(out);
        break;
      default:
        break;
    }
  }
}
