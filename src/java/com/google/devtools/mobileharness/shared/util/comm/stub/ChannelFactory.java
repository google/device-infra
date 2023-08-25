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

import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import java.util.concurrent.Executor;

/** Factory for creating {@link ManagedChannel} to the server. */
public class ChannelFactory {

  public static ManagedChannel createLocalChannel(int port, Executor executor) {
    return NettyChannelBuilder.forAddress("localhost", port)
        .negotiationType(NegotiationType.PLAINTEXT)
        .executor(executor)
        .build();
  }

  private ChannelFactory() {}
}
