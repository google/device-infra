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

package com.google.devtools.mobileharness.infra.client.api.controller.allocation.diagnostic.singledevice;

import com.google.wireless.qa.mobileharness.shared.model.job.JobScheduleUnit;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.lab.DeviceInfo;
import java.util.List;

/** Assessor for providing detail assessment of the support of a group of devices for a job. */
public class SingleDeviceAssessor {
  /** Assesses the support of the given job with the given device. */
  public SingleDeviceAssessment assess(JobScheduleUnit job, DeviceInfo device) {
    return new SingleDeviceAssessment(job).addResource(device);
  }

  /** Assesses the support of the given job with the given device. */
  public SingleDeviceAssessment assess(JobScheduleUnit job, List<DeviceInfo> devices) {
    SingleDeviceAssessment assessment = new SingleDeviceAssessment(job);
    devices.forEach(assessment::addResource);
    return assessment;
  }

  public SingleDeviceAssessment assess(JobScheduleUnit job, SubDeviceSpec spec, DeviceInfo device) {
    return new SingleDeviceAssessment(job, spec).addResource(device);
  }

  public SingleDeviceAssessment assess(
      JobScheduleUnit job, SubDeviceSpec spec, List<DeviceInfo> devices) {
    SingleDeviceAssessment assessment = new SingleDeviceAssessment(job, spec);
    devices.forEach(assessment::addResource);
    return assessment;
  }
}
