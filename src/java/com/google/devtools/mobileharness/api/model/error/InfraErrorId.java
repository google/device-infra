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
 * Errors from Mobile Harness infra, such as Client, Lab Server, Master Server, etc; or Mobile
 * Harness services such as Moscar, Moss, etc.
 */
public enum InfraErrorId implements ErrorId {
  // ***********************************************************************************************
  // Infra: 40_001 ~ 50_000
  // ***********************************************************************************************

  // Test Runner ERROR (1): 40_101 ~ 40_120
  TR_CHECK_DEVICE_UNMATCHED_DEVICE_COUNT(40_101, ErrorType.INFRA_ISSUE),
  TR_MULTIPLE_DEVICES_IN_DIFFERENT_LABS(40_102, ErrorType.UNDETERMINED),
  TR_FAILED_TO_RUN_SUB_DRIVER_IN_ADHOC_TESTBED_TEST(40_103, ErrorType.UNDETERMINED),
  TR_FAILED_TO_RUN_DEVICE_PRE_RUN_TEST_IN_LOCAL_TEST_FLOW(40_104, ErrorType.UNDETERMINED),
  TR_FAILED_TO_RUN_DEVICE_POST_RUN_TEST_IN_LOCAL_TEST_FLOW(40_105, ErrorType.UNDETERMINED),
  TR_SEND_TEST_MESSAGE_TEST_NOT_FOUND(40_106, ErrorType.UNDETERMINED),
  TR_JOB_TIMEOUT_AND_INTERRUPTED(40_107, ErrorType.CUSTOMER_ISSUE),
  TR_TEST_TIMEOUT_AND_INTERRUPTED(40_108, ErrorType.CUSTOMER_ISSUE),
  TR_TEST_INTERRUPTED_IN_SATELLITE_LAB(40_109, ErrorType.CUSTOMER_ISSUE),
  TR_TEST_TIMEOUT_AND_KILLED(40_110, ErrorType.CUSTOMER_ISSUE),
  TR_TEST_CLOSED_BEFORE_KICKED_OFF(40_111, ErrorType.INFRA_ISSUE),
  TR_TEST_INTERRUPTED_WHEN_WAITING_KICK_OFF_TEST(40_112, ErrorType.INFRA_ISSUE),
  TR_DEVICE_DISCONNECTED_BEFORE_TEST_START(40_113, ErrorType.DEPENDENCY_ISSUE),
  TR_TEST_RUNNER_FATAL_ERROR(40_114, ErrorType.UNDETERMINED),
  TR_TEST_FINISHED_WITHOUT_RESULT(40_115, ErrorType.INFRA_ISSUE),
  TR_SEND_TEST_MESSAGE_ILLEGAL_MESSAGE_NAMESPACE(40_116, ErrorType.CUSTOMER_ISSUE),
  TR_FAILED_TO_KICK_OFF_REMOTE_TEST(40_117, ErrorType.UNDETERMINED),
  TR_TEST_INTERRUPTED_IN_SHARED_LAB(40_118, ErrorType.INFRA_ISSUE),
  TR_TEST_INTERRUPTED_WHEN_MNM_DEVICE_TOO_HOT(40_119, ErrorType.DEPENDENCY_ISSUE),
  TR_TEST_INTERRUPTED_WHEN_USER_KILL_JOB(40_120, ErrorType.CUSTOMER_ISSUE),

  // Test Runner Plugin Error: 40_121 ~ 40_200
  TR_PLUGIN_USER_SKIP_JOB(40_121, ErrorType.CUSTOMER_ISSUE),
  TR_PLUGIN_USER_SKIP_JOB_IN_TEST_EVENT(40_122, ErrorType.CUSTOMER_ISSUE),
  TR_PLUGIN_USER_SKIP_TEST(40_123, ErrorType.CUSTOMER_ISSUE),
  TR_PLUGIN_USER_SKIP_TEST_IN_JOB_EVENT(40_124, ErrorType.CUSTOMER_ISSUE),
  TR_PLUGIN_USER_JOB_ERROR(40_125, ErrorType.CUSTOMER_ISSUE),
  TR_PLUGIN_USER_TEST_ERROR(40_126, ErrorType.CUSTOMER_ISSUE),
  TR_PLUGIN_UNKNOWN_JOB_ERROR(40_127, ErrorType.UNDETERMINED),
  TR_PLUGIN_UNKNOWN_TEST_ERROR(40_128, ErrorType.UNDETERMINED),
  TR_PLUGIN_INVALID_SKIP_EXCEPTION_ERROR(40_129, ErrorType.CUSTOMER_ISSUE),

  // Test Runner ERROR (2): 40_201 ~ 40_240.
  TR_TEST_INTERRUPTED_WHEN_PROCESS_SHUTDOWN(40_201, ErrorType.INFRA_ISSUE),
  TR_INVALID_TEST_MESSAGE_SUBSCRIBERS(40_202, ErrorType.CUSTOMER_ISSUE),
  TR_POST_RUN_GENERIC_ERROR(40_203, ErrorType.UNDETERMINED),
  TR_POST_RUN_INTERRUPTED_ERROR(40_204, ErrorType.UNDETERMINED),
  TR_POST_EVENT_ERROR(40_205, ErrorType.UNDETERMINED),

  // Test Runner DRAIN ERROR: 40_241 ~ 40_260
  TR_TEST_DRAIN_TIMEOUT_AND_FORCE_CLEAN_UP(40_241, ErrorType.CUSTOMER_ISSUE),

  // Test Manager ERROR: 40_301 ~ 40400
  TM_TEST_NOT_FOUND(40_301, ErrorType.INFRA_ISSUE),
  TM_TEST_IN_CONTAINER_MODE(40_302, ErrorType.INFRA_ISSUE),
  TM_TEST_NOT_KICKED_OFF(40_303, ErrorType.INFRA_ISSUE),
  TM_TEST_LAUNCHER_CONNECTED(40_304, ErrorType.INFRA_ISSUE),
  TM_TEST_RUNNER_STARTED(40_305, ErrorType.INFRA_ISSUE),
  TM_TEST_RUNNER_STARTED_TWICE(40_306, ErrorType.INFRA_ISSUE),
  DM_RESERVE_NON_READY_DEVICE(40_307, ErrorType.INFRA_ISSUE),
  DM_RESERVE_BUSY_DEVICE(40_308, ErrorType.INFRA_ISSUE),
  DM_DEVICE_DETECTOR_DETECTION_ERROR(40_309, ErrorType.DEPENDENCY_ISSUE),
  DM_RESERVE_NON_DUAL_STACK_READY_DEVICE(40_310, ErrorType.INFRA_ISSUE),
  DM_LOCAL_DEVICE_QUERIER_DEVICE_MANAGER_INIT_ERROR(40_311, ErrorType.INFRA_ISSUE),
  SCHEDULER_LOCAL_DEVICE_ALLOCATOR_SCHEDULER_INIT_ERROR(40_312, ErrorType.INFRA_ISSUE),
  SCHEDULER_LOCAL_DEVICE_ALLOCATOR_SCHEDULER_JOB_NOT_FOUND_ERROR(40_313, ErrorType.INFRA_ISSUE),
  DM_DEVICE_PROXY_CLASS_NOT_FOUND(40_314, ErrorType.UNDETERMINED),
  DM_DEVICE_PROXY_INSTANTIATION_ERROR(40_315, ErrorType.UNDETERMINED),
  TM_TEST_DUPLICATED_ALLOCATION(40_316, ErrorType.INFRA_ISSUE),

