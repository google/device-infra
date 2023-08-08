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

package com.google.devtools.mobileharness.shared.model.error;

import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import javax.annotation.Nullable;

/**
 * Signals that stubby/grpc error occurs during execution in MobileHarness.
 *
 * <p>Since we can't judge whether the rpc error code is from grpc or stubby, so use the integer
 * code instead. Currently, the error code number between stubby and grpc are same.
 */
public class MobileHarnessRpcException extends MobileHarnessException {

  private final int rpcCanonicalCode;

  /**
   * Constructs a new exception with the specified RPC canonical error/status code, error ID, detail
   * message and inner cause.
   */
  public MobileHarnessRpcException(
      int rpcCanonicalCode, ErrorId errorId, String message, @Nullable Throwable cause) {
    super(errorId, message, cause);
    this.rpcCanonicalCode = rpcCanonicalCode;
  }

  public int getRpcCanonicalCode() {
    return rpcCanonicalCode;
  }
}
