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

package com.google.devtools.mobileharness.infra.ats.common.olcserver;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerChannel;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.OlcServices;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import javax.inject.Singleton;

/** Module for providing gRPC in-process channels connecting to an in-process OLC server. */
public class OlcServerGrpcInProcessChannelModule extends AbstractModule {

  @Provides
  @Singleton
  @ServerChannel
  Channel provideInProcessServerChannel(
      ListeningExecutorService threadPool,
      ListeningScheduledExecutorService scheduledThreadPool,
      @OlcServices ImmutableList<BindableService> services)
      throws IOException {
    String serverName = "in-process-olc-grpc-server";
    InProcessServerBuilder serverBuilder =
        InProcessServerBuilder.forName(serverName)
            .executor(threadPool)
            .scheduledExecutorService(scheduledThreadPool);
    services.forEach(serverBuilder::addService);
    serverBuilder.build().start();
    return InProcessChannelBuilder.forName(serverName)
        .executor(threadPool)
        .scheduledExecutorService(scheduledThreadPool)
        .build();
  }
}
