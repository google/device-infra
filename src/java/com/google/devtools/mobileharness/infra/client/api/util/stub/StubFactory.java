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
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.shared.util.comm.filetransfer.cloud.rpc.stub.CloudFileTransferStubInterface;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.StubConfiguration;
import com.google.devtools.mobileharness.shared.version.rpc.stub.VersionStub;

/** Interface to create service stubs. */
public interface StubFactory {

  CloudFileTransferStubInterface createCloudFileTransferStub(StubConfiguration stubConfiguration);

  ExecTestStub createExecTestStub(StubConfiguration stubConfiguration);

  PrepareTestStub createPrepareTestStub(StubConfiguration stubConfiguration);

  VersionStub createVersionStub(StubConfiguration stubConfiguration);

  JobSyncStub createJobSyncStub(StubConfiguration stubConfiguration);

  com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub createSharedLabInfoStub(
      StubConfiguration stubConfiguration);

  /** Util to create an exception for unsupported stub configuration. */
  static IllegalArgumentException createUnsupportedConfigurationException(
      StubConfiguration stubConfiguration) {
    return new IllegalArgumentException("Unsupported stub configuration: " + stubConfiguration);
  }

  /** Util to validate the target configuration of the given stub configuration. */
  static void validateTargetConfiguration(
      StubConfiguration stubConfiguration,
      StubConfiguration.TargetConfigurationCase targetConfigurationCase) {
    checkArgument(
        stubConfiguration.getTargetConfigurationCase() == targetConfigurationCase,
        "Only %s is supported.",
        targetConfigurationCase.name());
  }
}
