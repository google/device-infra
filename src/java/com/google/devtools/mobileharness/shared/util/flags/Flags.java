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

package com.google.devtools.mobileharness.shared.util.flags;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.devtools.mobileharness.shared.util.flags.core.Flag;
import com.google.devtools.mobileharness.shared.util.flags.core.FlagConstraint;
import com.google.devtools.mobileharness.shared.util.flags.core.FlagSpec;
import com.google.devtools.mobileharness.shared.util.flags.core.ext.DurationFlag;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * All Device Infra flags.
 *
 * <p>Remember to sort all flags by @FlagSpec.name.
 *
 * <p>To parse flags at program entry points, please use {@code
 * com.google.devtools.mobileharness.shared.util.flags.core.FlagsManager.parse()} instead of {@code
 * com.google.common.flags.Flags.parse()}.
 *
 * <p>To set flags in unit tests, please use {@code
 * com.google.devtools.mobileharness.shared.util.flags.core.SetFlags}
 *
 * <pre>{@code
 * @Rule public final SetFlags flags = new SetFlags();
 *
 * @Test
 * public void test() {
 *   flags.set("flag_name", "flag_value");
 *   ...
 * }
 * }</pre>
 */
@SuppressWarnings({"NonPrivateFlag"})
public class Flags {

  @FlagSpec(name = "aapt", help = "Android AAPT path, overriding the SDK location")
  public static final Flag<String> aaptPath = Flag.value("");

  @SuppressWarnings("unused")
  @FlagSpec(name = "acloud_path", help = "Path to the acloud binary.")
  public static final Flag<String> acloudPath = Flag.value("/bin/acloud_prebuilt");

  @FlagSpec(name = "adb", help = "Android ADB path, overriding the SDK location.")
  public static final Flag<String> adbPathFromUser = Flag.value("");

  @FlagSpec(
      name = "adb_command_retry_attempts",
      help = "The max retry attempts for executing adb command. Default is 2.")
  public static final Flag<Integer> adbCommandRetryAttempts = Flag.value(2);

  @FlagSpec(
      name = "adb_command_retry_interval",
      help = "The wait interval between retry attempts for executing adb command. Default is 0.")
  public static final Flag<Duration> adbCommandRetryInterval = DurationFlag.zero();

  @FlagSpec(name = "adb_dont_kill_server", help = "Don't ever kill the adb server.")
  public static final Flag<Boolean> adbDontKillServer = Flag.value(false);

  /**
   * Force the reboot of adb server regardless of other flags conditions. e.g. If both this flag and
   * {@code adb_dont_kill_server} are set, this flag will override {@code adb_dont_kill_server}.
   */
  @FlagSpec(name = "adb_kill_server", help = "Force to kill the adb server.")
  public static final Flag<Boolean> adbForceKillServer = Flag.value(false);

  @FlagSpec(name = "adb_libusb", help = "Start the adb server with flag ADB_LIBUSB=1.")
  public static final Flag<Boolean> adbLibusb = Flag.value(false);

  @FlagSpec(
      name = "adb_max_no_device_detection_rounds",
      help =
          "The max rounds of detection when ADB detects no devices. If reaches, will restart ADB. 0"
              + " to disable the feature. Default is 20.")
  public static final Flag<Integer> adbMaxNoDeviceDetectionRounds = Flag.value(20);

  @FlagSpec(
      name = "add_required_dimension_for_partner_shared_pool",
      help = "Add the required dimension pool:partner_shared")
  public static final Flag<Boolean> addRequiredDimensionForPartnerSharedPool = Flag.value(false);

  @FlagSpec(
      name = "add_supported_dimension_for_omni_mode_usage",
      help = "Add the supported dimension for Omni mode usage.")
  public static final Flag<String> addSupportedDimensionForOmniModeUsage = Flag.nullString();

  @FlagSpec(
      name = "allow_insecure_plugin",
      help = "Whether to allow plugins built from untrusted sources.")
  public static final Flag<Boolean> allowInsecurePlugin = Flag.value(true);

  @FlagSpec(
      name = "alr_artifact",
      help =
          "Paths to test artifacts for the ATS local runner. Both directory paths and file paths"
              + " are supported.")
  public static final Flag<List<String>> alrArtifacts = Flag.stringList();

  @FlagSpec(
      name = "alr_olc_server_min_log_record_importance",
      help = "Minimum OLC server log record importance shown in ATS local runner. Default is 150.")
  public static final Flag<Integer> alrOlcServerMinLogRecordImportance = Flag.value(150);

  @FlagSpec(name = "alr_olc_server_path", help = "Path of OLC server for ATS local runner.")
  public static final Flag<String> alrOlcServerPath = Flag.nullString();

  @FlagSpec(
      name = "alr_serials",
      help = "Comma separated serials to specify devices for ATS local runner.")
  public static final Flag<List<String>> alrSerials = Flag.stringList();

  @FlagSpec(name = "alr_test_config", help = "Path to the test configuration for ATS local runner.")
  public static final Flag<String> alrTestConfig = Flag.nullString();

  @FlagSpec(
      name = "always_use_oss_detector_and_dispatcher",
      help = "True to always use OSS detectors and dispatchers. Default is false.")
  public static final Flag<Boolean> alwaysUseOssDetectorAndDispatcher = Flag.value(false);

  @FlagSpec(
      name = "android_account_manager_apk_path",
      help = "File path for the Android account manager apk.")
  public static final Flag<String> androidAccountManagerApkPath = Flag.value("");

  @FlagSpec(
      name = "android_account_manager_signed_apk_path",
      help = "File path for the Android account manager signed apk.")
  public static final Flag<String> androidAccountManagerSignedApkPath = Flag.value("");

  @FlagSpec(
      name = "android_auth_test_support_apk_path",
      help = "File path for the Android auth test support apk.")
  public static final Flag<String> androidAuthTestSupportApkPath = Flag.value("");

  @FlagSpec(
      name = "android_auth_test_support_signed_apk_path",
      help = "File path for the Android auth test support signed apk.")
  public static final Flag<String> androidAuthTestSupportSignedApkPath = Flag.value("");

  @FlagSpec(
      name = "android_desktop_executor_devices_num",
      help =
          "If the number is greater than 0, the lab server will create that many number of"
              + " AndroidDesktopExecutorDevices.")
  public static final Flag<Integer> androidDesktopExecutorDevicesNum = Flag.value(0);

  @FlagSpec(
      name = "android_desktop_executor_group",
      help =
          "The executor group of the Android Desktop device. Used to set network_zone dimension.")
  public static final Flag<String> androidDesktopExecutorGroup = Flag.value("");

  @FlagSpec(
      name = "android_device_daemon",
      help = "Whether to install Mobile Harness Android daemon app on the device.")
  public static final Flag<Boolean> enableDaemon = Flag.value(true);

  @FlagSpec(
      name = "android_factory_reset_wait_time",
      help =
          "The wait time for a device to be disconnected after calling factory reset. Default is 30"
              + " seconds.")
  public static final Flag<Duration> androidFactoryResetWaitTime = DurationFlag.seconds(30L);

  @FlagSpec(
      name = "android_jit_emulator_num",
      help =
          "The maximum number of android Just-in-time emulators that could be run on the server"
              + " simultaneously.")
  public static final Flag<Integer> androidJitEmulatorNum = Flag.value(0);

  @FlagSpec(name = "api_config", help = "Path of the text format protobuf API config file.")
  public static final Flag<String> apiConfigFile = Flag.value("");

  @FlagSpec(name = "as_on_borg", help = "Override the actual runtime system as Borg for debugging.")
  public static final Flag<Boolean> asOnBorg = Flag.value(false);

  @FlagSpec(
      name = "ats_console_always_restart_olc_server",
      help =
          "Whether to always restart OLC server (if possible) when starting ATS console instead of"
              + " reusing an existing one. Default is false.")
  public static final Flag<Boolean> atsConsoleAlwaysRestartOlcServer = Flag.value(false);

  @FlagSpec(
      name = "ats_console_cache_xts_devices",
      help = "Whether to cache devices during xTS execution in ATS console. Default is true.")
  public static final Flag<Boolean> atsConsoleCacheXtsDevices = Flag.value(true);

  @FlagSpec(
      name = "ats_console_list_device_timeout",
      help = "Timeout of listing devices in ATS console. Default is 3 seconds.")
  public static final Flag<Duration> atsConsoleListDeviceTimeout = DurationFlag.seconds(3L);

  @FlagSpec(
      name = "ats_console_min_log_record_importance",
      help = "Minimum console log record importance shown in ATS console. Default is 150.")
  public static final Flag<Integer> atsConsoleMinLogRecordImportance = Flag.value(150);

