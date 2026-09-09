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

/*
 * Copyright 2026 Google LLC
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

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess.worker;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.DeviceInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.DimensionEntry;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.FileEntry;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.JobInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestInfoSnapshot;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import java.util.Map;

/** Reconstructs local Mobile Harness in-memory models inside the worker subprocess. */
public final class LocalModelFactory {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Creates a local {@link JobInfo} instance populated from {@link JobInfoSnapshot}. */
  public static JobInfo createJobInfo(JobInfoSnapshot snapshot) {
    JobType.Builder jobTypeBuilder =
        JobType.newBuilder()
            .setDevice(
                snapshot.getDeviceType().isEmpty() ? "AndroidRealDevice" : snapshot.getDeviceType())
            .setDriver(
                snapshot.getDriverType().isEmpty() ? "NoOpDriver" : snapshot.getDriverType());
    for (String decorator : snapshot.getDecoratorTypesList()) {
      jobTypeBuilder.addDecorator(decorator);
    }
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator(snapshot.getJobId(), snapshot.getJobName()))
            .setType(jobTypeBuilder.build())
            .build();

    for (Map.Entry<String, String> entry : snapshot.getParamsMap().entrySet()) {
      jobInfo.params().add(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, String> entry : snapshot.getPropertiesMap().entrySet()) {
      jobInfo.properties().add(entry.getKey(), entry.getValue());
    }
    for (FileEntry fileEntry : snapshot.getFilesList()) {
      try {
        jobInfo.files().add(fileEntry.getTag(), fileEntry.getPath());
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to add job file tag %s -> %s", fileEntry.getTag(), fileEntry.getPath());
      }
    }
    return jobInfo;
  }

  /** Creates a local {@link TestInfo} instance populated from {@link TestInfoSnapshot}. */
  public static TestInfo createTestInfo(JobInfo jobInfo, TestInfoSnapshot snapshot)
      throws MobileHarnessException {
    TestInfo testInfo = jobInfo.tests().add(snapshot.getTestId(), snapshot.getTestName());

    for (Map.Entry<String, String> entry : snapshot.getPropertiesMap().entrySet()) {
      testInfo.properties().add(entry.getKey(), entry.getValue());
    }

    if (!snapshot.getStatus().isEmpty()) {
      try {
        testInfo.status().set(TestStatus.valueOf(snapshot.getStatus()));
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Unknown test status in snapshot: %s", snapshot.getStatus());
      }
    }

    if (!snapshot.getResult().isEmpty()) {
      try {
        testInfo.result().set(TestResult.valueOf(snapshot.getResult()));
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).log(
            "Unknown test result in snapshot: %s", snapshot.getResult());
      }
    }

    for (FileEntry fileEntry : snapshot.getInputFilesList()) {
      try {
        testInfo.files().add(fileEntry.getTag(), fileEntry.getPath());
      } catch (MobileHarnessException e) {
        logger.atWarning().withCause(e).log(
            "Failed to add test file tag %s -> %s", fileEntry.getTag(), fileEntry.getPath());
      }
    }

    return testInfo;
  }

  /** Creates a local mock/proxy {@link Device} populated from {@link DeviceInfoSnapshot}. */
  public static Device createDevice(DeviceInfoSnapshot snapshot) {
    String deviceId = snapshot.getDeviceId().isEmpty() ? "dummy_device_id" : snapshot.getDeviceId();
    NoOpDevice device = new NoOpDevice(deviceId);

    for (DimensionEntry dimension : snapshot.getDimensionsList()) {
      device.addDimension(dimension.getName(), dimension.getValue());
    }
    for (Map.Entry<String, String> property : snapshot.getPropertiesMap().entrySet()) {
      device.setProperty(property.getKey(), property.getValue());
    }
    return device;
  }

  private LocalModelFactory() {}
}
