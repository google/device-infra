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
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientComponentName;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerBinary;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerChannel;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.ControlStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.SessionStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.grpc.ManagedChannel;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Provider;
import javax.inject.Singleton;

/** Module for ATS console /local runner that connect to ATS OLC server. */
public class OlcServerModule extends AbstractModule {

  private final Provider<Path> serverBinary;
  private final ImmutableList<String> deviceInfraServiceFlags;
  private final String clientComponentName;

  public OlcServerModule(
      Provider<Path> serverBinary,
      List<String> deviceInfraServiceFlags,
      String clientComponentName) {
    this.serverBinary = serverBinary;
    this.deviceInfraServiceFlags = ImmutableList.copyOf(deviceInfraServiceFlags);
    this.clientComponentName = clientComponentName;
  }

  @Override
  protected void configure() {
    bind(Path.class).annotatedWith(ServerBinary.class).toProvider(serverBinary);
  }

  @Provides
  @ClientComponentName
  String provideClientComponentName() {
    return clientComponentName;
  }

  @Provides
  @DeviceInfraServiceFlags
  ImmutableList<String> provideDeviceInfraServiceFlags() {
    return deviceInfraServiceFlags;
  }

  @Provides
  @Singleton
  @ServerChannel
  ManagedChannel provideServerChannel(ListeningExecutorService threadPool) {
    int serverPort = Flags.instance().olcServerPort.getNonNull();
    return ChannelFactory.createLocalChannel(serverPort, threadPool);
  }

  @Provides
  @Singleton
  @ServerStub(ServerStub.Type.CONTROL_SERVICE)
  ControlStub provideServerControlStub(@ServerChannel ManagedChannel serverChannel) {
    return new ControlStub(serverChannel);
  }

  @Provides
  @Singleton
  @ServerStub(ServerStub.Type.SESSION_SERVICE)
  SessionStub provideServerSessionStub(@ServerChannel ManagedChannel serverChannel) {
    return new SessionStub(serverChannel);
  }

  @Provides
  @Singleton
  @ServerStub(ServerStub.Type.VERSION_SERVICE)
  VersionStub provideServerVersionStub(@ServerChannel ManagedChannel serverChannel) {
    return new VersionStub(serverChannel);
  }
}
