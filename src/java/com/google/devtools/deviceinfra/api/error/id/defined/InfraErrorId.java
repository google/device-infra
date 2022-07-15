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

/** {@link DeviceInfraErrorId}s for internal infrastructure. */
public enum InfraErrorId implements DeviceInfraErrorId, DeviceInfraExceptionGenerator {
  // Test Runner: 3_000_001 ~ 3_010_000
  TR_LOAD_DRIVER_ERROR(3_000_001, ErrorType.UNDETERMINED),
  TR_DRIVER_CLASS_NOT_FOUND(3_000_002, ErrorType.UNDETERMINED),
  TR_LOAD_DRIVER_CLASS_ERROR(3_000_003, ErrorType.UNDETERMINED),
  TR_LOAD_DRIVER_MODULE_CLASS_ERROR(3_000_004, ErrorType.UNDETERMINED),

  INFRA_ERROR_ID_PLACE_HOLDER(ErrorCodeRange.INFRA_ERROR_ID_MAX_CODE_VALUE, ErrorType.UNDETERMINED);

  private final int code;
  private final ErrorType type;

  InfraErrorId(int code, ErrorType type) {
    checkArgument(
        code >= ErrorCodeRange.INFRA_ERROR_ID_MIN_CODE_VALUE
            && code <= ErrorCodeRange.INFRA_ERROR_ID_MAX_CODE_VALUE);
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
