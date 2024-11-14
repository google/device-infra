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

package com.google.devtools.mobileharness.shared.util.comm.stub;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.devtools.mobileharness.shared.util.comm.stub.GrpcStatusUtils.createStatusRuntimeException;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status.Code;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/** Invocation handler for gRPC future interface. */
public abstract class GrpcFutureInterfaceInvocationHandler<
        ProxyReqT extends GeneratedMessage, ProxyRespT extends GeneratedMessage>
    implements InvocationHandler {

  protected final MessageTransformer<ProxyReqT, ProxyRespT> messageTransformer;
  protected final Executor executor;

  protected GrpcFutureInterfaceInvocationHandler(
      MessageTransformer<ProxyReqT, ProxyRespT> messageTransformer, Executor executor) {
    this.messageTransformer = messageTransformer;
    this.executor = executor;
  }

  protected abstract ListenableFuture<ProxyRespT> futureInvoke(ProxyReqT request);

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    checkArgument(args.length == 1, "Only one argument is allowed");
    final var rpcCall = RpcCall.parseFutureCall(method, /* reqIndex= */ 0, args[0]);
    ProxyReqT request;
    try {
      request = messageTransformer.transformRequest(rpcCall);
    } catch (InvalidProtocolBufferException e) {
      return immediateFailedFuture(
          createStatusRuntimeException(Code.DATA_LOSS, "Failed to parse the request.", e));
    } catch (ExecutionException e) {
      return immediateFailedFuture(
          createStatusRuntimeException(Code.INTERNAL, "Failed to retrieve cache.", e));
    }
    return transform(futureInvoke(request), resp -> transformResponse(resp, rpcCall), executor);
  }

  private <RespT extends GeneratedMessage> RespT transformResponse(
      ProxyRespT response, RpcCall<? extends GeneratedMessage, RespT> rpcCall) {
    try {
      return messageTransformer.transformResponse(response, rpcCall);
    } catch (InvalidProtocolBufferException e) {
      throw createStatusRuntimeException(Code.DATA_LOSS, "Failed to parse the response.", e);
    } catch (ExecutionException e) {
      throw createStatusRuntimeException(Code.INTERNAL, "Failed to retrieve cache.", e);
    }
  }
}
