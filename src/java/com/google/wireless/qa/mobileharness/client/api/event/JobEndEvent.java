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

package com.google.wireless.qa.mobileharness.client.api.event;

import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import javax.annotation.Nullable;

/** Event that signals the end of a job. */
public class JobEndEvent extends JobEvent {
  @Nullable private final Throwable jobError;

  public JobEndEvent(JobInfo jobInfo, @Nullable Throwable jobError) {
    super(jobInfo);
    this.jobError = jobError;
  }

  @Deprecated
  public JobEndEvent(
      com.google.wireless.qa.mobileharness.shared.api.job.JobInfo oldJobInfo,
      @Nullable Throwable jobError) {
    super(oldJobInfo);
    this.jobError = jobError;
  }

  public boolean hasJobError() {
    return jobError != null;
  }

  @Nullable
  public Throwable getJobError() {
    return jobError;
  }
}
