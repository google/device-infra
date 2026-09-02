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

package com.google.devtools.mobileharness.infra.ats.common.sessionplugin;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.List;
import java.util.Optional;

/** Interface for providing jobs for an ATS session. */
public interface SessionJobProvider {

  /** Creates the setup job. */
  Optional<JobInfo> createSetupJob() throws MobileHarnessException, InterruptedException;

  /** Creates the main Tradefed jobs. */
  ImmutableList<JobInfo> createTradefedJobs(List<String> mctsModules)
      throws MobileHarnessException, InterruptedException;

  /** Creates the main non-Tradefed jobs. */
  ImmutableList<JobInfo> createNonTradefedJobs()
      throws MobileHarnessException, InterruptedException;

  /** Creates the teardown job. */
  Optional<JobInfo> createTeardownJob() throws MobileHarnessException, InterruptedException;
}
