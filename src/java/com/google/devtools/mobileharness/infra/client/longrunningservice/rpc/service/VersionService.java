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

package com.google.devtools.mobileharness.infra.client.longrunningservice.rpc.service;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceGrpc;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionRequest;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.shared.version.Version;
import io.grpc.stub.StreamObserver;

/** Implementation of {@link VersionServiceGrpc}. */
public class VersionService extends VersionServiceGrpc.VersionServiceImplBase {

  @Override
  public void getVersion(
      GetVersionRequest request, StreamObserver<GetVersionResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::getVersion,
        VersionServiceGrpc.getServiceDescriptor(),
        VersionServiceGrpc.getGetVersionMethod());
  }

  private GetVersionResponse getVersion(GetVersionRequest request) {
    return GetVersionResponse.newBuilder().setLabVersion(Version.LAB_VERSION.toString()).build();
  }
}