  @FlagSpec(
      name = "ats_console_olc_server_copy_server_resource",
      help =
          "Whether to copy OLC binary and JDK to xTS resource dir before ATS console starts OLC."
              + " Default is true.")
  public static final Flag<Boolean> atsConsoleOlcServerCopyServerResource = Flag.value(true);

  @FlagSpec(
      name = "ats_console_olc_server_embedded_mode",
      help = "Whether ATS console and OLC server are in the single process. Default is false.")
  public static final Flag<Boolean> atsConsoleOlcServerEmbeddedMode = Flag.value(false);

  @FlagSpec(
      name = "ats_console_olc_server_min_lab_version",
      help = "Minimum OLC server lab version string required by ATS console. Default is 4.309.1.")
  public static final Flag<String> atsConsoleOlcServerMinLabVersion = Flag.value("4.309.1");

  @FlagSpec(
      name = "ats_console_olc_server_min_log_record_importance",
      help = "Minimum OLC server log record importance shown in ATS console. Default is 150.")
  public static final Flag<Integer> atsConsoleOlcServerMinLogRecordImportance = Flag.value(150);

  @FlagSpec(
      name = "ats_console_olc_server_output_path",
      help = "Path of OLC server stdout/stderr in ATS console. Default is /dev/null.")
  public static final Flag<String> atsConsoleOlcServerOutputPath = Flag.value("/dev/null");

  @FlagSpec(name = "ats_console_olc_server_path", help = "Path of OLC server in ATS console.")
  public static final Flag<String> atsConsoleOlcServerPath = Flag.nullString();

  @FlagSpec(
      name = "ats_console_olc_server_starting_timeout",
      help = "OLC server starting timeout of ATS console. Default is 1 minutes")
  public static final Flag<Duration> atsConsoleOlcServerStartingTimeout = DurationFlag.minutes(1L);

  @FlagSpec(
      name = "ats_console_olc_server_xmx",
      help = "-Xmx of OLC server of ATS console. Default is \"24g\".")
  public static final Flag<String> atsConsoleOlcServerXmx = Flag.value("24g");

  @FlagSpec(
      name = "ats_console_print_above_input",
      help =
          "Whether to print ATS console output above the input line rather than in the input line."
              + " Default is true.")
  public static final Flag<Boolean> atsConsolePrintAboveInput = Flag.value(true);

  @FlagSpec(
      name = "ats_console_shutdown_wait_session_timeout",
      help = "When ATS console shuts down, timeout of waiting sessions end. Default is 10s.")
  public static final Flag<Duration> atsConsoleShutdownWaitSessionTimeout =
      DurationFlag.seconds(10L);

  @FlagSpec(
      name = "ats_dda_lease_expiration_time",
      help = "Lease expiration time of ATS DDA. Default is 5 minutes")
  public static final Flag<Duration> atsDdaLeaseExpirationTime = DurationFlag.minutes(5L);

  @FlagSpec(
      name = "ats_device_recovery_timeout",
      help = "The timeout for ATS pre and post test device recovery. Default is 5 minutes.")
  public static final Flag<Duration> atsDeviceRecoveryTimeout = DurationFlag.minutes(5L);

  @FlagSpec(
      name = "ats_device_removal_time",
      help = "The interval before removing a missing device. Default is 7 days.")
  public static final Flag<Duration> atsDeviceRemovalTime = DurationFlag.days(7L);

  @FlagSpec(
      name = "ats_file_server",
      help = "The ATS file server address:port, Default is localhost:8006.")
  public static final Flag<String> atsFileServer = Flag.value("localhost:8006");

  @FlagSpec(
      name = "ats_lab_removal_time",
      help = "The interval before removing a missing lab. Default is 7 days.")
  public static final Flag<Duration> atsLabRemovalTime = DurationFlag.days(7L);

  @FlagSpec(
      name = "ats_run_tf_on_android_real_device",
      help =
          "Whether to require to run ATS TF jobs on Android real device. Otherwise, Android "
              + "emulator is allowed. Default is false.")
  public static final Flag<Boolean> atsRunTfOnAndroidRealDevice = Flag.value(false);

  @FlagSpec(name = "ats_storage_path", help = "The ATS server storage path.")
  public static final Flag<String> atsStoragePath = Flag.value("/data");

  @FlagSpec(
      name = "ats_worker_grpc_port",
      help = "Grpc port for ATS worker connections. By default, it is 7031.")
  public static final Flag<Integer> atsWorkerGrpcPort = Flag.value(7031);

  @FlagSpec(
      name = "ats_xts_work_dir",
      help =
          "The work directory of ATS xTS process. Default value is empty string, which is to let"
              + " Omnilab lab server determine location.")
  public static final Flag<String> atsXtsWorkDir = Flag.value("");

  @FlagSpec(
      name = "cache_eviction_check_interval",
      help = "Interval to check cache eviction. Default is 5 minutes.")
  public static final Flag<Duration> cacheEvictionCheckInterval = DurationFlag.minutes(5L);

  @FlagSpec(
      name = "cache_eviction_trim_to_ratio",
      help = "Cache eviction will trim the cache to this ratio of the max cache size.")
  public static final Flag<Double> cacheEvictionTrimToRatio = Flag.value(0.8);

  @FlagSpec(
      name = "cache_installed_apks",
      help = "Cache installed apk in device property to avoid installing again.")
  public static final Flag<Boolean> cacheInstalledApks = Flag.value(true);

  @FlagSpec(
      name = "cache_pushed_files",
      help = "Cache pushed dirs/files with their MD5 in device property to avoid pushing again.")
  public static final Flag<Boolean> cachePushedFiles = Flag.value(false);

  @FlagSpec(
      name = "check_android_device_sim_card_type",
      help = "Whether to check the sim card type of Android devices. Default is false.")
  public static final Flag<Boolean> checkAndroidDeviceSimCardType = Flag.value(false);

  @FlagSpec(
      name = "check_device_interval",
      help = "Interval for checking devices in local device runner. Default is 5 minutes.")
  public static final Flag<Duration> checkDeviceInterval = DurationFlag.minutes(5L);

  @FlagSpec(
      name = "check_fastboot_tools",
      help = "Whether to check presence of fastboot tools on the host. Default is true.")
  public static final Flag<Boolean> checkFastbootTools = Flag.value(true);

  @FlagSpec(
      name = "check_file_interval",
      help = "For file cleaner, sleep interval for checking and removing expired files or dirs.")
  public static final Flag<Duration> checkFilesInterval = DurationFlag.minutes(5L);

  @FlagSpec(
      name = "check_insecure_plugin",
      help = "Whether to check plugins built from untrusted sources.")
  public static final Flag<Boolean> checkInsecurePlugin = Flag.value(false);

  @FlagSpec(
      name = "clear_android_device_multi_users",
      help = "Whether to clear multi users in device setup and post-test. Default is true.")
  public static final Flag<Boolean> clearAndroidDeviceMultiUsers = Flag.value(true);

  @FlagSpec(
      name = "cloud_file_transfer_download_shard_size",
      help = "Size (in megabytes) of shards during uploading")
  public static final Flag<Integer> cloudFileTransferDownloadShardSize = Flag.value(200);

  @FlagSpec(
      name = "cloud_file_transfer_initial_timeout",
      help = "Timeout while starting uploading/downloading.")
  public static final Flag<Duration> cloudFileTransferInitialTimeout = DurationFlag.seconds(5L);

  @FlagSpec(
      name = "cloud_file_transfer_maximum_attempts",
      help = "Attempts to transferring a file. Default is 3.")
  public static final Flag<Integer> cloudFileTransferMaximumAttempts = Flag.value(3);

  @FlagSpec(
      name = "cloud_file_transfer_small_file_size_kb",
      help = "The bytes limitation for a *small* file, which will send/get direct without GCS.")
  public static final Flag<Long> cloudFileTransferSmallFileSizeKb = Flag.value(256L);

  @FlagSpec(
      name = "cloud_file_transfer_timeout",
      help = "Retry times if failed to transfer a file. Default is 20 minutes.")
  public static final Flag<Duration> cloudFileTransferTimeout = DurationFlag.minutes(20L);

  @FlagSpec(
      name = "cloud_file_transfer_upload_shard_size",
      help = "Size (in megabytes) of shards during uploading")
  public static final Flag<Integer> cloudFileTransferUploadShardSize = Flag.value(200);

  @FlagSpec(
      name = "cloud_orchestrator_service_url",
      help = "The URL of the Cloud Orchestrator service.")
  public static final Flag<String> cloudOrchestratorServiceUrl = Flag.value("");

