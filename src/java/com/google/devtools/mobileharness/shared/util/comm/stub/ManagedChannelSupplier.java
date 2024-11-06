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

import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import io.grpc.ManagedChannel;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** Lazy creator of {@link ManagedChannel} for a given gRPC target. */
public final class ManagedChannelSupplier implements Function<String, ManagedChannel> {

  private static class SingletonHolder {
    private static final ManagedChannelSupplier INSTANCE =
        new ManagedChannelSupplier(
            ThreadPools.createStandardThreadPool("managed-grpc-channel-executor"));
  }

  private final Executor executor;

  private ManagedChannelSupplier(Executor executor) {
    this.executor = executor;
  }

  public static ManagedChannelSupplier getInstance() {
    return SingletonHolder.INSTANCE;
  }

  @Override
  public ManagedChannel apply(String grpcTarget) {
    return ChannelFactory.createChannel(grpcTarget, executor);
  }
}
