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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;

/** A helper class to manage gRPC channel and an interceptor list. */
public class MasterGrpcStubHelper {

  private final ManagedChannel channel;
  private final ImmutableList<ClientInterceptor> interceptors;

  public MasterGrpcStubHelper(ManagedChannel channel) {
    this(channel, ImmutableList.of());
  }

  private MasterGrpcStubHelper(
      ManagedChannel channel, ImmutableList<ClientInterceptor> interceptors) {
    this.channel = checkNotNull(channel);
    this.interceptors = interceptors;
  }

  public ManagedChannel getChannel() {
    return channel;
  }

  public ImmutableList<ClientInterceptor> getInterceptors() {
    return interceptors;
  }

  public void closeChannel() {
    channel.shutdown();
  }
}
