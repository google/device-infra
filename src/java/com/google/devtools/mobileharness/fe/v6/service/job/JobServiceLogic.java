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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobLogRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobLogResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.job.GetJobResponse;

/** Logic interface for the Job Detail service. */
public interface JobServiceLogic {

  /**
   * Gets the full job detail (overview, execution details, config, troubleshooting, child tests).
   */
  ListenableFuture<GetJobResponse> getJob(GetJobRequest request);

  /** Gets a paginated chunk of the job log. */
  ListenableFuture<GetJobLogResponse> getJobLog(GetJobLogRequest request);
}
