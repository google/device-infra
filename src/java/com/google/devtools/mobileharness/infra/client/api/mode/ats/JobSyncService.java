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

package com.google.devtools.mobileharness.infra.client.api.mode.ats;

import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
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
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class JobSyncService extends JobSyncServiceGrpc.JobSyncServiceImplBase {

  @Inject
  JobSyncService() {}

  @Override
  public void openJob(OpenJobRequest request, StreamObserver<OpenJobResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doOpenJob,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getOpenJobMethod());
  }

  private OpenJobResponse doOpenJob(OpenJobRequest request) {
    return OpenJobResponse.getDefaultInstance();
  }

  @Override
  public void addExtraTests(
      AddExtraTestsRequest request, StreamObserver<AddExtraTestsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doAddExtraTests,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getAddExtraTestsMethod());
  }

  private AddExtraTestsResponse doAddExtraTests(AddExtraTestsRequest request) {
    return AddExtraTestsResponse.getDefaultInstance();
  }

  @Override
  public void getAllocations(
      GetAllocationsRequest request, StreamObserver<GetAllocationsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doGetAllocations,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getGetAllocationsMethod());
  }

  private GetAllocationsResponse doGetAllocations(GetAllocationsRequest request) {
    return GetAllocationsResponse.getDefaultInstance();
  }

  @Override
  public void closeTest(
      CloseTestRequest request, StreamObserver<CloseTestResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doCloseTest,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getCloseTestMethod());
  }

  private CloseTestResponse doCloseTest(CloseTestRequest request) {
    return CloseTestResponse.getDefaultInstance();
  }

  @Override
  public void closeJob(CloseJobRequest request, StreamObserver<CloseJobResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doCloseJob,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getCloseJobMethod());
  }

  private CloseJobResponse doCloseJob(CloseJobRequest request) {
    return CloseJobResponse.getDefaultInstance();
  }

  @Override
  public void checkJobs(
      CheckJobsRequest request, StreamObserver<CheckJobsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doCheckJobs,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getCheckJobsMethod());
  }

  private CheckJobsResponse doCheckJobs(CheckJobsRequest request) {
    return CheckJobsResponse.getDefaultInstance();
  }

  @Override
  public void upsertDeviceTempRequiredDimensions(
      UpsertDeviceTempRequiredDimensionsRequest request,
      StreamObserver<UpsertDeviceTempRequiredDimensionsResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doUpsertDeviceTempRequiredDimensions,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getUpsertDeviceTempRequiredDimensionsMethod());
  }

  private UpsertDeviceTempRequiredDimensionsResponse doUpsertDeviceTempRequiredDimensions(
      UpsertDeviceTempRequiredDimensionsRequest request) {
    return UpsertDeviceTempRequiredDimensionsResponse.getDefaultInstance();
  }

  @Override
  public void killJob(KillJobRequest request, StreamObserver<KillJobResponse> responseObserver) {
    GrpcServiceUtil.invoke(
        request,
        responseObserver,
        this::doKillJob,
        JobSyncServiceGrpc.getServiceDescriptor(),
        JobSyncServiceGrpc.getKillJobMethod());
  }

  private KillJobResponse doKillJob(KillJobRequest request) {
    return KillJobResponse.getDefaultInstance();
  }
}
