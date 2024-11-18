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
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabSyncGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.StubConfiguration;
import com.google.devtools.mobileharness.shared.version.rpc.stub.grpc.VersionGrpcStub;

/** Interface to create blocking interfaces for different services. */
interface BlockingInterfaceFactory {
  CloudFileTransferGrpcStub.BlockingInterface createCloudFileTransferBlockingInterface(
      StubConfiguration stubConfiguration);

  ExecTestGrpcStub.BlockingInterface createExecTestBlockingInterface(
      StubConfiguration stubConfiguration);

  PrepareTestGrpcStub.BlockingInterface createPrepareTestBlockingInterface(
      StubConfiguration stubConfiguration);

  VersionGrpcStub.BlockingInterface createVersionBlockingInterface(
      StubConfiguration stubConfiguration);

  JobSyncGrpcStub.BlockingInterface createJobSyncBlockingInterface(
      StubConfiguration stubConfiguration);

  LabSyncGrpcStub.BlockingInterface createLabSyncBlockingInterface(
      StubConfiguration stubConfiguration);
}
