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

package com.google.devtools.mobileharness.api.model.error;

import com.google.common.base.Preconditions;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.util.ErrorIdFormatter;

/**
 * Basic errors from low level utilities, libraries, which are not related to any device platforms.
 * Device platform specific errors should go to {@link ExtErrorId}.
 */
public enum BasicErrorId implements ErrorId {
  NON_MH_EXCEPTION(20_001, ErrorType.UNCLASSIFIED),

  // ***********************************************************************************************
  // Data Model: 20_101 ~ 30_000
  // ***********************************************************************************************
  JOB_PARAM_VALUE_FORMAT_ERROR(20_101, ErrorType.UNDETERMINED),
  JOB_PARAM_VALUE_NOT_FOUND(20_102, ErrorType.UNDETERMINED),
  JOB_TIMEOUT(20_103, ErrorType.UNDETERMINED),
  JOB_GET_EXPIRE_TIME_ERROR_BEFORE_START(20_104, ErrorType.UNDETERMINED),
  JOB_TYPE_AND_FIRST_DEVICE_DO_NOT_MATCH(20_105, ErrorType.CUSTOMER_ISSUE),
  JOB_SET_JOB_SCOPED_SPECS_ERROR_IN_LAB(20_106, ErrorType.UNDETERMINED),
  JOB_KILLED_BY_USER_FROM_FE(20_107, ErrorType.CUSTOMER_ISSUE),
  JOB_SUSPENDED_DUE_TO_CONSECUTIVE_FAILURE(20_108, ErrorType.CUSTOMER_ISSUE),

  JOB_TYPE_NOT_SUPPORTED(20_110, ErrorType.CUSTOMER_ISSUE),

  JOB_CONFIG_INVALID_DEVICE_ERROR(20_120, ErrorType.CUSTOMER_ISSUE),
  JOB_CONFIG_GENERIC_ERROR(20_121, ErrorType.CUSTOMER_ISSUE),
  JOB_CONFIG_NO_JOB_NAME_ERROR(20_123, ErrorType.CUSTOMER_ISSUE),
  JOB_CONFIG_INVALID_JOB_TYPE_ERROR(20_124, ErrorType.CUSTOMER_ISSUE),
  JOB_CONFIG_INVALID_RUN_AS_ERROR(20_125, ErrorType.CUSTOMER_ISSUE),
  JOB_CONFIG_DEVICE_TARGET_PARSE_ERROR(20_126, ErrorType.CUSTOMER_ISSUE),
  JOB_CONFIG_FROM_TEXT_PROTOBUF_ERROR(20_127, ErrorType.INFRA_ISSUE),
  JOB_CONFIG_FROM_JSON_ERROR(20_128, ErrorType.INFRA_ISSUE),
  JOB_CONFIG_TO_JSON_ERROR(20_129, ErrorType.INFRA_ISSUE),
  JOB_CONFIG_MERGE_ERROR(20_130, ErrorType.INFRA_ISSUE),

  JOB_SPEC_PARSE_PROTOBUF_ERROR(20_131, ErrorType.CUSTOMER_ISSUE),
  JOB_SPEC_INVALID_JOB_TYPE_ERROR(20_132, ErrorType.CUSTOMER_ISSUE),
  JOB_SPEC_INVALID_FILE_PATH_ERROR(20_133, ErrorType.CUSTOMER_ISSUE),
  JOB_SPEC_PARSE_JSON_ERROR(20_134, ErrorType.CUSTOMER_ISSUE),
  JOB_SPEC_INVALID_FILE_FIELD_ERROR(20_135, ErrorType.CUSTOMER_ISSUE),
  JOB_CONFIG_INVALID_PLUGIN_FILE_PATH_ERROR(20_136, ErrorType.CUSTOMER_ISSUE),

  JOB_INFO_CREATE_INVALID_GEN_DIR_ERROR(20_151, ErrorType.UNDETERMINED),
  JOB_INFO_CREATE_OVERRIDE_INFO_ERROR(20_152, ErrorType.INFRA_ISSUE),
  JOB_INFO_CREATE_RESOLVED_DIR_NOT_FOUND_ERROR(20_153, ErrorType.INFRA_ISSUE),

  JOB_FAIL_TO_GENERATE_SPONGE_LINK(20_161, ErrorType.UNDETERMINED),

  TEST_TIMEOUT(20_201, ErrorType.UNDETERMINED),
  TEST_GET_EXPIRE_TIME_ERROR_BEFORE_START(20_202, ErrorType.UNDETERMINED),
  TEST_ADD_TEST_GENERATE_DUPLICATED_ID(20_203, ErrorType.DEPENDENCY_ISSUE), // UUID gen not unique
  TEST_ADD_TEST_WITH_DUPLICATED_ID(20_204, ErrorType.UNDETERMINED),
  TEST_REMOVE_RUNNING_TEST_ERROR(20_205, ErrorType.UNDETERMINED),
  TEST_RESULT_NOT_FOUND(20_206, ErrorType.UNDETERMINED),

