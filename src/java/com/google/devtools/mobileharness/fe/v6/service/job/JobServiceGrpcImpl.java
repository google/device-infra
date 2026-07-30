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

package com.google.devtools.mobileharness.fe.v6.service.job;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcServiceUtil;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobLogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobLogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.JobServiceGrpc;
import io.grpc.stub.StreamObserver;
import javax.inject.Inject;

/** gRPC implementation of the JobService. */
public final class JobServiceGrpcImpl extends JobServiceGrpc.JobServiceImplBase {

  private final JobServiceLogic logic;
  private final ListeningExecutorService executor;

  @Inject
  JobServiceGrpcImpl(JobServiceLogic logic, ListeningExecutorService executor) {
    this.logic = logic;
    this.executor = executor;
  }

  @Override
  public void getJob(GetJobRequest request, StreamObserver<GetJobResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getJob,
        executor,
        JobServiceGrpc.getServiceDescriptor(),
        JobServiceGrpc.getGetJobMethod());
  }

  @Override
  public void getJobLog(
      GetJobLogRequest request, StreamObserver<GetJobLogResponse> responseObserver) {
    GrpcServiceUtil.invokeAsync(
        request,
        responseObserver,
        logic::getJobLog,
        executor,
        JobServiceGrpc.getServiceDescriptor(),
        JobServiceGrpc.getGetJobLogMethod());
  }
}
