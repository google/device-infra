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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceGrpc;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.AddExtraTestsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.AddExtraTestsResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CheckJobsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CheckJobsResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CloseJobRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CloseJobResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CloseTestRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.CloseTestResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.GetAllocationsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.GetAllocationsResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.KillJobRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.KillJobResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.OpenJobRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.OpenJobResponse;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.UpsertDeviceTempRequiredDimensionsRequest;
import com.google.devtools.mobileharness.infra.master.rpc.proto.JobSyncServiceProto.UpsertDeviceTempRequiredDimensionsResponse;
import com.google.devtools.mobileharness.infra.master.rpc.stub.JobSyncStub;
import com.google.devtools.mobileharness.shared.util.comm.stub.MasterGrpcStubHelper;
import io.grpc.ClientInterceptors;
import javax.inject.Inject;

/** GRPC stub class for talking to Master JobSyncService via OnePlatform API. */
public class JobSyncGrpcStub implements JobSyncStub {

  private final MasterGrpcStubHelper helper;
  private final JobSyncServiceGrpc.JobSyncServiceBlockingStub jobSyncGrpcBlockingStub;
  private final JobSyncServiceGrpc.JobSyncServiceFutureStub jobSyncGrpcFutureStub;

  @Inject
  public JobSyncGrpcStub(MasterGrpcStubHelper helper) {
    this.helper = helper;
    this.jobSyncGrpcBlockingStub =
        JobSyncServiceGrpc.newBlockingStub(
            ClientInterceptors.intercept(helper.getChannel(), helper.getInterceptors()));
    this.jobSyncGrpcFutureStub =
        JobSyncServiceGrpc.newFutureStub(
            ClientInterceptors.intercept(helper.getChannel(), helper.getInterceptors()));
  }

  @Override
  public OpenJobResponse openJob(OpenJobRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        jobSyncGrpcBlockingStub::openJob,
        request,
        InfraErrorId.MASTER_RPC_STUB_JOB_SYNC_OPEN_JOB_ERROR,
        String.format("Failed to open job %s", request.getId()));
  }

  @Override
  public AddExtraTestsResponse addExtraTests(AddExtraTestsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        jobSyncGrpcBlockingStub::addExtraTests,
        request,
        InfraErrorId.MASTER_RPC_STUB_JOB_SYNC_ADD_TEST_ERROR,
        String.format("Failed to add test to job %s", request.getJobId()));
  }

  @Override
  public GetAllocationsResponse getAllocations(GetAllocationsRequest request)
      throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        jobSyncGrpcBlockingStub::getAllocations,
        request,
        InfraErrorId.MASTER_RPC_STUB_JOB_SYNC_GET_ALLOC_ERROR,
        String.format("Failed to get allocations of job %s", request.getJobId()));
  }

  @Override
  public ListenableFuture<CloseTestResponse> closeTest(CloseTestRequest request) {
    return jobSyncGrpcFutureStub.closeTest(request);
  }

  @Override
  public CloseJobResponse closeJob(CloseJobRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        jobSyncGrpcBlockingStub::closeJob,
        request,
        InfraErrorId.MASTER_RPC_STUB_JOB_SYNC_CLOSE_JOB_ERROR,
        String.format("Failed to close job %s", request.getJobId()));
  }

  @Override
  public CheckJobsResponse checkJobs(CheckJobsRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        jobSyncGrpcBlockingStub::checkJobs,
        request,
        InfraErrorId.MASTER_RPC_STUB_JOB_SYNC_CHECK_JOB_ERROR,
        String.format("Failed to check job %s", request.getJobIdList()));
  }

  @Override
  public UpsertDeviceTempRequiredDimensionsResponse upsertDeviceTempRequiredDimensions(
      UpsertDeviceTempRequiredDimensionsRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        jobSyncGrpcBlockingStub::upsertDeviceTempRequiredDimensions,
        request,
        InfraErrorId.MASTER_RPC_STUB_JOB_SYNC_UPSERT_TEMP_REQUIRED_DIMENSIONS_ERROR,
        String.format(
            "Failed upsert temp required dimensions of device %s in lab %s",
            request.getDeviceLocator().getId(),
            request.getDeviceLocator().getLabLocator().getHostName()));
  }

  @Override
  public KillJobResponse killJob(KillJobRequest request) throws GrpcExceptionWithErrorId {
    return GrpcStubUtil.invoke(
        jobSyncGrpcBlockingStub::killJob,
        request,
        InfraErrorId.MASTER_RPC_STUB_JOB_SYNC_KILL_JOB_ERROR,
        String.format("Failed to kill job %s", request.getJobId()));
  }

  @Override
  public void close() {
    helper.closeChannel();
  }
}
