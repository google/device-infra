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

/**
 * {@link DeviceInfraErrorId}s for low-level device-platform-independent basic utilities/libraries.
 */
public enum BasicErrorId implements DeviceInfraErrorId, DeviceInfraExceptionGenerator {
  // ReflectionUtil: 4_000_001 ~ 4_000_100
  REFLECTION_LOAD_CLASS_ERROR(4_000_001, ErrorType.UNDETERMINED),
  REFLECTION_LOAD_CLASS_TYPE_MISMATCH(4_000_002, ErrorType.UNDETERMINED),

  // Command library: 4_000_101 ~ 4_000_300
  CMD_START_ERROR(4_000_101, ErrorType.UNDETERMINED),
  CMD_EXEC_FAIL(4_000_102, ErrorType.UNDETERMINED),
  CMD_EXEC_TIMEOUT(4_000_103, ErrorType.UNDETERMINED),
  CMD_PROCESS_GET_ID_FAILURE(4_000_104, ErrorType.DEPENDENCY_ISSUE),
  CMD_PROCESS_AWAIT_TIMEOUT(4_000_105, ErrorType.UNDETERMINED),

  BASIC_ERROR_ID_PLACE_HOLDER(ErrorCodeRange.BASIC_ERROR_ID_MAX_CODE_VALUE, ErrorType.UNDETERMINED);

  private final int code;
  private final ErrorType type;

  BasicErrorId(int code, ErrorType type) {
    checkArgument(
        code >= ErrorCodeRange.BASIC_ERROR_ID_MIN_CODE_VALUE
            && code <= ErrorCodeRange.BASIC_ERROR_ID_MAX_CODE_VALUE);
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
