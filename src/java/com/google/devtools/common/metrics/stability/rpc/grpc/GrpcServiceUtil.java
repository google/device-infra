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

import com.google.common.flogger.FluentLogger;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import io.grpc.stub.StreamObserver;

/** Utility for implementing gRPC services. */
public class GrpcServiceUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** A generic gRPC service method. */
  public interface GrpcMethod<RequestT, ResponseT> {
    ResponseT execute(RequestT request) throws Exception;
  }

  private GrpcServiceUtil() {}

  public static <RequestT, ResponseT> void invoke(
      RequestT request,
      StreamObserver<ResponseT> responseObserver,
      GrpcMethod<RequestT, ResponseT> method,
      ServiceDescriptor serviceDescriptor,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    try {
      ResponseT response = method.execute(request);
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception | Error e) {
      logger.atWarning().withCause(e).log(
          "gRPC service method exception, service=[%s], method=[%s]",
          serviceDescriptor.getName(), methodDescriptor.getFullMethodName());
      responseObserver.onError(GrpcExceptionUtil.toStatusRuntimeException(e));
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
