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

import com.google.devtools.mobileharness.fe.v6.service.device.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.device.OssConfigurationProviderImpl;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostAuxiliaryInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.OssHostAuxiliaryInfoProviderImpl;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.oss.OssLabInfoProviderImpl;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.MasterStubAnnotation.GrpcStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStubModule;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
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

    bind(ConfigurationProvider.class).to(OssConfigurationProviderImpl.class).in(Singleton.class);
    bind(LabInfoProvider.class).to(OssLabInfoProviderImpl.class).in(Singleton.class);
    bind(HostAuxiliaryInfoProvider.class).to(OssHostAuxiliaryInfoProviderImpl.class);
  }

  @Provides
  @Singleton
  LabInfoStub provideLabInfoStub(@GrpcStub LabInfoStub grpcStub) {
    return grpcStub;
  }
}
