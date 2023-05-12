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

import static com.google.common.util.concurrent.Futures.addCallback;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;

/** Utility for implementing gRPC services. */
public class GrpcServiceUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** A generic gRPC service method. */
  public interface GrpcMethod<RequestT, ResponseT> {
    ResponseT execute(RequestT request) throws Exception;
  }

  /** A generic asynchronous gRPC service method. */
  public interface AsyncGrpcMethod<RequestT, ResponseT> {
    ListenableFuture<ResponseT> executeAsync(RequestT request) throws Exception;
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
      handleFailure(e, responseObserver, "method", serviceDescriptor, methodDescriptor);
    }
  }

  public static <RequestT, ResponseT> void invokeAsync(
      RequestT request,
      StreamObserver<ResponseT> responseObserver,
      AsyncGrpcMethod<RequestT, ResponseT> method,
      Executor executor,
      ServiceDescriptor serviceDescriptor,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    try {
      ListenableFuture<ResponseT> responseFuture = method.executeAsync(request);
      addCallback(
          responseFuture,
          new ResponseFutureCallback<>(responseObserver, serviceDescriptor, methodDescriptor),
          executor);
    } catch (Exception | Error e) {
      handleFailure(e, responseObserver, "async method", serviceDescriptor, methodDescriptor);
    }
  }

  private static class ResponseFutureCallback<RequestT, ResponseT>
      implements FutureCallback<ResponseT> {

    private final StreamObserver<ResponseT> responseObserver;
    private final ServiceDescriptor serviceDescriptor;
    private final MethodDescriptor<RequestT, ResponseT> methodDescriptor;

    private ResponseFutureCallback(
        StreamObserver<ResponseT> responseObserver,
        ServiceDescriptor serviceDescriptor,
        MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
      this.responseObserver = responseObserver;
      this.serviceDescriptor = serviceDescriptor;
      this.methodDescriptor = methodDescriptor;
    }

    @Override
    public void onSuccess(@Nullable ResponseT result) {
      responseObserver.onNext(result);
      responseObserver.onCompleted();
    }

    @Override
    public void onFailure(Throwable e) {
      handleFailure(
          e, responseObserver, "async method callback", serviceDescriptor, methodDescriptor);
    }
  }

  private static <RequestT, ResponseT> void handleFailure(
      Throwable e,
      StreamObserver<ResponseT> responseObserver,
      String type,
      ServiceDescriptor serviceDescriptor,
      MethodDescriptor<RequestT, ResponseT> methodDescriptor) {
    logger.atWarning().withCause(e).log(
        "gRPC service %s exception, service=[%s], method=[%s]",
        type, serviceDescriptor.getName(), methodDescriptor.getFullMethodName());
    responseObserver.onError(GrpcExceptionUtil.toStatusRuntimeException(e));
    if (e instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
  }
}
