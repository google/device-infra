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

package com.google.devtools.mobileharness.fe.v6.service.shared;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostAuxiliaryInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.OssHostAuxiliaryInfoProviderImpl;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProviderImpl;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.NoOpConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.oss.OssLabInfoProviderImpl;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.MasterStubAnnotation.GrpcStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStubModule;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.Annotation.DeviceConfigGrpcStub;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.grpc.DeviceConfigGrpcStubModule;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import io.grpc.ManagedChannel;
import javax.inject.Singleton;

/** Module for providing OSS (gRPC) stubs. */
public final class OssStubsModule extends AbstractModule {
  @Override
  protected void configure() {
    ManagedChannel olcServerChannel =
        ChannelFactory.createLocalChannel(
            Flags.instance().olcServerPort.getNonNull(), directExecutor());
    install(new LabInfoGrpcStubModule(olcServerChannel));
    if (Flags.instance().feConnectToConfigServer.getNonNull()) {

      install(new DeviceConfigGrpcStubModule());

      bind(ConfigurationProvider.class).to(ConfigurationProviderImpl.class).in(Singleton.class);
      bind(DeviceConfigStub.class)
          .to(Key.get(DeviceConfigStub.class, DeviceConfigGrpcStub.class))
          .in(Singleton.class);
    } else {
      bind(ConfigurationProvider.class).to(NoOpConfigurationProvider.class).in(Singleton.class);
    }

    install(new FactoryModuleBuilder().build(ConfigServiceCapabilityFactory.class));

    bind(LabInfoProvider.class).to(OssLabInfoProviderImpl.class).in(Singleton.class);
    bind(HostAuxiliaryInfoProvider.class).to(OssHostAuxiliaryInfoProviderImpl.class);
  }

  @Provides
  @Singleton
  LabInfoStub provideLabInfoStub(@GrpcStub LabInfoStub grpcStub) {
    return grpcStub;
  }
}
