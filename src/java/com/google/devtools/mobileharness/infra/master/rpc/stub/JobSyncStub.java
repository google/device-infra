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

package com.google.devtools.mobileharness.infra.master.rpc.stub;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.common.metrics.stability.rpc.RpcExceptionWithErrorId;
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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import javax.annotation.Nullable;

/** RPC stub for talking to Master JobSyncService. */
public interface JobSyncStub extends AutoCloseable {

  /** Sends the job information to master server. */
  OpenJobResponse openJob(OpenJobRequest request) throws RpcExceptionWithErrorId;

  /** Sends the job information to master server with the impersonation user. */
  default OpenJobResponse openJob(OpenJobRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return openJob(request);
  }

  /** Adds tests to an existing job. */
  AddExtraTestsResponse addExtraTests(AddExtraTestsRequest request) throws RpcExceptionWithErrorId;

  /** Adds tests to an existing job with the impersonation user. */
  @CanIgnoreReturnValue
  default AddExtraTestsResponse addExtraTests(
      AddExtraTestsRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return addExtraTests(request);
  }

  /** Gets the allocation info. */
  GetAllocationsResponse getAllocations(GetAllocationsRequest request)
      throws RpcExceptionWithErrorId;

  /** Gets the allocation info with the impersonation user. */
  default GetAllocationsResponse getAllocations(
      GetAllocationsRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return getAllocations(request);
  }

  /** Closes the test and releases the device. */
  ListenableFuture<CloseTestResponse> closeTest(CloseTestRequest request);

  /** Closes the test and releases the device with the impersonation user. */
  default ListenableFuture<CloseTestResponse> closeTest(
      CloseTestRequest request, @Nullable String impersonationUser) {
    return closeTest(request);
  }

  /** Closes the job and releases the devices. */
  CloseJobResponse closeJob(CloseJobRequest request) throws RpcExceptionWithErrorId;

  /** Closes the job and releases the devices with the impersonation user. */
  @CanIgnoreReturnValue
  default CloseJobResponse closeJob(CloseJobRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return closeJob(request);
  }

  /** Checks whether the jobs are alive. */
  CheckJobsResponse checkJobs(CheckJobsRequest request) throws RpcExceptionWithErrorId;

  /** Checks whether the jobs are alive with the impersonation user. */
  default CheckJobsResponse checkJobs(CheckJobsRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return checkJobs(request);
  }

  /** Upserts temp required dimensions into a device. */
  @CanIgnoreReturnValue
  UpsertDeviceTempRequiredDimensionsResponse upsertDeviceTempRequiredDimensions(
      UpsertDeviceTempRequiredDimensionsRequest request) throws RpcExceptionWithErrorId;

  /** Upserts temp required dimensions into a device with the impersonation user. */
  default UpsertDeviceTempRequiredDimensionsResponse upsertDeviceTempRequiredDimensions(
      UpsertDeviceTempRequiredDimensionsRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return upsertDeviceTempRequiredDimensions(request);
  }

  /** Kill a job if it exists and is running. */
  KillJobResponse killJob(KillJobRequest request) throws RpcExceptionWithErrorId;

  /** Kill a job if it exists and is running with the impersonation user. */
  default KillJobResponse killJob(KillJobRequest request, @Nullable String impersonationUser)
      throws RpcExceptionWithErrorId {
    return killJob(request);
  }
}
