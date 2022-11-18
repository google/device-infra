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
