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

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.concurrent.ExecutionException;

/** Transforms rpc messages. */
public interface MessageTransformer<
    ProxyReqT extends GeneratedMessage, ProxyRespT extends GeneratedMessage> {

  /** Transforms a client rpc call to a request used by the proxy. */
  ProxyReqT transformRequest(
      RpcCall<? extends GeneratedMessage, ? extends GeneratedMessage> rpcCall)
      throws ExecutionException, InvalidProtocolBufferException;

  /** Transforms a rpc response from the proxy to client response. */
  <RespT extends GeneratedMessage> RespT transformResponse(
      ProxyRespT response, RpcCall<? extends GeneratedMessage, RespT> rpcCall)
      throws ExecutionException, InvalidProtocolBufferException;
}
