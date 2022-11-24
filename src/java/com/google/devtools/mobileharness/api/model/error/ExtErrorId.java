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
 * Extended error IDs for other Mobile Harness platform(iOS, mobly, etc) supports except Android,
 * like utilities, Driver/Decorator, Detector/Device implementations. For Android platform related,
 * check/update in {@link AndroidErrorId} instead.
 */
public enum ExtErrorId implements ErrorId {
  // ***********************************************************************************************
  // iOS: 60_001 ~ 80_000
  // ***********************************************************************************************

  // Mobly: 81_001 ~ 81_100
  MOBLY_SPONGE_OUTPUT_PARSING_ERROR(81_001, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TESTBED_SUBDEVICE_CALLABLE_ERROR(81_002, ErrorType.INFRA_ISSUE),
  MOBLY_TESTBED_ADHOC_DRIVER_END_WITH_UNKNOWN_RESULT(81_003, ErrorType.INFRA_ISSUE),
  MOBLY_FAILED_TO_FIND_CONFIG_FILE_ERROR(81_004, ErrorType.CUSTOMER_ISSUE),
  MOBLY_FAILED_TO_READ_TESTBED_CONFIG_ERROR(81_005, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TESTBED_CONFIG_MISSING_KEY_ERROR(81_006, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TESTBED_CONFIG_PARSING_ERROR(81_007, ErrorType.CUSTOMER_ISSUE),
  MOBLY_INVALID_TESTBED_MODEL_CONFIG_ERROR(81_008, ErrorType.CUSTOMER_ISSUE),
  MOBLY_INVALID_TESTBED_CONTROLLER_CONFIG_ERROR(81_009, ErrorType.CUSTOMER_ISSUE),
  MOBLY_CONFIG_GENERATION_ERROR(81_010, ErrorType.CUSTOMER_ISSUE),
  MOBLY_FAILED_TO_CREATE_TEMP_DIRECTORY_ERROR(81_011, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TESTBED_NAME_EMPTY_ERROR(81_012, ErrorType.CUSTOMER_ISSUE),
  MOBLY_EXECUTE_ERROR(81_013, ErrorType.UNDETERMINED),
  MOBLY_OUTPUT_PARSING_ERROR(81_014, ErrorType.INFRA_ISSUE),
  MOBLY_MISC_TESTBED_SUBDEVICE_JSON_TYPE_NAME_ERROR(81_015, ErrorType.INFRA_ISSUE),
  MOBLY_SUBDEVICE_TYPE_NOT_FOUND_ERROR(81_016, ErrorType.INFRA_ISSUE),
  MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_INFRA_ERROR_RESULT(81_017, ErrorType.INFRA_ISSUE),
  MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_DEPENDENCY_ERROR_RESULT(
      81_018, ErrorType.DEPENDENCY_ISSUE),
  MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_CUSTOMER_ERROR_RESULT(81_019, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_UNDETERMINED_ERROR_RESULT(81_020, ErrorType.UNDETERMINED),
  MOBLY_TESTBED_ADHOC_DRIVER_SUBTEST_WITH_FAIL_RESULT(81_021, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TEST_FAILURE(81_022, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TEST_TIMEOUT(81_023, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TEST_SUMMARY_YAML_PARSING_ERROR(81_024, ErrorType.INFRA_ISSUE),
  MOBLY_TEST_SCRIPT_ERROR(81_025, ErrorType.CUSTOMER_ISSUE),
  MOBLY_COMMAND_OUTPUT_EMPTY(81_026, ErrorType.INFRA_ISSUE),
  MOBLY_FAILED_TO_READ_COMMAND_OUTPUT(81_027, ErrorType.INFRA_ISSUE),
  MOBLY_TEST_CASE_FAILURE(81_028, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TEST_CASE_ERROR(81_029, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TEST_CASE_SKIPPED(81_030, ErrorType.CUSTOMER_ISSUE),
  MOBLY_TEST_SUMMARY_YAML_CONVERT_XML_ERROR(81_031, ErrorType.INFRA_ISSUE),

  MOBLY_AOSP_CREATE_VENV_ERROR(81_081, ErrorType.INFRA_ISSUE),
  MOBLY_AOSP_PYTHON_VERSION_NOT_FOUND_ERROR(81_082, ErrorType.INFRA_ISSUE),
  MOBLY_AOSP_UNZIP_TEST_PACKAGE_ERROR(81_083, ErrorType.CUSTOMER_ISSUE),
  MOBLY_AOSP_RESOLVE_TEST_PATH_ERROR(81_084, ErrorType.CUSTOMER_ISSUE),
  MOBLY_AOSP_PIP_INSTALL_ERROR(81_085, ErrorType.CUSTOMER_ISSUE),

  // NoOpDriver: 83_401 ~ 83_420
  NO_OP_DRIVER_NON_PASSING_RESULT_SET_BY_MESSAGE(83_401, ErrorType.CUSTOMER_ISSUE),
  NO_OP_DRIVER_NON_PASSING_RESULT_SET_BY_PARAM(83_402, ErrorType.CUSTOMER_ISSUE),

  EXT_PLACE_HOLDER_TO_BE_RENAMED(100_000, ErrorType.UNDETERMINED);

  public static final int MIN_CODE = InfraErrorId.MAX_CODE + 1;
  public static final int MAX_CODE = 100_000;

  private final int code;
  private final ErrorType type;

  ExtErrorId(int code, ErrorType type) {
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
