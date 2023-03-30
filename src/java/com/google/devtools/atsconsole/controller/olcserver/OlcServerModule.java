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

package com.google.devtools.atsconsole.controller.olcserver;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.atsconsole.controller.olcserver.Annotations.ServerChannel;
import com.google.devtools.atsconsole.controller.olcserver.Annotations.ServerSessionStub;
import com.google.devtools.atsconsole.controller.olcserver.Annotations.ServerVersionStub;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ChannelFactory;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.VersionStub;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.grpc.ManagedChannel;
import javax.inject.Singleton;

/** Module for ATS console OLC server. */
public class OlcServerModule extends AbstractModule {

  @Provides
  @Singleton
  @ServerChannel
  ManagedChannel provideServerChannel(ListeningScheduledExecutorService threadPool) {
    int serverPort = Flags.instance().olcServerPort.getNonNull();
    return ChannelFactory.createLocalChannel(serverPort, threadPool);
  }

  @Provides
  @Singleton
  @ServerVersionStub
  VersionStub provideServerVersionStub(@ServerChannel ManagedChannel serverChannel) {
    return new VersionStub(serverChannel);
  }

  @Provides
  @Singleton
  @ServerSessionStub
  SessionStub provideServerSessionStub(@ServerChannel ManagedChannel serverChannel) {
    return new SessionStub(serverChannel);
  }
}