  @FlagSpec(name = "cloud_orchestrator_zone", help = "The zone of the Cloud Orchestrator service.")
  public static final Flag<String> cloudOrchestratorZone = Flag.value("local");

  @FlagSpec(name = "cloud_pubsub_cred_file", help = "The credential file to use for Cloud Pub/Sub.")
  public static final Flag<String> cloudPubsubCredFile = Flag.nullString();

  @FlagSpec(
      name = "cloud_pubsub_project_id",
      help = "The project ID of the Cloud Pub/Sub topic to upload monitoring data to.")
  public static final Flag<String> cloudPubsubProjectId = Flag.nullString();

  @FlagSpec(
      name = "cloud_pubsub_publish_interval",
      help = "The period duration between two publish actions.")
  public static final Flag<Duration> cloudPubsubPublishInterval = DurationFlag.minutes(1);

  @FlagSpec(
      name = "cloud_pubsub_topic_id",
      help = "The topic ID of the Cloud Pub/Sub topic to upload monitoring data to.")
  public static final Flag<String> cloudPubsubTopicId = Flag.nullString();

  @FlagSpec(
      name = "command_port",
      help = "Command port for the lab server to issue command to Daemon.")
  public static final Flag<Integer> commandPort = Flag.value(9995);

  @FlagSpec(name = "config_service_grpc_port", help = "gRPC port of the config service.")
  public static final Flag<Integer> configServiceGrpcPort = Flag.value(8081);

  @FlagSpec(name = "config_service_grpc_target", help = "gRPC target of the config service.")
  public static final Flag<String> configServiceGrpcTarget = Flag.value("localhost:8081");

  @FlagSpec(
      name = "config_service_jdbc_url",
      help = "The JDBC URL of the config service backend storage.")
  public static final Flag<String> configServiceJdbcUrl = Flag.value("jdbc:mysql:///ats_db");

  @FlagSpec(
      name = "config_service_local_storage_dir",
      help = "Local storage directory of the config service.")
  public static final Flag<String> configServiceLocalStorageDir = Flag.value("/tmp/ats/config");

  /** Backend storage type for the config service. */
  public enum ConfigServiceStorageType {
    LOCAL_FILE,
    JDBC_CONNECTOR
  }

  @FlagSpec(
      name = "config_service_storage_type",
      help = "Type of the backend storage for the config service.")
  public static final Flag<ConfigServiceStorageType> configServiceStorageType =
      Flag.value(ConfigServiceStorageType.LOCAL_FILE);

  @FlagSpec(
      name = "connect_to_lab_server_using_ip",
      help =
          "True to use IP to connect to lab servers and false to use host name."
              + "Default is false.")
  public static final Flag<Boolean> connectToLabServerUsingIp = Flag.value(false);

  @FlagSpec(
      name = "connect_to_lab_server_using_master_detected_ip",
      help =
          "True to use master-detected IP to connect to lab servers and false to use lab-reported"
              + " IP. Need connect_to_lab_server_using_ip to be true. Default is false.")
  public static final Flag<Boolean> connectToLabServerUsingMasterDetectedIp = Flag.value(false);

  @FlagSpec(name = "da_bundletool", help = "Path of bundletool jar for device action")
  public static final Flag<String> daBundletool = Flag.nullString();

  @FlagSpec(name = "da_cred_file", help = "Path to credential json file for use in device action.")
  public static final Flag<String> daCredFile = Flag.nullString();

  @FlagSpec(name = "da_gen_file_dir", help = "Path to device action gen file dir.")
  public static final Flag<String> daGenFileDir = Flag.nullString();

  @FlagSpec(
      name = "debug_random_exit",
      help = "Randomly exit and rely on prod scheduling for restart, only for debug/test purpose.")
  public static final Flag<Boolean> debugRandomExit = Flag.value(false);

  @FlagSpec(name = "detect_adb_device", help = "Whether to enable ADB detector. Default is true.")
  public static final Flag<Boolean> detectAdbDevice = Flag.value(true);

  @SuppressWarnings("NumericFlagWithTimeUnit")
  @FlagSpec(
      name = "detect_device_interval_sec",
      help = "Interval in seconds for detecting the current active devices.")
  public static final Flag<Integer> detectDeviceIntervalSec = Flag.value(1);

  @FlagSpec(name = "device_admin_apk_path", help = "Path to the device admin APK.")
  public static final Flag<String> deviceAdminApkPath = Flag.value("");

  @FlagSpec(name = "device_admin_cli_path", help = "Path to the device admin CLI binary.")
  public static final Flag<String> deviceAdminCliPath = Flag.value("");

  @FlagSpec(
      name = "device_admin_kms_key",
      help = "Path to the Cloud KMS key for signing device admin messages.")
  public static final Flag<String> deviceAdminKmsKey = Flag.value("");

  @FlagSpec(
      name = "device_admin_kms_key_cred",
      help =
          "Path to the credential file to access the KMS key specified by"
              + " --device_admin_kms_key.")
  public static final Flag<String> deviceAdminKmsKeyCred = Flag.value("");

  @FlagSpec(
      name = "device_admin_lock_required",
      help =
          "Whether to require the Android real device to be locked by device admin when setup."
              + " Default is false.")
  public static final Flag<Boolean> deviceAdminLockRequired = Flag.value(false);

  @FlagSpec(
      name = "device_list_to_debug_allocation",
      help = "The list of serial ids of the devices to debug allocation.")
  public static final Flag<List<String>> deviceListToDebugAllocation = Flag.stringList();

  @FlagSpec(
      name = "device_ping_google",
      help = "Whether to enable dimension ping_google_stability. Default is false.")
  public static final Flag<Boolean> pingGoogle = Flag.value(false);

  @FlagSpec(
      name = "device_removal_threshold",
      help = "Threshold for considering a device to be removed from Master.")
  public static final Flag<Duration> deviceRemovalThreshold = DurationFlag.days(14);

  @FlagSpec(name = "dexdump", help = "File path of the dexdump tool")
  public static final Flag<String> dexdumpPath = Flag.value("");

  @FlagSpec(
      name = "disable_calling",
      help =
          "Whether to disable outbound calling. "
              + "By default it is TRUE. After calling is disabled, only reboot can re-enable it.")
  public static final Flag<Boolean> disableCalling = Flag.value(true);

  @FlagSpec(
      name = "disable_cellbroadcastreceiver",
      help =
          "Whether to disable cellbroadcast receiver. It stops the device to receive any "
              + "message sent by cellbroadcast, e.g., emergency alert. Test runner is in charge to "
              + "enable cellbroadcast receiver if the test wants the function.")
  public static final Flag<Boolean> disableCellBroadcastReceiver = Flag.value(false);

  @FlagSpec(
      name = "disable_device_querier",
      help = "Whether to disable device querier in client. Default is false.")
  public static final Flag<Boolean> disableDeviceQuerier = Flag.value(false);

  @FlagSpec(
      name = "disable_device_reboot",
      help = "Whether to disable device reboot. Default is false.")
  public static final Flag<Boolean> disableDeviceReboot = Flag.value(false);

  @FlagSpec(
      name = "disable_device_reboot_for_ro_properties",
      help = "Whether to disable 'device reboot for read-only properties'. Default is false.")
  public static final Flag<Boolean> disableDeviceRebootForRoProperties = Flag.value(false);

  @FlagSpec(
      name = "disable_wait_for_device",
      help = "Whether to disable 'adb wait-for-device'. Default is false.")
  public static final Flag<Boolean> disableWaitForDevice = Flag.value(false);

  @FlagSpec(
      name = "disable_wifi_util_func",
      help = "Whether to disable WifiUtil functionality on device. Default is false.")
  public static final Flag<Boolean> disableWifiUtilFunc = Flag.value(false);

  @SuppressWarnings("DurationFlagWithUnits")
  @FlagSpec(
      name = "dispatch_device_interval_sec",
      help = "Interval for dispatching the current active devices")
  public static final Flag<Duration> dispatchDeviceInterval = DurationFlag.seconds(1L);

  @FlagSpec(
      name = "enable_android_device_ready_check",
      help = "Whether to enable android device ready check.")
  public static final Flag<Boolean> enableAndroidDeviceReadyCheck = Flag.value(true);

  @FlagSpec(
      name = "enable_ate_dual_stack",
      help = "Whether to enable ATE dual stack mode, which runs tests from both MH and TFC.")
  public static final Flag<Boolean> enableAteDualStack = Flag.value(false);

  @FlagSpec(
      name = "enable_ats_console_olc_server",
      help = "Whether to enable OmniLab long-running client in ATS console. Default is true.")
  public static final Flag<Boolean> enableAtsConsoleOlcServer = Flag.value(true);

