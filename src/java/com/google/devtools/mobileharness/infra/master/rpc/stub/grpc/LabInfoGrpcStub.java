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

package com.google.devtools.mobileharness.infra.master.rpc.stub.grpc;

import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.comm.stub.Stubs.withDeadline;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceGrpc;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import io.grpc.ClientInterceptors;
import io.grpc.Deadline;
import java.time.Duration;
import javax.inject.Inject;

/** gRPC stub of {@link LabInfoStub}. */
public class LabInfoGrpcStub implements LabInfoStub {

  private final LabInfoServiceGrpc.LabInfoServiceBlockingStub labInfoServiceBlockingStub;
  private final LabInfoServiceGrpc.LabInfoServiceFutureStub labInfoServiceFutureStub;

  @Inject
  LabInfoGrpcStub(MasterGrpcStubHelper masterGrpcStubHelper) {
    this.labInfoServiceBlockingStub =
        LabInfoServiceGrpc.newBlockingStub(
            ClientInterceptors.intercept(
                masterGrpcStubHelper.getChannel(), masterGrpcStubHelper.getInterceptors()));
    this.labInfoServiceFutureStub =
        LabInfoServiceGrpc.newFutureStub(
            ClientInterceptors.intercept(
                masterGrpcStubHelper.getChannel(), masterGrpcStubHelper.getInterceptors()));
  }

  @Override
  public GetLabInfoResponse getLabInfo(GetLabInfoRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        withDeadline(labInfoServiceBlockingStub, Duration.ofSeconds(30L))::getLabInfo,
        request,
        InfraErrorId.MASTER_RPC_STUB_LAB_INFO_GET_LAB_INFO_ERROR,
        String.format("Failed to get lab info, request=%s", shortDebugString(request)));
  }

  @Override
  public ListenableFuture<GetLabInfoResponse> getLabInfoAsync(GetLabInfoRequest request) {
    return GrpcStubUtil.invokeAsync(
        labInfoServiceFutureStub.withDeadline(Deadline.after(30, SECONDS))::getLabInfo,
        request,
        InfraErrorId.MASTER_RPC_STUB_LAB_INFO_GET_LAB_INFO_ERROR,
        String.format("Failed to get lab info, request=%s", shortDebugString(request)));
  }
}
