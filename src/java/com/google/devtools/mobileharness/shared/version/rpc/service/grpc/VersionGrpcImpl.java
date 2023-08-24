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

package com.google.devtools.mobileharness.shared.version.rpc.service.grpc;

import com.google.devtools.mobileharness.shared.version.proto.VersionServiceGrpc.VersionServiceImplBase;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionRequest;
import com.google.devtools.mobileharness.shared.version.proto.VersionServiceProto.GetVersionResponse;
import com.google.devtools.mobileharness.shared.version.rpc.service.VersionServiceImpl;
import io.grpc.stub.StreamObserver;

/** gRPC service implementation of {@code VersionService}. */
public class VersionGrpcImpl extends VersionServiceImplBase {

  private final VersionServiceImpl versionService;

  public VersionGrpcImpl(VersionServiceImpl service) {
    this.versionService = service;
  }

  @Override
  public void getVersion(
      GetVersionRequest request, StreamObserver<GetVersionResponse> responseObserver) {
    GetVersionResponse response = versionService.getVersion(request);
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
