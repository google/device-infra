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

package com.google.wireless.qa.mobileharness.shared.constant;

import java.util.HashMap;
import java.util.Map;

/** Mobile Harness error code. */
public enum ErrorCode {
  UNKNOWN(1),
  LEGACY_ERROR(2),
  INTERRUPTED(3),
  VERSION_FORMAT_ERROR(4),
  ILLEGAL_ARGUMENT(5),
  ACTION_ABORT(6),
  URI_ERROR(7),
  HTTP_ERROR(8),
  PERMISSION_ERROR(9),
  NEXT_GEN_ERROR(10),

  SPONGE_ERROR(11),
  ENCODING_ERROR(12),
  DECODING_ERROR(13),
  PLUGIN_ERROR(14),
  SERVER_NOT_ACCESSIBLE(15),

  PROCESS_ERROR(22),
  STUB_VERSION_TOO_LOW(23),
  SERVICE_VERSION_TOO_LOW(24),
  NOT_IMPLEMENTED(25),
  RACE_CONDITION(26),
  NETWORK_ERROR(27),
  ILLEGAL_STATE(28),
  SEARCH_ERROR(29),
  BUILD_ERROR(30),
  MAIL_ERROR(31),
  NUMBER_FORMAT_ERROR(32),

  // ***********************************************************************************************
  // End. You should double check whether your error codes can fit into the above ranges before
  // adding error codes >= 20,000.
  // ***********************************************************************************************

  END_OF_ERROR_CODE(20_000);

  private final int code;

  ErrorCode(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }

  private static final Map<Integer, ErrorCode> intToEnum = new HashMap<>();

  static {
    for (ErrorCode errorCode : ErrorCode.values()) {
      intToEnum.put(errorCode.code(), errorCode);
    }
  }

  public static ErrorCode enumOf(int code) {
    ErrorCode result = intToEnum.get(code);
    return result == null ? ErrorCode.UNKNOWN : result;
  }
}