  @FlagSpec(
      name = "enable_ats_console_olc_server_log",
      help =
          "If true, start printing OLC server streaming log in ATS console (run log command) when"
              + " console starts. If false, start/stop the streaming log when \"run\" command"
              + " starts/stops. Default is false.")
  public static final Flag<Boolean> enableAtsConsoleOlcServerLog = Flag.value(true);

  @FlagSpec(
      name = "enable_ats_mode",
      help = "Enable ATS mode if it's true. This flag is intended to serve ATS UI traffic only.")
  public static final Flag<Boolean> enableAtsMode = Flag.value(false);

  @FlagSpec(
      name = "enable_caching_reserved_device",
      help = "Whether to enable caching reserved device. Default is false.")
  public static final Flag<Boolean> enableCachingReservedDevice = Flag.value(false);

  @FlagSpec(
      name = "enable_client_experiment_manager",
      help = "Whether to enable client experiment manager. Default is true.")
  public static final Flag<Boolean> enableClientExperimentManager = Flag.value(true);

  @FlagSpec(
      name = "enable_client_file_transfer",
      help = "Whether to enable client file transfer. Default is true.")
  public static final Flag<Boolean> enableClientFileTransfer = Flag.value(true);

  @FlagSpec(
      name = "enable_cloud_file_transfer",
      help = "Whether enable cloud file transfer. Default is false.")
  public static final Flag<Boolean> enableCloudFileTransfer = Flag.value(false);

  @FlagSpec(name = "enable_cloud_logging", help = "Whether to enable cloud logging.")
  public static final Flag<Boolean> enableCloudLogging = Flag.value(true);

  @FlagSpec(
      name = "enable_cloud_metrics",
      help =
          "Whether to enable sending metrics to Google Cloud. It should only be enabled when"
              + " deploying in Google Cloud.")
  public static final Flag<Boolean> enableCloudMetrics = Flag.value(false);

  @FlagSpec(
      name = "enable_cloud_pubsub_monitoring",
      help = "Whether to enable sending lab monitoring data to Cloud Pub/Sub. Default is false.")
  public static final Flag<Boolean> enableCloudPubsubMonitoring = Flag.value(false);

  @FlagSpec(
      name = "enable_cts_verifier_result_reporter",
      help = "Whether enable result reporter for cts verifier.")
  public static final Flag<Boolean> enableCtsVerifierResultReporter = Flag.value(false);

  @FlagSpec(
      name = "enable_debug_mode",
      help = "Whether enable debug mode to print more detailed logs.")
  public static final Flag<Boolean> enableDebugMode = Flag.value(false);

  @FlagSpec(
      name = "enable_device_airplane_mode",
      help = "Turn device airplane mode on or off. True is on, false is off. Default is false.")
  public static final Flag<Boolean> enableDeviceAirplaneMode = Flag.value(false);

  @FlagSpec(
      name = "enable_device_config_manager",
      help = "Whether to enable device config manager. Default is true.")
  public static final Flag<Boolean> enableDeviceConfigManager = Flag.value(true);

  @FlagSpec(
      name = "enable_device_resource_service",
      help = "Whether to enable device resource service. default is false.")
  public static final Flag<Boolean> enableDeviceResourceService = Flag.value(false);

  @FlagSpec(
      name = "enable_device_state_change_recover",
      help =
          "Whether to change device state, like from recovery mode to normal mode, to recover the"
              + " device. Default is true.")
  public static final Flag<Boolean> enableDeviceStateChangeRecover = Flag.value(true);

  @FlagSpec(
      name = "enable_device_system_settings_change",
      help =
          "Whether to change device system settings, like enable/disable airplane mode, etc."
              + " Default is true.")
  public static final Flag<Boolean> enableDeviceSystemSettingsChange = Flag.value(true);

  @FlagSpec(
      name = "enable_device_test_decoupling",
      help = "Whether to enable device/test decoupling mode. Default is false.")
  public static final Flag<Boolean> enableDeviceTestDecoupling = Flag.value(false);

  @FlagSpec(
      name = "enable_disk_check",
      help = "For file cleaner, enable/disable checkDiskSpace in each check interval.")
  public static final Flag<Boolean> enableDiskCheck = Flag.value(true);

  // Removes this flag once b/152073867 is fixed
  @FlagSpec(
      name = "enable_emulator_detection",
      help =
          "Whether this lab server is enabled for emulator detection. When emulator detection is"
              + " disabled, the emulator device will be considered as the real device. Do NOT set"
              + " it to false in remote labs. Default is true.")
  public static final Flag<Boolean> enableEmulatorDetection = Flag.value(true);

  @FlagSpec(
      name = "enable_external_config_service",
      help = "Whether to enable external config service. Default is false.")
  public static final Flag<Boolean> enableExternalConfigService = Flag.value(false);

  @FlagSpec(
      name = "enable_external_master_server",
      help = "Whether to enable external master server. Default is false.")
  public static final Flag<Boolean> enableExternalMasterServer = Flag.value(false);

  @FlagSpec(
      name = "enable_failed_device_creation",
      help =
          "Whether the lab server should create FailedDevice when devices constantly fail to"
              + " initialize. In some rare use cases devices might not finish initialization but"
              + " still be able to work sometimes. This flag does not work in shared lab, the"
              + " default value of this flag is true.")
  public static final Flag<Boolean> createFailedDevice = Flag.value(true);

  @FlagSpec(
      name = "enable_fastboot_detector",
      help = "Whether to enable fastboot detector. Default is true.")
  public static final Flag<Boolean> enableFastbootDetector = Flag.value(true);

  @FlagSpec(
      name = "enable_fastboot_in_android_real_device",
      help =
          "Whether to enable fastboot support when initializing AndroidRealDevice."
              + " Default is true.")
  public static final Flag<Boolean> enableFastbootInAndroidRealDevice = Flag.value(true);

  @FlagSpec(name = "enable_file_cleaner", help = "Whether to enable file cleaner.")
  public static final Flag<Boolean> enableFileCleaner = Flag.value(true);

  @FlagSpec(
      name = "enable_file_system_io_check",
      help = "For file cleaner, enable/disable checkFileSystemIo in each check interval.")
  public static final Flag<Boolean> enableFileSystemIoCheck = Flag.value(true);

  @FlagSpec(
      name = "enable_grpc_lab_server",
      help = "Whether to enable gRPC connection to lab server. Default is false.")
  public static final Flag<Boolean> enableGrpcLabServer = Flag.value(false);

  @FlagSpec(name = "enable_grpc_relay", help = "Whether to enable gRPC relay. Default is false.")
  public static final Flag<Boolean> enableGrpcRelay = Flag.value(false);

  @FlagSpec(name = "enable_master_syncer", help = "Whether to enable master syncer in lab.")
  public static final Flag<Boolean> enableMasterSyncer = Flag.value(true);

  @FlagSpec(
      name = "enable_messaging_service",
      help = "Whether to enable OmniLab messaging service. Default is true.")
  public static final Flag<Boolean> enableMessagingService = Flag.value(true);

  @FlagSpec(
      name = "enable_mobly_resultstore_upload",
      help = "Whether to enable Mobly result store upload. Default is false.")
  public static final Flag<Boolean> enableMoblyResultstoreUpload = Flag.value(false);

  @FlagSpec(
      name = "enable_olc_publisher_extended_device_info",
      help = "Whether to enable extended device info in OLC publisher.")
  public static final Flag<Boolean> enableOlcPublisherExtendedDeviceInfo = Flag.value(false);

  @FlagSpec(name = "enable_paris", help = "Whether this server supports paris detection.")
  public static final Flag<Boolean> enableParis = Flag.value(false);

  @FlagSpec(name = "enable_persistent_cache", help = "Whether to enable persistent cache.")
  public static final Flag<Boolean> enablePersistentCache = Flag.value(false);

  @FlagSpec(name = "enable_proxy_mode", help = "Whether to enable proxy mode.")
  public static final Flag<Boolean> enableProxyMode = Flag.value(false);

  @FlagSpec(name = "enable_rdh", help = "Whether to enable the remote DeviceProxyHostService.")
  public static final Flag<Boolean> enableRdh = Flag.value(false);

  @FlagSpec(name = "enable_root_device", help = "Whether to root devices. Default is true.")
  public static final Flag<Boolean> enableRootDevice = Flag.value(true);

  @FlagSpec(
      name = "enable_simple_scheduler_shuffle",
      help =
          "Whether to enable the shuffle of the devices in the single scheduler, to randomly"
              + " allocate devices for the same requests. The default value is false.")
  public static final Flag<Boolean> enableSimpleSchedulerShuffle = Flag.value(false);

