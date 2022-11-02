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

import com.google.wireless.qa.mobileharness.shared.controller.event.ControllerEvent;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.EventInjectionScope;
import com.google.wireless.qa.mobileharness.shared.controller.event.util.InjectionEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import javax.annotation.Nullable;

/** Event that signals the progress of a job. */
public class JobEvent implements ControllerEvent, InjectionEvent {
  private final JobInfo jobInfo;
  private final EventInjectionScope eventInjectionScope;

  @Nullable @Deprecated
  private final com.google.wireless.qa.mobileharness.shared.api.job.JobInfo oldJobInfo;

  public JobEvent(JobInfo jobInfo) {
    this.jobInfo = jobInfo;
    this.oldJobInfo = null;
    this.eventInjectionScope = EventInjectionScope.instance;
  }

  @Deprecated
  public JobEvent(com.google.wireless.qa.mobileharness.shared.api.job.JobInfo oldJobInfo) {
    this.jobInfo = oldJobInfo.toNewJobInfo();
    this.oldJobInfo = oldJobInfo;
    this.eventInjectionScope = EventInjectionScope.instance;
  }

  public JobInfo getJob() {
    return jobInfo;
  }

  @Deprecated
  public com.google.wireless.qa.mobileharness.shared.api.job.JobInfo getJobInfo() {
    if (oldJobInfo != null) {
      return oldJobInfo;
    } else {
      return new com.google.wireless.qa.mobileharness.shared.api.job.JobInfo(jobInfo);
    }
  }

  @Override
  public void enter() {
    eventInjectionScope.put(JobInfo.class, getJob());
    eventInjectionScope.put(
        com.google.wireless.qa.mobileharness.shared.api.job.JobInfo.class, getJobInfo());

    eventInjectionScope.enter();
  }

  @Override
  public void leave() {
    eventInjectionScope.exit();
  }
}
