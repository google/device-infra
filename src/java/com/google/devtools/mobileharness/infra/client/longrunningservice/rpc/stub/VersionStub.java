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

package com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.stub;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceGrpc.VersionServiceBlockingStub;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.Channel;

/** Stub of {@link VersionServiceGrpc}. */
public class VersionStub {

  private final VersionServiceBlockingStub versionServiceStub;

  public VersionStub(Channel channel) {
    this.versionServiceStub = VersionServiceGrpc.newBlockingStub(channel);
  }

  @CanIgnoreReturnValue
  public GetVersionResponse getVersion() throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        versionServiceStub::getVersion,
        GetVersionRequest.getDefaultInstance(),
        InfraErrorId.OLCS_STUB_GET_SERVER_VERSION_ERROR,
        "Failed to get server version");
  }
}