  @FlagSpec(
      name = "enable_stackdriver_debug_mode",
      help = "Whether enable debug mode to print more detailed logs in Stackdriver.")
  public static final Flag<Boolean> enableStackdriverDebugMode = Flag.value(false);

  @FlagSpec(
      name = "enable_stubby_file_transfer",
      help = "Whether to enable stubby file transfer. default is true.")
  public static final Flag<Boolean> enableStubbyFileTransfer = Flag.value(true);

  @FlagSpec(
      name = "enable_stubby_rpc_server",
      help = "Whether to enable stubby RPC server. default is true.")
  public static final Flag<Boolean> enableStubbyRpcServer = Flag.value(true);

  // TODO: b/444562857 - Remove this flag after figuring out why devices cannot be allocated with
  // required dimension.
  @FlagSpec(
      name = "enable_test_harness_check_for_required_tests",
      help =
          "Whether to enable test harness check for required tests in ATS server (b/444562857)."
              + " Default is false.")
  public static final Flag<Boolean> enableTestHarnessCheckForRequiredTests = Flag.value(false);

  @FlagSpec(
      name = "enable_test_log_collector",
      help = "Whether to enable test log collector. Default is false.")
  public static final Flag<Boolean> enableTestLogCollector = Flag.value(false);

  @FlagSpec(
      name = "enable_trace_span_processor",
      help = "Whether to enable trace span processor. Default is true.")
  public static final Flag<Boolean> enableTraceSpanProcessor = Flag.value(true);

  @FlagSpec(
      name = "enable_wrangler_agent_dummy_allocation",
      help =
          "Whether to enable dummy allocation (always return success without OLC communication).")
  public static final Flag<Boolean> enableWranglerAgentDummyAllocation = Flag.value(false);

  @FlagSpec(
      name = "enable_xts_dynamic_downloader",
      help = "Whether to enable xts dynamic downloader. Default is false.")
  public static final Flag<Boolean> enableXtsDynamicDownloader = Flag.value(false);

  @FlagSpec(
      name = "enable_xts_tradefed_invocation_agent",
      help = "Whether to enable xts tradefed invocation agent. Default is false.")
  public static final Flag<Boolean> enableXtsTradefedInvocationAgent = Flag.value(true);

  @FlagSpec(
      name = "enable_zombie_file_clean",
      help =
          "For file cleaner, enable/disable cleanZombieFile in each check interval. By default is"
              + " false.")
  public static final Flag<Boolean> enableZombieFileClean = Flag.value(false);

  @FlagSpec(
      name = "enforce_flash_safety_checks",
      help =
          "Whether to enable flash safety checks, which disables flash support when risky devices"
              + " don't have DPM installed.")
  public static final Flag<Boolean> enforceFlashSafetyChecks = Flag.value(false);

  @FlagSpec(
      name = "enforce_mtaas_device_checkin_group",
      help = "Whether to enforce the mtaas device checkin group on the device. Default is false.")
  public static final Flag<Boolean> enforceMtaasDeviceCheckinGroup = Flag.value(false);

  @FlagSpec(
      name = "enforce_safe_discharge",
      help =
          "Enable enforcing safe discharge mode for supported devices. For supported devices this "
              + "will try to keep battery level at safe_charge_level. For devices which do not "
              + "support safe_charge_level, this will try to turn charge off and on when reached "
              + "stop_charge_level and start_charge_level respectively.")
  public static final Flag<Boolean> enforceSafeDischarge = Flag.value(false);

  @FlagSpec(
      name = "ephemeral_removal_threshold",
      help =
          "If a MISSING ephemeral lab or device's last modify time is older than this threshold, it"
              + " will be removed from Master.")
  public static final Flag<Duration> ephemeralRemovalThreshold = DurationFlag.hours(1);

  @FlagSpec(
      name = "external_adb_initializer_template",
      help =
          "Whether to use the external adb initializer template to initialize adb. Default is"
              + " false.")
  public static final Flag<Boolean> externalAdbInitializerTemplate = Flag.value(true);

  @FlagSpec(
      name = "external_res_jar",
      help =
          "Absolute path to the jar file of external resources. This jar contains the resources"
              + " that are not in binary jar.")
  public static final Flag<String> externalResJar = Flag.value("");

  @FlagSpec(
      name = "extra_adb_keys",
      help =
          ("Colon-separated list of adb keys (files or directories) to be used (see ADB_VENDOR_KEYS"
              + " in adb --help for formatting details)."))
  public static final Flag<String> adbKeyPathsFromUser = Flag.value("");

  @FlagSpec(
      name = "extra_device_labels",
      help =
          "Device labels which will be appended to the dimensions of all the devices "
              + "in the current host.")
  public static final Flag<List<String>> extraDeviceLabels = Flag.stringList();

  @FlagSpec(name = "fastboot", help = "File path of the fastboot tool")
  public static final Flag<String> fastbootPathFromUser = Flag.value("");

  @FlagSpec(
      name = "fe_connect_to_config_server",
      help =
          "Whether to connect to the config server in the FE server. When true, both host and"
              + " device config will be enabled.")
  public static final Flag<Boolean> feConnectToConfigServer = Flag.value(false);

  @FlagSpec(name = "fe_grpc_port", help = "gRPC port to listen on for FE servers.")
  public static final Flag<Integer> feGrpcPort = Flag.value(8080);

  @FlagSpec(
      name = "file_expire_time",
      help =
          "For file cleaner, file expire time for default managed directories, such as receive file"
              + " directory.")
  public static final Flag<Duration> fileExpireTime = DurationFlag.hours(3L);

  @FlagSpec(
      name = "file_transfer_cloud_bucket",
      help = "Google Cloud Storage bucket of file transfer.")
  public static final Flag<String> fileTransferBucket = Flag.value("");

  @FlagSpec(
      name = "file_transfer_cloud_cache_ttl",
      help = "TTL of File Transfer caches in Google Cloud Storage. Default is 12 hours.")
  public static final Flag<Duration> fileTransferCloudCacheTtl = DurationFlag.hours(12);

  @FlagSpec(
      name = "file_transfer_cred_file",
      help = "The credential file path for the service account to use file transfer.")
  public static final Flag<String> fileTransferCredFile = Flag.nullString();

  @FlagSpec(
      name = "file_transfer_local_cache_ttl",
      help = "TTL of File Transfer caches in Google Cloud Storage. Default is 3 hour.")
  public static final Flag<Duration> fileTransferLocalCacheTtl = DurationFlag.hours(3);

  @FlagSpec(
      name = "force_device_reboot_after_test",
      help =
          "Whether to force a device reboot after each test. This option has the highest priority"
              + " to determine whether the device should reboot after each test. When this option"
              + " is true, other related flags (e.g. --disable_device_reboot) or related"
              + " implementations (e.g Device#canReboot()) may be ignored in some cases. This is an"
              + " advanced flag, make sure you understand the effects when using this flag. The"
              + " default value is false.")
  public static final Flag<Boolean> forceDeviceRebootAfterTest = Flag.value(false);

  @FlagSpec(name = "force_to_use_grpc", help = "Force to use GRPC for debugging.")
  public static final Flag<Boolean> forceToUseGrpc = Flag.value(false);

  @FlagSpec(
      name = "gcs_resolver_credential_file",
      help = "The credential file path for the service account to use GCS resolver.")
  public static final Flag<String> gcsResolverCredentialFile = Flag.nullString();

  @FlagSpec(name = "gcs_resolver_project_id", help = "The project ID of the GCS resolver.")
  public static final Flag<String> gcsResolverProjectId = Flag.nullString();

  @FlagSpec(
      name = "gcs_util_threads",
      help = "Thread pool size for uploading/downloading GCS files in parallel.")
  public static final Flag<Integer> gcsUtilThreads = Flag.value(50);

  @FlagSpec(
      name = "get_test_status_rpc_call_interval",
      help = "Default RPC call interval when getting the test result.")
  public static final Flag<Duration> getTestStatusRpcCallInterval = DurationFlag.seconds(5L);

  @FlagSpec(name = "grpc_port", help = "Port of server gRPC services. Default is 9994.")
  public static final Flag<Integer> grpcPort = Flag.value(9994);

  @FlagSpec(
      name = "ignore_check_device_failure",
      help = "Whether to ignore failures during checking device. Default is false.")
  public static final Flag<Boolean> ignoreCheckDeviceFailure = Flag.value(false);

  @FlagSpec(
      name = "internal_service_cred_file",
      help = "Path to the credential key file to access internal services.")
  public static final Flag<String> internalServiceCredentialFile = Flag.nullString();

