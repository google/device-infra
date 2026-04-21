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

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.LogcatActionHelper;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.NoOpLogcatActionHelper;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.OssTestbedConfigBuilderImpl;
import com.google.devtools.mobileharness.fe.v6.service.device.handlers.TestbedConfigBuilder;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostAuxiliaryInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.OssHostAuxiliaryInfoProviderImpl;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProviderImpl;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProviderImpl;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.NoOpConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.NoOpRoutedUniverseLabInfoStubFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.RoutedUniverseLabInfoStubFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.JobSyncGrpcStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.Annotation.DeviceConfigGrpcStub;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.DeviceConfigStub;
import com.google.devtools.mobileharness.service.deviceconfig.rpc.stub.grpc.DeviceConfigGrpcStubModule;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import io.grpc.ManagedChannel;
import java.util.List;
import javax.inject.Singleton;

/** Module for providing OSS (gRPC) stubs. */
public final class OssStubsModule extends AbstractModule {
  @Override
  protected void configure() {
    ManagedChannel olcServerChannel =
        ChannelFactory.createLocalChannel(
            Flags.instance().olcServerPort.getNonNull(), directExecutor());
    bind(MasterGrpcStubHelper.class).toInstance(new MasterGrpcStubHelper(olcServerChannel));
    bind(LabInfoStub.class).to(LabInfoGrpcStub.class).in(Singleton.class);
    bind(JobSyncStub.class).to(JobSyncGrpcStub.class).in(Singleton.class);

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
    install(new FactoryModuleBuilder().build(FeatureManagerFactory.class));

    bind(LabInfoProvider.class).to(LabInfoProviderImpl.class).in(Singleton.class);
    bind(RoutedUniverseLabInfoStubFactory.class)
        .to(NoOpRoutedUniverseLabInfoStubFactory.class)
        .in(Singleton.class);

    bind(HostAuxiliaryInfoProvider.class).to(OssHostAuxiliaryInfoProviderImpl.class);
    bind(LogcatActionHelper.class).to(NoOpLogcatActionHelper.class).in(Singleton.class);
    bind(GroupMembershipProvider.class).to(NoOpGroupMembershipProvider.class).in(Singleton.class);
    bind(TestbedConfigBuilder.class).to(OssTestbedConfigBuilderImpl.class).in(Singleton.class);
  }

  private static final class NoOpGroupMembershipProvider implements GroupMembershipProvider {
    @Override
    public ListenableFuture<Boolean> isMemberOfAny(String username, List<String> groupNames) {
      return immediateFuture(false);
    }
  }
}