  JOB_OR_TEST_FILE_ADD_NONEXIST_FILE(20_301, ErrorType.UNDETERMINED),
  JOB_OR_TEST_FILE_HANDLER_GENERATE_NO_FILE(20_302, ErrorType.UNDETERMINED),
  JOB_OR_TEST_FILE_HANDLER_ERROR(20_303, ErrorType.UNDETERMINED),
  JOB_OR_TEST_REPLACE_NONEXIST_FILE(20_304, ErrorType.UNDETERMINED),
  JOB_OR_TEST_FILE_NOT_FOUND(20_305, ErrorType.UNDETERMINED),
  JOB_OR_TEST_FILE_MULTI_PATHS(20_306, ErrorType.UNDETERMINED),
  JOB_OR_TEST_FILE_CHECK_ERROR(20_307, ErrorType.UNDETERMINED),
  JOB_OR_TEST_GEN_FILE_DIR_PREPARE_ERROR(20_308, ErrorType.INFRA_ISSUE),
  JOB_OR_TEST_RUN_FILE_DIR_PREPARE_ERROR(20_309, ErrorType.INFRA_ISSUE),
  JOB_OR_TEST_TMP_FILE_DIR_PREPARE_ERROR(20_310, ErrorType.INFRA_ISSUE),

  JOB_OR_TEST_RESULT_LEGACY_FAIL(20_311, ErrorType.CUSTOMER_ISSUE),
  JOB_OR_TEST_RESULT_LEGACY_ALLOC_FAIL(20_312, ErrorType.CUSTOMER_ISSUE),
  JOB_OR_TEST_RESULT_LEGACY_ERROR(20_313, ErrorType.UNDETERMINED),
  JOB_OR_TEST_RESULT_LEGACY_INFRA_ERROR(20_314, ErrorType.INFRA_ISSUE),
  JOB_OR_TEST_RESULT_LEGACY_ALLOC_ERROR(20_315, ErrorType.INFRA_ISSUE),
  JOB_OR_TEST_RESULT_LEGACY_ABORT(20_316, ErrorType.INFRA_ISSUE),
  JOB_OR_TEST_RESULT_LEGACY_TIMEOUT(20_317, ErrorType.CUSTOMER_ISSUE),
  JOB_OR_TEST_RESULT_LEGACY_SKIP(20_318, ErrorType.CUSTOMER_ISSUE),
  TEST_RESULT_FAILED_IN_TEST_XML(20_319, ErrorType.CUSTOMER_ISSUE),

  TEST_DOWNLOAD_GEN_DIRECTORY_FROM_LAB_INTERRUPTED(20_320, ErrorType.UNDETERMINED),
  TEST_RESULT_SKIPPED_IN_TEST_XML(20_321, ErrorType.CUSTOMER_ISSUE),
  TEST_RESULT_ERRORED_IN_TEST_XML(20_322, ErrorType.CUSTOMER_ISSUE),

  MESSAGING_MESSAGE_DESTINATION_NOT_FOUND(20_401, ErrorType.CUSTOMER_ISSUE),

