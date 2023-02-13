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

package com.google.devtools.common.metrics.stability.rpc.grpc;

import com.google.devtools.common.metrics.stability.converter.DeserializedException;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
import io.grpc.StatusRuntimeException;
import javax.annotation.Nullable;

/**
 * {@link RpcExceptionWithErrorId} based on a {@link StatusRuntimeException}.
 *
 * <p>Use {@link #getApplicationError()} or {@link #getCause()} to get the deserialized server side
 * {@linkplain com.google.devtools.common.metrics.stability.model.ErrorIdProvider} application error
 * if any.
 *
 * <p>Use {@link #getUnderlyingRpcException()} or {@code getSuppressed()[0]} to get the underlying
 * {@link StatusRuntimeException}.
 *
 * @see RpcExceptionWithErrorId
 */
public class GrpcExceptionWithErrorId extends RpcExceptionWithErrorId {

  /** Do not make it public. */
  GrpcExceptionWithErrorId(
      ErrorId errorId,
      String message,
      int rpcCanonicalCode,
      @Nullable DeserializedException applicationError,
      StatusRuntimeException underlyingGrpcException) {
    super(errorId, message, rpcCanonicalCode, applicationError, underlyingGrpcException);
  }

  /**
   * Returns the underlying {@link StatusRuntimeException}.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public StatusRuntimeException getUnderlyingRpcException() {
    return (StatusRuntimeException) super.getUnderlyingRpcException();
  }
}
