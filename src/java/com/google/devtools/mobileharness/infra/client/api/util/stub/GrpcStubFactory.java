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

package com.google.devtools.mobileharness.infra.client.api.util.stub;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.devtools.mobileharness.infra.lab.rpc.stub.ExecTestStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.PrepareTestStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.grpc.ExecTestGrpcStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.grpc.PrepareTestGrpcStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.JobSyncGrpcStub;
import com.google.devtools.mobileharness.shared.constant.closeable.NoOpAutoCloseable;
import com.google.devtools.mobileharness.shared.constant.closeable.NonThrowingAutoCloseable;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferStubInterface;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.StubConfiguration;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.StubConfiguration.TargetConfigurationCase;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.Transport;
import com.google.devtools.mobileharness.shared.version.rpc.stub.VersionStub;
import com.google.devtools.mobileharness.shared.version.rpc.stub.grpc.VersionGrpcStub;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/** Factory to create gRPC stubs. */
final class GrpcStubFactory implements StubFactory {

  private static final NonThrowingAutoCloseable NO_OP_AUTO_CLOSEABLE_INSTANCE =
      new NoOpAutoCloseable();

  private final Map<TargetConfigurationCase, BlockingInterfaceFactory> factoryMap;
  private final Map<TargetConfigurationCase, GrpcFutureInterfaceFactory> futureFactoryMap;

  @Inject
  GrpcStubFactory(
      Map<TargetConfigurationCase, BlockingInterfaceFactory> factoryMap,
      Map<TargetConfigurationCase, GrpcFutureInterfaceFactory> futureFactoryMap) {
    this.factoryMap = factoryMap;
    this.futureFactoryMap = futureFactoryMap;
  }

  @Override
  public CloudFileTransferStubInterface createCloudFileTransferStub(
      StubConfiguration stubConfiguration) {
    return new CloudFileTransferGrpcStub(
        getBlockingInterfaceFactory(stubConfiguration)
            .createCloudFileTransferBlockingInterface(stubConfiguration));
  }

  @Override
  public PrepareTestStub createPrepareTestStub(StubConfiguration stubConfiguration) {
    return new PrepareTestGrpcStub(
        getBlockingInterfaceFactory(stubConfiguration)
            .createPrepareTestBlockingInterface(stubConfiguration));
  }

  @Override
  public ExecTestStub createExecTestStub(StubConfiguration stubConfiguration) {
    return new ExecTestGrpcStub(
        getBlockingInterfaceFactory(stubConfiguration)
            .createExecTestBlockingInterface(stubConfiguration));
  }

  @Override
  public VersionStub createVersionStub(StubConfiguration stubConfiguration) {
    return new VersionGrpcStub(
        getBlockingInterfaceFactory(stubConfiguration)
            .createVersionBlockingInterface(stubConfiguration));
  }

  @Override
  public JobSyncStub createJobSyncStub(StubConfiguration stubConfiguration) {
    return new JobSyncGrpcStub(
        NO_OP_AUTO_CLOSEABLE_INSTANCE,
        getBlockingInterfaceFactory(stubConfiguration)
            .createJobSyncBlockingInterface(stubConfiguration),
        getFutureInterfaceFactory(stubConfiguration)
            .createJobSyncFutureInterface(stubConfiguration));
  }

  @Override
  public com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub
      createSharedLabInfoStub(StubConfiguration stubConfiguration) {
    return new com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub(
        getBlockingInterfaceFactory(stubConfiguration)
            .createSharedLabInfoBlockingInterface(stubConfiguration),
        getFutureInterfaceFactory(stubConfiguration)
            .createSharedLabInfoFutureInterface(stubConfiguration));
  }

  private BlockingInterfaceFactory getBlockingInterfaceFactory(
      StubConfiguration stubConfiguration) {
    checkArgument(
        stubConfiguration.getTransport() == Transport.GRPC, "Only grpc transport is supported.");
    return Optional.ofNullable(factoryMap.get(stubConfiguration.getTargetConfigurationCase()))
        .orElseThrow(() -> StubFactory.createUnsupportedConfigurationException(stubConfiguration));
  }

  private GrpcFutureInterfaceFactory getFutureInterfaceFactory(
      StubConfiguration stubConfiguration) {
    checkArgument(
        stubConfiguration.getTransport() == Transport.GRPC, "Only grpc transport is supported.");
    return Optional.ofNullable(futureFactoryMap.get(stubConfiguration.getTargetConfigurationCase()))
        .orElseThrow(() -> StubFactory.createUnsupportedConfigurationException(stubConfiguration));
  }
}
