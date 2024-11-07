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

import com.google.protobuf.GeneratedMessage;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/** Invocation handler for grpc blocking stub interface. */
public abstract class BlockingInterfaceInvocationHandler implements InvocationHandler {

  /**
   * Invokes the sync rpc call.
   *
   * @throws StatusRuntimeException if the rpc call failed.
   */
  protected abstract <RespT extends GeneratedMessage> RespT syncInvoke(
      RpcCall<? extends GeneratedMessage, RespT> rpcCall);

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
    checkArgument(args.length == 1, "Only one argument is allowed");
    return syncInvoke(RpcCall.parseSyncCall(method, /* reqIndex= */ 0, args[0]));
  }

  /** Creates a {@link StatusRuntimeException} with the given code, message and cause. */
  protected static StatusRuntimeException createStatusRuntimeException(
      Code code, String message, Throwable cause) {
    return new StatusRuntimeException(
        Status.fromCode(code).withDescription(message).withCause(cause));
  }
}
