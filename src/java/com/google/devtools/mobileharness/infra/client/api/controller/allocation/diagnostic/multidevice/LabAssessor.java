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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.multidevice;

import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier.LabQueryResult;
import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;

/** Generates {@link LabAssessment}s for a set of requirements and lab details. */
public class LabAssessor {

  /** Returns a {@link LabAssessment} for the given job and lab results. */
  public LabAssessment assess(JobScheduleUnit job, LabQueryResult labResult) {
    return new LabAssessment(job).addResource(labResult);
  }
}
