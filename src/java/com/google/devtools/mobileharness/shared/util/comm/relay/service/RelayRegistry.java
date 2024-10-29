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

package com.google.devtools.mobileharness.shared.util.comm.relay.service;

import static java.util.function.Function.identity;

import com.google.devtools.mobileharness.shared.util.comm.relay.ByteMarshaller;
import io.grpc.HandlerRegistry;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;

/** Registry for relay server. */
public final class RelayRegistry extends HandlerRegistry {

  private final ServerCallHandler<byte[], byte[]> handler;

  public RelayRegistry(ConnectionManager connectionManager) {
    this(
        new RelayHandler<byte[], byte[], byte[], byte[]>(
            connectionManager, identity(), identity(), identity()));
  }

  private RelayRegistry(ServerCallHandler<byte[], byte[]> handler) {
    this.handler = handler;
  }

  @Override
  public ServerMethodDefinition<?, ?> lookupMethod(String methodName, String authority) {
    MethodDescriptor<byte[], byte[]> methodDescriptor =
        MethodDescriptor.newBuilder(new ByteMarshaller(), new ByteMarshaller())
            .setFullMethodName(methodName)
            .setType(MethodDescriptor.MethodType.UNKNOWN)
            .build();
    return ServerMethodDefinition.create(methodDescriptor, handler);
  }
}
