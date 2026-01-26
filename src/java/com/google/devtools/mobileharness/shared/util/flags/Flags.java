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
import static com.google.devtools.mobileharness.shared.util.flags.Flags.TenantConfigMode.NOOP;
import static java.util.Arrays.stream;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * All Device Infra flags.
 *
 * <p>Remember to sort all flags by @FlagSpec.name.
 */
@com.beust.jcommander.Parameters(separators = "=")
@SuppressWarnings({
  "BooleanFlagNameStartingWithNo",
  "NonPrivateFlag",
  "UnnecessarilyFullyQualified",
  "unused"
})
public class Flags {

  private static final Flag<String> aaptPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--aapt",
      description = "Android AAPT path.",
      converter = Flag.StringConverter.class)
  public Flag<String> aaptPath = aaptPathDefault;

  private static final Flag<String> acloudPathDefault = Flag.value("/bin/acloud_prebuilt");

  @com.beust.jcommander.Parameter(
      names = "--acloud_path",
      description = "Path to the acloud binary.",
      converter = Flag.StringConverter.class)
  public Flag<String> acloudPath = acloudPathDefault;

  private static final Flag<Integer> adbCommandRetryAttemptsDefault = Flag.value(2);

  @com.beust.jcommander.Parameter(
      names = "--adb_command_retry_attempts",
      description = "The max retry attempts for executing adb command. Default is 2.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> adbCommandRetryAttempts = adbCommandRetryAttemptsDefault;

  private static final Flag<Duration> adbCommandRetryIntervalDefault =
      DurationFlag.value(Duration.ZERO);

  @com.beust.jcommander.Parameter(
      names = "--adb_command_retry_interval",
      description =
          "The wait interval between retry attempts for executing adb command. Default is 0.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> adbCommandRetryInterval = adbCommandRetryIntervalDefault;

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

  private static final Flag<Integer> adbMaxNoDeviceDetectionRoundsDefault = Flag.value(20);

  @com.beust.jcommander.Parameter(
      names = "--adb_max_no_device_detection_rounds",
      description =
          "The max rounds of detection when ADB detects no devices. If reaches, will restart ADB. 0"
              + " to disable the feature. Default is 20.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> adbMaxNoDeviceDetectionRounds = adbMaxNoDeviceDetectionRoundsDefault;

  private static final Flag<Boolean> addRequiredDimensionForPartnerSharedPoolDefault =
      Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--add_required_dimension_for_partner_shared_pool",
      description = "Add the required dimension pool:partner_shared",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> addRequiredDimensionForPartnerSharedPool =
      addRequiredDimensionForPartnerSharedPoolDefault;

  private static final Flag<String> addSupportedDimensionForOmniModeUsageDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--add_supported_dimension_for_omni_mode_usage",
      description = "Add the supported dimension for Omni mode usage.",
      converter = Flag.StringConverter.class)
  public Flag<String> addSupportedDimensionForOmniModeUsage =
      addSupportedDimensionForOmniModeUsageDefault;

  private static final Flag<List<String>> alrArtifactsDefault = Flag.stringList();

  @com.beust.jcommander.Parameter(
      names = "--alr_artifact",
      description =
          "Paths to test artifacts for the ATS local runner. Both directory paths and file paths"
              + " are supported.",
      converter = Flag.StringListConverter.class)
  public Flag<List<String>> alrArtifacts = alrArtifactsDefault;

  private static final Flag<Integer> alrOlcServerMinLogRecordImportanceDefault = Flag.value(150);

  @com.beust.jcommander.Parameter(
      names = "--alr_olc_server_min_log_record_importance",
      description =
          "Minimum OLC server log record importance shown in ATS local runner. Default is 150.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> alrOlcServerMinLogRecordImportance =
      alrOlcServerMinLogRecordImportanceDefault;

  private static final Flag<String> alrOlcServerPathDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--alr_olc_server_path",
      description = "Path of OLC server for ATS local runner.",
      converter = Flag.StringConverter.class)
  public Flag<String> alrOlcServerPath = alrOlcServerPathDefault;

  private static final Flag<List<String>> alrSerialsDefault = Flag.stringList();

  @com.beust.jcommander.Parameter(
      names = "--alr_serials",
      description = "Comma separated serials to specify devices for ATS local runner.",
      converter = Flag.StringListConverter.class)
  public Flag<List<String>> alrSerials = alrSerialsDefault;

  private static final Flag<String> alrTestConfigDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--alr_test_config",
      description = "Path to the test configuration for ATS local runner.",
      converter = Flag.StringConverter.class)
  public Flag<String> alrTestConfig = alrTestConfigDefault;

  private static final Flag<Boolean> alwaysUseOssDetectorAndDispatcherDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--always_use_oss_detector_and_dispatcher",
      description = "True to always use OSS detectors and dispatchers. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> alwaysUseOssDetectorAndDispatcher = alwaysUseOssDetectorAndDispatcherDefault;

  private static final Flag<String> androidAccountManagerApkPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--android_account_manager_apk_path",
      description = "File path for the Android account manager apk.",
      converter = Flag.StringConverter.class)
  public Flag<String> androidAccountManagerApkPath = androidAccountManagerApkPathDefault;

  private static final Flag<String> androidAccountManagerSignedApkPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--android_account_manager_signed_apk_path",
      description = "File path for the Android account manager signed apk.",
      converter = Flag.StringConverter.class)
  public Flag<String> androidAccountManagerSignedApkPath =
      androidAccountManagerSignedApkPathDefault;

  private static final Flag<String> androidAuthTestSupportApkPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--android_auth_test_support_apk_path",
      description = "File path for the Android auth test support apk.",
      converter = Flag.StringConverter.class)
  public Flag<String> androidAuthTestSupportApkPath = androidAuthTestSupportApkPathDefault;

  private static final Flag<String> androidAuthTestSupportSignedApkPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--android_auth_test_support_signed_apk_path",
      description = "File path for the Android auth test support signed apk.",
      converter = Flag.StringConverter.class)
  public Flag<String> androidAuthTestSupportSignedApkPath =
      androidAuthTestSupportSignedApkPathDefault;

  private static final Flag<Boolean> enableDaemonDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--android_device_daemon",
      description = "Whether to install Mobile Harness Android daemon app on the device.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDaemon = enableDaemonDefault;

  private static final Flag<Duration> androidFactoryResetWaitTimeDefault =
      DurationFlag.value(Duration.ofSeconds(30L));

  @com.beust.jcommander.Parameter(
      names = "--android_factory_reset_wait_time",
      description =
          "The wait time for a device to be disconnected after calling factory reset."
              + " Default is 30 seconds.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> androidFactoryResetWaitTime = androidFactoryResetWaitTimeDefault;

  private static final Flag<Integer> androidJitEmulatorNumDefault = Flag.value(0);

  @com.beust.jcommander.Parameter(
      names = "--android_jit_emulator_num",
      description =
          "The naximum number of android Just-in-time emulators that could be run on the server"
              + " simultaneously.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> androidJitEmulatorNum = androidJitEmulatorNumDefault;

  private static final Flag<String> apiConfigFileDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--api_config",
      description = "Path of the text format protobuf API config file.",
      converter = Flag.StringConverter.class)
  public Flag<String> apiConfigFile = apiConfigFileDefault;

  private static final Flag<Boolean> asOnBorgDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--as_on_borg",
      description = "Override the actual runtime system as Borg for debugging.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> asOnBorg = asOnBorgDefault;

  private static final Flag<Boolean> atsConsoleAlwaysRestartOlcServerDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--ats_console_always_restart_olc_server",
      description =
          "Whether to always restart OLC server (if possible) when starting ATS console instead of"
              + " reusing an existing one. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> atsConsoleAlwaysRestartOlcServer = atsConsoleAlwaysRestartOlcServerDefault;

  private static final Flag<Boolean> atsConsoleCacheXtsDevicesDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--ats_console_cache_xts_devices",
      description =
          "Whether to cache devices during xTS execution in ATS console. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> atsConsoleCacheXtsDevices = atsConsoleCacheXtsDevicesDefault;

  private static final Flag<Duration> atsConsoleListDeviceTimeoutDefault =
      DurationFlag.value(Duration.ofSeconds(3L));

  @com.beust.jcommander.Parameter(
      names = "--ats_console_list_device_timeout",
      description = "Timeout of listing devices in ATS console. Default is 3 seconds.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> atsConsoleListDeviceTimeout = atsConsoleListDeviceTimeoutDefault;

  private static final Flag<Integer> atsConsoleMinLogRecordImportanceDefault = Flag.value(150);

  @com.beust.jcommander.Parameter(
      names = "--ats_console_min_log_record_importance",
      description = "Minimum console log record importance shown in ATS console. Default is 150.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> atsConsoleMinLogRecordImportance = atsConsoleMinLogRecordImportanceDefault;

  private static final Flag<Boolean> atsConsoleOlcServerCopyServerResourceDefault =
      Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--ats_console_olc_server_copy_server_resource",
      description =
          "Whether to copy OLC binary and JDK to xTS resource dir before ATS console starts OLC."
              + " Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> atsConsoleOlcServerCopyServerResource =
      atsConsoleOlcServerCopyServerResourceDefault;

  private static final Flag<String> atsConsoleOlcServerMinLabVersionDefault = Flag.value("4.309.1");

  @com.beust.jcommander.Parameter(
      names = "--ats_console_olc_server_min_lab_version",
      description =
          "Minimum OLC server lab version string required by ATS console. Default is 4.309.1",
      converter = Flag.StringConverter.class)
  public Flag<String> atsConsoleOlcServerMinLabVersion = atsConsoleOlcServerMinLabVersionDefault;

  private static final Flag<Integer> atsConsoleOlcServerMinLogRecordImportanceDefault =
      Flag.value(150);

  @com.beust.jcommander.Parameter(
      names = "--ats_console_olc_server_min_log_record_importance",
      description =
          "Minimum OLC server log record importance shown in ATS console. Default is 150.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> atsConsoleOlcServerMinLogRecordImportance =
      atsConsoleOlcServerMinLogRecordImportanceDefault;

  private static final Flag<String> atsConsoleOlcServerOutputPathDefault = Flag.value("/dev/null");

  @com.beust.jcommander.Parameter(
      names = "--ats_console_olc_server_output_path",
      description = "Path of OLC server stdout/stderr in ATS console. Default is /dev/null.",
      converter = Flag.StringConverter.class)
  public Flag<String> atsConsoleOlcServerOutputPath = atsConsoleOlcServerOutputPathDefault;

  private static final Flag<String> atsConsoleOlcServerPathDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--ats_console_olc_server_path",
      description = "Path of OLC server in ATS console.",
      converter = Flag.StringConverter.class)
  public Flag<String> atsConsoleOlcServerPath = atsConsoleOlcServerPathDefault;

  private static final Flag<Boolean> atsConsoleOlcServerEmbeddedModeDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--ats_console_olc_server_embedded_mode",
      description =
          "Whether ATS console and OLC server are in the single process. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> atsConsoleOlcServerEmbeddedMode = atsConsoleOlcServerEmbeddedModeDefault;

  private static final Flag<Duration> atsConsoleOlcServerStartingTimeoutDefault =
      DurationFlag.value(Duration.ofMinutes(1L));

  @com.beust.jcommander.Parameter(
      names = "--ats_console_olc_server_starting_timeout",
      description = "OLC server starting timeout of ATS console. Default is 1 minutes",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> atsConsoleOlcServerStartingTimeout =
      atsConsoleOlcServerStartingTimeoutDefault;

  private static final Flag<String> atsConsoleOlcServerXmxDefault = Flag.value("24g");

  @com.beust.jcommander.Parameter(
      names = "--ats_console_olc_server_xmx",
      description = "-Xmx of OLC server of ATS console. Default is \"24g\".",
      converter = Flag.StringConverter.class)
  public Flag<String> atsConsoleOlcServerXmx = atsConsoleOlcServerXmxDefault;

  private static final Flag<Boolean> atsConsolePrintAboveInputDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--ats_console_print_above_input",
      description =
          "Whether to print ATS console output above the input line rather than in the input line."
              + " Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> atsConsolePrintAboveInput = atsConsolePrintAboveInputDefault;

  private static final Flag<Duration> atsConsoleShutdownWaitSessionTimeoutDefault =
      DurationFlag.value(Duration.ofSeconds(10L));

  @com.beust.jcommander.Parameter(
      names = "--ats_console_shutdown_wait_session_timeout",
      description = "When ATS console shuts down, timeout of waiting sessions end. Default is 10s.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> atsConsoleShutdownWaitSessionTimeout =
      atsConsoleShutdownWaitSessionTimeoutDefault;

  private static final Flag<Duration> atsDdaLeaseExpirationTimeDefault =
      DurationFlag.value(Duration.ofMinutes(5L));

  @com.beust.jcommander.Parameter(
      names = "--ats_dda_lease_expiration_time",
      description = "Lease expiration time of ATS DDA. Default is 5 minutes",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> atsDdaLeaseExpirationTime = atsDdaLeaseExpirationTimeDefault;

  private static final Flag<Duration> atsDeviceRecoveryTimeoutDefault =
      DurationFlag.value(Duration.ofMinutes(5L));

  @com.beust.jcommander.Parameter(
      names = "--ats_device_recovery_timeout",
      description = "The timeout for ATS pre and post test device recovery. Default is 5 minutes.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> atsDeviceRecoveryTimeout = atsDeviceRecoveryTimeoutDefault;

  private static final Flag<Duration> atsDeviceRemovalTimeDefault =
      DurationFlag.value(Duration.ofDays(7L));

  @com.beust.jcommander.Parameter(
      names = "--ats_device_removal_time",
      description = "The interval before removing a missing device. Default is 7 days.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> atsDeviceRemovalTime = atsDeviceRemovalTimeDefault;

  private static final Flag<String> atsFileServerDefault = Flag.value("localhost:8006");

  @com.beust.jcommander.Parameter(
      names = "--ats_file_server",
      description = "The ATS file server address:port, Default is localhost:8006.",
      converter = Flag.StringConverter.class)
  public Flag<String> atsFileServer = atsFileServerDefault;

  private static final Flag<Duration> atsLabRemovalTimeDefault =
      DurationFlag.value(Duration.ofDays(7L));

  @com.beust.jcommander.Parameter(
      names = "--ats_lab_removal_time",
      description = "The interval before removing a missing lab. Default is 7 days.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> atsLabRemovalTime = atsLabRemovalTimeDefault;

  private static final Flag<Boolean> atsRunTfOnAndroidRealDeviceDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--ats_run_tf_on_android_real_device",
      description =
          "Whether to require to run ATS TF jobs on Android real device. Otherwise, Android "
              + "emulator is allowed. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> atsRunTfOnAndroidRealDevice = atsRunTfOnAndroidRealDeviceDefault;

  private static final Flag<String> atsStoragePathDefault = Flag.value("/data");

  @com.beust.jcommander.Parameter(
      names = "--ats_storage_path",
      description = "The ATS storage path, Default is /data.",
      converter = Flag.StringConverter.class)
  public Flag<String> atsStoragePath = atsStoragePathDefault;

  private static final Flag<Integer> atsWorkerGrpcPortDefault = Flag.value(7031);

  @com.beust.jcommander.Parameter(
      names = "--ats_worker_grpc_port",
      description = "Grpc port for ATS worker connections. By default, it is 7031.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> atsWorkerGrpcPort = atsWorkerGrpcPortDefault;

  private static final Flag<String> atsXtsWorkDirDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--ats_xts_work_dir",
      description = "The work directory of ATS xTS process.",
      converter = Flag.StringConverter.class)
  public Flag<String> atsXtsWorkDir = atsXtsWorkDirDefault;

  private static final Flag<Duration> cacheEvictionCheckIntervalDefault =
      DurationFlag.value(Duration.ofMinutes(5L));

  @com.beust.jcommander.Parameter(
      names = "--cache_eviction_check_interval",
      description = "Interval to check cache eviction. Default is 5 minutes.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> cacheEvictionCheckInterval = cacheEvictionCheckIntervalDefault;

  private static final Flag<Double> cacheEvictionTrimToRatioDefault = Flag.value(0.8);

  @com.beust.jcommander.Parameter(
      names = "--cache_eviction_trim_to_ratio",
      description = "Cache eviction will trim the cache to this ratio of the max cache size.",
      converter = Flag.DoubleConverter.class)
  public Flag<Double> cacheEvictionTrimToRatio = cacheEvictionTrimToRatioDefault;

  private static final Flag<Boolean> cacheInstalledApksDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--cache_installed_apks",
      description = "Cache installed apk in device property to avoid installing again.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> cacheInstalledApks = cacheInstalledApksDefault;

  private static final Flag<Boolean> cachePushedFilesDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--cache_pushed_files",
      description =
          "Cache pushed dirs/files with their MD5 in device property to avoid pushing again."
              + "Disabled by default.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> cachePushedFiles = cachePushedFilesDefault;

  private static final Flag<Boolean> checkAndroidDeviceSimCardTypeDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--check_android_device_sim_card_type",
      description = "Whether to check the sim card type of Android devices. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> checkAndroidDeviceSimCardType = checkAndroidDeviceSimCardTypeDefault;

  private static final Flag<Duration> checkDeviceIntervalDefault =
      DurationFlag.value(Duration.ofMinutes(5L));

  @com.beust.jcommander.Parameter(
      names = "--check_device_interval",
      description =
          "Interval to check device in local device runner when the device is IDLE. "
              + "Default is \"5m\"",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> checkDeviceInterval = checkDeviceIntervalDefault;

  private static final Flag<Duration> checkFilesIntervalDefault =
      DurationFlag.value(Duration.ofMinutes(5L));

  @com.beust.jcommander.Parameter(
      names = "--check_file_interval",
      description =
          "For file cleaner, sleep interval for checking and removing expired files or dirs.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> checkFilesInterval = checkFilesIntervalDefault;

  private static final Flag<Boolean> clearAndroidDeviceMultiUsersDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--clear_android_device_multi_users",
      description = "Whether to clear multi users in device setup and post-test. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> clearAndroidDeviceMultiUsers = clearAndroidDeviceMultiUsersDefault;

  private static final Flag<Integer> cloudFileTransferMaximumAttemptsDefault = Flag.value(3);

  @com.beust.jcommander.Parameter(
      names = "--cloud_file_transfer_maximum_attempts",
      description = "Attempts to transferring a file. Default is 3.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> cloudFileTransferMaximumAttempts = cloudFileTransferMaximumAttemptsDefault;

  private static final Flag<Duration> cloudFileTransferTimeoutDefault =
      DurationFlag.value(Duration.ofMinutes(20L));

  @com.beust.jcommander.Parameter(
      names = "--cloud_file_transfer_timeout",
      description = "Retry times if failed to transfer a file. Default is 20 minutes.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> cloudFileTransferTimeout = cloudFileTransferTimeoutDefault;

  private static final Flag<Integer> cloudFileTransferUploadShardSizeDefault = Flag.value(200);

  @com.beust.jcommander.Parameter(
      names = "--cloud_file_transfer_upload_shard_size",
      description = "Size (in megabytes) of shards during uploading",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> cloudFileTransferUploadShardSize = cloudFileTransferUploadShardSizeDefault;

  private static final Flag<Integer> cloudFileTransferDownloadShardSizeDefault = Flag.value(200);

  @com.beust.jcommander.Parameter(
      names = "--cloud_file_transfer_download_shard_size",
      description = "Size (in megabytes) of shards during uploading",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> cloudFileTransferDownloadShardSize =
      cloudFileTransferDownloadShardSizeDefault;

  private static final Flag<Duration> cloudFileTransferInitialTimeoutDefault =
      DurationFlag.value(Duration.ofSeconds(5L));

  @com.beust.jcommander.Parameter(
      names = "--cloud_file_transfer_initial_timeout",
      description = "Timeout while starting uploading/downloading.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> cloudFileTransferInitialTimeout = cloudFileTransferInitialTimeoutDefault;

  private static final Flag<Long> cloudFileTransferSmallFileSizeKbDefault = Flag.value(256L);

  @com.beust.jcommander.Parameter(
      names = "--cloud_file_transfer_small_file_size_kb",
      description =
          "The bytes limitation for a *small* file, which will send/get direct without GCS.",
      converter = Flag.LongConverter.class)
  public Flag<Long> cloudFileTransferSmallFileSizeKb = cloudFileTransferSmallFileSizeKbDefault;

  private static final Flag<String> cloudPubsubCredFileDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--cloud_pubsub_cred_file",
      description = "The credential file to use for Cloud Pub/Sub.",
      converter = Flag.StringConverter.class)
  public Flag<String> cloudPubsubCredFile = cloudPubsubCredFileDefault;

  private static final Flag<String> cloudPubsubProjectIdDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--cloud_pubsub_project_id",
      description = "The project ID of the Cloud Pub/Sub topic to upload monitoring data to.",
      converter = Flag.StringConverter.class)
  public Flag<String> cloudPubsubProjectId = cloudPubsubProjectIdDefault;

  private static final Flag<String> cloudPubsubTopicIdDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--cloud_pubsub_topic_id",
      description = "The topic ID of the Cloud Pub/Sub topic to upload monitoring data to.",
      converter = Flag.StringConverter.class)
  public Flag<String> cloudPubsubTopicId = cloudPubsubTopicIdDefault;

  private static final Flag<Duration> cloudPubsubPublishIntervalDefault =
      DurationFlag.value(Duration.ofMinutes(1L));

  @com.beust.jcommander.Parameter(
      names = "--cloud_pubsub_publish_interval",
      description = "The period duration between two publish actions.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> cloudPubsubPublishInterval = cloudPubsubPublishIntervalDefault;

  private static final Flag<Integer> commandPortDefault = Flag.value(9995);

  @com.beust.jcommander.Parameter(
      names = "--command_port",
      description = "Command port for the lab server to issue command to Daemon.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> commandPort = commandPortDefault;

  private static final Flag<Integer> configServiceGrpcPortDefault = Flag.value(8081);

  @com.beust.jcommander.Parameter(
      names = "--config_service_grpc_port",
      description = "gRPC port of the config service.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> configServiceGrpcPort = configServiceGrpcPortDefault;

  private static final Flag<String> configServiceGrpcTargetDefault = Flag.value("localhost:8081");

  @com.beust.jcommander.Parameter(
      names = "--config_service_grpc_target",
      description = "gRPC target of the config service.",
      converter = Flag.StringConverter.class)
  public Flag<String> configServiceGrpcTarget = configServiceGrpcTargetDefault;

  private static final Flag<String> configServiceJdbcUrlDefault =
      Flag.value("jdbc:mysql:///ats_db");

  @com.beust.jcommander.Parameter(
      names = "--config_service_jdbc_url",
      description = "The JDBC URL of the config service backend storage.",
      converter = Flag.StringConverter.class)
  public Flag<String> configServiceJdbcUrl = configServiceJdbcUrlDefault;

  private static final Flag<String> configServiceLocalStorageDirDefault =
      Flag.value("/tmp/ats/config");

  @com.beust.jcommander.Parameter(
      names = "--config_service_local_storage_dir",
      description = "Local storage directory of the config service.",
      converter = Flag.StringConverter.class)
  public Flag<String> configServiceLocalStorageDir = configServiceLocalStorageDirDefault;

  /** Backend storage type for the config service. */
  public enum ConfigServiceStorageType {
    LOCAL_FILE,
    JDBC_CONNECTOR
  }

  private static final Flag<ConfigServiceStorageType> configServiceStorageTypeDefault =
      Flag.value(ConfigServiceStorageType.LOCAL_FILE);

  @com.beust.jcommander.Parameter(
      names = "--config_service_storage_type",
      description = "Type of the backend storage for the config service.")
  public Flag<ConfigServiceStorageType> configServiceStorageType = configServiceStorageTypeDefault;

  private static final Flag<Boolean> connectToLabServerUsingIpDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--connect_to_lab_server_using_ip",
      description =
          "True to use IP to connect to lab servers and false to use host name."
              + "Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> connectToLabServerUsingIp = connectToLabServerUsingIpDefault;

  private static final Flag<Boolean> connectToLabServerUsingMasterDetectedIpDefault =
      Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--connect_to_lab_server_using_master_detected_ip",
      description =
          "True to use master-detected IP to connect to lab servers and false to use lab-reported"
              + " IP. Need connect_to_lab_server_using_ip to be true. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> connectToLabServerUsingMasterDetectedIp =
      connectToLabServerUsingMasterDetectedIpDefault;

  private static final Flag<String> daBundletoolDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--da_bundletool",
      description = "Path of bundletool jar for device action",
      converter = Flag.StringConverter.class)
  public Flag<String> daBundletool = daBundletoolDefault;

  private static final Flag<String> daCredFileDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--da_cred_file",
      description = "Path to credential json file for use in device action.",
      converter = Flag.StringConverter.class)
  public Flag<String> daCredFile = daCredFileDefault;

  private static final Flag<String> daGenFileDirDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--da_gen_file_dir",
      description = "Path to device action gen file dir.",
      converter = Flag.StringConverter.class)
  public Flag<String> daGenFileDir = daGenFileDirDefault;

  private static final Flag<Boolean> debugRandomExitDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--debug_random_exit",
      description =
          "Randomly exit and rely on prod scheduling for restart, only for debug/test purpose.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> debugRandomExit = debugRandomExitDefault;

  private static final Flag<Boolean> detectAdbDeviceDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--detect_adb_device",
      description = "Whether to enable ADB detector. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> detectAdbDevice = detectAdbDeviceDefault;

  private static final Flag<Integer> detectDeviceIntervalSecDefault = Flag.value(1);

  @com.beust.jcommander.Parameter(
      names = "--detect_device_interval_sec",
      description = "Interval in seconds for detecting the current active devices.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> detectDeviceIntervalSec = detectDeviceIntervalSecDefault;

  private static final Flag<String> deviceAdminApkPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--device_admin_apk_path",
      description = "Path to the device admin APK.",
      converter = Flag.StringConverter.class)
  public Flag<String> deviceAdminApkPath = deviceAdminApkPathDefault;

  private static final Flag<String> deviceAdminCliPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--device_admin_cli_path",
      description = "Path to the device admin CLI binary.",
      converter = Flag.StringConverter.class)
  public Flag<String> deviceAdminCliPath = deviceAdminCliPathDefault;

  private static final Flag<String> deviceAdminKmsKeyDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--device_admin_kms_key",
      description = "Path to the KMS key for signing device admin messages.",
      converter = Flag.StringConverter.class)
  public Flag<String> deviceAdminKmsKey = deviceAdminKmsKeyDefault;

  private static final Flag<String> deviceAdminKmsKeyCredDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--device_admin_kms_key_cred",
      description =
          "Path to the credetinal file to access the KMS key specified by --device_admin_kms_key.",
      converter = Flag.StringConverter.class)
  public Flag<String> deviceAdminKmsKeyCred = deviceAdminKmsKeyCredDefault;

  private static final Flag<Boolean> deviceAdminLockRequiredDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--device_admin_lock_required",
      description =
          "Whether to require the Android real device to be locked by device admin when setup."
              + " Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> deviceAdminLockRequired = deviceAdminLockRequiredDefault;

  private static final Flag<List<String>> deviceListToDebugAllocationDefault = Flag.stringList();

  @com.beust.jcommander.Parameter(
      names = "--device_list_to_debug_allocation",
      description = "The list of serial ids of the devices to debug allocation.",
      converter = Flag.StringListConverter.class)
  public Flag<List<String>> deviceListToDebugAllocation = deviceListToDebugAllocationDefault;

  private static final Flag<Duration> deviceRemovalThresholdDefault =
      DurationFlag.value(Duration.ofDays(14));

  @com.beust.jcommander.Parameter(
      names = "--device_removal_threshold",
      description = "Threshold for considering a device to be removed from Master.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> deviceRemovalThreshold = deviceRemovalThresholdDefault;

  private static final Flag<Boolean> enforceMtaasDeviceCheckinGroupDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enforce_mtaas_device_checkin_group",
      description =
          "Whether to enforce the mtaas device checkin group on the device. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enforceMtaasDeviceCheckinGroup = enforceMtaasDeviceCheckinGroupDefault;

  private static final Flag<Boolean> pingGoogleDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--device_ping_google",
      description = "Whether to enable dimension ping_google_stability. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> pingGoogle = pingGoogleDefault;

  private static final Flag<String> dexdumpPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--dexdump",
      description = "File path of the dexdump tool",
      converter = Flag.StringConverter.class)
  public Flag<String> dexdumpPath = dexdumpPathDefault;

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

  private static final Flag<Boolean> disableDeviceQuerierDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--disable_device_querier",
      description = "Whether to disable device querier in client. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> disableDeviceQuerier = disableDeviceQuerierDefault;

  private static final Flag<Boolean> disableDeviceRebootDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--disable_device_reboot",
      description = "Whether to disable device reboot. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> disableDeviceReboot = disableDeviceRebootDefault;

  private static final Flag<Boolean> disableDeviceRebootForRoPropertiesDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--disable_device_reboot_for_ro_properties",
      description =
          "Whether to disable 'device reboot for read-only properties'. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> disableDeviceRebootForRoProperties =
      disableDeviceRebootForRoPropertiesDefault;

  private static final Flag<Boolean> disableWaitForDeviceDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--disable_wait_for_device",
      description = "Whether to disable 'adb wait-for-device'. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> disableWaitForDevice = disableWaitForDeviceDefault;

  private static final Flag<Boolean> disableWifiUtilFuncDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--disable_wifi_util_func",
      description = "Whether to disable WifiUtil functionality on device. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> disableWifiUtilFunc = disableWifiUtilFuncDefault;

  private static final Flag<Duration> dispatchDeviceIntervalDefault =
      DurationFlag.value(Duration.ofSeconds(1L));

  @com.beust.jcommander.Parameter(
      names = "--dispatch_device_interval_sec",
      description = "Interval for dispatching the current active devices",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> dispatchDeviceInterval = dispatchDeviceIntervalDefault;

  private static final Flag<Boolean> enableAndroidDeviceReadyCheckDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_android_device_ready_check",
      description = "Whether to enable android device ready check.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableAndroidDeviceReadyCheck = enableAndroidDeviceReadyCheckDefault;

  private static final Flag<Boolean> enableAteDualStackDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_ate_dual_stack",
      description = "Whether to enable ATE dual stack mode, which runs tests from both MH and TFC.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableAteDualStack = enableAteDualStackDefault;

  private static final Flag<Boolean> enableAtsConsoleOlcServerDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_ats_console_olc_server",
      description =
          "Whether to enable OmniLab long-running client in ATS console. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableAtsConsoleOlcServer = enableAtsConsoleOlcServerDefault;

  private static final Flag<Boolean> enableAtsConsoleOlcServerLogDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_ats_console_olc_server_log",
      description =
          "If true, start printing OLC server streaming log in ATS console (run log command) when"
              + " console starts. If false, start/stop the streaming log when \"run\" command"
              + " starts/stops. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableAtsConsoleOlcServerLog = enableAtsConsoleOlcServerLogDefault;

  private static final Flag<Boolean> enableAtsModeDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_ats_mode",
      description =
          "Enable ATS mode if it's true. This flag is intended to serve ATS UI traffic only.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableAtsMode = enableAtsModeDefault;

  private static final Flag<Boolean> enableCachingReservedDeviceDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_caching_reserved_device",
      description = "Whether to enable caching reserved device. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableCachingReservedDevice = enableCachingReservedDeviceDefault;

  private static final Flag<Boolean> enableClientExperimentManagerDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_client_experiment_manager",
      description = "Whether to enable client experiment manager. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableClientExperimentManager = enableClientExperimentManagerDefault;

  private static final Flag<Boolean> enableClientFileTransferDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_client_file_transfer",
      description = "Whether to enable client file transfer. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableClientFileTransfer = enableClientFileTransferDefault;

  private static final Flag<Boolean> enableCloudLoggingDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_cloud_logging",
      description = "Whether to enable cloud logging.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableCloudLogging = enableCloudLoggingDefault;

  private static final Flag<Boolean> enableCloudMetricsDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_cloud_metrics",
      description =
          "Whether to enable sending metrics to Google Cloud. It should only be enabled when"
              + " deploying in Google Cloud.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableCloudMetrics = enableCloudMetricsDefault;

  private static final Flag<Boolean> enableCloudPubsubMonitoringDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_cloud_pubsub_monitoring",
      description =
          "Whether to enable sending lab monitoring data to Cloud Pub/Sub. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableCloudPubsubMonitoring = enableCloudPubsubMonitoringDefault;

  private static final Flag<Boolean> enableCloudFileTransferDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_cloud_file_transfer",
      description = "Whether enable cloud file transfer. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableCloudFileTransfer = enableCloudFileTransferDefault;

  private static final Flag<Boolean> enableCtsVerifierResultReporterDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_cts_verifier_result_reporter",
      description = "Whether enable result reporter for cts verifier.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableCtsVerifierResultReporter = enableCtsVerifierResultReporterDefault;

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

  private static final Flag<Boolean> enableDeviceConfigManagerDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_device_config_manager",
      description = "Whether to enable device config manager. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDeviceConfigManager = enableDeviceConfigManagerDefault;

  private static final Flag<Boolean> enableDeviceResourceServiceDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_device_resource_service",
      description = "Whether to enable device resource service. default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDeviceResourceService = enableDeviceResourceServiceDefault;

  private static final Flag<Boolean> enableDeviceStateChangeRecoverDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_device_state_change_recover",
      description =
          "Whether to change device state, like from recovery mode to normal mode, to recover the"
              + " device. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDeviceStateChangeRecover = enableDeviceStateChangeRecoverDefault;

  private static final Flag<Boolean> enableDeviceSystemSettingsChangeDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_device_system_settings_change",
      description =
          "Whether to change device system settings, like enable/disable airplane mode, etc."
              + " Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDeviceSystemSettingsChange = enableDeviceSystemSettingsChangeDefault;

  private static final Flag<Boolean> enableDeviceTestDecouplingDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_device_test_decoupling",
      description = "Whether to enable device/test decoupling mode. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDeviceTestDecoupling = enableDeviceTestDecouplingDefault;

  private static final Flag<Boolean> enableDiskCheckDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_disk_check",
      description = "For file cleaner, enable/disable checkDiskSpace in each check interval.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableDiskCheck = enableDiskCheckDefault;

  private static final Flag<Boolean> enableEmulatorDetectionDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_emulator_detection",
      description =
          "Whether this lab server is enabled for emulator detection. When emulator detection is"
              + " disabled, the emulator device will be considered as the real device. Do NOT set"
              + " it to false in remote labs. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableEmulatorDetection = enableEmulatorDetectionDefault;

  private static final Flag<Boolean> enableExternalConfigServiceDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_external_config_service",
      description = "Whether to enable external config service. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableExternalConfigService = enableExternalConfigServiceDefault;

  private static final Flag<Boolean> enableExternalMasterServerDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_external_master_server",
      description = "Whether to enable external master server. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableExternalMasterServer = enableExternalMasterServerDefault;

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

  private static final Flag<Boolean> enableFileCleanerDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_file_cleaner",
      description = "Whether to enable file cleaner.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableFileCleaner = enableFileCleanerDefault;

  private static final Flag<Boolean> enableFileSystemIoCheckDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_file_system_io_check",
      description = "For file cleaner, enable/disable checkFileSystemIo in each check interval.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableFileSystemIoCheck = enableFileSystemIoCheckDefault;

  private static final Flag<Boolean> enableFastbootDetectorDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_fastboot_detector",
      description = "Whether to enable fastboot detector. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableFastbootDetector = enableFastbootDetectorDefault;

  private static final Flag<Boolean> enableFastbootInAndroidRealDeviceDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_fastboot_in_android_real_device",
      description =
          "Whether to enable fastboot support when initializing AndroidRealDevice."
              + "Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableFastbootInAndroidRealDevice = enableFastbootInAndroidRealDeviceDefault;

  private static final Flag<Boolean> enableGrpcLabServerDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_grpc_lab_server",
      description = "Whether to enable gRPC connection to lab server. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableGrpcLabServer = enableGrpcLabServerDefault;

  private static final Flag<Boolean> enableGrpcRelayDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_grpc_relay",
      description = "Whether to enable gRPC relay. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableGrpcRelay = enableGrpcRelayDefault;

  private static final Flag<Boolean> enableMasterSyncerDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_master_syncer",
      description = "Whether to enable master syncer in lab.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableMasterSyncer = enableMasterSyncerDefault;

  private static final Flag<Boolean> enableMessagingServiceDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_messaging_service",
      description = "Whether to enable OmniLab messaging service. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableMessagingService = enableMessagingServiceDefault;

  private static final Flag<Boolean> enableMoblyResultstoreUploadDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_mobly_resultstore_upload",
      description = "Whether to enable Mobly result store upload. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableMoblyResultstoreUpload = enableMoblyResultstoreUploadDefault;

  private static final Flag<Boolean> enablePersistentCacheDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_persistent_cache",
      description = "Whether to enable persistent cache.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enablePersistentCache = enablePersistentCacheDefault;

  private static final Flag<Boolean> enableProxyModeDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_proxy_mode",
      description = "Whether to enable proxy mode.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableProxyMode = enableProxyModeDefault;

  private static final Flag<Boolean> enableRdhDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_rdh",
      description = "Whether to enable the remote DeviceProxyHostService.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableRdh = enableRdhDefault;

  private static final Flag<Boolean> enableRepositoryRefactorInMasterDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_repository_refactor_in_master",
      description = "Whether to enable repository refactor in master. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableRepositoryRefactorInMaster = enableRepositoryRefactorInMasterDefault;

  private static final Flag<Boolean> enableRootDeviceDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_root_device",
      description = "Whether to root devices. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableRootDevice = enableRootDeviceDefault;

  private static final Flag<Boolean> enableSimpleSchedulerShuffleDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_simple_scheduler_shuffle",
      description =
          "Whether to enable the shuffle of the devices in the single scheduler, to randomly"
              + " allocate devices for the same requests. The default value is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableSimpleSchedulerShuffle = enableSimpleSchedulerShuffleDefault;

  private static final Flag<Boolean> enableStackdriverDebugModeDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_stackdriver_debug_mode",
      description = "Whether enable debug mode to print more detailed logs in Stackdriver.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableStackdriverDebugMode = enableStackdriverDebugModeDefault;

  private static final Flag<Boolean> enableStubbyFileTransferDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_stubby_file_transfer",
      description = "Whether to enable stubby file transfer. default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableStubbyFileTransfer = enableStubbyFileTransferDefault;

  private static final Flag<Boolean> enableStubbyRpcServerDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_stubby_rpc_server",
      description = "Whether to enable stubby RPC server. default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableStubbyRpcServer = enableStubbyRpcServerDefault;

  private static final Flag<Boolean> enableTestHarnessCheckForRequiredTestsDefault =
      Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_test_harness_check_for_required_tests",
      description =
          "Whether to enable test harness check for required tests in ATS server. Default is"
              + " false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableTestHarnessCheckForRequiredTests =
      enableTestHarnessCheckForRequiredTestsDefault;

  private static final Flag<Boolean> enableTestLogCollectorDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_test_log_collector",
      description = "Whether to enable test log collector. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableTestLogCollector = enableTestLogCollectorDefault;

  private static final Flag<Boolean> enableTraceSpanProcessorDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_trace_span_processor",
      description = "Whether to enable trace span processor. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableTraceSpanProcessor = enableTraceSpanProcessorDefault;

  private static final Flag<Boolean> enableXtsDynamicDownloaderDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_xts_dynamic_downloader",
      description = "Whether to enable xts dynamic downloader. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableXtsDynamicDownloader = enableXtsDynamicDownloaderDefault;

  private static final Flag<Boolean> enableXtsTradefedInvocationAgentDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--enable_xts_tradefed_invocation_agent",
      description = "Whether to enable xts tradefed invocation agent. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableXtsTradefedInvocationAgent = enableXtsTradefedInvocationAgentDefault;

  private static final Flag<Boolean> enableZombieFileCleanDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enable_zombie_file_clean",
      description =
          "For file cleaner, enable/disable cleanZombieFile in each check interval. By default is"
              + " false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enableZombieFileClean = enableZombieFileCleanDefault;

  private static final Flag<Boolean> enforceFlashSafetyChecksDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--enforce_flash_safety_checks",
      description =
          "Whether to enable flash safety checks, which disables flash support when devices are in"
              + " risky status.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> enforceFlashSafetyChecks = enforceFlashSafetyChecksDefault;

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

  private static final Flag<Duration> ephemeralRemovalThresholdDefault =
      DurationFlag.value(Duration.ofHours(1));

  @com.beust.jcommander.Parameter(
      names = "--ephemeral_removal_threshold",
      description =
          "If a MISSING ephemeral lab or device's last modify time is older than this"
              + " threshold, it will be removed from Master.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> ephemeralRemovalThreshold = ephemeralRemovalThresholdDefault;

  private static final Flag<String> externalResJarDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--external_res_jar",
      description =
          "Absolute path to the jar file of external resources. This jar contains the"
              + "resources that are not in binary jar.",
      converter = Flag.StringConverter.class)
  public Flag<String> externalResJar = externalResJarDefault;

  private static final Flag<String> adbKeyPathsFromUserDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--extra_adb_keys",
      description =
          ("Colon-separated list of adb keys (files or directories) to be used (see ADB_VENDOR_KEYS"
              + " in adb --help for formatting details)."),
      converter = Flag.StringConverter.class)
  public Flag<String> adbKeyPathsFromUser = adbKeyPathsFromUserDefault;

  private static final Flag<List<String>> extraDeviceLabelsDefault = Flag.stringList();

  @com.beust.jcommander.Parameter(
      names = "--extra_device_labels",
      description =
          "Device labels which will be appended to the dimensions of all the devices "
              + "in the current host.",
      converter = Flag.StringListConverter.class)
  public Flag<List<String>> extraDeviceLabels = extraDeviceLabelsDefault;

  private static final Flag<String> fastbootPathFromUserDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--fastboot",
      description = "File path of the fastboot tool",
      converter = Flag.StringConverter.class)
  public Flag<String> fastbootPathFromUser = fastbootPathFromUserDefault;

  private static final Flag<Integer> feGrpcPortDefault = Flag.value(8080);

  @com.beust.jcommander.Parameter(
      names = "--fe_grpc_port",
      description = "gRPC port to listen on for FE servers.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> feGrpcPort = feGrpcPortDefault;

  private static final Flag<Duration> fileExpireTimeDefault =
      DurationFlag.value(Duration.ofHours(3L));

  @com.beust.jcommander.Parameter(
      names = "--file_expire_time",
      description =
          "For file cleaner, file expire time for default managed directories, such as receive file"
              + " directory.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> fileExpireTime = fileExpireTimeDefault;

  private static final Flag<String> fileTransferBucketDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--file_transfer_cloud_bucket",
      description = "Google Cloud Storage bucket of file transfer.",
      converter = Flag.StringConverter.class)
  public Flag<String> fileTransferBucket = fileTransferBucketDefault;

  private static final Flag<Duration> fileTransferCloudCacheTtlDefault =
      DurationFlag.value(Duration.ofHours(12));

  @com.beust.jcommander.Parameter(
      names = "--file_transfer_cloud_cache_ttl",
      description = "TTL of File Transfer caches in Google Cloud Storage. Default is 12 hours.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> fileTransferCloudCacheTtl = fileTransferCloudCacheTtlDefault;

  private static final Flag<String> fileTransferCredFileDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--file_transfer_cred_file",
      description = "The credential file path for the service account to use file transfer.",
      converter = Flag.StringConverter.class)
  public Flag<String> fileTransferCredFile = fileTransferCredFileDefault;

  private static final Flag<Duration> fileTransferLocalCacheTtlDefault =
      DurationFlag.value(Duration.ofHours(3));

  @com.beust.jcommander.Parameter(
      names = "--file_transfer_local_cache_ttl",
      description = "TTL of File Transfer caches in Google Cloud Storage. Default is 3 hour.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> fileTransferLocalCacheTtl = fileTransferLocalCacheTtlDefault;

  private static final Flag<Boolean> forceDeviceRebootAfterTestDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--force_device_reboot_after_test",
      description =
          "Whether to force a device reboot after each test. This option has the highest priority"
              + " to determine whether the device should reboot after each test. When thisoption is"
              + " true, other related flags (e.g. --disable_device_reboot) or related"
              + " implementations (e.g Device#canReboot()) may be ignored in some cases. This is an"
              + " advanced flag, make sure you understand the effects when using this flag. The"
              + " default value is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> forceDeviceRebootAfterTest = forceDeviceRebootAfterTestDefault;

  private static final Flag<Boolean> forceToUseGrpcDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--force_to_use_grpc",
      description = "Force to use GRPC for debugging.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> forceToUseGrpc = forceToUseGrpcDefault;

  private static final Flag<String> gcsResolverCredentialFileDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--gcs_resolver_credential_file",
      description = "The credential file path for the service account to use GCS resolver.",
      converter = Flag.StringConverter.class)
  public Flag<String> gcsResolverCredentialFile = gcsResolverCredentialFileDefault;

  private static final Flag<String> gcsResolverProjectIdDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--gcs_resolver_project_id",
      description = "The project ID of the GCS resolver.",
      converter = Flag.StringConverter.class)
  public Flag<String> gcsResolverProjectId = gcsResolverProjectIdDefault;

  private static final Flag<Integer> gcsUtilThreadsDefault = Flag.value(50);

  @com.beust.jcommander.Parameter(
      names = "--gcs_util_threads",
      description = "Thread pool size for uploading/downloading GCS files in parallel.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> gcsUtilThreads = gcsUtilThreadsDefault;

  private static final Flag<String> internalServiceCredentialFileDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--internal_service_cred_file",
      description = "Path to the credential key file to access internal services.",
      converter = Flag.StringConverter.class)
  public Flag<String> internalServiceCredentialFile = internalServiceCredentialFileDefault;

  private static final Flag<Duration> getTestStatusRpcCallIntervalDefault =
      DurationFlag.value(Duration.ofSeconds(5L));

  @com.beust.jcommander.Parameter(
      names = "--get_test_status_rpc_call_interval",
      description = "Default RPC call interval when getting the test result.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> getTestStatusRpcCallInterval = getTestStatusRpcCallIntervalDefault;

  private static final Flag<Integer> grpcPortDefault = Flag.value(9994);

  @com.beust.jcommander.Parameter(
      names = "--grpc_port",
      description = "Port of server gRPC services. Default is 9994.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> grpcPort = grpcPortDefault;

  private static final Flag<Boolean> ignoreCheckDeviceFailureDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--ignore_check_device_failure",
      description = "Whether to ignore failures during checking device. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> ignoreCheckDeviceFailure = ignoreCheckDeviceFailureDefault;

  private static final Flag<Integer> internalStorageAlertDefault = Flag.value(200);

  @com.beust.jcommander.Parameter(
      names = "--internal_storage_alert_mb",
      description =
          "The threshold for insufficient internal storage alert. If the internal storage is lower "
              + "than the threshold, the device dimension 'internal_storage_status' will go from "
              + "'ok' to 'low'. Unit is MB. Default is 200 MB.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> internalStorageAlert = internalStorageAlertDefault;

  private static final Flag<Boolean> isOmniModeDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--is_omni_mode",
      description = "Whether the controller is in Omni mode. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> isOmniMode = isOmniModeDefault;

  private static final Flag<String> javaCommandPathDefault = Flag.value("java");

  @com.beust.jcommander.Parameter(
      names = "--java_command_path",
      description = "The path of Java",
      converter = Flag.StringConverter.class)
  public Flag<String> javaCommandPath = javaCommandPathDefault;

  private static final Flag<String> jobConfigsJsonDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--job_configs_json",
      description = "File path of json string that is parsed from mobileharness.client.JobConfigs.",
      converter = Flag.StringConverter.class)
  public Flag<String> jobConfigsJson = jobConfigsJsonDefault;

  private static final Flag<Duration> jobGenFileExpiredTimeDefault =
      DurationFlag.value(Duration.ZERO);

  @com.beust.jcommander.Parameter(
      names = "--job_gen_file_expired_time",
      description =
          "How soon to clean up the genfile after each test. Default is 0, which means the genfile "
              + "is removed immediately when a test finishes. It has the risk to blow up the disk "
              + " of the lab host when setting to non-zero.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> jobGenFileExpiredTime = jobGenFileExpiredTimeDefault;

  private static final Flag<Boolean> keepTestHarnessFalseDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--keep_test_harness_false",
      description =
          "If true, keep the device property persist.sys.test_harness false by adding required"
              + " dimension. Moreover reset_device_in_android_real_device_setup flag can't be true"
              + " in this case. Only turn on the flag in omni mode for CTS device pool.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> keepTestHarnessFalse = keepTestHarnessFalseDefault;

  private static final Flag<String> labDeviceConfigFileDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--lab_device_config",
      description = "Path of the text format protobuf lab device config file.",
      converter = Flag.StringConverter.class)
  public Flag<String> labDeviceConfigFile = labDeviceConfigFileDefault;

  private static final Flag<Duration> labExpirationThresholdDefault =
      DurationFlag.value(Duration.ofMinutes(4));

  @com.beust.jcommander.Parameter(
      names = "--lab_expiration_threshold",
      description =
          "If a lab's last modify time is older than this threshold, it will be marked as missing.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> labExpirationThreshold = labExpirationThresholdDefault;

  private static final Flag<Duration> labRemovalThresholdDefault =
      DurationFlag.value(Duration.ofDays(30));

  @com.beust.jcommander.Parameter(
      names = "--lab_removal_threshold",
      description =
          "If a MISSING lab's last modify time is older than this threshold, it will be removed"
              + " from Master.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> labRemovalThreshold = labRemovalThresholdDefault;

  private static final Flag<String> localTenantDeviceConfigPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--local_tenant_device_config_path",
      description = "Path of the text format protobuf for the local tenant device config file.",
      converter = Flag.StringConverter.class)
  public Flag<String> localTenantDeviceConfigPath = localTenantDeviceConfigPathDefault;

  /** Source for the tenant device config. */
  public enum TenantConfigMode {
    NOOP,
    LOCAL,
    REMOTE
  }

  private static final Flag<Integer> logFileNumberDefault = Flag.value(100);

  @com.beust.jcommander.Parameter(
      names = "--log_file_number",
      description = "Max number of the rotated log files in local host.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> logFileNumber = logFileNumberDefault;

  private static final Flag<Boolean> logFileSizeNoLimitDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--log_file_size_no_limit",
      description = "True to write all log content into one file. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> logFileSizeNoLimit = logFileSizeNoLimitDefault;

  private static final Flag<Duration> logUploadDelayDefault =
      DurationFlag.value(Duration.ofSeconds(60L));

  @com.beust.jcommander.Parameter(
      names = "--log_upload_delay",
      description =
          "The interval in seconds between end of last periodic log uploading to the start of the"
              + " next one.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> logUploadDelay = logUploadDelayDefault;

  private static final Flag<Integer> loggerConsoleHandlerMinLogRecordImportanceDefault =
      Flag.value(100);

  @com.beust.jcommander.Parameter(
      names = "--logger_console_handler_min_log_record_importance",
      description =
          "Minimum console log record importance shown in System.err. Check LogRecordImportance for"
              + " importance of log records. Default is 100.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> loggerConsoleHandlerMinLogRecordImportance =
      loggerConsoleHandlerMinLogRecordImportanceDefault;

  private static final Flag<Duration> longPingTimeoutDefault =
      DurationFlag.value(Duration.ofMinutes(1L));

  @com.beust.jcommander.Parameter(
      names = "--long_ping_timeout",
      description = "Set the default timeout for long ping commands.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> longPingTimeout = longPingTimeoutDefault;

  private static final Flag<Long> lowerLimitOfJvmMaxMemoryAllowForAllocationDiagnosticDefault =
      Flag.value(512L * 1024 * 1024);

  @com.beust.jcommander.Parameter(
      names = "--lower_limit_of_jvm_max_memory_allow_for_allocation_diagnostic",
      description =
          "The lower limit of jvm -Xmx that allows to generate allocation diagnostic without OOM.",
      converter = Flag.LongConverter.class)
  public Flag<Long> lowerLimitOfJvmMaxMemoryAllowForAllocationDiagnostic =
      lowerLimitOfJvmMaxMemoryAllowForAllocationDiagnosticDefault;

  private static final Flag<Map<String, String>> masterCentralDatabaseJdbcPropertyDefault =
      Flag.value(ImmutableMap.of());

  @com.beust.jcommander.Parameter(
      names = "--master_central_database_jdbc_property",
      description = "Master central database JDBC property. e.g. user=root,password=password",
      converter = Flag.StringMapConverter.class)
  public Flag<Map<String, String>> masterCentralDatabaseJdbcProperty =
      masterCentralDatabaseJdbcPropertyDefault;

  private static final Flag<String> masterCentralDatabaseJdbcUrlDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--master_central_database_jdbc_url",
      description = "Master central database JDBC URL, e.g. jdbc:mysql://localhost/master_db.",
      converter = Flag.StringConverter.class)
  public Flag<String> masterCentralDatabaseJdbcUrl = masterCentralDatabaseJdbcUrlDefault;

  /** The backend type of the master central and scheduler database. */
  public enum MasterDatabaseBackend {
    INFRA_SPANNER,
    MYSQL
  }

  private static final Flag<MasterDatabaseBackend> masterDatabaseBackendDefault =
      Flag.value(MasterDatabaseBackend.MYSQL);

  @com.beust.jcommander.Parameter(
      names = "--master_database_backend",
      description = "The backend type of the master central and scheduler database.")
  public Flag<MasterDatabaseBackend> masterDatabaseBackend = masterDatabaseBackendDefault;

  private static final Flag<String> masterGrpcTargetDefault = Flag.value("localhost:9990");

  @com.beust.jcommander.Parameter(
      names = "--master_grpc_target",
      description =
          "gRPC target string of master server. Default is localhost:9990. See"
              + " ManagedChannelBuilder.forTarget().",
      converter = Flag.StringConverter.class)
  public Flag<String> masterGrpcTarget = masterGrpcTargetDefault;

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

  private static final Flag<Duration> maxConsecutiveGetTestStatusErrorDurationDefault =
      DurationFlag.value(Duration.ofSeconds(1L));

  @com.beust.jcommander.Parameter(
      names = "--max_consecutive_get_test_status_error_duration",
      description =
          "How long we can wait for the next successful RPC call before marking the test as ERROR"
              + " when the RPC fails.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> maxConsecutiveGetTestStatusErrorDuration =
      maxConsecutiveGetTestStatusErrorDurationDefault;

  private static final Flag<Duration> maxDrainTimeoutDefault =
      DurationFlag.value(Duration.ofDays(3L));

  @com.beust.jcommander.Parameter(
      names = "--max_drain_timeout",
      description =
          "Maximum timeout for releases to be drained. The release will be marked as DRAINED if it"
              + " execeeds the timeout. Default is 3 days.",
      converter = DurationFlag.DurationConverter.class)
  public Flag<Duration> maxDrainTimeout = maxDrainTimeoutDefault;

  private static final Flag<Long> maxPersistentCacheSizeInGigabytesDefault = Flag.value(200L);

  @com.beust.jcommander.Parameter(
      names = "--max_persistent_cache_size_in_gigabytes",
      description = "Maximum size in gigabytes for persistent cache.",
      converter = Flag.LongConverter.class)
  public Flag<Long> maxPersistentCacheSizeInGigabytes = maxPersistentCacheSizeInGigabytesDefault;

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

  private static final Flag<Integer> maxInitFailuresBeforeFailDefault = Flag.value(3);

  @com.beust.jcommander.Parameter(
      names = "--mh_dm_max_init_failures_before_fail",
      description =
          "After how many INIT failures do we consider the device to be a FailedDevice. The default"
              + "value is 3 times.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> maxInitFailuresBeforeFail = maxInitFailuresBeforeFailDefault;

  private static final Flag<String> mhProxySpecDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--mhproxy_spec",
      description = "GSLB blade target for MH Proxy.",
      converter = Flag.StringConverter.class)
  public Flag<String> mhProxySpec = mhProxySpecDefault;

  private static final Flag<Boolean> monitorCloudRpcDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--monitor_cloudrpc",
      description = "Whether enable the cloudrpc monitor. default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> monitorCloudRpc = monitorCloudRpcDefault;

  private static final Flag<Boolean> monitorLabDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--monitor_lab",
      description = "Whether enable the lab monitor. default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> monitorLab = monitorLabDefault;

  private static final Flag<Boolean> monitorGcsDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--monitor_gcs",
      description = "Whether enable the gcs monitor. default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> monitorGcs = monitorGcsDefault;

  private static final Flag<Boolean> monitorSignalsDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--monitor_signals",
      description = "Whether to monitor signals. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> monitorSignals = monitorSignalsDefault;

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

  private static final Flag<Boolean> noOpDeviceRandomOfflineDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--no_op_device_random_offline",
      description = "If enabled, randomly take some NoOpDevice offline.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> noOpDeviceRandomOffline = noOpDeviceRandomOfflineDefault;

  private static final Flag<Integer> noOpDeviceStartIndexDefault = Flag.value(0);

  @com.beust.jcommander.Parameter(
      names = "--no_op_device_start_index",
      description = "The start index of NoOpDevice.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> noOpDeviceStartIndex = noOpDeviceStartIndexDefault;

  private static final Flag<String> noOpDeviceTypeDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--no_op_device_type",
      description =
          "Device type string supported, e.g. AndroidRealDevice, only for debug/test purpose.",
      converter = Flag.StringConverter.class)
  public Flag<String> noOpDeviceType = noOpDeviceTypeDefault;

  private static final Flag<Boolean> noOpLabServerDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--no_op_lab_server",
      description =
          "If true, the lab server will sleep forever rather than starting services and connecting"
              + " to master. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> noOpLabServer = noOpLabServerDefault;

  private static final Flag<Boolean> noopJitEmulatorDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--noop_jit_emulator",
      description = "Make jit emulator no-op and work as placeholder",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> noopJitEmulator = noopJitEmulatorDefault;

  private static final Flag<Map<String, String>> olcDatabaseJdbcPropertyDefault =
      Flag.value(ImmutableMap.of());

  @com.beust.jcommander.Parameter(
      names = "--olc_database_jdbc_property",
      description = "OLC database JDBC property.",
      converter = Flag.StringMapConverter.class)
  public Flag<Map<String, String>> olcDatabaseJdbcProperty = olcDatabaseJdbcPropertyDefault;

  private static final Flag<String> olcDatabaseJdbcUrlDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--olc_database_jdbc_url",
      description = "OLC database JDBC URL.",
      converter = Flag.StringConverter.class)
  public Flag<String> olcDatabaseJdbcUrl = olcDatabaseJdbcUrlDefault;

  private static final Flag<Integer> olcServerMaxStartedRunningSessionNumDefault = Flag.value(200);

  @com.beust.jcommander.Parameter(
      names = "--olc_server_max_started_running_session_num",
      description = "OLC server max started and running session number. Default is 200.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> olcServerMaxStartedRunningSessionNum =
      olcServerMaxStartedRunningSessionNumDefault;

  private static final Flag<Integer> olcServerPortDefault = Flag.value(7030);

  @com.beust.jcommander.Parameter(
      names = "--olc_server_port",
      description = "OLC server port. By default, it is 7030.",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> olcServerPort = olcServerPortDefault;

  private static final Flag<String> perfettoScriptPathDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--perfetto_script_path",
      description = "File path for the perfetto script used by the Perfetto decorator.",
      converter = Flag.StringConverter.class)
  public Flag<String> perfettoScriptPath = perfettoScriptPathDefault;

  private static final Flag<String> persistentCacheDirDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--persistent_cache_dir",
      description = "Root directory for persistent cache.",
      converter = Flag.StringConverter.class)
  public Flag<String> persistentCacheDir = persistentCacheDirDefault;

  private static final Flag<Boolean> prepareDeviceAfterTestDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--prepare_device_after_test",
      description = "If true, prepare the device after test. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> prepareDeviceAfterTest = prepareDeviceAfterTestDefault;

  private static final Flag<Boolean> printLabStatsDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--print_lab_stats",
      description =
          "If true, print binary stats of Lab, and return silently. All other settings will be "
              + "ignored.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> printLabStats = printLabStatsDefault;

  private static final Flag<Boolean> proxyModeLeaseDevicesImmediatelyDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--proxy_mode_lease_devices_immediately",
      description = "Always lease all devices immediately in proxy mode. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> proxyModeLeaseDevicesImmediately = proxyModeLeaseDevicesImmediatelyDefault;

  private static final Flag<String> publicDirDefault = Flag.value(getPublicDirDefaultOss());

  @com.beust.jcommander.Parameter(
      names = "--public_dir",
      description = "The public directory.",
      converter = Flag.StringConverter.class)
  public Flag<String> publicDir = publicDirDefault;

  private static String getPublicDirDefaultOss() {
    return "/tmp";
  }

  private static final Flag<Boolean> realTimeJobDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--real_time_job",
      description = "If this flag is true, all submitted jobs will run as real-time jobs.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> realTimeJob = realTimeJobDefault;

  private static final Flag<Boolean> realTimeTestDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--real_time_test",
      description = "If this flag is true, all tests will run as real-time tests.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> realTimeTest = realTimeTestDefault;

  private static final Flag<Boolean> removeJobGenFilesWhenFinishedDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--remove_job_gen_files_when_finished",
      description =
          "If this flag is true, all job generated files are removed after the job is done.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> removeJobGenFilesWhenFinished = removeJobGenFilesWhenFinishedDefault;

  private static final Flag<Boolean> resetDeviceInAndroidRealDeviceSetupDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--reset_device_in_android_real_device_setup",
      description =
          "If this flag is true, Android real device will be reset first in setup process. The flag"
              + " can't be set to true if keep_test_harness_false is true. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> resetDeviceInAndroidRealDeviceSetup =
      resetDeviceInAndroidRealDeviceSetupDefault;

  private static final Flag<String> resDirNameDefault = Flag.value("mh_res_files");

  @com.beust.jcommander.Parameter(
      names = "--resource_dir_name",
      description = "Name of resource directory.",
      converter = Flag.StringConverter.class)
  public Flag<String> resDirName = resDirNameDefault;

  private static final Flag<List<String>> restrictOlcServiceToUsersDefault = Flag.stringList();

  @com.beust.jcommander.Parameter(
      names = "--restrict_olc_service_to_users",
      description =
          "A list of authorized users. If the list is nonempty, "
              + "restrict the OLC service to users on the list.",
      converter = Flag.StringListConverter.class)
  public Flag<List<String>> restrictOlcServiceToUsers = restrictOlcServiceToUsersDefault;

  private static final Flag<Boolean> reverseTunnelingLabServerDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--reverse_tunneling_lab_server",
      description = "Whether lab servers have been reverse tunneled to client. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> reverseTunnelingLabServer = reverseTunnelingLabServerDefault;

  private static final Flag<Integer> rpcPortDefault = Flag.value(9999);

  @com.beust.jcommander.Parameter(
      names = "--rpc_port",
      description = "Stubby port of the server",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> rpcPort = rpcPortDefault;

  private static final Flag<Boolean> runDynamicDownloadMctsOnlyDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--run_dynamic_download_mcts_only",
      description = "If true, only run dynamic download mcts. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> runDynamicDownloadMctsOnly = runDynamicDownloadMctsOnlyDefault;

  private static final Flag<Integer> safeChargeLevelDefault = Flag.value(50);

  @com.beust.jcommander.Parameter(
      names = "--safe_charge_level",
      description =
          "Battery level devices should be kept at. Devices will be charged at most to this level."
              + "Only works for devices which support this (i.e., marlin, sailfish).",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> safeChargeLevel = safeChargeLevelDefault;

  private static final Flag<Boolean> servViaCloudRpcDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--serv_via_cloud_rpc",
      description = "Whether to serve the inbound gRPC requests via Cloud RPC. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> servViaCloudRpc = servViaCloudRpcDefault;

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

  private static final Flag<Boolean> skipCheckDeviceInternetDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--skip_check_device_internet",
      description =
          "Whether to skip checking device connect to Internet via ping. Default is false. When set"
              + " to true, it means you have confidence that the device can successfully connect to"
              + " Internet, and OmniLab will set dimension internet to true without checking the"
              + " connection.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> skipCheckDeviceInternet = skipCheckDeviceInternetDefault;

  private static final Flag<Boolean> skipConnectDeviceToWifiDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--skip_connect_device_to_wifi",
      description =
          "Whether to skip connecting device to their default Wi-Fi network. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> skipConnectDeviceToWifi = skipConnectDeviceToWifiDefault;

  private static final Flag<Boolean> skipLabJobGenFileCleanupDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--skip_lab_job_gen_file_cleanup",
      description =
          "whether to skip job gen file cleanup when job ended. Default is false. Use when the gen"
              + " files are placed in /tmp directory and make sure they can be cleaned by operating"
              + " system.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> skipLabJobGenFileCleanup = skipLabJobGenFileCleanupDefault;

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

  private static final Flag<Boolean> simplifiedLogFormatDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--simplified_log_format",
      description = "True to use simplified log format. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> simplifiedLogFormat = simplifiedLogFormatDefault;

  private static final Flag<Integer> socketPortDefault = Flag.value(9998);

  @com.beust.jcommander.Parameter(
      names = "--socket_port",
      description = "Socket port of the file transfer service of the lab server",
      converter = Flag.IntegerConverter.class)
  public Flag<Integer> socketPort = socketPortDefault;

  private static final Flag<String> stackdriverCredentialFileDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--stackdriver_cred_file",
      description = "Path to the credential key file for stackdriver api.",
      converter = Flag.StringConverter.class)
  public Flag<String> stackdriverCredentialFile = stackdriverCredentialFileDefault;

  private static final Flag<String> stackdriverGcpProjectNameDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--stackdriver_gcp_project_name",
      description = "The GCP name of stackdriver logging",
      converter = Flag.StringConverter.class)
  public Flag<String> stackdriverGcpProjectName = stackdriverGcpProjectNameDefault;

  private static final Flag<String> stackdriverResourceTypeDefault = Flag.value("deployment");

  @com.beust.jcommander.Parameter(
      names = "--stackdriver_resource_type",
      description = "The resource type of stackdriver logging",
      converter = Flag.StringConverter.class)
  public Flag<String> stackdriverResourceType = stackdriverResourceTypeDefault;

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

  private static final Flag<TenantConfigMode> tenantConfigModeDefault = Flag.value(NOOP);

  @com.beust.jcommander.Parameter(
      names = "--tenant_device_config_mode",
      description = "Source for the tenant device config. One of NOOP, LOCAL, or REMOTE.")
  public Flag<TenantConfigMode> tenantConfigMode = tenantConfigModeDefault;

  private static final Flag<List<String>> testbedConfigPathsDefault =
      Flag.stringList(getTmpDirRootDefaultOss() + "/testbeds");

  @com.beust.jcommander.Parameter(
      names = "--testbed_config_paths",
      description = "The source to load the local testbed configurations from.",
      converter = Flag.StringListConverter.class)
  public Flag<List<String>> testbedConfigPaths = testbedConfigPathsDefault;

  private static final Flag<String> tmpDirRootDefault = Flag.value(getTmpDirRootDefaultOss());

  @com.beust.jcommander.Parameter(
      names = "--tmp_dir_root",
      description = "The tmp Dir Root.",
      converter = Flag.StringConverter.class)
  public Flag<String> tmpDirRoot = tmpDirRootDefault;

  private static String getTmpDirRootDefaultOss() {
    return Strings.nullToEmpty(System.getenv("HOME")) + "/mobileharness";
  }

  private static final Flag<String> tradefedBinaryDirDefault = Flag.value("/bin/tradefed");

  @com.beust.jcommander.Parameter(
      names = "--tradefed_binary_dir",
      description = "The directory of tradefed binaries.",
      converter = Flag.StringConverter.class)
  public Flag<String> tradefedBinaryDir = tradefedBinaryDirDefault;

  private static final Flag<String> tradefedCurlDownloadLimitRateDefault = Flag.value(null);

  @com.beust.jcommander.Parameter(
      names = "--tradefed_curl_download_limit_rate",
      description =
          "The limit rate of curl download for Tradefed. The value should be given with a letter"
              + " suffix using one of K, M and G for kilobytes, megabytes and gigabytes per second."
              + " Default is null and curl will try to saturate all available network connections.",
      converter = Flag.StringConverter.class)
  public Flag<String> tradefedCurlDownloadLimitRate = tradefedCurlDownloadLimitRateDefault;

  private static final Flag<Boolean> transferResourcesFromControllerDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--transfer_resources_from_controller",
      description =
          "Whether to transfer all resources from the controller to workers. The default is true."
              + " Only set it to false when the controller and workers cross different networks.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> transferResourcesFromController = transferResourcesFromControllerDefault;

  private static final Flag<Boolean> useAltsDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--use_alts",
      description =
          "Use ALTS for OLC server auth.This is supported by GCP vm. " + "The default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> useAlts = useAltsDefault;

  private static final Flag<Boolean> useEmulatorNameInUuidDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--use_emulator_name_in_uuid",
      description =
          "Whether to use the emulator name in the device UUID. This is to make Omnilab"
              + " device naming scheme consistent with ATS server's. Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> useEmulatorNameInUuid = useEmulatorNameInUuidDefault;

  private static final Flag<Boolean> useTfRetryDefault = Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--use_tf_retry",
      description = "Delegate retry to TF. The default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> useTfRetry = useTfRetryDefault;

  private static final Flag<String> virtualDeviceServerIpDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--virtual_device_server_ip",
      description = "The IP address of the remote virtual device server.",
      converter = Flag.StringConverter.class)
  public Flag<String> virtualDeviceServerIp = virtualDeviceServerIpDefault;

  private static final Flag<String> virtualDeviceServerUsernameDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--virtual_device_server_username",
      description = "The username of the remote virtual device server.",
      converter = Flag.StringConverter.class)
  public Flag<String> virtualDeviceServerUsername = virtualDeviceServerUsernameDefault;

  private static final Flag<Boolean> xtsDisableTfResultLogDefault = Flag.value(true);

  @com.beust.jcommander.Parameter(
      names = "--xts_disable_tf_result_log",
      description = "Disable xTS TF result logs in terminal. Default is true.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> xtsDisableTfResultLog = xtsDisableTfResultLogDefault;

  private static final Flag<String> xtsJdkDirDefault = Flag.value("");

  @com.beust.jcommander.Parameter(
      names = "--xts_jdk_dir",
      description = "The xTS JDK directory.",
      converter = Flag.StringConverter.class)
  public Flag<String> xtsJdkDir = xtsJdkDirDefault;

  private static final Flag<String> xtsResDirRootDefault = Flag.value(getXtsResDirRootDefaultOss());

  @com.beust.jcommander.Parameter(
      names = "--xts_res_dir_root",
      description = "The xTS resources dir root.",
      converter = Flag.StringConverter.class)
  public Flag<String> xtsResDirRoot = xtsResDirRootDefault;

  private static String getXtsResDirRootDefaultOss() {
    return Strings.nullToEmpty(System.getenv("HOME")) + "/xts";
  }

  private static final Flag<Boolean> xtsRetryReportMergerParallelTestCaseMergeDefault =
      Flag.value(false);

  @com.beust.jcommander.Parameter(
      names = "--xts_retry_report_merger_parallel_test_case_merge",
      description =
          "Whether to merge test cases in parallel in the xTS retry report merger. "
              + "Default is false.",
      converter = Flag.BooleanConverter.class)
  public Flag<Boolean> xtsRetryReportMergerParallelTestCaseMerge =
      xtsRetryReportMergerParallelTestCaseMergeDefault;

  private static final Flag<String> xtsServerResDirRootDefault =
      Flag.value(getXtsServerResDirRootDefaultOss());

  @com.beust.jcommander.Parameter(
      names = "--xts_server_res_dir_root",
      description = "The xTS server resources dir root.",
      converter = Flag.StringConverter.class)
  public Flag<String> xtsServerResDirRoot = xtsServerResDirRootDefault;

  private static String getXtsServerResDirRootDefaultOss() {
    return Strings.nullToEmpty(System.getenv("HOME")) + "/xts_server";
  }

  private static final Flag<String> xtsTfXmxDefault = Flag.value("24g");

  @com.beust.jcommander.Parameter(
      names = "--xts_tf_xmx",
      description = "-Xmx of TF of XtsTradeTest. Default is \"24g\".",
      converter = Flag.StringConverter.class)
  public Flag<String> xtsTfXmx = xtsTfXmxDefault;

  private static final Flags INSTANCE = new Flags();

  public static Flags instance() {
    return INSTANCE;
  }

  /** See {@link #parse(String[])}. */
  public static void parse(List<String> args) {
    parse(args.toArray(new String[0]));
  }

  /**
   * Parses flags.
   *
   * <p>Call this method in the main method.
   *
   * <p>This method will also switch between the OSS/internal version.
   */
  public static void parse(String[] args) {
    parseOss(args);
  }

  /** Parses flags in OSS. */
  public static void parseOss(String[] args) {
    com.beust.jcommander.JCommander commander = new com.beust.jcommander.JCommander(instance());
    commander.setAcceptUnknownOptions(true);
    commander.setAllowParameterOverwriting(true);
    commander.parse(args);
    checkConstraints();
  }

  private static void checkConstraints() {
    checkArgument(
        !Flags.instance().resetDeviceInAndroidRealDeviceSetup.getNonNull()
            || !Flags.instance().keepTestHarnessFalse.getNonNull(),
        "--reset_device_in_android_real_device_setup and --keep_test_harness_false cannot be both"
            + " true.");
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