  @FlagSpec(
      name = "internal_storage_alert_mb",
      help =
          "The threshold for insufficient internal storage alert. If the internal storage is lower"
              + " than the threshold, the device dimension 'internal_storage_status' will go from"
              + " 'ok' to 'low'. Unit is MB. Default is 200 MB.")
  public static final Flag<Integer> internalStorageAlert = Flag.value(200);

  @FlagSpec(
      name = "is_omni_mode",
      help = "Whether the ATS controller is in Omni mode. Default is false.")
  public static final Flag<Boolean> isOmniMode = Flag.value(false);

  @FlagSpec(name = "java_command_path", help = "The path of Java")
  public static final Flag<String> javaCommandPath = Flag.value("java");

  @FlagSpec(
      name = "job_configs_json",
      help =
          "File path of json string that is parsed from mobileharness.client.JobConfigs. It will "
              + "be overrode by the values defined in mobile_test blaze target. So *DO NOT* "
              + "specify it while using mobile_test. ")
  public static final Flag<String> jobConfigsJson = Flag.value("");

  @FlagSpec(
      name = "job_gen_file_expired_time",
      help =
          "How soon to clean up the genfile after each test. Default is 0, which means the genfile "
              + "is removed immediately when a test finishes. It has the risk to blow up the disk "
              + " of the lab host when setting to non zero.")
  public static final Flag<Duration> jobGenFileExpiredTime = DurationFlag.zero();

  @FlagSpec(
      name = "keep_test_harness_false",
      help =
          "If true, keep the device property persist.sys.test_harness false by adding required"
              + " dimension. Moreover reset_device_in_android_real_device_setup flag can't be true"
              + " in this case. Only turn on the flag in omni mode for CTS device pool.")
  public static final Flag<Boolean> keepTestHarnessFalse = Flag.value(false);

  @FlagSpec(
      name = "lab_device_config",
      help = "Path of the text format protobuf lab device config file.")
  public static final Flag<String> labDeviceConfigFile = Flag.value("");

  @FlagSpec(
      name = "lab_expiration_threshold",
      help =
          "If a lab's last modify time is older than this threshold, it will be marked as missing.")
  public static final Flag<Duration> labExpirationThreshold = DurationFlag.minutes(4);

  @FlagSpec(
      name = "lab_removal_threshold",
      help =
          "If a MISSING lab's last modify time is older than this threshold, it will be removed"
              + " from Master.")
  public static final Flag<Duration> labRemovalThreshold = DurationFlag.days(30);

  @FlagSpec(
      name = "lab_server_check_jobs_from_master",
      help =
          "If true, lab server will periodically check jobs from master to remove expired jobs."
              + " Default is true.")
  public static final Flag<Boolean> labServerCheckJobsFromMaster = Flag.value(true);

  @FlagSpec(name = "lab_type", help = "The type of the lab server (e.g., Satellite, Core, SLaaS).")
  public static final Flag<String> labType = Flag.value("Satellite");

  @SuppressWarnings("unused")
  @FlagSpec(
      name = "local_tenant_device_config_path",
      help = "Path of the text format protobuf for the local tenant device config file.")
  public static final Flag<String> localTenantDeviceConfigPath = Flag.value("");

  /** Source for the tenant device config. */
  public enum TenantConfigMode {
    NOOP,
    LOCAL,
    REMOTE
  }

  @FlagSpec(name = "log_file_number", help = "Max number of the rotated log files in local host.")
  public static final Flag<Integer> logFileNumber = Flag.value(100);

  @FlagSpec(
      name = "log_file_size_no_limit",
      help = "True to write all log content into one file. Default is false.")
  public static final Flag<Boolean> logFileSizeNoLimit = Flag.value(false);

  @FlagSpec(
      help =
          "The interval in seconds between end of last periodic log uploading to the start of the"
              + " next one.",
      name = "log_upload_delay")
  public static final Flag<Duration> logUploadDelay = DurationFlag.seconds(60);

  @FlagSpec(
      name = "logger_console_handler_min_log_record_importance",
      help =
          "Minimum console log record importance shown in System.err. Check LogRecordImportance for"
              + " importance of log records. Default is 100.")
  public static final Flag<Integer> loggerConsoleHandlerMinLogRecordImportance = Flag.value(100);

  @FlagSpec(name = "long_ping_timeout", help = "Set the default timeout for long ping commands.")
  public static final Flag<Duration> longPingTimeout = DurationFlag.minutes(1L);

  @FlagSpec(
      help =
          "The lower limit of jvm -Xmx that allows to generate allocation diagnostic without OOM.",
      name = "lower_limit_of_jvm_max_memory_allow_for_allocation_diagnostic")
  public static final Flag<Long> lowerLimitOfJvmMaxMemoryAllowForAllocationDiagnostic =
      Flag.value(512L * 1024 * 1024);

  @FlagSpec(
      name = "master_central_database_jdbc_property",
      help = "Master central database JDBC property. e.g. user=root,password=password")
  public static final Flag<Map<String, String>> masterCentralDatabaseJdbcProperty =
      Flag.stringMap();

  @FlagSpec(
      name = "master_central_database_jdbc_url",
      help = "Master central database JDBC URL, e.g. jdbc:mysql://localhost/master_db.")
  public static final Flag<String> masterCentralDatabaseJdbcUrl = Flag.nullString();

  /** The backend type of the master central and scheduler database. */
  public enum MasterDatabaseBackend {
    INFRA_SPANNER,
    MYSQL
  }

  @FlagSpec(
      name = "master_database_backend",
      help = "The backend type of the master central and scheduler database.")
  public static final Flag<MasterDatabaseBackend> masterDatabaseBackend =
      Flag.value(MasterDatabaseBackend.MYSQL);

  @FlagSpec(
      name = "master_grpc_target",
      help =
          "gRPC target string of master server. Default is localhost:9990. See"
              + " ManagedChannelBuilder.forTarget().")
  public static final Flag<String> masterGrpcTarget = Flag.value("localhost:9990");

  @FlagSpec(
      name = "max_concurrent_adb_push_large_file",
      help = "Maximum number of concurrent ADB push commands for large files")
  public static final Flag<Integer> maxConcurrentAdbPushLargeFile = Flag.nonnegativeValue(4);

  @FlagSpec(
      name = "max_concurrent_flash_device",
      help =
          "Maximum number of concurrent device flashing. "
              + "Do not set this flag too larger than max_concurrent_adb_push_large_file, "
              + "because flashing img to different partitions is controlled by that flag. "
              + "Setting this flag too larger may cause cache device timeout if there are "
              + "many devices on the lab to be flashed at the same time.")
  public static final Flag<Integer> maxConcurrentFlashDevice = Flag.nonnegativeValue(2);

  @FlagSpec(
      name = "max_concurrent_unzip_large_file",
      help = "Maximum number of concurrent large file unzipping")
  public static final Flag<Integer> maxConcurrentUnzipLargeFile = Flag.nonnegativeValue(2);

  @FlagSpec(
      name = "max_consecutive_get_test_status_error_duration",
      help =
          "How long we can wait for the next successful RPC call before marking the test as ERROR"
              + " when the RPC fails.")
  public static final Flag<Duration> maxConsecutiveGetTestStatusErrorDuration =
      DurationFlag.seconds(1L);

  @FlagSpec(
      name = "max_drain_timeout",
      help =
          "Maximum timeout for releases to be drained. The release will be marked as DRAINED if it"
              + " exceeds the timeout. This timeout may be overwritten by the maxDrainTimeout from"
              + " the Daemon Server if it is shorter. Default is 3 days.")
  public static final Flag<Duration> maxDrainTimeout = DurationFlag.days(3L);

  @FlagSpec(
      name = "max_persistent_cache_size_in_gigabytes",
      help = "Maximum size in gigabytes for persistent cache.")
  public static final Flag<Long> maxPersistentCacheSizeInGigabytes = Flag.value(200L);

  @FlagSpec(
      name = "mh_adb_command_default_redirect_stderr",
      help =
          "Default redirect_stderr setting for each Device Infra(DI) ADB command executed by DI Adb"
              + " library. Default is true (stderr will be redirected to stdout).")
  public static final Flag<Boolean> defaultAdbCommandRedirectStderr = Flag.value(true);

  @FlagSpec(
      name = "mh_adb_command_extra_timeout",
      help =
          "Extra timeout for each Device Infra(DI) ADB command executed by DI Adb library. Default"
              + " is 0. Example: '4m'. When DI Adb library (used by most of DI Android utilities)"
              + " executes an ADB command, the timeout of the command will be the original timeout"
              + " plus this extra timeout. For example, when the extra timeout is 4 minutes, if an"
              + " ADB command does not specify timeout (uses the default 5 minutes timeout), then"
              + " the timeout will be 9 minutes, if an ADB command specifies 10 seconds timeout,"
              + " then the timeout will be 4 minutes and 10 seconds.")
  public static final Flag<Duration> extraAdbCommandTimeout = DurationFlag.zero();

