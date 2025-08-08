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

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.infra.ats.common.FlagsString;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientComponentName;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ClientId;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.DeviceInfraServiceFlags;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerChannel;
import com.google.devtools.mobileharness.infra.ats.common.olcserver.Annotations.ServerStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.grpc.ControlGrpcStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.grpc.SessionGrpcStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub.grpc.VersionGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.grpc.ManagedChannel;
import javax.inject.Singleton;

/** Module for ATS console /local runner that connect to ATS OLC server. */
public class OlcServerConnectorModule extends AbstractModule {

  private final FlagsString deviceInfraServiceFlags;
  private final String clientComponentName;
  private final String clientId;

  public OlcServerConnectorModule(
      FlagsString deviceInfraServiceFlags, String clientComponentName, String clientId) {
    this.deviceInfraServiceFlags = deviceInfraServiceFlags;
    this.clientComponentName = clientComponentName;
    this.clientId = clientId;
  }

  @Provides
  @ClientComponentName
  String provideClientComponentName() {
    return clientComponentName;
  }

  @Provides
  @ClientId
  String provideClientId() {
    return clientId;
  }

  @Provides
  @DeviceInfraServiceFlags
  FlagsString provideDeviceInfraServiceFlags() {
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
  ControlGrpcStub provideServerControlStub(@ServerChannel ManagedChannel serverChannel) {
    return new ControlGrpcStub(serverChannel);
  }

  @Provides
  @Singleton
  @ServerStub(ServerStub.Type.SESSION_SERVICE)
  SessionGrpcStub provideServerSessionStub(@ServerChannel ManagedChannel serverChannel) {
    return new SessionGrpcStub(serverChannel);
  }

  @Provides
  @Singleton
  @ServerStub(ServerStub.Type.VERSION_SERVICE)
  VersionGrpcStub provideServerVersionStub(@ServerChannel ManagedChannel serverChannel) {
    return new VersionGrpcStub(serverChannel);
  }
}