  // ***********************************************************************************************
  // Util: 30_001 ~ 35_000,
  // ***********************************************************************************************
  // File: 30_001 ~ 30_500
  LOCAL_FILE_OR_DIR_NOT_FOUND(30_001, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_COPY_ERROR(30_002, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_REMOVE_ERROR(30_003, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_CHANGE_GROUP_ERROR(30_004, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_CHANGE_OWNER_ERROR(30_005, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_GET_SIZE_ERROR(30_006, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_PARSE_SIZE_ERROR(30_007, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_LINK_ERROR(30_008, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_MOVE_ERROR(30_009, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_READ_SYMLINK_ERROR(30_010, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_GET_REAL_PATH_ERROR(30_011, ErrorType.UNDETERMINED),
  LOCAL_FILE_OR_DIR_GRANT_PERMISSION_RECURSIVELY_ERROR(30_012, ErrorType.UNDETERMINED),

  LOCAL_FILE_IS_DIR(30_101, ErrorType.UNDETERMINED),
  LOCAL_FILE_CREATE_NEW_ERROR(30_102, ErrorType.UNDETERMINED),
  LOCAL_FILE_APPEND_ERROR(30_103, ErrorType.UNDETERMINED),
  LOCAL_FILE_GET_OWNER_ERROR(30_104, ErrorType.UNDETERMINED),
  LOCAL_FILE_GET_PERMISSION_ERROR(30_105, ErrorType.UNDETERMINED),
  LOCAL_FILE_GRANT_PERMISSION_ERROR(30_106, ErrorType.UNDETERMINED),
  LOCAL_FILE_GET_MODIFIED_TIME_ERROR(30_107, ErrorType.INFRA_ISSUE),
  LOCAL_FILE_GET_SIZE_ERROR(30_108, ErrorType.UNDETERMINED),
  LOCAL_FILE_TOO_LARGE_TO_READ(30_109, ErrorType.INFRA_ISSUE),
  LOCAL_FILE_READ_BINARY_ERROR(30_110, ErrorType.UNDETERMINED),
  LOCAL_FILE_READ_STRING_ERROR(30_111, ErrorType.UNDETERMINED),
  LOCAL_FILE_READ_HEAD_ERROR(30_112, ErrorType.UNDETERMINED),
  LOCAL_FILE_READ_TAIL_ERROR(30_113, ErrorType.UNDETERMINED),
  LOCAL_FILE_READ_LINES_FROM_FILE_SET(30_114, ErrorType.UNDETERMINED),
  LOCAL_FILE_READ_LINES_FROM_FILE(30_115, ErrorType.UNDETERMINED),
  LOCAL_FILE_WRITE_STRING_ERROR(30_116, ErrorType.UNDETERMINED),
  LOCAL_FILE_WRITE_BYTE_ERROR(30_117, ErrorType.UNDETERMINED),
  LOCAL_FILE_WRITE_STREAM_ERROR(30_118, ErrorType.UNDETERMINED),
  LOCAL_FILE_WRITE_STREAM_WITH_BUFFER_ERROR(30_119, ErrorType.UNDETERMINED),
  LOCAL_FILE_WRITE_STREAM_CREATE_BUFFER_ERROR(30_120, ErrorType.UNDETERMINED),
  LOCAL_FILE_SET_PERMISSION_ERROR(30_121, ErrorType.UNDETERMINED),
  LOCAL_FILE_RESET_ERROR(30_122, ErrorType.INFRA_ISSUE),
  LOCAL_FILE_UNZIP_ERROR(30_123, ErrorType.UNDETERMINED),
  LOCAL_FILE_UNZIP_PARTICULAR_FILES_ERROR(30_124, ErrorType.UNDETERMINED),
  LOCAL_FILE_CREATE_HARD_LINK_ERROR(30_125, ErrorType.UNDETERMINED),
  LOCAL_FILE_UNZIP_FILENAME_NOT_MATCHED(30_126, ErrorType.CUSTOMER_ISSUE),
  LOCAL_FILE_NEW_BUFFERED_WRITER_ERROR(30_127, ErrorType.UNDETERMINED),
  LOCAL_FILE_TAR_EXTRACT_ERROR(30_128, ErrorType.UNDETERMINED),

  LOCAL_DIR_IS_FILE(30_201, ErrorType.UNDETERMINED),
  LOCAL_DIR_CREATE_ERROR(30_202, ErrorType.UNDETERMINED),
  LOCAL_DIR_CREATE_TMP_ERROR(30_203, ErrorType.UNDETERMINED),
  LOCAL_DIR_LINK_ERROR(30_204, ErrorType.UNDETERMINED),
  LOCAL_DIR_LINK_ERROR_WITH_RETRY(30_205, ErrorType.UNDETERMINED),
  LOCAL_DIR_LIST_LINKS_ERROR(30_206, ErrorType.UNDETERMINED),
  LOCAL_DIR_LIST_DIR_DEPTH_PARAM_ERROR(30_207, ErrorType.UNDETERMINED),
  LOCAL_DIR_LIST_FILE_PATHS_ERROR(30_208, ErrorType.UNDETERMINED),
  LOCAL_DIR_LIST_SUB_DIR_ERROR(30_209, ErrorType.UNDETERMINED),
  LOCAL_DIR_MERGE_ERROR(30_210, ErrorType.UNDETERMINED),
  LOCAL_DIR_ZIP_ERROR(30_211, ErrorType.UNDETERMINED),
  LOCAL_DIR_LIST_FILE_OR_DIRS_ERROR(30_212, ErrorType.UNDETERMINED),
  LOCAL_MOUNT_ZIP_TO_DIR_ERROR(30_213, ErrorType.UNDETERMINED),
  LOCAL_UNMOUNT_DIR_ERROR(30_214, ErrorType.UNDETERMINED),

  FILE_CHECKSUM_ERROR(30_490, ErrorType.UNDETERMINED),

  // Network: 30_501 ~ 30_600
  LOCAL_NETWORK_ERROR(30_501, ErrorType.INFRA_ISSUE),
  LOCAL_NETWORK_INTERFACE_DETECTION_ERROR(30_502, ErrorType.INFRA_ISSUE),
  LOCAL_NETWORK_HOSTNAME_DETECTION_ERROR(30_503, ErrorType.INFRA_ISSUE),

  // MobileHarnessLogger: 30_601 ~ 30_650
  MOBILE_HARNESS_LOGGER_CREATE_FILE_HANDLER_ERROR(30_601, ErrorType.UNDETERMINED),

  // Tradefed FileUtil: 30_651 ~ 30_850
  FILE_UTIL_LOW_DISK_SPACE(30_651, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_LAB_HOST_FILESYSTEM_ERROR(30_652, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_CREATE_DIR_ERROR(30_653, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_READ_STRING_FROM_FILE_ERROR(30_654, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_CALCULATE_CRC32_ERROR(30_655, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_SAVE_RESOURCE_FILE_ERROR(30_656, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_CREATE_TEMP_FILE_ERROR(30_657, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_HARDLINK_FILE_ERROR(30_658, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_WRITE_TO_FILE_ERROR(30_659, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_COPY_FILE_ERROR(30_660, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_SYMLINK_FILE_ERROR(30_661, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_LIST_FILES_ERROR(30_662, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_WALK_FILES_ERROR(30_663, ErrorType.DEPENDENCY_ISSUE),
  FILE_UTIL_COMPARE_FILE_CONTENTS_ERROR(30_664, ErrorType.DEPENDENCY_ISSUE),

  // Tradefed StreamUtil: 30_851 ~ 30_900
  STREAM_UTIL_NO_DISK_SPACE(30_851, ErrorType.DEPENDENCY_ISSUE),
  STREAM_UTIL_COPY_STREAMS_ERROR(30_852, ErrorType.DEPENDENCY_ISSUE),
  STREAM_UTIL_SKIP_BYTES_ERROR(30_853, ErrorType.DEPENDENCY_ISSUE),
  STREAM_UTIL_COPY_FILE_TO_STREAM_ERROR(30_854, ErrorType.DEPENDENCY_ISSUE),
  STREAM_UTIL_GET_BYTE_ARRAY_LIST_FROM_STREAM_ERROR(30_855, ErrorType.DEPENDENCY_ISSUE),
  STREAM_UTIL_GET_STRING_FROM_STREAM_ERROR(30_856, ErrorType.DEPENDENCY_ISSUE),
  STREAM_UTIL_COPY_STREAM_TO_WRITER_ERROR(30_857, ErrorType.DEPENDENCY_ISSUE),
  STREAM_UTIL_CALCULATE_CRC32_ERROR(30_858, ErrorType.DEPENDENCY_ISSUE),
  STREAM_UTIL_CLOSE_STREAM_ERROR(30_859, ErrorType.DEPENDENCY_ISSUE),
  STREAM_UTIL_CALCULATE_MD5_DIGEST_ERROR(30_860, ErrorType.DEPENDENCY_ISSUE),

  // Tradefed ZipUtil: 30_901 ~ 30_950
  ZIP_UTIL_ARTIFACT_INVALID(30_901, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_CHECK_ZIP_FILE_ERROR(30_902, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_GET_INPUT_STREAM_ERROR(30_903, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_VALIDATE_DESTINATION_DIR_ERROR(30_904, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_CREATE_ZIP_ERROR(30_905, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_GZIP_FILE_ERROR(30_906, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_LIST_FILES_ERROR(30_907, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_ADD_TO_ZIP_ERROR(30_908, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_WRITE_TO_STREAM_ERROR(30_909, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_SET_POSIX_FILE_PERMISSION_ERROR(30_910, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_UNZIP_TO_STREAM_ERROR(30_911, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_UNZIP_TO_STREAM_DATA_FORMAT_ERROR(30_912, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_CREATE_NEW_FILE_ERROR(30_913, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_GET_OUTPUT_STREAM_ERROR(30_914, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_CRC_NOT_MATCH_ERROR(30_915, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_CREATE_SYMBOLIC_LINK_ERROR(30_916, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_UNZIP_SYMLINK_COMPRESSION_METHOD_NOT_SUPPORTED(30_917, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_EXTRACT_ZIP_ERROR(30_918, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_OPEN_ZIP_FILE_ERROR(30_919, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_INVALID_CENTRAL_DIRECTORY_INFO(30_920, ErrorType.DEPENDENCY_ISSUE),
  ZIP_UTIL_INVALID_LOCAL_FILE_HEADER_ERROR(30_921, ErrorType.DEPENDENCY_ISSUE),

  // Concurrency: 31_601 ~ 31_620
  UNEXPECTED_NON_MH_CHECKED_EXCEPTION_FROM_SUB_TASK(31_601, ErrorType.INFRA_ISSUE),

  // System: 31_651 ~ 31_900
  SYSTEM_NO_PROCESS_FOUND(31_651, ErrorType.UNDETERMINED),
  SYSTEM_MORE_THAN_ONE_PROCESS_FOUND(31_652, ErrorType.UNDETERMINED),
  SYSTEM_PROCESS_HEADER_NOT_FOUND(31_653, ErrorType.UNDETERMINED),
  SYSTEM_INVALID_PROCESS_INFO_LINE(31_654, ErrorType.UNDETERMINED),
  SYSTEM_INVALID_PROCESS_ID(31_655, ErrorType.UNDETERMINED),
  SYSTEM_NO_PROCESS_FOUND_BY_PORT(31_656, ErrorType.UNDETERMINED),
  SYSTEM_INVALID_PROCESS_LIST_ERROR(31_657, ErrorType.UNDETERMINED),
  SYSTEM_UNEXPECTED_PROCESS_HEADER(31_658, ErrorType.UNDETERMINED),
  SYSTEM_PARENT_PROCESS_NOT_FOUND(31_659, ErrorType.UNDETERMINED),
  SYSTEM_KILL_PROCESS_ERROR(31_660, ErrorType.UNDETERMINED),
  SYSTEM_FAILED_TO_ADD_USER_TO_GROUP(31_661, ErrorType.UNDETERMINED),
  SYSTEM_FAILED_TO_GET_USER_GROUPS(31_662, ErrorType.UNDETERMINED),
  SYSTEM_NO_GROUPS_FOR_USER(31_663, ErrorType.UNDETERMINED),
  SYSTEM_EMPTY_LOGNAME(31_664, ErrorType.UNDETERMINED),
  SYSTEM_ROOT_ACCESS_REQUIRED(31_665, ErrorType.UNDETERMINED),
  SYSTEM_USER_HOME_NOT_FOUND(31_666, ErrorType.UNDETERMINED),
  SYSTEM_NOT_RUN_ON_LINUX_OR_MAC(31_667, ErrorType.UNDETERMINED),
  SYSTEM_NOT_RUN_ON_LINUX(31_668, ErrorType.UNDETERMINED),
  SYSTEM_ACCESS_PROC_MEMINFO_ERROR(31_669, ErrorType.DEPENDENCY_ISSUE),
  SYSTEM_TAG_NOT_FOUND_IN_PROC_MEMINFO(31_670, ErrorType.UNDETERMINED),
  SYSTEM_CHECK_KVM_ERROR(31_671, ErrorType.UNDETERMINED),
  SYSTEM_LIST_OPEN_FILES_ERROR(31_672, ErrorType.UNDETERMINED),
  SYSTEM_LIST_PROCESSES_ERROR(31_673, ErrorType.UNDETERMINED),
  SYSTEM_GET_GIVEN_PROCESS_INFO_ERROR(31_674, ErrorType.UNDETERMINED),
  SYSTEM_KILLALL_PROCESS_ERROR(31_675, ErrorType.UNDETERMINED),
  SYSTEM_ADD_USER_TO_GROUP_ERROR(31_676, ErrorType.UNDETERMINED),
  SYSTEM_GET_USER_GROUP_ERROR(31_677, ErrorType.UNDETERMINED),
  SYSTEM_MAC_LAUNCHCTL_MANAGERUID_ERROR(31_678, ErrorType.UNDETERMINED),
  SYSTEM_MAC_LAUNCHCTL_LIST_ERROR(31_679, ErrorType.UNDETERMINED),
  SYSTEM_GET_LOGNAME_ERROR(31_680, ErrorType.UNDETERMINED),
  SYSTEM_MAC_DSCL_CMD_ERROR(31_681, ErrorType.UNDETERMINED),
  SYSTEM_GETENT_CMD_ERROR(31_682, ErrorType.UNDETERMINED),
  SYSTEM_MAC_SYSTEM_PROFILER_ERROR(31_683, ErrorType.UNDETERMINED),
  SYSTEM_MAC_GET_MEMORY_SIZE_ERROR(31_684, ErrorType.UNDETERMINED),
  SYSTEM_GET_MAC_DISK_INFO_ERROR(31_685, ErrorType.UNDETERMINED),
  SYSTEM_GET_DISK_TYPE_NON_MAC_UNIMPLEMENTED(31_686, ErrorType.INFRA_ISSUE),

  // Reflection: 31_901 ~ 32_000
  REFLECTION_CLASS_NOT_FOUND(31_901, ErrorType.UNDETERMINED),
  REFLECTION_CLASS_TYPE_NOT_MATCHED(31_902, ErrorType.UNDETERMINED),
  REFLECTION_CONSTRUCTOR_NOT_FOUND(31_903, ErrorType.UNDETERMINED),
  REFLECTION_INSTANTIATION_ERROR(31_904, ErrorType.UNDETERMINED),
  REFLECTION_CREATE_DEVICE_CONSTRUCTOR_NOT_FOUND(31_905, ErrorType.CUSTOMER_ISSUE),
  REFLECTION_CREATE_DEVICE_ERROR(31_906, ErrorType.CUSTOMER_ISSUE),
  CLASS_DETECTOR_CLASS_NOT_FOUND(31_907, ErrorType.DEPENDENCY_ISSUE),
  CLASS_DEVICE_CLASS_NOT_FOUND(31_908, ErrorType.CUSTOMER_ISSUE),
  CLASS_DRIVER_CLASS_NOT_FOUND(31_909, ErrorType.CUSTOMER_ISSUE),
  CLASS_DECORATOR_CLASS_NOT_FOUND(31_910, ErrorType.CUSTOMER_ISSUE),
  CLASS_STEP_FIELD_ACCESS_ERROR(31_911, ErrorType.DEPENDENCY_ISSUE),
  CLASS_ILLEGAL_VALIDATOR_METHOD(31_912, ErrorType.DEPENDENCY_ISSUE),
  CLASS_DISPATCHER_CLASS_NOT_FOUND(31_913, ErrorType.DEPENDENCY_ISSUE),
  REFLECTION_CLASS_LOAD_ERROR(31_914, ErrorType.UNDETERMINED),

  // Jar Resource files: 32_001 ~ 32_050
  JAR_RES_NOT_FOUND(32_001, ErrorType.UNDETERMINED),
  JAR_RES_COPY_ERROR(32_002, ErrorType.UNDETERMINED),

  // Generic file resolver: 32_251 ~ 32_300
  RESOLVE_FILE_GENERIC_ERROR(32_251, ErrorType.INFRA_ISSUE),
  RESOLVE_FILE_MISS_NOTEST_LOASD_ERROR(32_252, ErrorType.CUSTOMER_ISSUE),
  RESOLVE_FILE_MISS_PACKAGE_INFO_ERROR(32_253, ErrorType.INFRA_ISSUE),
  RESOLVE_FILE_INVALID_FILE_ERROR(32_254, ErrorType.CUSTOMER_ISSUE),
  RESOLVE_FILE_CACHED_EXCEPTION_ERROR(32_255, ErrorType.INFRA_ISSUE),
  RESOLVE_FILE_TIMEOUT(32_300, ErrorType.INFRA_ISSUE),

  // GCS file resolver, 32_301 ~ 32_350
  GCS_SECURITY_ERROR(32_301, ErrorType.DEPENDENCY_ISSUE),
  GCS_FILE_NOT_FOUND(32_302, ErrorType.DEPENDENCY_ISSUE),
  GCS_ILLEGAL_OFFSET(32_303, ErrorType.INFRA_ISSUE),
  GCS_ILLEGAL_SIZE(32_304, ErrorType.INFRA_ISSUE),
  GCS_NO_DOWNLOADER(32_305, ErrorType.INFRA_ISSUE),
  GCS_DOWNLOAD_FILE_ERROR(32_306, ErrorType.DEPENDENCY_ISSUE),
  GCS_UPLOAD_FILE_ERROR(32_307, ErrorType.DEPENDENCY_ISSUE),
  GCS_GET_METADATA_ERROR(32_308, ErrorType.DEPENDENCY_ISSUE),
  GCS_NO_METADATA(32_309, ErrorType.CUSTOMER_ISSUE),
  GCS_LIST_FILES_ERROR(32_310, ErrorType.DEPENDENCY_ISSUE),
  GCS_DELETE_FILE_ERROR(32_311, ErrorType.DEPENDENCY_ISSUE),
  GCS_MEET_QUOTA_ISSUE(32_312, ErrorType.INFRA_ISSUE),
  GCS_LOAD_CACHE_ERROR(32_313, ErrorType.INFRA_ISSUE),
  GCS_SAVE_CACHE_ERROR(32_314, ErrorType.INFRA_ISSUE),
  GCS_COMPRESS_DIRECTORY(32_315, ErrorType.INFRA_ISSUE),
  GCS_DOWNLOAD_FILE_NO_SPACE_ERROR(32_316, ErrorType.DEPENDENCY_ISSUE),
  GCS_INVALID_PARAMS(32_317, ErrorType.CUSTOMER_ISSUE),
  GCS_ILLEGAL_PATH_ERROR(32_318, ErrorType.CUSTOMER_ISSUE),
  GCS_MEET_NETWORK_ISSUE(32_319, ErrorType.DEPENDENCY_ISSUE),
  GCS_CLOUD_GAIA_CONNECT_ERROR(32_320, ErrorType.DEPENDENCY_ISSUE),

  // ATS file server file resolver: 32_351 ~ 32_400
  ATS_FILE_SERVER_RESOLVE_FILE_ERROR(32_351, ErrorType.INFRA_ISSUE),
  HTTP_INVALID_FILE_PATH_ERROR(32_352, ErrorType.CUSTOMER_ISSUE),
  ATS_FILE_SERVER_UPLOAD_FILE_ERROR(32_353, ErrorType.INFRA_ISSUE),

  // SSH ERROR: 32_751 ~ 32_780
  SSH_CONNECTION_ERROR(32_751, ErrorType.UNDETERMINED),
  SSH_INVALID_PARAMS(32_752, ErrorType.UNDETERMINED),
  SSH_COMMAND_EXECUTION_ERROR(32_753, ErrorType.UNDETERMINED),
  SSH_SFTP_EXECUTION_ERROR(32_754, ErrorType.UNDETERMINED),
  SSH_UPLOAD_DIR_TO_FILE_ERROR(32_755, ErrorType.UNDETERMINED),

  // DHCP ERROR: 32_781 ~ 32_800
  DHCP_DB_ERROR(32_781, ErrorType.UNDETERMINED),

  // Blobstore ERROR: 32_801 ~ 32_820
  BLOBSTORE_BLOBFILE_NOT_EXIST_ERROR(32_801, ErrorType.CUSTOMER_ISSUE),
  BLOBSTORE_READ_BLOB_METADATA_ERROR(32_802, ErrorType.DEPENDENCY_ISSUE),
  BLOBSTORE_OPEN_AND_READ_FILE_ERROR(32_803, ErrorType.DEPENDENCY_ISSUE),
  BLOBSTORE_FETCH_FILE_ERROR(32_804, ErrorType.DEPENDENCY_ISSUE),

  // Sponge operation: 33_001 ~ 33_100
  SPONGE_PARSE_XML_ERROR(33_005, ErrorType.UNDETERMINED),
  SPONGE_DOCUMENT_BUILDER_FACTORY_SET_FEATURE_ERROR(33_019, ErrorType.INFRA_ISSUE),

  // Plugin loader: 33_101 ~ 33_150
  PLUGIN_LOADER_FAILED_TO_GET_JAR_URL(33_101, ErrorType.CUSTOMER_ISSUE),
  PLUGIN_LOADER_FAILED_TO_CREATE_PLUGIN_MODULE_INSTANCE(33_102, ErrorType.CUSTOMER_ISSUE),
  PLUGIN_LOADER_FAILED_TO_CREATE_PLUGIN_INSTANCE(33_103, ErrorType.CUSTOMER_ISSUE),
  PLUGIN_LOADER_MALFORMED_URL(33_104, ErrorType.UNDETERMINED),

  // ExperimentManager: 33_151 ~ 33_200
  EXPERIMENT_MANAGER_READ_SOURCE_WITH_TEST_LOAS(33_151, ErrorType.CUSTOMER_ISSUE),

  // JobSuspender: 33_201 ~ 33_250
  JOB_SUSPENDER_CONFIG_FILE_FORMAT_ERROR(33_201, ErrorType.CUSTOMER_ISSUE),
  JOB_SUSPENDER_CONFIG_FILE_READ_ERROR(33_202, ErrorType.CUSTOMER_ISSUE),

  // L-SPACE file resolver: 33_251 ~ 33_300
  LSPACE_RESOLVE_ERROR(33_251, ErrorType.DEPENDENCY_ISSUE),
  LSPACE_ILLEGAL_ARGUMENT(33_252, ErrorType.CUSTOMER_ISSUE),
  LSPACE_ACL_ERROR(33_253, ErrorType.CUSTOMER_ISSUE),
  LSPACE_PERMISSION_DENIED_ERROR(33_254, ErrorType.CUSTOMER_ISSUE),
  LSPACE_CREDENTIAL_NOT_PERMIT_ERROR(33_255, ErrorType.CUSTOMER_ISSUE),
  LSPACE_FAIL_TO_PREPARE_PARENT_DIR(33_256, ErrorType.CUSTOMER_ISSUE),

  // Config file related: 33_350 ~ 33_400
  API_CONFIG_FILE_READ_ERROR(33_351, ErrorType.DEPENDENCY_ISSUE),
  LAB_DEVICE_CONFIG_FILE_READ_ERROR(33_352, ErrorType.DEPENDENCY_ISSUE),

  // Database connections: 33_401 ~ 33_450
  DATABASE_CONNECTIONS_INITIALIZE_ERROR(33_401, ErrorType.UNDETERMINED),
  DATABASE_CONNECTIONS_GET_CONNECTION_ERROR(33_402, ErrorType.UNDETERMINED),
  DATABASE_LIST_TABLES_ERROR(33_403, ErrorType.UNDETERMINED),
  DATABASE_TABLE_UPDATE_ERROR(33_404, ErrorType.UNDETERMINED),
  DATABASE_TABLE_DELETE_ERROR(33_405, ErrorType.UNDETERMINED),
  DATABASE_TABLE_READ_ERROR(33_406, ErrorType.UNDETERMINED),
  DATABASE_TABLE_READ_PROTO_PARSE_ERROR(33_407, ErrorType.UNDETERMINED),
  DATABASE_DRIVER_LOAD_ERROR(33_408, ErrorType.INFRA_ISSUE),

  // Legacy Command: 33_451 ~ 33_460
  LEGACY_COMMAND_START_ERROR(33_451, ErrorType.UNDETERMINED),
  LEGACY_COMMAND_EXEC_FAIL(33_452, ErrorType.UNDETERMINED),
  LEGACY_COMMAND_EXEC_TIMEOUT(33_453, ErrorType.UNDETERMINED),

  // Perfgate: 33_461 ~ 33_470
  PERFGATE_ILLEGAL_STATE(33_461, ErrorType.UNDETERMINED),
  PERFGATE_FILE_WRITE_ERROR(33_462, ErrorType.UNDETERMINED),

  // URL related: 33_471 ~ 33_500
  URI_ERROR(33_471, ErrorType.UNDETERMINED),

  // Metadata generator: 33_521 ~ 33_530
  METADATA_GENERATOR_REFLECTION_CLASS_NOT_FOUND(33_521, ErrorType.INFRA_ISSUE),
  METADATA_GENERATOR_JOB_TAG_ANNOTATION_INVALID(33_522, ErrorType.INFRA_ISSUE),

  // Device: 33_581 ~ 33_630
  DEVICE_INFO_ALREADY_EXIST_ERROR(33_581, ErrorType.INFRA_ISSUE),

  // Secret Manager: 33_671 ~ 33_680
  SECRET_MANAGER_ACCESS_SECRET_VERSION_ERROR(33_671, ErrorType.DEPENDENCY_ISSUE),

  // RPC status code mapping: 39_701 ~ 39_800
  RPC_STUBBY_EXCEPTION_STATUS_CODE_DEFAULT(39_701, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_CANCELLED(39_702, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_UNKNOWN(39_703, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_INVALID_ARGUMENT(39_704, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_DEADLINE_EXCEEDED(39_705, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_NOT_FOUND(39_706, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_ALREADY_EXISTS(39_707, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_PERMISSION_DENIED(39_708, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_RESOURCE_EXHAUSTED(39_709, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_FAILED_PRECONDITION(39_710, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_ABORTED(39_711, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_OUT_OF_RANGE(39_712, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_UNIMPLEMENTED(39_713, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_INTERNAL(39_714, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_UNAVAILABLE(39_715, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_STATUS_CODE_UNAUTHENTICATED(39_716, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_DEFAULT(39_751, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_CANCELLED(39_752, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_UNKNOWN(39_753, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_INVALID_ARGUMENT(39_754, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_DEADLINE_EXCEEDED(39_755, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_NOT_FOUND(39_756, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_ALREADY_EXISTS(39_757, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_PERMISSION_DENIED(39_758, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_RESOURCE_EXHAUSTED(39_759, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_FAILED_PRECONDITION(39_760, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_ABORTED(39_761, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_OUT_OF_RANGE(39_762, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_UNIMPLEMENTED(39_763, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_INTERNAL(39_764, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_UNAVAILABLE(39_765, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_STATUS_CODE_UNAUTHENTICATED(39_766, ErrorType.INFRA_ISSUE),

  // RPC: 39_951 ~ 39_975
  RPC_STUBBY_EXCEPTION_WITHOUT_STATUS_CODE(39_961, ErrorType.INFRA_ISSUE),
  RPC_STUBBY_EXCEPTION_WITHOUT_ERROR_DETAIL(39_963, ErrorType.INFRA_ISSUE),
  RPC_GRPC_EXCEPTION_WITHOUT_ERROR_DETAIL(39_972, ErrorType.INFRA_ISSUE),

  // Event: 39_976 ~ 39_980
  EVENT_HANDLER_ERROR(39_976, ErrorType.UNDETERMINED),

  // Shared utilities
  // Base64: 39_801 ~ 39_810
  BASE64_ARGUMENT_ERROR(39_801, ErrorType.UNDETERMINED),
  BASE64_ENCODING_ERROR(39_802, ErrorType.UNDETERMINED),

  // Cache: 39_811 ~ 39_820
  CACHE_LOAD_ERROR(39_811, ErrorType.UNDETERMINED),

  // Proto: 39_821 ~ 39_830
  PROTO_FIELD_NOT_IN_MESSAGE(39_821, ErrorType.UNDETERMINED),
  PROTO_MESSAGE_FIELD_TYPE_UNSUPPORTED(39_822, ErrorType.UNDETERMINED),
  PROTO_FIELD_VALUE_NUMBER_FORMAT_ERROR(39_823, ErrorType.UNDETERMINED),

  // Flag: 39_831 ~ 39_840
  FLAG_FORMAT_ERROR(39_831, ErrorType.INFRA_ISSUE),

  // Pubsub: 39_841 ~ 39_850
  PUBSUB_SUBSCRIBER_START_ERROR(39_841, ErrorType.INFRA_ISSUE),
  PUBSUB_SUBSCRIBER_STOP_ERROR(39_842, ErrorType.INFRA_ISSUE),

  // Filter: 39_911 ~ 39_930
  FILTER_EMPTY_INPUT(39_911, ErrorType.CUSTOMER_ISSUE),
  FILTER_ILLEGAL_INPUT(39_912, ErrorType.CUSTOMER_ISSUE),
  FILTER_PREDICATE_TYPE_ERROR(39_913, ErrorType.CUSTOMER_ISSUE),
  FILTER_CONDITION_ERROR(39_914, ErrorType.CUSTOMER_ISSUE),
  FILTER_TOKEN_ERROR(39_915, ErrorType.CUSTOMER_ISSUE),
  FILTER_PATTERN_ERROR(39_929, ErrorType.INFRA_ISSUE),
  FILTER_ILLEGAL_STATE(39_930, ErrorType.INFRA_ISSUE),

  // Version: 39_931 ~ 39_950
  VERSION_SEQUENCE_LENGTH_ERROR(39_931, ErrorType.INFRA_ISSUE),
  VERSION_NUM_FORMAT_ERROR(39_932, ErrorType.INFRA_ISSUE),
  VERSION_NUM_RANGE_ERROR(39_933, ErrorType.INFRA_ISSUE),
  VERSION_SERVICE_FORMAT_ERROR(39_934, ErrorType.INFRA_ISSUE),
  VERSION_STUB_FORMAT_ERROR(39_935, ErrorType.INFRA_ISSUE),
  VERSION_SERVICE_TOO_OLD(39_936, ErrorType.INFRA_ISSUE),
  VERSION_STUB_TOO_OLD(39_937, ErrorType.INFRA_ISSUE),
  VERSION_STUB_GET_VERSION_ERROR(39_938, ErrorType.INFRA_ISSUE),

  // User plugin: 39_951 ~ 39_955
  USER_PLUGIN_ERROR(39_951, ErrorType.CUSTOMER_ISSUE),

  /**
   * @deprecated Please use USER_PLUGIN_SKIP_TEST or USER_PLUGIN_SKIP_JOB
   */
  @Deprecated
  USER_PLUGIN_SKIP_TEST_OR_JOB(39_952, ErrorType.CUSTOMER_ISSUE),
  INTEGRATION_TEST_ASSERTION_ERROR(39_953, ErrorType.CUSTOMER_ISSUE),
  USER_PLUGIN_SKIP_JOB(
      39_954, ErrorType.CUSTOMER_ISSUE), // Used by MobileHarnessUserPluginException only.
  USER_PLUGIN_SKIP_TEST(
      39_955, ErrorType.CUSTOMER_ISSUE), // Used by MobileHarnessUserPluginException only.
  USER_PLUGIN_SKIP_JOB_BY_INTERRUPTED_EXCEPTION(39_956, ErrorType.CUSTOMER_ISSUE),
  USER_PLUGIN_SKIP_TEST_BY_INTERRUPTED_EXCEPTION(39_957, ErrorType.CUSTOMER_ISSUE),

  // Command: 39_981 ~ 40_000
  COMMAND_START_ERROR(39_997, ErrorType.UNDETERMINED), // can be caused by bad start timeout config
  COMMAND_EXEC_FAIL(39_998, ErrorType.UNDETERMINED),
  COMMAND_EXEC_TIMEOUT(39_999, ErrorType.UNDETERMINED),
  COMMAND_CALLBACK_ERROR(40_000, ErrorType.UNDETERMINED);

  public static final int MIN_CODE = 20_001;
  public static final int MAX_CODE = 40_000;

  private final int code;
  private final ErrorType type;

  BasicErrorId(int code, ErrorType type) {
    Preconditions.checkArgument(code >= MIN_CODE);
    Preconditions.checkArgument(code <= MAX_CODE);
    Preconditions.checkArgument(code == MIN_CODE || type != ErrorType.UNCLASSIFIED);
    this.code = code;
    this.type = type;
  }

  @Override
  public int code() {
    return code;
  }

  @Override
  public ErrorType type() {
    return type;
  }

  @Override
  public String toString() {
    return ErrorIdFormatter.formatErrorId(this);
  }
}
