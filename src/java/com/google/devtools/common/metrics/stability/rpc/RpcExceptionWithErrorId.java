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

package com.google.devtools.common.metrics.stability.rpc;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.devtools.common.metrics.stability.converter.DeserializedException;
import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.devtools.common.metrics.stability.model.ErrorIdProvider;
import java.util.Optional;
import javax.annotation.Nullable;

/** Exception with the RPC canonical code and ErrorId. */
public class RpcExceptionWithErrorId extends Exception implements ErrorIdProvider<ErrorId> {

  private final ErrorId errorId;

  private final int rpcCanonicalCode;

  /** Do not make it public. */
  protected RpcExceptionWithErrorId(
      ErrorId errorId,
      String message,
      int rpcCanonicalCode,
      @Nullable DeserializedException applicationError,
      Throwable underlyingRpcException) {
    super(formatMessage(message, rpcCanonicalCode), applicationError);
    this.errorId = checkNotNull(errorId);
    this.rpcCanonicalCode = rpcCanonicalCode;
    addSuppressed(checkNotNull(underlyingRpcException));
  }

  public int getRpcCanonicalCode() {
    return rpcCanonicalCode;
  }

  /**
   * Returns the underlying RPC exception ({@linkplain com.google.net.rpc3.RpcException
   * RpcException} or {@linkplain io.grpc.StatusRuntimeException StatusRuntimeException}).
   */
  public Throwable getUnderlyingRpcException() {
    return getSuppressed()[0];
  }

  /** Returns the deserialized server side application error with ErrorId. */
  public Optional<DeserializedException> getApplicationError() {
    return Optional.ofNullable(getCause());
  }

  /**
   * Returns the deserialized server side application error with ErrorId.
   *
   * <p>{@inheritDoc}
   *
   * @see #getApplicationError()
   */
  @Nullable
  @Override
  public DeserializedException getCause() {
    return (DeserializedException) super.getCause();
  }

  /** Gets the client side ErrorId. */
  @Override
  public ErrorId getErrorId() {
    return errorId;
  }

  private static String formatMessage(String message, int rpcCanonicalCode) {
    return String.format("%s [rpc_code=%s]", message, rpcCanonicalCode);
  }
}