  @FlagSpec(
      name = "mh_dm_max_init_failures_before_fail",
      help =
          "After how many INIT failures do we consider the device to be a FailedDevice. The default"
              + " value is 3 times.")
  public static final Flag<Integer> maxInitFailuresBeforeFail = Flag.value(3);

  @FlagSpec(name = "mhproxy_spec", help = "GSLB blade target for MH Proxy.")
  public static final Flag<String> mhProxySpec = Flag.value("");

  @FlagSpec(
      name = "monitor_cloudrpc",
      help = "Whether enable the cloudrpc monitor. default is true.")
  public static final Flag<Boolean> monitorCloudRpc = Flag.value(true);

  @FlagSpec(name = "monitor_gcs", help = "Whether enable the gcs monitor. default is true.")
  public static final Flag<Boolean> monitorGcs = Flag.value(true);

  @SuppressWarnings("unused")
  @FlagSpec(name = "monitor_lab", help = "Whether enable the lab monitor. default is true.")
  public static final Flag<Boolean> monitorLab = Flag.value(true);

  @FlagSpec(name = "monitor_signals", help = "Whether to monitor signals. Default is true.")
  public static final Flag<Boolean> monitorSignals = Flag.value(true);

  @FlagSpec(
      name = "mute_android",
      help =
          "Whether to mute Android rooted devices. "
              + "By default it is TRUE. After a device is muted, only reboot can re-enable it.")
  public static final Flag<Boolean> muteAndroid = Flag.value(true);

  @SuppressWarnings("BooleanFlagNameStartingWithNo")
  @FlagSpec(
      name = "no_op_device_num",
      help = "The number of NoOpDevice to be started. If set all other devices will be disabled.")
  public static final Flag<Integer> noOpDeviceNum = Flag.value(0);

  @SuppressWarnings("BooleanFlagNameStartingWithNo")
  @FlagSpec(
      name = "no_op_device_random_offline",
      help = "If enabled, randomly take some NoOpDevice offline.")
  public static final Flag<Boolean> noOpDeviceRandomOffline = Flag.value(false);

  @SuppressWarnings("BooleanFlagNameStartingWithNo")
  @FlagSpec(name = "no_op_device_start_index", help = "The start index of NoOpDevice.")
  public static final Flag<Integer> noOpDeviceStartIndex = Flag.value(0);

  @SuppressWarnings("BooleanFlagNameStartingWithNo")
  @FlagSpec(
      name = "no_op_device_type",
      help = "Device type string supported, e.g. AndroidRealDevice, only for debug/test purpose.")
  public static final Flag<String> noOpDeviceType = Flag.value("");

  @SuppressWarnings("BooleanFlagNameStartingWithNo")
  @FlagSpec(
      name = "no_op_lab_server",
      help =
          "If true, the lab server will sleep forever rather than starting services and connecting"
              + " to master. Default is false.")
  public static final Flag<Boolean> noOpLabServer = Flag.value(false);

  @SuppressWarnings("BooleanFlagNameStartingWithNo")
  @FlagSpec(
      name = "noop_jit_emulator",
      help =
          "Make jit emulator no-op and work as placeholder, delegating actual device creation and"
              + " launch fully to the underlying Tradefed driver.")
  public static final Flag<Boolean> noopJitEmulator = Flag.value(false);

  @FlagSpec(name = "olc_database_jdbc_property", help = "OLC database JDBC property.")
  public static final Flag<Map<String, String>> olcDatabaseJdbcProperty = Flag.stringMap();

  @FlagSpec(name = "olc_database_jdbc_url", help = "OLC database JDBC URL.")
  public static final Flag<String> olcDatabaseJdbcUrl = Flag.nullString();

  @FlagSpec(
      name = "olc_server_max_started_running_session_num",
      help = "OLC server max started and running session number. Default is 200.")
  public static final Flag<Integer> olcServerMaxStartedRunningSessionNum = Flag.value(200);

  @FlagSpec(name = "olc_server_port", help = "OLC server port. By default, it is 7030.")
  public static final Flag<Integer> olcServerPort = Flag.value(7030);

  @FlagSpec(name = "paris_path", help = "The user provided path to run paris health check/repair")
  public static final Flag<String> parisPath = Flag.value("");

  @FlagSpec(
      name = "perfetto_script_path",
      help = "File path for the perfetto script used by the Perfetto decorator.")
  public static final Flag<String> perfettoScriptPath = Flag.value("");

  @FlagSpec(name = "persistent_cache_dir", help = "Root directory for persistent cache.")
  public static final Flag<String> persistentCacheDir = Flag.nullString();

  @FlagSpec(
      name = "prepare_device_after_test",
      help = "If true, prepare the device after test. Default is false.")
  public static final Flag<Boolean> prepareDeviceAfterTest = Flag.value(false);

  @FlagSpec(
      name = "print_lab_stats",
      help =
          "If true, print binary stats of Lab, and return silently. All other settings will be "
              + "ignored.")
  public static final Flag<Boolean> printLabStats = Flag.value(false);

  @FlagSpec(
      name = "proxy_mode_lease_devices_immediately",
      help = "Always lease all devices immediately in proxy mode. Default is true.")
  public static final Flag<Boolean> proxyModeLeaseDevicesImmediately = Flag.value(true);

  @FlagSpec(name = "public_dir", help = "The public directory.")
  public static final Flag<String> publicDir = Flag.value(getPublicDirDefaultOss());

  private static String getPublicDirDefaultOss() {
    return "/tmp";
  }

  @FlagSpec(
      name = "real_time_job",
      help = "If this flag is true, all submitted jobs will run as real-time jobs.")
  public static final Flag<Boolean> realTimeJob = Flag.value(false);

  @FlagSpec(
      name = "real_time_test",
      help = "If this flag is true, all tests will run as real-time tests.")
  public static final Flag<Boolean> realTimeTest = Flag.value(false);

  @FlagSpec(
      name = "remove_job_gen_files_when_finished",
      help = "If this flag is true, all job generated files are removed after the job is done.")
  public static final Flag<Boolean> removeJobGenFilesWhenFinished = Flag.value(false);

  @FlagSpec(
      name = "reset_device_in_android_real_device_setup",
      help =
          "If this flag is true, Android real device will be reset first in setup process. The flag"
              + " can't be set to true if keep_test_harness_false is true. Default is false.")
  public static final Flag<Boolean> resetDeviceInAndroidRealDeviceSetup = Flag.value(false);

  @FlagSpec(name = "resource_dir_name", help = "Name of resource directory.")
  public static final Flag<String> resDirName = Flag.value("mh_res_files");

  @FlagSpec(
      name = "restrict_olc_service_to_users",
      help =
          "A list of authorized users. If the list is nonempty, restrict the OLC service to users"
              + " on the list.")
  public static final Flag<List<String>> restrictOlcServiceToUsers = Flag.stringList();

  @FlagSpec(
      name = "reverse_tunneling_lab_server",
      help = "Whether lab servers have been reverse tunneled to client. Default is false.")
  public static final Flag<Boolean> reverseTunnelingLabServer = Flag.value(false);

  @FlagSpec(name = "rpc_port", help = "Stubby port of the server")
  public static final Flag<Integer> rpcPort = Flag.value(9999);

  @FlagSpec(
      name = "run_dynamic_download_mcts_only",
      help = "If true, only run dynamic download mcts. Default is false.")
  public static final Flag<Boolean> runDynamicDownloadMctsOnly = Flag.value(false);

  @FlagSpec(
      name = "safe_charge_level",
      help =
          "Battery level devices should be kept at. Devices will be charged at most to this level."
              + "Only works for devices which support this (i.e., marlin, sailfish).")
  public static final Flag<Integer> safeChargeLevel = Flag.value(50);

  @FlagSpec(
      name = "serv_via_cloud_rpc",
      help = "Whether to serve the inbound gRPC requests via Cloud RPC. Default is true.")
  public static final Flag<Boolean> servViaCloudRpc = Flag.value(true);

  @FlagSpec(
      name = "set_test_harness_property",
      help =
          "Whether to set ro.test_harness property on Android devices. If set restarting Zygote"
              + " will skip setup wizard. By default, it is TRUE.")
  public static final Flag<Boolean> setTestHarnessProperty = Flag.value(true);

