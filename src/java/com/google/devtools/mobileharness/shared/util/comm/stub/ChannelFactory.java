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

import static java.util.concurrent.TimeUnit.SECONDS;

import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import java.time.Duration;
import java.util.concurrent.Executor;

/** Factory for creating {@link ManagedChannel} to the server. */
public class ChannelFactory {

  public static ManagedChannel createLocalChannel(int port, Executor executor) {
    return createChannel(NettyChannelBuilder.forTarget("dns:///localhost:" + port), executor);
  }

  /**
   * See {@link io.grpc.ManagedChannelBuilder#forTarget(String)} about all valid formats of {@code
   * target}.
   */
  public static ManagedChannel createChannel(String target, Executor executor) {
    return createChannel(NettyChannelBuilder.forTarget(target), executor);
  }

  private static ManagedChannel createChannel(
      NettyChannelBuilder channelBuilder, Executor executor) {
    return channelBuilder
        .enableRetry()
        .negotiationType(NegotiationType.PLAINTEXT)
        .executor(executor)
        .maxInboundMessageSize(32 * 1024 * 1024) // 32 MB
        .build();
  }

  /**
   * Shuts down the managed channel and awaits its termination.
   *
   * @param channel the channel to shut down
   * @param timeout the time to wait for termination
   */
  public static void shutdown(ManagedChannel channel, Duration timeout) {
    channel.shutdown();
    try {
      var unused = channel.awaitTermination(timeout.toSeconds(), SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private ChannelFactory() {}
}
