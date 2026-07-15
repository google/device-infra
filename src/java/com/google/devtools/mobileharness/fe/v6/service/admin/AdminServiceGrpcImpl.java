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

package com.google.devtools.mobileharness.fe.v6.service.admin;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.fe.v6.service.grpc.FeGrpcInvoker;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.AdminServiceGrpc;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.ListWifiCredentialsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.ListWifiCredentialsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.SetWifiCredentialsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.admin.SetWifiCredentialsResponse;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** Implementation of the gRPC AdminService. */
public final class AdminServiceGrpcImpl extends AdminServiceGrpc.AdminServiceImplBase {

  private final AdminServiceLogic logic;
  private final ListeningExecutorService executor;

  @Inject
  AdminServiceGrpcImpl(AdminServiceLogic logic, ListeningExecutorService executor) {
    this.logic = logic;
    this.executor = executor;
  }

  @Override
  public void setWifiCredentials(
      SetWifiCredentialsRequest request,
      StreamObserver<SetWifiCredentialsResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::setWifiCredentials,
        executor,
        AdminServiceGrpc.getServiceDescriptor(),
        AdminServiceGrpc.getSetWifiCredentialsMethod());
  }

  @Override
  public void listWifiCredentials(
      ListWifiCredentialsRequest request,
      StreamObserver<ListWifiCredentialsResponse> responseObserver) {
    FeGrpcInvoker.invokeAsync(
        request,
        responseObserver,
        logic::listWifiCredentials,
        executor,
        AdminServiceGrpc.getServiceDescriptor(),
        AdminServiceGrpc.getListWifiCredentialsMethod());
  }
}
