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

package com.google.devtools.mobileharness.infra.client.longrunningservice.model;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.concurrent.GuardedBy;

/** Information of a running session. */
public class SessionInfo {

  private final SessionDetailHolder sessionDetailHolder;

  @GuardedBy("itself")
  private final List<JobInfo> jobs;

  public SessionInfo(SessionDetailHolder sessionDetailHolder, List<JobInfo> jobs) {
    this.sessionDetailHolder = sessionDetailHolder;
    this.jobs = new ArrayList<>(jobs);
  }

  public String getSessionId() {
    return sessionDetailHolder.getSessionId();
  }

  public Map<String, String> getAllSessionProperties() {
    return sessionDetailHolder.getAllSessionProperties();
  }

  public Optional<String> getSessionProperty(String key) {
    return sessionDetailHolder.getSessionProperty(key);
  }

  @CanIgnoreReturnValue
  public Optional<String> putSessionProperty(String key, String value) {
    return sessionDetailHolder.putSessionProperty(key, value);
  }

  /**
   * Adds a job to the session.
   *
   * <p>Note that the job will be started only if this method is called before the session runner
   * starts all jobs of it (e.g., in {@code SessionStartingEvent}).
   */
  public void addJob(JobInfo jobInfo) {
    synchronized (jobs) {
      jobs.add(jobInfo);
    }
  }

  public List<JobInfo> getAllJobs() {
    synchronized (jobs) {
      return ImmutableList.copyOf(jobs);
    }
  }
}
