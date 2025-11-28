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

import com.google.devtools.mobileharness.infra.lab.rpc.stub.grpc.ExecTestGrpcStub;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.grpc.PrepareTestGrpcStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.JobSyncGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.GrpcDirectTargetConfigures;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.StubConfiguration;
import com.google.devtools.mobileharness.shared.version.rpc.stub.grpc.VersionGrpcStub;
import javax.inject.Inject;

/** Creates interfaces for direct targets. */
final class GrpcDirectTargetFactory
    implements BlockingInterfaceFactory, GrpcFutureInterfaceFactory {

  private final GrpcDirectTargetConfigures directTargetConfigures;

  @Inject
  GrpcDirectTargetFactory(GrpcDirectTargetConfigures directTargetConfigures) {
    this.directTargetConfigures = directTargetConfigures;
  }

  @Override
  public CloudFileTransferGrpcStub.BlockingInterface createCloudFileTransferBlockingInterface(
      StubConfiguration stubConfiguration) {
    return directTargetConfigures.createStubInterface(
        CloudFileTransferGrpcStub::newBlockingInterface, stubConfiguration);
  }

  @Override
  public ExecTestGrpcStub.BlockingInterface createExecTestBlockingInterface(
      StubConfiguration stubConfiguration) {
    return directTargetConfigures.createStubInterface(
        ExecTestGrpcStub::newBlockingInterface, stubConfiguration);
  }

  @Override
  public PrepareTestGrpcStub.BlockingInterface createPrepareTestBlockingInterface(
      StubConfiguration stubConfiguration) {
    return directTargetConfigures.createStubInterface(
        PrepareTestGrpcStub::newBlockingInterface, stubConfiguration);
  }

  @Override
  public VersionGrpcStub.BlockingInterface createVersionBlockingInterface(
      StubConfiguration stubConfiguration) {
    return directTargetConfigures.createStubInterface(
        VersionGrpcStub::newBlockingInterface, stubConfiguration);
  }

  @Override
  public JobSyncGrpcStub.BlockingInterface createJobSyncBlockingInterface(
      StubConfiguration stubConfiguration) {
    return directTargetConfigures.createStubInterface(
        JobSyncGrpcStub::newBlockingInterface, stubConfiguration);
  }

  @Override
  public com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub
          .BlockingInterface
      createSharedLabInfoBlockingInterface(StubConfiguration stubConfiguration) {
    return directTargetConfigures.createStubInterface(
        com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub
            ::newBlockingInterface,
        stubConfiguration);
  }

  @Override
  public JobSyncGrpcStub.FutureInterface createJobSyncFutureInterface(
      StubConfiguration stubConfiguration) {
    return directTargetConfigures.createStubInterface(
        JobSyncGrpcStub::newFutureInterface, stubConfiguration);
  }

  @Override
  public com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub
          .FutureInterface
      createSharedLabInfoFutureInterface(StubConfiguration stubConfiguration) {
    return directTargetConfigures.createStubInterface(
        com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabInfoGrpcStub
            ::newFutureInterface,
        stubConfiguration);
  }
}
