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

package com.google.devtools.mobileharness.shared.version.rpc.stub.grpc;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceGrpc;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionRequest;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.shared.version.rpc.stub.VersionStub;
import io.grpc.Channel;

/** GRPC stub for talking to VersionService. */
public class VersionGrpcStub implements VersionStub {

  private final VersionServiceGrpc.VersionServiceBlockingStub stub;

  public VersionGrpcStub(Channel channel) {
    this.stub = VersionServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public GetVersionResponse getVersion(GetVersionRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        stub::getVersion,
        request,
        BasicErrorId.VERSION_STUB_GET_VERSION_ERROR,
        "Failed to get version");
  }
}