  @FlagSpec(
      name = "should_manage_devices",
      help =
          "Whether the lab server should actively manage and recover devices from bad state, or"
              + " just let a test fail. True for traditional deployments, false for labs where some"
              + " other component manages and recovers the devices, e.g. http://go/m&mlabs. For"
              + " Shared Lab, it is false. For Satellite Lab, it is true. By default, it is true.")
  public static final Flag<Boolean> shouldManageDevices = Flag.value(true);

  @FlagSpec(
      name = "simplified_log_format",
      help = "True to use simplified log format. Default is false.")
  public static final Flag<Boolean> simplifiedLogFormat = Flag.value(false);

  @FlagSpec(
      name = "skip_check_device_internet",
      help =
          "Whether to skip checking device connect to Internet via ping. Default is false. When set"
              + " to true, it means you have confidence that the device can successfully connect to"
              + " Internet, and OmniLab will set dimension internet to true without checking the"
              + " connection.")
  public static final Flag<Boolean> skipCheckDeviceInternet = Flag.value(false);

  @FlagSpec(
      name = "skip_connect_device_to_wifi",
      help = "Whether to skip connecting device to their default wifi network. Default is false.")
  public static final Flag<Boolean> skipConnectDeviceToWifi = Flag.value(false);

  @FlagSpec(
      name = "skip_lab_job_gen_file_cleanup",
      help =
          "whether to skip job gen file cleanup when job ended. Default is false. Use when the gen"
              + " files are placed in /tmp directory and make sure they can be cleaned by operating"
              + " system.")
  public static final Flag<Boolean> skipLabJobGenFileCleanup = Flag.value(false);

  @FlagSpec(
      name = "skip_network",
      help =
          "Whether to skip network connection when set up and periodically check the device."
              + " Default is false. Only used for satellite labs")
  public static final Flag<Boolean> skipNetwork = Flag.value(false);

  @FlagSpec(
      name = "skip_recover_device_network",
      help =
          "Whether to skip recovering device network by connecting device to saved ssid. Default "
              + "is false.")
  public static final Flag<Boolean> skipRecoverDeviceNetwork = Flag.value(false);

  @FlagSpec(
      name = "socket_port",
      help = "Socket port of the file transfer service of the lab server")
  public static final Flag<Integer> socketPort = Flag.value(9998);

  @FlagSpec(
      name = "stackdriver_cred_file",
      help = "Path to the credential key file for stackdriver api.")
  public static final Flag<String> stackdriverCredentialFile = Flag.nullString();

  @FlagSpec(name = "stackdriver_gcp_project_name", help = "The GCP name of stackdriver logging")
  public static final Flag<String> stackdriverGcpProjectName = Flag.value("");

  @FlagSpec(name = "stackdriver_resource_type", help = "The resource type of stackdriver logging")
  public static final Flag<String> stackdriverResourceType = Flag.value("deployment");

  @FlagSpec(
      name = "start_charge_level",
      help =
          "Battery level at which charging should start. Only works for devices which support this "
              + "(i.e., angler, bullhead)")
  public static final Flag<Integer> startChargeLevel = Flag.value(40);

  @FlagSpec(
      name = "stop_charge_level",
      help =
          "Battery level at which charging should stop. Only works for devices which support this "
              + "(i.e., angler, bullhead)")
  public static final Flag<Integer> stopChargeLevel = Flag.value(80);

  // The flag for dynamically loading resource files from the supplemental directory instead of
  // unpacking from the JAR binary. It allows updating resource files without rebuilding the JAR
  // binary. Please only use it for local development and do not use it in production.
  // See b/255255107.
  @FlagSpec(
      name = "supplemental_res_dir",
      help =
          "Absolute path to the supplemental resource directory. If a resource exists in the"
              + " supplemental dir, this util won't extract it from the jar package. Please do not"
              + " use it in production environment.")
  public static final Flag<String> supplementalResDir = Flag.value("");

  @SuppressWarnings("unused")
  @FlagSpec(
      name = "tenant_device_config_mode",
      help = "Source for the tenant device config. One of NOOP, LOCAL, or REMOTE.")
  public static final Flag<TenantConfigMode> tenantConfigMode = Flag.value(TenantConfigMode.NOOP);

  @FlagSpec(
      name = "testbed_config_paths",
      help = "The source to load the local testbed configurations from.")
  public static final Flag<List<String>> testbedConfigPaths =
      Flag.stringList("/usr/local/google/mobileharness/testbeds");

  @FlagSpec(name = "tmp_dir_root", help = "The tmp Dir Root.")
  public static final Flag<String> tmpDirRoot = Flag.value(getTmpDirRootDefaultOss());

  private static String getTmpDirRootDefaultOss() {
    return Strings.nullToEmpty(System.getenv("HOME")) + "/mobileharness";
  }

  @FlagSpec(name = "tradefed_binary_dir", help = "The directory of tradefed binaries.")
  public static final Flag<String> tradefedBinaryDir = Flag.value("/bin/tradefed");

  @FlagSpec(
      name = "tradefed_curl_download_limit_rate",
      help =
          "The limit rate of curl download for Tradefed. The value should be given with a letter"
              + " suffix using one of K, M and G for kilobytes, megabytes and gigabytes per second."
              + " Default is null and curl will try to saturate all available network connections.")
  public static final Flag<String> tradefedCurlDownloadLimitRate = Flag.nullString();

  @FlagSpec(name = "tradefed_host_config", help = "The host config xml file for tradefed to use.")
  public static final Flag<String> tradefedHostConfig = Flag.value("");

  @FlagSpec(
      name = "transfer_resources_from_controller",
      help =
          "Whether to transfer all resources from the controller to workers. The default is true."
              + " Only set it to false when the controller and workers cross different networks.")
  public static final Flag<Boolean> transferResourcesFromController = Flag.value(true);

  @FlagSpec(
      name = "use_alts",
      help = "Use ALTS for OLC server auth.This is supported by GCP vm. The default is false.")
  public static final Flag<Boolean> useAlts = Flag.value(false);

  @FlagSpec(
      name = "use_emulator_name_in_uuid",
      help =
          "Whether to use the emulator name in the device UUID. This is to make Omnilab device"
              + " naming scheme consistent with ATS server's. Default is false.")
  public static final Flag<Boolean> useEmulatorNameInUuid = Flag.value(false);

  @FlagSpec(name = "use_tf_retry", help = "Delegate retry to TF. The default is false.")
  public static final Flag<Boolean> useTfRetry = Flag.value(false);

  @FlagSpec(
      name = "virtual_device_server_ip",
      help = "The IP address of the remote virtual device server.")
  public static final Flag<String> virtualDeviceServerIp = Flag.value("");

  @FlagSpec(
      name = "virtual_device_server_username",
      help = "The username of the remote virtual device server.")
  public static final Flag<String> virtualDeviceServerUsername = Flag.value("");

  @FlagSpec(
      name = "xts_disable_tf_result_log",
      help = "Disable xTS TF result logs in terminal. Default is true.")
  public static final Flag<Boolean> xtsDisableTfResultLog = Flag.value(true);

  @FlagSpec(name = "xts_jdk_dir", help = "The xTS JDK directory.")
  public static final Flag<String> xtsJdkDir = Flag.value("");

  @FlagSpec(name = "xts_res_dir_root", help = "The xTS resources dir root.")
  public static final Flag<String> xtsResDirRoot = Flag.value(getXtsResDirRootDefaultOss());

  private static String getXtsResDirRootDefaultOss() {
    return Strings.nullToEmpty(System.getenv("HOME")) + "/xts";
  }

  @FlagSpec(
      name = "xts_retry_report_merger_parallel_test_case_merge",
      help =
          "Whether to merge test cases in parallel in the xTS retry report merger. Default is"
              + " false.")
  public static final Flag<Boolean> xtsRetryReportMergerParallelTestCaseMerge = Flag.value(false);

  @FlagSpec(name = "xts_server_res_dir_root", help = "The xTS server resources dir root.")
  public static final Flag<String> xtsServerResDirRoot =
      Flag.value(getXtsServerResDirRootDefaultOss());

  private static String getXtsServerResDirRootDefaultOss() {
    return Strings.nullToEmpty(System.getenv("HOME")) + "/xts_server";
  }

  @FlagSpec(name = "xts_tf_xmx", help = "-Xmx of TF of TradefedTest. Default is \"24g\".")
  public static final Flag<String> xtsTfXmx = Flag.value("24g");

  @FlagConstraint
  public static void checkConstraints() {
    checkArgument(
        !Flags.resetDeviceInAndroidRealDeviceSetup.getNonNull()
            || !Flags.keepTestHarnessFalse.getNonNull(),
        "--reset_device_in_android_real_device_setup and --keep_test_harness_false cannot be both"
            + " true.");
  }

  private Flags() {}
}
