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

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.shared.util.base.ProtoReflectionUtil;
import com.google.protobuf.GeneratedMessage;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Data class for an RPC call.
 *
 * <p>It contains all the information needed to invoke an RPC call. Currently, only unary RPC is
 * supported.
 */
@AutoValue
public abstract class RpcCall<ReqT extends GeneratedMessage, RespT extends GeneratedMessage> {

  public abstract Class<ReqT> requestType();

  public abstract Class<RespT> responseType();

  public abstract String methodName();

  public abstract ReqT request();

  @Memoized
  public String responseTypeFullName() {
    return ProtoReflectionUtil.getDefaultInstance(responseType())
        .getDescriptorForType()
        .getFullName();
  }

  static <ReqT extends GeneratedMessage, RespT extends GeneratedMessage>
      RpcCall<ReqT, RespT> create(
          Class<ReqT> requestType, Class<RespT> responseType, String methodName, ReqT request) {
    return new AutoValue_RpcCall<>(requestType, responseType, methodName, request);
  }

  /**
   * Parses the {@link RpcCall} from a sync unary RPC method.
   *
   * @param method of the sync unary rpc call. The return type must be the response.
   * @param reqIndex the index of the request parameter
   * @param reqArg the request argument
   */
  static <ReqT extends GeneratedMessage, RespT extends GeneratedMessage>
      RpcCall<ReqT, RespT> parseSyncCall(Method method, int reqIndex, Object reqArg) {
    Class<ReqT> requestType = getRequestType(method, reqIndex);
    checkArgument(
        GeneratedMessage.class.isAssignableFrom(method.getReturnType()),
        "The return type %s is not a valid response message",
        method.getReturnType());
    @SuppressWarnings("unchecked") // Safe by rpc method.
    Class<RespT> responseType = (Class<RespT>) method.getReturnType();
    ReqT request = requestType.cast(reqArg);
    return RpcCall.create(requestType, responseType, method.getName(), request);
  }

  /**
   * Parses the {@link RpcCall} from a future unary RPC method.
   *
   * @param method of the async unary rpc call. The return type must be a {@link ListenableFuture}
   *     of the response.
   * @param reqIndex the index of the request parameter
   * @param reqArg the request argument
   */
  static <ReqT extends GeneratedMessage, RespT extends GeneratedMessage>
      RpcCall<ReqT, RespT> parseFutureCall(Method method, int reqIndex, Object reqArg) {
    Class<ReqT> requestType = getRequestType(method, reqIndex);
    Type returnType = method.getGenericReturnType();
    checkArgument(
        returnType instanceof ParameterizedType,
        "The return type %s is not a parameterized type.",
        returnType);
    ParameterizedType type = (ParameterizedType) returnType;
    Type rawType = type.getRawType();
    checkArgument(
        ListenableFuture.class.isAssignableFrom((Class<?>) rawType),
        "The return type %s is not a valid Future return type.",
        rawType);
    Type[] typeArguments = type.getActualTypeArguments();
    checkArgument(
        typeArguments.length == 1,
        "The return type %s should have exactly one type arguments.",
        type);
    Class<?> typeArgClazz = (Class) typeArguments[0];
    checkArgument(
        GeneratedMessage.class.isAssignableFrom(typeArgClazz),
        "The return type %s is not a valid response message",
        typeArgClazz);
    @SuppressWarnings("unchecked") // Safe by rpc method.
    Class<RespT> responseType = (Class<RespT>) typeArgClazz;
    ReqT request = requestType.cast(reqArg);
    return RpcCall.create(requestType, responseType, method.getName(), request);
  }

  @SuppressWarnings("unchecked") // Safe by rpc method.
  private static <ReqT extends GeneratedMessage> Class<ReqT> getRequestType(
      Method method, int reqIndex) {
    checkArgument(
        GeneratedMessage.class.isAssignableFrom(method.getParameterTypes()[reqIndex]),
        "The %s parameter %s is not a valid request message",
        reqIndex,
        method.getParameterTypes()[reqIndex]);
    return (Class<ReqT>) method.getParameterTypes()[reqIndex];
  }
}
