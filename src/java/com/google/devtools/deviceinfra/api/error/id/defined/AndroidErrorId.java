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

package com.google.devtools.deviceinfra.api.error.id.defined;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.common.metrics.stability.util.ErrorIdFormatter;
import com.google.devtools.deviceinfra.api.error.DeviceInfraException;
import com.google.devtools.deviceinfra.api.error.DeviceInfraExceptionGenerator;
import com.google.devtools.deviceinfra.api.error.id.DeviceInfraErrorId;
import com.google.devtools.deviceinfra.api.error.id.proto.ErrorCodeRangeProto.ErrorCodeRange;
import javax.annotation.Nullable;

/** {@link DeviceInfraErrorId}s for Android platform supports. */
public enum AndroidErrorId implements DeviceInfraErrorId, DeviceInfraExceptionGenerator {
  // ***********************************************************************************************
  // Standard Android Platforms: 5_000_001 ~ 5_200_000
  // ***********************************************************************************************

  // Adb: 5_000_001 ~ 5_000_100
  ANDROID_ADB_SYNC_CMD_EXEC_ERR(5_000_001, ErrorType.UNDETERMINED),
  ANDROID_ADB_SYNC_CMD_EXEC_TIMEOUT(5_000_002, ErrorType.UNDETERMINED),
  ANDROID_ADB_SYNC_CMD_EXEC_FAILURE(5_000_003, ErrorType.UNDETERMINED),
  ANDROID_ADB_CMD_RETRY_ERR(5_000_004, ErrorType.UNDETERMINED),
  ANDROID_ADB_SYNC_CMD_START_ERR(5_000_005, ErrorType.UNDETERMINED),
  ANDROID_ADB_SHELL_CMD_RETRY_ERR(5_000_006, ErrorType.UNDETERMINED),
  ANDROID_ADB_SHELL_CMD_START_ERR(5_000_007, ErrorType.UNDETERMINED),
  ANDROID_ADB_ASYNC_CMD_START_ERR(5_000_008, ErrorType.UNDETERMINED),
  ANDROID_ADB_SYNC_CMD_EXEC_ASSERTION_FAILURE(5_000_009, ErrorType.CUSTOMER_ISSUE),

  // Android fastboot: 5_000_101 ~ 5_000_200
  ANDROID_FASTBOOT_UNKNOWN_SLOT_ERR(5_000_101, ErrorType.INFRA_ISSUE),
  ANDROID_FASTBOOT_FLASH_PARTITION_ERR(5_000_102, ErrorType.INFRA_ISSUE),
  ANDROID_FASTBOOT_UPDATE_CMD_EXEC_ERR(5_000_103, ErrorType.INFRA_ISSUE),
  ANDROID_FASTBOOT_CMD_EXEC_ERR(5_000_104, ErrorType.UNDETERMINED),
  ANDROID_FASTBOOT_MISSING_FASTBOOT_BINARY_ERR(5_000_105, ErrorType.INFRA_ISSUE),

  ANDROID_ERROR_ID_PLACE_HOLDER(
      ErrorCodeRange.ANDROID_ERROR_ID_MAX_CODE_VALUE, ErrorType.UNDETERMINED);

  private final int code;
  private final ErrorType type;

  AndroidErrorId(int code, ErrorType type) {
    checkArgument(
        code >= ErrorCodeRange.ANDROID_ERROR_ID_MIN_CODE_VALUE
            && code <= ErrorCodeRange.ANDROID_ERROR_ID_MAX_CODE_VALUE);
    this.code = code;
    this.type = checkNotNull(type);
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

  @Override
  public DeviceInfraException toException(String message) {
    return new DeviceInfraException(this, message);
  }

  @Override
  public DeviceInfraException toException(String message, @Nullable Throwable cause) {
    return new DeviceInfraException(this, message, cause);
  }
}
