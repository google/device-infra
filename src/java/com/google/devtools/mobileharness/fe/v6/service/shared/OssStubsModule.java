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
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.CopyDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.CopyDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.CopyLabConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.CopyLabConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.DeleteLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.GetLabConfigResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateDeviceConfigsResponse;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigRequest;
import com.google.devtools.mobileharness.api.deviceconfig.proto.DeviceConfigServiceProto.UpdateLabConfigResponse;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.HostAuxiliaryInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.host.provider.OssHostAuxiliaryInfoProviderImpl;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.ConfigurationProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.oss.OssConfigurationProviderImpl;
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
import com.google.inject.Provides;
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
    install(new LabInfoGrpcStubModule(olcServerChannel));
    if (Flags.instance().feConnectToConfigServer.getNonNull()) {
      install(new DeviceConfigGrpcStubModule(""));
    } else {
      bind(DeviceConfigStub.class)
          .annotatedWith(DeviceConfigGrpcStub.class)
          .to(NoOpDeviceConfigStub.class)
          .in(Singleton.class);
    }

    bind(ConfigurationProvider.class).to(OssConfigurationProviderImpl.class).in(Singleton.class);
    bind(LabInfoProvider.class).to(OssLabInfoProviderImpl.class).in(Singleton.class);
    bind(HostAuxiliaryInfoProvider.class).to(OssHostAuxiliaryInfoProviderImpl.class);
    bind(GroupMembershipProvider.class).to(NoOpGroupMembershipProvider.class).in(Singleton.class);
  }

  @Provides
  @Singleton
  LabInfoStub provideLabInfoStub(@GrpcStub LabInfoStub grpcStub) {
    return grpcStub;
  }

  @Provides
  @Singleton
  DeviceConfigStub provideDeviceConfigStub(@DeviceConfigGrpcStub DeviceConfigStub grpcStub) {
    return grpcStub;
  }

  private static final class NoOpDeviceConfigStub implements DeviceConfigStub {
    @Override
    public GetDeviceConfigsResponse getDeviceConfigs(GetDeviceConfigsRequest request) {
      return GetDeviceConfigsResponse.getDefaultInstance();
    }

    @Override
    public ListenableFuture<GetDeviceConfigsResponse> getDeviceConfigsAsync(
        GetDeviceConfigsRequest request, boolean useClientRpcAuthority) {
      return immediateFuture(GetDeviceConfigsResponse.getDefaultInstance());
    }

    @Override
    public GetLabConfigResponse getLabConfig(GetLabConfigRequest request) {
      return GetLabConfigResponse.getDefaultInstance();
    }

    @Override
    public ListenableFuture<GetLabConfigResponse> getLabConfigAsync(
        GetLabConfigRequest request, boolean useClientRpcAuthority) {
      return immediateFuture(GetLabConfigResponse.getDefaultInstance());
    }

    @Override
    public DeleteDeviceConfigsResponse deleteDeviceConfigs(DeleteDeviceConfigsRequest request) {
      return DeleteDeviceConfigsResponse.getDefaultInstance();
    }

    @Override
    public DeleteLabConfigResponse deleteLabConfig(DeleteLabConfigRequest request) {
      return DeleteLabConfigResponse.getDefaultInstance();
    }

    @Override
    public UpdateDeviceConfigsResponse updateDeviceConfigs(UpdateDeviceConfigsRequest request) {
      return UpdateDeviceConfigsResponse.getDefaultInstance();
    }

    @Override
    public UpdateLabConfigResponse updateLabConfig(UpdateLabConfigRequest request) {
      return UpdateLabConfigResponse.getDefaultInstance();
    }

    @Override
    public ListenableFuture<UpdateDeviceConfigsResponse> updateDeviceConfigsAsync(
        UpdateDeviceConfigsRequest request, boolean useClientRpcAuthority) {
      return immediateFuture(UpdateDeviceConfigsResponse.getDefaultInstance());
    }

    @Override
    public ListenableFuture<UpdateLabConfigResponse> updateLabConfigAsync(
        UpdateLabConfigRequest request, boolean useClientRpcAuthority) {
      return immediateFuture(UpdateLabConfigResponse.getDefaultInstance());
    }

    @Override
    public CopyDeviceConfigsResponse copyDeviceConfigs(CopyDeviceConfigsRequest request) {
      return CopyDeviceConfigsResponse.getDefaultInstance();
    }

    @Override
    public CopyLabConfigsResponse copyLabConfigs(CopyLabConfigsRequest request) {
      return CopyLabConfigsResponse.getDefaultInstance();
    }
  }

  private static final class NoOpGroupMembershipProvider implements GroupMembershipProvider {
    @Override
    public ListenableFuture<Boolean> isMemberOfAny(String username, List<String> groupNames) {
      return immediateFuture(false);
    }
  }
}
