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
import static com.google.devtools.mobileharness.shared.util.comm.stub.GrpcStatusUtils.createStatusRuntimeException;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status.Code;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;

/** Invocation handler for grpc blocking stub interface. */
public abstract class BlockingInterfaceInvocationHandler<
        ProxyReqT extends GeneratedMessage, ProxyRespT extends GeneratedMessage>
    implements InvocationHandler {

  protected final MessageTransformer<ProxyReqT, ProxyRespT> messageTransformer;

  protected BlockingInterfaceInvocationHandler(
      MessageTransformer<ProxyReqT, ProxyRespT> messageTransformer) {
    this.messageTransformer = messageTransformer;
  }

  /**
   * Invokes the sync rpc call.
   *
   * @throws StatusRuntimeException if the rpc call failed.
   */
  protected abstract ProxyRespT syncInvoke(ProxyReqT request);

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    checkArgument(args.length == 1, "Only one argument is allowed");
    final var rpcCall = RpcCall.parseSyncCall(method, /* reqIndex= */ 0, args[0]);
    ProxyReqT request = getRequest(rpcCall);
    try {
      return messageTransformer.transformResponse(syncInvoke(request), rpcCall);
    } catch (InvalidProtocolBufferException e) {
      throw createStatusRuntimeException(Code.DATA_LOSS, "Failed to parse the response.", e);
    } catch (ExecutionException e) {
      throw createStatusRuntimeException(Code.INTERNAL, "Failed to retrieve cache.", e);
    }
  }

  private ProxyReqT getRequest(
      RpcCall<? extends GeneratedMessage, ? extends GeneratedMessage> rpcCall) {
    try {
      return messageTransformer.transformRequest(rpcCall);
    } catch (InvalidProtocolBufferException e) {
      throw createStatusRuntimeException(Code.DATA_LOSS, "Failed to parse the request.", e);
    } catch (ExecutionException e) {
      throw createStatusRuntimeException(Code.INTERNAL, "Failed to retrieve cache.", e);
    }
  }
}
