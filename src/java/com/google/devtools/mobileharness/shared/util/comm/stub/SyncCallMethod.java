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
import com.google.devtools.mobileharness.shared.util.base.ProtoReflectionUtil;
import com.google.protobuf.GeneratedMessage;
import java.lang.reflect.Method;

/** Data class for a sync call method. */
@AutoValue
public abstract class SyncCallMethod<
    ReqT extends GeneratedMessage, RespT extends GeneratedMessage> {

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
      SyncCallMethod<ReqT, RespT> create(
          Class<ReqT> requestType, Class<RespT> responseType, String methodName, ReqT request) {
    return new AutoValue_SyncCallMethod<>(requestType, responseType, methodName, request);
  }

  /**
   * Parses the {@link SyncCallMethod} from a sync rpc method.
   *
   * @param method of the sync rpc call. The return type must be the response.
   * @param reqIndex the index of the request parameter
   * @param reqArg the request argument
   */
  static <ReqT extends GeneratedMessage, RespT extends GeneratedMessage>
      SyncCallMethod<ReqT, RespT> parse(Method method, int reqIndex, Object reqArg) {
    checkArgument(
        GeneratedMessage.class.isAssignableFrom(method.getParameterTypes()[reqIndex]),
        "The %s parameter %s is not a valid request message",
        reqIndex,
        method.getParameterTypes()[reqIndex]);
    @SuppressWarnings("unchecked") // Safe by rpc method.
    Class<ReqT> requestType = (Class<ReqT>) method.getParameterTypes()[reqIndex];
    checkArgument(
        GeneratedMessage.class.isAssignableFrom(method.getReturnType()),
        "The return type %s is not a valid response message",
        method.getReturnType());
    @SuppressWarnings("unchecked") // Safe by rpc method.
    Class<RespT> responseType = (Class<RespT>) method.getReturnType();
    ReqT request = requestType.cast(reqArg);
    return SyncCallMethod.create(requestType, responseType, method.getName(), request);
  }
}
