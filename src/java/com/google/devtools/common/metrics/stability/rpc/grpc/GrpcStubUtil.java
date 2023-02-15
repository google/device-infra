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

import com.google.devtools.common.metrics.stability.model.ErrorId;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.StatusRuntimeException;

/** The util class for invoking common gRPC methods. */
public final class GrpcStubUtil {

  /** The functional interface for all gRPC methods in the stub which takes one request. */
  @FunctionalInterface
  public interface GrpcMethod<ReqT, RespT> {
    RespT execute(ReqT request) throws StatusRuntimeException;
  }

  private GrpcStubUtil() {}

  /**
   * Invokes a Grpc method.
   *
   * @param errorId the error ID set into the thrown exception if failed to invoke the rpc
   * @param errorMessage the error message set into the thrown exception if failed to invoke the rpc
   * @throws GrpcExceptionWithErrorId if a {@link StatusRuntimeException} occurs during the rpc call
   */
  @CanIgnoreReturnValue
  public static <ReqT, RespT> RespT invoke(
      GrpcMethod<ReqT, RespT> grpcMethod, ReqT request, ErrorId errorId, String errorMessage)
      throws GrpcExceptionWithErrorId {
    try {
      return grpcMethod.execute(request);
    } catch (StatusRuntimeException e) {
      throw GrpcExceptionUtil.toGrpcExceptionWithErrorId(errorId, errorMessage, e);
    }
  }
}