  // Lab server: 40_401 ~ 40_500
  LAB_RPC_PREPARE_TEST_INTERRUPTED(40_401, ErrorType.INFRA_ISSUE),
  LAB_RPC_EXEC_TEST_GET_TEST_GEN_DATA_INTERRUPTED(40_402, ErrorType.INFRA_ISSUE),
  LAB_RPC_DEVICE_OPS_TAKE_SCREENSHOT_INTERRUPTED(40_403, ErrorType.INFRA_ISSUE),
  LAB_RPC_EXEC_TEST_KICK_OFF_TEST_INTERRUPTED(40_404, ErrorType.INFRA_ISSUE),
  LAB_RPC_EXEC_TEST_KICK_OFF_TEST_DEVICE_NOT_FOUND(40_405, ErrorType.DEPENDENCY_ISSUE),
  LAB_RPC_PREPARE_TEST_JOB_TYPE_NOT_SUPPORTED(40_406, ErrorType.INFRA_ISSUE),
  LAB_RPC_PREPARE_TEST_DEVICE_NOT_FOUND(40_407, ErrorType.INFRA_ISSUE),
  LAB_JM_JOB_NOT_FOUND(40_408, ErrorType.INFRA_ISSUE),
  LAB_JM_TEST_NOT_FOUND(40_409, ErrorType.INFRA_ISSUE),
  LAB_RPC_PREPARE_TEST_TEST_RUNNER_START_ERROR(40_410, ErrorType.UNDETERMINED),
  LAB_RPC_EXEC_TEST_NULL_PREPARE_TEST_SERVICE(40_411, ErrorType.INFRA_ISSUE),
  LAB_RPC_DEVICE_OPS_TAKE_SCREENSHOT_DEVICE_NOT_FOUND(40_412, ErrorType.INFRA_ISSUE),
  LAB_RPC_GET_DEVICE_STAT_DEVICE_NOT_FOUND(40_413, ErrorType.INFRA_ISSUE),
  LAB_RPC_DEVICE_OPS_TAKE_SCREENSHOT_ERROR(40_414, ErrorType.UNDETERMINED),
  LAB_RPC_DEVICE_OPS_TAKE_SCREENSHOT_UNSUPPORTED_FORMAT(40_415, ErrorType.DEPENDENCY_ISSUE),
  LAB_RPC_DEVICE_OPS_TAKE_SCREENSHOT_CONVERTING_ERROR(40_416, ErrorType.INFRA_ISSUE),
  LAB_RPC_DEVICE_OPS_TAKE_SCREENSHOT_UNKNOWN_FORMAT(40_417, ErrorType.INFRA_ISSUE),
  LAB_FILE_PUBLISHER_ENCODING_ERROR(40_418, ErrorType.INFRA_ISSUE),
  LAB_FILE_PUBLISHER_INVALID_FILE_PATH(40_419, ErrorType.INFRA_ISSUE),
  LAB_FILE_PUBLISHER_CREATING_URI_ERROR(40_420, ErrorType.INFRA_ISSUE),
  LAB_NON_PASSING_TEST_RESULT_WITHOUT_CAUSE(40_421, ErrorType.UNDETERMINED),
  LAB_FILE_NOTIFIER_HANDLE_FILE_ERROR(40_422, ErrorType.UNDETERMINED),
  LAB_FILE_NOTIFIER_HANDLE_FILE_INTERRUPTED(40_423, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_ILLEGAL_CONTAINER_MODE_PREFERENCE(40_424, ErrorType.CUSTOMER_ISSUE),
  LAB_FILE_NOTIFIER_ADD_FILE_ERROR(40_425, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_ILLEGAL_SANDBOX_MODE_PREFERENCE(40_427, ErrorType.CUSTOMER_ISSUE),
  LAB_QEMU_NOT_INSTALLED(40_428, ErrorType.INFRA_ISSUE),
  LAB_KVM_NOT_SUPPORTED(40_429, ErrorType.INFRA_ISSUE),
  LAB_RPC_PREPARE_TEST_DEVICE_NOT_ALIVE(40_430, ErrorType.INFRA_ISSUE),
  LAB_RPC_EXEC_TEST_KICK_OFF_TEST_MNM_DEVICE_NOT_FOUND(40_431, ErrorType.INFRA_ISSUE),
  LAB_COMMAND_CLIENT_EXECUTE_ERROR(40_432, ErrorType.UNDETERMINED),
  LAB_RPC_DEVICE_OPS_GET_DEVICE_LOG_DEVICE_NOT_FOUND(40_433, ErrorType.INFRA_ISSUE),
  LAB_RPC_DEVICE_OPS_GET_DEVICE_LOG_ERROR(40_434, ErrorType.INFRA_ISSUE),
  LAB_RPC_DEVICE_OPS_GET_DEVICE_LOG_INTERRUPTED(40_435, ErrorType.INFRA_ISSUE),
  LAB_GET_DEVICE_LOG_METHOD_UNSUPPORTED(40_436, ErrorType.INFRA_ISSUE),
  LAB_GET_DEVICE_LOG_LOGCAT_ERROR(40_437, ErrorType.INFRA_ISSUE),
  LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_ERROR(40_438, ErrorType.DEPENDENCY_ISSUE),
  LAB_JM_CHECK_JOB_WITH_MASTER_ERROR(40_439, ErrorType.INFRA_ISSUE),
  LAB_RPC_EXEC_TEST_KICK_OFF_TEST_STUBBY_ERROR(40_440, ErrorType.UNDETERMINED),
  LAB_RPC_EXEC_TEST_GET_TEST_STATUS_STUBBY_ERROR(40_441, ErrorType.UNDETERMINED),
  LAB_RPC_EXEC_TEST_GET_TEST_DETAIL_STUBBY_ERROR(40_442, ErrorType.UNDETERMINED),
  LAB_RPC_EXEC_TEST_FORWARD_TEST_MESSAGE_STUBBY_ERROR(40_443, ErrorType.UNDETERMINED),
  LAB_RPC_EXEC_TEST_GET_TEST_GEN_DATA_STUBBY_ERROR(40_444, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_CREATE_TEST_STUBBY_ERROR(40_445, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_CLOSE_TEST_STUBBY_ERROR(40_446, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_GET_TEST_ENGINE_STATUS_STUBBY_ERROR(40_447, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_START_TEST_ENGINE_STUBBY_ERROR(40_448, ErrorType.UNDETERMINED),
  LAB_SYNC_SIGN_UP_ERROR(40_449, ErrorType.INFRA_ISSUE),
  LAB_SYNC_HEARTBEAT_ERROR(40_450, ErrorType.INFRA_ISSUE),
  LAB_JOB_SYNC_CHECK_JOB_ERROR(40_451, ErrorType.INFRA_ISSUE),
  LAB_UTRS_SERVER_START_ERROR(40_452, ErrorType.INFRA_ISSUE),
  LAB_INIT_ENV_PREPARE_DIR_ERROR(40_453, ErrorType.INFRA_ISSUE),
  LAB_RPC_EXEC_TEST_KICK_OFF_TEST_GRPC_ERROR(40_454, ErrorType.UNDETERMINED),
  LAB_RPC_EXEC_TEST_GET_TEST_STATUS_GRPC_ERROR(40_455, ErrorType.UNDETERMINED),
  LAB_RPC_EXEC_TEST_GET_TEST_DETAIL_GRPC_ERROR(40_456, ErrorType.UNDETERMINED),
  LAB_RPC_EXEC_TEST_FORWARD_TEST_MESSAGE_GRPC_ERROR(40_457, ErrorType.UNDETERMINED),
  LAB_RPC_EXEC_TEST_GET_TEST_GEN_DATA_GRPC_ERROR(40_458, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_CREATE_TEST_GRPC_ERROR(40_459, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_CLOST_TEST_GRPC_ERROR(40_460, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_GET_TEST_ENGINE_STATUS_GRPC_ERROR(40_461, ErrorType.UNDETERMINED),
  LAB_RPC_PREPARE_TEST_START_TEST_ENGINE_GRPC_ERROR(40_462, ErrorType.UNDETERMINED),
  LAB_JM_ADD_RESOLVE_FILE_FUTURE_TO_CLOSED_JOB_ERROR(40_463, ErrorType.INFRA_ISSUE),
  LAB_EXTERNAL_DEVICE_MANAGER_RESERVE_FAIL_WHEN_DRAIN(40_464, ErrorType.INFRA_ISSUE),
  LAB_RPC_GET_VERSION_STUBBY_ERROR(40_465, ErrorType.INFRA_ISSUE),
  LAB_RPC_GET_LAB_DETAIL_ERROR(40_466, ErrorType.INFRA_ISSUE),

  LAB_GET_CONFIG_FROM_CONFIG_SERVER_ERROR(40_467, ErrorType.INFRA_ISSUE),
  LAB_STORE_CONFIG_TO_CONFIG_SERVER_ERROR(40_468, ErrorType.INFRA_ISSUE),
  LAB_TAKE_SCREENSHOT_METHOD_UNSUPPORTED(40_469, ErrorType.INFRA_ISSUE),
  LAB_REBOOT_METHOD_UNSUPPORTED(40_470, ErrorType.INFRA_ISSUE),
  LAB_UPLOAD_FILE_TIMEOUT(40_471, ErrorType.DEPENDENCY_ISSUE),
  LAB_DOWNLOAD_FILE_TIMEOUT(40_472, ErrorType.DEPENDENCY_ISSUE),

  // Test engine/container: 40_701 ~ 40_800
  TE_CREATE_DEVICE_HELPER_CONTAINER_DOES_NOT_HAVE(40_701, ErrorType.INFRA_ISSUE),
  TE_TEST_NOT_KICKED_OFF_IN_TEST_ENGINE(40_702, ErrorType.INFRA_ISSUE),
  TE_ACCESS_TEST_NOT_IN_TEST_ENGINE(40_703, ErrorType.INFRA_ISSUE),
  TE_DENIED_MANDATORY_CONTAINER_PREFERENCE(40_704, ErrorType.UNDETERMINED),
  TE_TEST_ENGINE_FAILURE_WHEN_CLIENT_WAITING_TEST_ENGINE_READY(40_705, ErrorType.INFRA_ISSUE),
  TE_TEST_ENGINE_CLOSED_WHEN_CLIENT_WAITING_TEST_ENGINE_READY(40_706, ErrorType.INFRA_ISSUE),
  TE_CONTAINER_TEST_RUNNER_FINALIZED(40_707, ErrorType.UNDETERMINED),
  TE_CREATE_TEST_ENGINE_BINARY_SYMBOL_LINK_INTERRUPTED(40_708, ErrorType.INFRA_ISSUE),
  TE_TEST_ENGINE_EXIT_ABNORMALLY(40_709, ErrorType.UNDETERMINED),
  SANDBOX_CREATE_DELTA_IMAGE_ERROR(40_710, ErrorType.INFRA_ISSUE),
  SANDBOX_PREPARE_BOOTLOADER_DIR_ERROR(40_711, ErrorType.INFRA_ISSUE),
  SANDBOX_START_SANDBOX_ERROR(40_712, ErrorType.INFRA_ISSUE),
  SANDBOX_CLOUD_RPC_DISABLED(40_713, ErrorType.INFRA_ISSUE),
  SANDBOX_ORIGINAL_IMAGE_NOT_SPECIFIED(40_714, ErrorType.INFRA_ISSUE),
  SANDBOX_DEVICE_TYPE_NOT_SUPPORTED(40_715, ErrorType.CUSTOMER_ISSUE),
  SANDBOX_HOST_WITHOUT_UNIQUE_IP(40_716, ErrorType.INFRA_ISSUE),
  TE_DENIED_MANDATORY_SANDBOX_PREFERENCE(40_717, ErrorType.UNDETERMINED),
  SANDBOX_DECORATOR_TYPE_NOT_SUPPORTED(40_718, ErrorType.CUSTOMER_ISSUE),
  DEVICE_TYPE_ILLEGALLY_SPECIFIED(40_719, ErrorType.INFRA_ISSUE),
  CONTAINER_DEVICE_TYPE_NOT_SUPPORTED(40_720, ErrorType.CUSTOMER_ISSUE),
  CONTAINER_MULTIPLE_DEVICES_NOT_SUPPORTED(40_721, ErrorType.INFRA_ISSUE),
  SANDBOX_MULTIPLE_DEVICES_NOT_SUPPORTED(40_722, ErrorType.INFRA_ISSUE),
  LAB_SPECIFIED_INVALID_ARGS_FOR_SANDBOX(40_723, ErrorType.INFRA_ISSUE),
  SANDBOX_ONLY_SUPPORTED_ON_LINUX(40_724, ErrorType.INFRA_ISSUE),
  CONTAINER_DECORATOR_TYPE_NOT_SUPPORTED(40_725, ErrorType.INFRA_ISSUE),
  SANDBOX_ONLINE_DEVICE_LISTER_NOT_FOUND(40_726, ErrorType.INFRA_ISSUE),
  CONTAINER_DEVICE_INFO_RPC_PUT_PROPERTY_ERROR(40_727, ErrorType.INFRA_ISSUE),
  CONTAINER_DEVICE_INFO_RPC_REPLACE_DIMENSION_ERROR(40_728, ErrorType.INFRA_ISSUE),
  CONTAINER_DEVICE_INFO_RPC_GET_DEVICE_INFO_ERROR(40_729, ErrorType.INFRA_ISSUE),

  // ID space here for Master:49_250 ~ 49_280
  MASTER_RPC_STUB_JOB_SYNC_OPEN_JOB_ERROR(49_251, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_JOB_SYNC_ADD_TEST_ERROR(49_252, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_JOB_SYNC_GET_ALLOC_ERROR(49_253, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_JOB_SYNC_CLOSE_JOB_ERROR(49_254, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_JOB_SYNC_CHECK_JOB_ERROR(49_255, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_JOB_SYNC_UPSERT_TEMP_REQUIRED_DIMENSIONS_ERROR(49_256, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_JOB_SYNC_KILL_JOB_ERROR(49_257, ErrorType.INFRA_ISSUE),

  MASTER_RPC_STUB_LAB_SYNC_SIGN_UP_LAB_ERROR(49_258, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_LAB_SYNC_HEARTBEAT_LAB_ERROR(49_259, ErrorType.INFRA_ISSUE),

  MASTER_RPC_STUB_LAB_INFO_GET_LAB_INFO_ERROR(49_260, ErrorType.INFRA_ISSUE),

  MASTER_RPC_STUB_LAB_INFO_GET_LAB_METADATAS_ERROR(49_261, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_LAB_INFO_GET_LAB_OVERVIEWS_ERROR(49_262, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_LAB_INFO_GET_LAB_SUMMARIES_ERROR(49_263, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_LAB_INFO_GET_LAB_DETAIL_ERROR(49_264, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_LAB_INFO_GET_DEVICE_DETAIL_ERROR(49_265, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_LAB_INFO_GET_DEVICE_DIMENSIONS_ERROR(49_266, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_LAB_INFO_GET_COMPRESSED_DEVICE_METADATAS_ERROR(49_267, ErrorType.INFRA_ISSUE),

  MASTER_RPC_STUB_JOB_INFO_GET_JOB_SUMMARY_LIST_ERROR(49_271, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_JOB_INFO_GET_JOB_DETAIL_ERROR(49_272, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_JOB_INFO_GET_TEST_DETAIL_ERROR(49_273, ErrorType.INFRA_ISSUE),

  MASTER_RPC_STUB_DEBUG_RECYCLE_EXPIRED_LAB_ERROR(49_281, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_DEBUG_GET_CONFIG_ERROR(49_282, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_MASTER_VERSION_ERROR(49_283, ErrorType.INFRA_ISSUE),

  MASTER_RPC_STUB_DEVICE_ALLOCATE_ERROR(49_284, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_DEVICE_DEALLOCATE_ERROR(49_285, ErrorType.INFRA_ISSUE),
  MASTER_RPC_STUB_DEVICE_ALLOCATE_GET_DEVICES_ERROR(49_286, ErrorType.INFRA_ISSUE),

  MASTER_RPC_STUB_DEVICE_INFO_GET_DEVICE_SUMMARIES_ERROR(49_287, ErrorType.INFRA_ISSUE),

  MASTER_RPC_REMOVE_MISSING_HOST_INTERRUPTED(49_288, ErrorType.INFRA_ISSUE),
  MASTER_RPC_REMOVE_MISSING_HOST_FAILED(49_289, ErrorType.INFRA_ISSUE),

  // Client: 49_301 ~ 49_500
  CLIENT_JR_JOB_EXEC_INTERRUPTED(49_301, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_JOB_EXEC_FATAL_ERROR(49_302, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_TEAR_DOWN_ALLOCATOR_INTERRUPTED(49_303, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_JOB_TEAR_DOWN_ALLOCATOR_FATAL_ERROR(49_304, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_SHUT_DOWN_THRAD_POOL_INTERRUPTED(49_305, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_JOB_SHUT_DOWN_THREAD_POOL_FATAL_ERROR(49_306, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_FINALIZE_RESULT_FATAL_ERROR(49_307, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_END_EVENT_POST_FATAL_ERROR(49_308, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_TEAR_DOWN_FATAL_ERROR(49_309, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_CLEAN_UP_DIR_ERROR(49_310, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_EXPIRED(49_312, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_JOB_START_WITHOUT_TEST(49_313, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_JOB_END_WITHOUT_TEST(49_314, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_START_DUPLICATED_ID(49_315, ErrorType.CUSTOMER_ISSUE),

  CLIENT_API_START_JOB_ERROR(49_321, ErrorType.UNDETERMINED),

  CLIENT_JR_JOB_HAS_INFRA_ERROR_TEST(49_341, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_HAS_ERROR_TEST(49_342, ErrorType.UNDETERMINED),
  CLIENT_JR_JOB_HAS_FAIL_TEST(49_343, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_JOB_HAS_ALLOC_ERROR_TEST(49_344, ErrorType.INFRA_ISSUE),
  CLIENT_JR_JOB_HAS_ALLOC_FAIL_TEST(49_345, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_JOB_HAS_ALL_SKIPPED_TESTS(49_346, ErrorType.CUSTOMER_ISSUE),

  CLIENT_JR_TEST_START_ERROR(49_351, ErrorType.INFRA_ISSUE),
  CLIENT_JR_TEST_HAS_UNKNOWN_RESULT(49_352, ErrorType.INFRA_ISSUE),
  CLIENT_JR_TEST_HAS_UNKNOWN_STATUS(49_353, ErrorType.INFRA_ISSUE),
  CLIENT_JR_TEST_HAS_JOB_LEVEL_ERROR(49_354, ErrorType.UNDETERMINED),

  CLIENT_JR_MPM_FETCH_TIMEOUT_PARAM_ILLEGAL(49_371, ErrorType.CUSTOMER_ISSUE),

  CLIENT_GA_JOB_ERROR_NO_NAME(49_381, ErrorType.INFRA_ISSUE),
  CLIENT_GA_JOB_ERROR_NO_ID(49_382, ErrorType.INFRA_ISSUE),
  CLIENT_GA_JOB_ERROR_UPLOAD(49_383, ErrorType.INFRA_ISSUE),
  CLIENT_GA_TEST_ERROR_NO_JOB_NAME(49_384, ErrorType.INFRA_ISSUE),
  CLIENT_GA_TEST_ERROR_NO_TEST_ID(49_385, ErrorType.INFRA_ISSUE),
  CLIENT_GA_TEST_ERROR_UPLOAD(49_386, ErrorType.INFRA_ISSUE),
  CLIENT_GA_HTTP_CONNECTON_ERROR(49_387, ErrorType.INFRA_ISSUE),
  CLIENT_GA_HTTP_IO_ERROR(49_388, ErrorType.INFRA_ISSUE),

  // Shared Lab Only
  CLIENT_JR_MNM_ALLOC_INFRA_ERROR(49_402, ErrorType.INFRA_ISSUE),
  CLIENT_JR_MNM_ALLOC_DEVICE_NOT_AVAILABLE(49_403, ErrorType.INFRA_ISSUE),
  CLIENT_JR_MNM_ALLOC_DEVICE_EXCEEDS_CEILING(49_404, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_MNM_ALLOC_DEVICE_NOT_SATISFY_SLO(49_405, ErrorType.INFRA_ISSUE),

  // Satellite Lab
  CLIENT_JR_ALLOC_UNKNOWN_ERROR(49_461, ErrorType.UNDETERMINED),
  CLIENT_JR_ALLOC_INFRA_ERROR(49_462, ErrorType.INFRA_ISSUE),
  CLIENT_JR_ALLOC_USER_CONFIG_ERROR(49_463, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_ALLOC_DIAGNOSTIC_ERROR(49_464, ErrorType.INFRA_ISSUE),
  CLIENT_JR_ALLOC_DEVICE_NOT_FOUND(49_465, ErrorType.CUSTOMER_ISSUE),
  // 49_466 ~ 49_469 is the fine-grained classification of 49_463
  CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_NOT_EXIST(49_466, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_NO_ACCESS(49_467, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_BUSY(49_468, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_ALLOC_USER_CONFIG_ERROR_DEVICE_MISSING(49_469, ErrorType.CUSTOMER_ISSUE),
  // temporary usage for b/339119385
  CLIENT_JR_ALLOC_DIAGNOSTIC_ERROR_DUE_TO_MEMORY_LIMIT(49_470, ErrorType.CUSTOMER_ISSUE),
  CLIENT_JR_ALLOC_RESULT_TEST_NOT_IN_JOB(49_471, ErrorType.INFRA_ISSUE),
  CLIENT_JR_ALLOC_RESULT_TEST_NOT_FOUND(49_472, ErrorType.INFRA_ISSUE),
  CLIENT_JR_ALLOC_RESULT_TEST_ALREADY_ALLOCATED(49_473, ErrorType.INFRA_ISSUE),

  // Client LocalLite Mode: 49_501 ~ 49_530
  CLIENT_LOCAL_LITE_MODE_QUERIER_NOT_IMPLEMENTED(49_501, ErrorType.INFRA_ISSUE),
  CLIENT_LOCAL_LITE_MODE_RESOLVE_DEVICE_TYPE_ERROR(49_502, ErrorType.CUSTOMER_ISSUE),
  CLIENT_LOCAL_LITE_MODE_DETECTOR_PRECONDITOIN_FAIL(49_503, ErrorType.CUSTOMER_ISSUE),
  CLIENT_LOCAL_LITE_MODE_DEVICE_ALLOCATION_FAIL(49_504, ErrorType.CUSTOMER_ISSUE),

  // Client Local Mode / ATS Mode: 49_531 ~ 49_600
  CLIENT_ATS_MODE_START_GRPC_SERVER_ERROR(49_531, ErrorType.UNDETERMINED),
  CLIENT_LOCAL_MODE_ALLOCATED_DEVICE_NOT_FOUND(49_532, ErrorType.UNDETERMINED),
  CLIENT_LOCAL_MODE_TEST_NOT_FOUND(49_533, ErrorType.INFRA_ISSUE),
  CLIENT_LOCAL_MODE_TEST_NOT_NEW(49_534, ErrorType.INFRA_ISSUE),
  CLIENT_LOCAL_MODE_DEVICE_NOT_READY(49_535, ErrorType.INFRA_ISSUE),
  CLIENT_LOCAL_MODE_JOB_ALREADY_EXIST(49_536, ErrorType.INFRA_ISSUE),
  CLIENT_LOCAL_MODE_TEST_ALREADY_EXIST(49_537, ErrorType.INFRA_ISSUE),

  // Client Remote Mode: 49_601 ~ 49_700
  CLIENT_REMOTE_MODE_TEST_ADD_ERROR(49_601, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_GET_STATUS_ERROR(49_602, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_GET_GEN_DATA_ERROR(49_603, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_DOWNLOAD_FILE_ERROR(49_604, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_CONSECUTIVE_GET_STATUS_ERROR(49_605, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_CREATE_ERROR(49_606, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_ENGINE_NOT_READY(49_607, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_NOT_FOUND(49_608, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_MESSAGE_FORWARD_ERROR(49_609, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_SEND_FILE_ERROR(49_610, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_TEST_CREATE_FILE_TRANSFER_CLIENT_ERROR(49_611, ErrorType.INFRA_ISSUE),

  CLIENT_REMOTE_MODE_ALLOC_POLL_ERROR(49_621, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_ALLOC_WITH_UNKNOWN_TEST(49_622, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_ALLOC_WITHOUT_DEVICE(49_623, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_ALLOC_WITHOUT_LAB_PORT(49_624, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_ALLOC_NO_LOAS(49_425, ErrorType.CUSTOMER_ISSUE),
  CLIENT_REMOTE_MODE_ALLOC_OLD_MASTER_ERROR(49_426, ErrorType.CUSTOMER_ISSUE),
  CLIENT_REMOTE_MODE_ALLOC_UNKNOWN_MNM_ERROR(49_427, ErrorType.UNDETERMINED), // b/112320443,
  CLIENT_REMOTE_MODE_ALLOC_MNM_WHITELIST_CONFIG_ERROR(49_428, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_ALLOC_MNM_INCOMPATIBLE_ERROR(49_429, ErrorType.CUSTOMER_ISSUE),

  CLIENT_REMOTE_MODE_LEGACY_FORGE_JOB(49_631, ErrorType.CUSTOMER_ISSUE),
  CLIENT_REMOTE_MODE_NO_FORGE(49_632, ErrorType.CUSTOMER_ISSUE),
  CLIENT_REMOTE_MODE_UPSERT_TEMP_REQUIRED_DIMENSION_ERROR(49_633, ErrorType.CUSTOMER_ISSUE),
  CLIENT_REMOTE_MODE_GET_LAB_VERSION_ERROR(49_634, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_UPDATE_DEVICE_FEATURE_ERROR(49_635, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_USER_CONFIG_CLOUDRPC_ERROR(49_636, ErrorType.CUSTOMER_ISSUE),

  CLIENT_REMOTE_MODE_JOB_CLOSE_ERROR(49_640, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_JOB_OPEN_ERROR(49_641, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_JOB_SEND_FILE_ERROR(49_642, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_JOB_CREATE_FILE_TRANSFER_CLIENT_ERROR(49_643, ErrorType.INFRA_ISSUE),
  CLIENT_REMOTE_MODE_JOB_SEND_FILE_ERROR_SATELLITE_LAB_TIMEOUT(49_644, ErrorType.CUSTOMER_ISSUE),
  CLIENT_REMOTE_MODE_JOB_SEND_FILE_ERROR_CORE_LAB_TIMEOUT(49_645, ErrorType.INFRA_ISSUE),

  // Client longevity tests: 49_900 ~ 49_910
  CLIENT_LONGEVITY_JOB_INFO_PERSISTENT_FILE_EXIST(49_900, ErrorType.CUSTOMER_ISSUE),
  CLIENT_LONGEVITY_JOB_INFO_PERSISTENT_ERROR(49_901, ErrorType.INFRA_ISSUE),
  CLIENT_LONGEVITY_TEST_ENGINE_LOCATOR_RECOVER_ERROR(49_902, ErrorType.INFRA_ISSUE),
  CLIENT_LONGEVITY_JOB_INFO_RECOVER_ERROR(49_903, ErrorType.INFRA_ISSUE),
  CLIENT_LONGEVITY_STORAGE_BACKEND_NOT_FOUND(49_904, ErrorType.INFRA_ISSUE),

  // Monitoring: 49_911 ~ 49_930
  CLOUD_PUB_SUB_PUBLISHER_CREATE_PUBLISHER_ERROR(49_911, ErrorType.INFRA_ISSUE),
  CLOUD_PUB_SUB_PUBLISHER_GET_CREDENTIAL_ERROR(49_912, ErrorType.INFRA_ISSUE),
  CLOUD_PUB_SUB_PUBLISHER_GET_TOPIC_NAME_ERROR(49_913, ErrorType.INFRA_ISSUE),
  MONITORING_PULL_DATA_ERROR(49_914, ErrorType.INFRA_ISSUE),
  MONITORING_STUB_NOT_AVAILABLE(49_915, ErrorType.INFRA_ISSUE),
  MONITORING_STUB_NOT_CLOSED(49_916, ErrorType.INFRA_ISSUE),
  FAIL_TO_GET_SERVICE_ACCOUNT_CREDENTIAL_FROM_FILE(49_917, ErrorType.INFRA_ISSUE),
  FAIL_TO_PUBLISH_MESSAGE_TO_CLOUD_PUB_SUB(49_918, ErrorType.INFRA_ISSUE),

  // Device Querier: 49_931 ~ 49_950
  CLIENT_MASTER_DEVICE_QUERIER_GET_LAB_OVERVIEW_LIST_ERROR(49_931, ErrorType.INFRA_ISSUE),

  // Client GenFileHandler: 49_951 ~ 49_960
  CLIENT_GEN_FILE_HANDLER_GET_TEST_GEN_FILE_DIR_ERROR(49_951, ErrorType.INFRA_ISSUE),
  CLIENT_GEN_FILE_HANDLER_GET_JOB_GEN_FILE_DIR_ERROR(49_952, ErrorType.INFRA_ISSUE),
  CLIENT_GEN_FILE_HANDLER_WRITE_JOB_OUTPUT_ERROR(49_953, ErrorType.INFRA_ISSUE),
  CLIENT_GEN_FILE_HANDLER_WRITE_TEST_OUTPUT_ERROR(49_954, ErrorType.INFRA_ISSUE),

  // FileTransfer Error 50_601 ~ 50_800
  FT_INVALID_PROTOCOL(50_601, ErrorType.INFRA_ISSUE),
  FT_FILE_UPLOAD_ERROR(50_602, ErrorType.INFRA_ISSUE),
  FT_FILE_NOT_EXIST(50_603, ErrorType.INFRA_ISSUE),
  FT_FILE_DOWNLOAD_ERROR(50_604, ErrorType.INFRA_ISSUE),
  FT_FILE_PATH_ERROR(50_605, ErrorType.INFRA_ISSUE),
  FT_METADATA_CLASS_MISMATCH(50_606, ErrorType.INFRA_ISSUE),
  FT_METADATA_CLASS_TYPE_ERROR(50_607, ErrorType.INFRA_ISSUE),
  FT_CLOUD_PROCESS_INTERRUPTED(50_608, ErrorType.INFRA_ISSUE),
  FT_RPC_DOWNLOAD_GCS_FILE_INTERRUPTED(50_609, ErrorType.INFRA_ISSUE),
  FT_RPC_START_DOWNLOADING_GCS_FILE_INTERRUPTED(50_610, ErrorType.INFRA_ISSUE),
  FT_RPC_SAVE_FILE_INTERRUPTED(50_611, ErrorType.INFRA_ISSUE),
  FT_RPC_GET_FILE_INTERRUPTED(50_612, ErrorType.INFRA_ISSUE),
  FT_RPC_UPLOAD_FILE_INTERRUPTED(50_613, ErrorType.INFRA_ISSUE),
  FT_RPC_START_UPLOADING_FILE_INTERRUPTED(50_614, ErrorType.INFRA_ISSUE),
  FT_RPC_MERGE_SHARDS_INTERRUPTED(50_615, ErrorType.INFRA_ISSUE),
  FT_RPC_REUSE_CACHE_INTERRUPTED(50_616, ErrorType.INFRA_ISSUE),
  FT_RPC_DELEGATE_CLOUD_FILE_TRANSFER_ERROR(50_617, ErrorType.INFRA_ISSUE),
  FT_LOCAL_FILE_NOT_WRITABLE(50_618, ErrorType.INFRA_ISSUE),
  FT_CHECK_FILE_MD5_ERROR(50_619, ErrorType.INFRA_ISSUE),
  FT_WRITE_FILE_ERROR(50_620, ErrorType.INFRA_ISSUE),

  FT_RPC_STUB_DOWNLOAD_GCS_FILE_ERROR(50_621, ErrorType.INFRA_ISSUE),
  FT_RPC_STUB_UPLOAD_GCS_FILE_ERROR(50_622, ErrorType.INFRA_ISSUE),
  FT_RPC_STUB_LIST_FILE_ERROR(50_623, ErrorType.INFRA_ISSUE),
  FT_RPC_STUB_START_DOWNLOAD_GCS_FILE_ERROR(50_624, ErrorType.INFRA_ISSUE),
  FT_RPC_STUB_START_UPLOADING_GCS_FILE_ERROR(50_625, ErrorType.INFRA_ISSUE),
  FT_RPC_STUB_GET_PROCESS_STATUS_ERROR(50_626, ErrorType.INFRA_ISSUE),
  FT_RPC_STUB_SAVE_FILE_ERROR(50_627, ErrorType.INFRA_ISSUE),
  FT_RPC_STUB_GET_FILE_ERROR(50_628, ErrorType.INFRA_ISSUE),
  FT_RPC_STUB_MERGE_SHARDS_ERROR(50_629, ErrorType.INFRA_ISSUE),
  FT_RPC_STUB_REUSE_CACHE_ERROR(50_630, ErrorType.INFRA_ISSUE),

  FT_REUSE_CACHE_IN_SERVER_ERROR(50_631, ErrorType.INFRA_ISSUE),
  FT_LIST_FILE_ERROR(50_632, ErrorType.INFRA_ISSUE),
  FT_MERGE_FILE_ERROR(50_633, ErrorType.INFRA_ISSUE),
  FT_SPLIT_FILE_ERROR(50_634, ErrorType.INFRA_ISSUE),
  FT_SEND_FILE_ERROR(50_635, ErrorType.INFRA_ISSUE),
  FT_SEND_SHARD_ERROR(50_636, ErrorType.INFRA_ISSUE),
  FT_SEND_SHARD_ATTEMPT_ERROR(50_637, ErrorType.INFRA_ISSUE),

  FT_SEND_SHARD_IO_ERROR(50_641, ErrorType.INFRA_ISSUE),
  FT_SEND_SHARD_ERROR_WITH_RETRY(50_642, ErrorType.INFRA_ISSUE),
  FT_SEND_SHARD_RPC_STREAM_ERROR(50_643, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_SHARD_ERROR_WITH_RETRY(50_644, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_SHARD_IO_ERROR(50_645, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_SHARD_READ_WRITE_ERROR(50_646, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_SHARD_RPC_RESPONSE_ERROR(50_647, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_SHARD_CLOSE_STREAM_ERROR(50_648, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_SHARD_ERROR_SIZE_MISMATCH(50_649, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_SHARD_RPC_STREAM_ERROR(50_650, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_FILE_VALIDATION_ERROR(50_651, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_FILE_MD5_MISMATCH(50_652, ErrorType.INFRA_ISSUE),
  FT_RECEIVE_FILE_CHECK_SUM_ERROR(50_653, ErrorType.INFRA_ISSUE),
  FT_RECEIVED_FILE_NOT_FOUND(50_654, ErrorType.INFRA_ISSUE),
  FT_RELEASE_CONFIG_ERROR(50_655, ErrorType.INFRA_ISSUE),
  FT_GENERAL_ERROR(50_656, ErrorType.INFRA_ISSUE),

  FT_SOCKET_TRANSFER_ERROR(50_661, ErrorType.INFRA_ISSUE),
  FT_SOCKET_TRANSFER_TIMEOUT(53_662, ErrorType.INFRA_ISSUE),
  FT_SOCKET_INTERRUPTED(53_663, ErrorType.INFRA_ISSUE),

  FT_STREAM_UNREACHABLE(53_671, ErrorType.INFRA_ISSUE),
  FT_STREAM_BROKEN(53_672, ErrorType.INFRA_ISSUE),
  FT_STREAM_RPC_ERROR(53_673, ErrorType.INFRA_ISSUE),
  FT_STREAM_STUBBY_ERROR(53_674, ErrorType.INFRA_ISSUE),

  // OLC Server: 52_201 ~ 52_300
  OLCS_STUB_GET_SERVER_VERSION_ERROR(52_201, ErrorType.INFRA_ISSUE),
  OLCS_STUB_CREATE_SESSION_ERROR(52_202, ErrorType.UNDETERMINED),
  OLCS_STUB_GET_SESSION_ERROR(52_203, ErrorType.UNDETERMINED),
  OLCS_CREATE_SESSION_ERROR_SESSION_QUEUE_FULL(52_204, ErrorType.UNDETERMINED),
  OLCS_GET_SESSION_SESSION_NOT_FOUND(52_205, ErrorType.UNDETERMINED),
  OLCS_BUILTIN_SESSION_PLUGIN_NOT_FOUND(52_206, ErrorType.CUSTOMER_ISSUE),
  OLCS_LOAD_BUILTIN_SESSION_PLUGIN_CLASS_ERROR(52_207, ErrorType.UNDETERMINED),
  OLCS_CREATE_SESSION_PLUGIN_ERROR(52_208, ErrorType.UNDETERMINED),
  OLCS_DUPLICATED_SESSION_PLUGIN_LABEL(52_209, ErrorType.CUSTOMER_ISSUE),
  OLCS_STUB_KILL_SERVER_ERROR(52_210, ErrorType.INFRA_ISSUE),
  OLCS_STUB_RUN_SESSION_ERROR(52_211, ErrorType.UNDETERMINED),
  OLCS_STUB_GET_ALL_SESSIONS_ERROR(52_212, ErrorType.UNDETERMINED),
  OLCS_STUB_SET_LOG_LEVEL_ERROR(52_213, ErrorType.UNDETERMINED),
  OLCS_ABORT_SESSIONS_SESSION_NOT_FOUND(52_214, ErrorType.UNDETERMINED),
  OLCS_STUB_ABORT_SESSIONS_ERROR(52_215, ErrorType.UNDETERMINED),
  OLCS_SUBSCRIBE_SESSION_SESSION_NOT_FOUND(52_216, ErrorType.UNDETERMINED),
  OLCS_STUB_NOTIFY_SESSION_ERROR(52_217, ErrorType.UNDETERMINED),
  OLCS_STUB_HEARTBEAT_ERROR(52_218, ErrorType.UNDETERMINED),
  OLCS_SESSION_ABORTED_WHEN_QUEUEING(52_219, ErrorType.UNDETERMINED),
  OLCS_NO_AVAILABLE_DEVICE(52_220, ErrorType.UNDETERMINED),
  OLCS_NO_ENOUGH_MATCHED_DEVICES(52_221, ErrorType.UNDETERMINED),
  OLCS_NO_FILTER_FOUND_IN_RETRY_SUBPLAN(52_222, ErrorType.CUSTOMER_ISSUE),
  OLCS_NO_CORRESPONDING_FILTER_FOUND_IN_SUBPLAN(52_223, ErrorType.CUSTOMER_ISSUE),
  OLCS_INEXISTENT_XTS_ROOT_DIR(52_224, ErrorType.UNDETERMINED),
  OLCS_SESSION_DATABASE_ERROR(52_225, ErrorType.INFRA_ISSUE),

  // ATS console: 52_301 ~ 52_400
  ATSC_SERVER_PREPARER_CONNECT_EXISTING_OLC_SERVER_ERROR(52_301, ErrorType.UNDETERMINED),
  ATSC_SERVER_PREPARER_CONNECT_NEW_OLC_SERVER_ERROR(52_302, ErrorType.UNDETERMINED),
  ATSC_SERVER_PREPARER_START_OLC_SERVER_ERROR(52_303, ErrorType.UNDETERMINED),
  ATSC_SESSION_STUB_GET_SESSION_STATUS_ERROR(52_304, ErrorType.UNDETERMINED),
  ATSC_SESSION_STUB_GET_SESSION_RESULT_ERROR(52_305, ErrorType.UNDETERMINED),
  ATSC_SESSION_STUB_UNPACK_SESSION_PLUGIN_OUTPUT_ERROR(52_306, ErrorType.INFRA_ISSUE),
  ATSC_SESSION_STUB_SESSION_RUNNER_ERROR(52_307, ErrorType.UNDETERMINED),
  ATSC_SESSION_STUB_ATS_SESSION_PLUGIN_ERROR(52_308, ErrorType.UNDETERMINED),
  ATSC_SESSION_STUB_ATS_SESSION_PLUGIN_NO_OUTPUT_ERROR(52_309, ErrorType.INFRA_ISSUE),
  ATSC_SESSION_STUB_OTHER_SESSION_PLUGIN_ERROR(52_310, ErrorType.UNDETERMINED),
  ATSC_SERVER_PREPARER_OLC_SERVER_INITIALIZE_ERROR(52_311, ErrorType.UNDETERMINED),
  ATSC_SERVER_PREPARER_OLC_SERVER_ABNORMAL_EXIT_WHILE_INITIALIZATION(52_312, ErrorType.INFRA_ISSUE),
  ATSC_LIST_DEVICES_COMMAND_EXECUTION_ERROR(52_313, ErrorType.UNDETERMINED),
  ATSC_LIST_DEVICES_QUERY_DEVICE_ERROR(52_314, ErrorType.INFRA_ISSUE),
  ATSC_SESSION_STUB_CREATE_SESSION_ERROR(52_315, ErrorType.UNDETERMINED),
  ATSC_SESSION_STUB_RUN_SESSION_ERROR(52_316, ErrorType.UNDETERMINED),
  ATSC_SESSION_STUB_GET_ALL_SESSIONS_ERROR(52_317, ErrorType.INFRA_ISSUE),
  ATSC_RUN_COMMAND_QUERY_DEVICE_ERROR(52_318, ErrorType.INFRA_ISSUE),
  ATSC_SERVER_PREPARER_KILL_EXISTING_OLC_SERVER_RPC_ERROR(52_319, ErrorType.UNDETERMINED),
  ATSC_SERVER_PREPARER_EXISTING_OLC_SERVER_STILL_RUNNING_ERROR(52_320, ErrorType.INFRA_ISSUE),
  ATSC_SERVER_PREPARER_CANNOT_KILL_EXISTING_OLC_SERVER_ERROR(52_321, ErrorType.UNDETERMINED),
  ATSC_RUN_RETRY_COMMAND_PREPARE_SUBPLAN_ERROR(52_322, ErrorType.INFRA_ISSUE),
  ATSC_RUN_COMMAND_MULTIPLE_MODULES_FOUND_ERROR(52_323, ErrorType.CUSTOMER_ISSUE),
  ATSC_RUN_RETRY_COMMAND_MISSING_SESSION_INDEX_ERROR(52_324, ErrorType.INFRA_ISSUE),
  ATSC_XTS_TEST_PLAN_LOADER_JARFILE_CREATION_ERROR(52_325, ErrorType.INFRA_ISSUE),
  ATSC_XTS_TEST_PLAN_LOADER_XML_PARSE_ERROR(52_326, ErrorType.INFRA_ISSUE),
  ATSC_XTS_TEST_PLAN_LOADER_TEST_PLAN_NOT_FOUND(52_327, ErrorType.CUSTOMER_ISSUE),
  ATSC_RUN_SUBPLAN_COMMAND_SUBPLAN_XML_NOT_FOUND(52_328, ErrorType.INFRA_ISSUE),
  ATSC_RUN_SUBPLAN_COMMAND_PARSE_SUBPLAN_XML_ERROR(52_329, ErrorType.INFRA_ISSUE),
  ATSC_RUN_SUBPLAN_COMMAND_WRITE_SUBPLAN_XML_ERROR(52_330, ErrorType.INFRA_ISSUE),
  ATSC_XTS_TEST_PLAN_LOADER_JARFILE_CLOSE_ERROR(52_331, ErrorType.INFRA_ISSUE),
  ATSC_SUBPLAN_INVALID_FILTER_ERROR(52_332, ErrorType.CUSTOMER_ISSUE),
  ATSC_CMDFILE_PARSE_ERROR(52_333, ErrorType.CUSTOMER_ISSUE),
  ATSC_CMDFILE_READ_ERROR(52_334, ErrorType.INFRA_ISSUE),
  ATSC_RUN_RETRY_COMMAND_PREV_SESSION_MISS_RESULT_FILES(52_335, ErrorType.CUSTOMER_ISSUE),
  ATSC_SESSION_STUB_ABORT_SESSION_ERROR(52_336, ErrorType.UNDETERMINED),
  ATSC_RUN_RETRY_COMMAND_TEST_REPORT_PROPERTIES_FILE_READ_ERROR(52_337, ErrorType.INFRA_ISSUE),
  ATSC_SESSION_STUB_CANCEL_UNFINISHED_SESSIONS_ERROR(52_338, ErrorType.INFRA_ISSUE),
  ATSC_LOAD_MOBLY_TEST_NAMES_ERROR(52_339, ErrorType.INFRA_ISSUE),
  ATSC_TF_RETRY_WITHOUT_TF_MODULE(52_340, ErrorType.CUSTOMER_ISSUE),
  ATSC_INVALID_MODULE_ARG(52_341, ErrorType.CUSTOMER_ISSUE),

  // XTS test suite: 52_401 ~ 52_500
  XTS_CONFIG_XML_PARSE_ERROR(52_401, ErrorType.UNDETERMINED),
  XTS_DEVICE_CONFIG_FILE_PARSE_ERROR(52_402, ErrorType.UNDETERMINED),
  XTS_DEVICE_CONFIG_FILE_VALIDATE_ERROR(52_403, ErrorType.CUSTOMER_ISSUE),
  XTS_NO_MATCHED_TRADEFED_MODULES(52_404, ErrorType.CUSTOMER_ISSUE),
  XTS_NO_MATCHED_NON_TRADEFED_MODULES(52_405, ErrorType.CUSTOMER_ISSUE),
  XTS_NO_DEVICE_SPEC_DEFINED(52_406, ErrorType.CUSTOMER_ISSUE),
  XTS_ILLEGAL_DEVICE_SPEC(52_407, ErrorType.CUSTOMER_ISSUE),
  XTS_NO_MATCHED_NON_TF_MODULES_TO_RETRY(52_408, ErrorType.CUSTOMER_ISSUE),
  XTS_EMPTY_RUN_COMMAND_ARGS(52_409, ErrorType.CUSTOMER_ISSUE),
  XTS_NO_JOB_CREATED_FOR_SESSION(52_410, ErrorType.CUSTOMER_ISSUE),

  // RBE Mode: 52_501 ~ 52_600
  RBE_EXECUTOR_CREATE_TEST_RUNNER_ERROR(50_501, ErrorType.INFRA_ISSUE),
  RBE_EXECUTOR_CREATE_TEST_INFO_ERROR(50_502, ErrorType.INFRA_ISSUE),
  RBE_EXECUTOR_DEVICES_SETUP_TIMEOUT(50_503, ErrorType.INFRA_ISSUE),
  RBE_CLIENT_START_REPROXY_ERROR(50_504, ErrorType.INFRA_ISSUE),

  // Config Pusher 52_601 ~ 52_700
  CONFIG_PUSHER_CL_NUMBER_NOT_MATCH(52_601, ErrorType.INFRA_ISSUE),
  CONFIG_PUSHER_YAML_PARSE_ERROR(52_602, ErrorType.UNDETERMINED),

  // ATS Server: 52_701 ~ 52_800
  ATS_SERVER_INVALID_REQUEST_ERROR(52_701, ErrorType.CUSTOMER_ISSUE),
  ATS_SERVER_FAILED_TO_GENERATE_XML_TEST_CONFIG(52_702, ErrorType.UNDETERMINED),
  ATS_SERVER_INVALID_TEST_RESOURCE(52_703, ErrorType.CUSTOMER_ISSUE),
  ATS_SERVER_USE_TF_RETRY_ERROR(52_704, ErrorType.CUSTOMER_ISSUE),

  // ATS Message Relay: 52_801 ~ 52_900
  MESSAGE_RELAY_NO_AVAILABLE_STREAM(52_801, ErrorType.UNDETERMINED),
  MESSAGE_RELAY_NO_STREAM_INFO(52_802, ErrorType.INFRA_ISSUE),

  // Logging: 52_901 ~ 53_000
  LOGGER_CREATE_FILE_HANDLER_ERROR(52_901, ErrorType.INFRA_ISSUE),
  LOGGER_STACKDRIVER_WRITE_RPC_ERROR(52_902, ErrorType.INFRA_ISSUE),
  LOGGER_STACKDRIVER_CLIENT_SECRET_FILE_ERROR(52_903, ErrorType.INFRA_ISSUE),

  // Test Lister: 53_001 ~ 53_100
  TEST_LISTER_LOAD_FILE_ERROR(53_001, ErrorType.UNDETERMINED),
  TEST_LISTER_JOB_PARAMETER_ERROR(53_002, ErrorType.CUSTOMER_ISSUE),
  TEST_LISTER_NOT_FOUND_ERROR(53_003, ErrorType.INFRA_ISSUE),
  TEST_LISTER_RETURN_EMPTY_LIST_ERROR(53_004, ErrorType.INFRA_ISSUE),
  TEST_LISTER_INVALID_FILTER_ERROR(53_005, ErrorType.CUSTOMER_ISSUE),
  TEST_LISTER_CREATE_ERROR(53_006, ErrorType.INFRA_ISSUE),

  // Bigstore Result Upload: 53_101 ~ 53_200
  BIGSTORE_RESULT_UPLOAD_JOB_FILE_UPLOAD_ERROR(53_101, ErrorType.UNDETERMINED),
  BIGSTORE_RESULT_UPLOAD_TEST_FILE_UPLOAD_ERROR(53_102, ErrorType.UNDETERMINED),

  // Patron Test Config: 53_201 ~ 53_300
  PATRON_TEST_CONFIG_ERROR(53_201, ErrorType.INFRA_ISSUE),
  PATRON_TEST_FILE_TRANSFER_ERROR(53_202, ErrorType.INFRA_ISSUE),

  // Cloud RPC: 53_301 ~ 53_400
  CLOUD_RPC_CLIENT_ERROR(53_301, ErrorType.INFRA_ISSUE),
  CLOUD_RPC_INIT_ERROR(53_302, ErrorType.INFRA_ISSUE),

  // Moreto: 53_401 ~ 53_450
  MORETO_TIMEOUT_ERROR(53_401, ErrorType.INFRA_ISSUE),
  MORETO_CREATE_JOB_INFO_ERROR(53_402, ErrorType.INFRA_ISSUE),

  // Nezha ERROR: 53_451 ~ 53_460
  NEZHA_LEGACY_ERROR(53_501, ErrorType.UNDETERMINED),

  // ID space here: 53_461 ~ 60_000
  PLACE_HOLDER_TO_BE_RENAMED(60_000, ErrorType.UNDETERMINED);

  public static final int MIN_CODE = BasicErrorId.MAX_CODE + 1;
  public static final int MAX_CODE = 60_000;

  private final int code;
  private final ErrorType type;

  InfraErrorId(int code, ErrorType type) {
    Preconditions.checkArgument(code >= MIN_CODE);
    Preconditions.checkArgument(code <= MAX_CODE);
    Preconditions.checkArgument(type != ErrorType.UNCLASSIFIED);
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
