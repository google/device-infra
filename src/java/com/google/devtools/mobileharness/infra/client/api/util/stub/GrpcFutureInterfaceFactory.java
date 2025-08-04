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

import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.JobSyncGrpcStub;
import com.google.devtools.mobileharness.infra.master.rpc.stub.grpc.LabSyncGrpcStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.StubConfigurationProto.StubConfiguration;

/** Interface to create future interface for different services. */
interface GrpcFutureInterfaceFactory {

  JobSyncGrpcStub.FutureInterface createJobSyncFutureInterface(StubConfiguration stubConfiguration);

  LabSyncGrpcStub.FutureInterface createLabSyncFutureInterface(StubConfiguration stubConfiguration);
}
