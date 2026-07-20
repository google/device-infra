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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobLogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobLogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobResponse;

/** No-op implementation of {@link JobServiceLogic} used when no MOSS backend is available. */
public final class NoOpJobServiceLogic implements JobServiceLogic {

  @Override
  public ListenableFuture<GetJobResponse> getJob(GetJobRequest request) {
    return immediateFailedFuture(
        new UnsupportedOperationException("JobService.GetJob is not available."));
  }

  @Override
  public ListenableFuture<GetJobLogResponse> getJobLog(GetJobLogRequest request) {
    return immediateFailedFuture(
        new UnsupportedOperationException("JobService.GetJobLog is not available."));
  }
}
