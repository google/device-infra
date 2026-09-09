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

package com.google.devtools.mobileharness.infra.controller.plugin.subprocess;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.DeviceInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.DimensionEntry;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.FileEntry;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.JobInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestInfoSnapshot;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Common.StrPair;
import java.util.Map;
import javax.annotation.Nullable;

/** Serializes Mobile Harness data models to Protobuf snapshots for the subprocess worker. */
public final class TestInfoSnapshotter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Serializes a {@link TestInfo} instance to {@link TestInfoSnapshot}. */
  public static TestInfoSnapshot snapshotTestInfo(TestInfo testInfo) {
    TestInfoSnapshot.Builder builder =
        TestInfoSnapshot.newBuilder()
            .setTestId(testInfo.locator().getId())
            .setTestName(testInfo.locator().getName())
            .putAllProperties(testInfo.properties().getAll())
            .setStatus(testInfo.status().get().name())
            .setResult(testInfo.result().get().name());

    for (Map.Entry<String, String> entry : testInfo.files().getAll().entries()) {
      builder.addInputFiles(
          FileEntry.newBuilder().setTag(entry.getKey()).setPath(entry.getValue()).build());
    }

    try {
      builder.setGenFileDir(testInfo.getGenFileDir());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to get gen file dir for test %s", testInfo.locator().getId());
    }

    try {
      builder.setTmpFileDir(testInfo.getTmpFileDir());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to get tmp file dir for test %s", testInfo.locator().getId());
    }

    return builder.build();
  }

  /** Serializes a {@link JobInfo} instance to {@link JobInfoSnapshot}. */
  public static JobInfoSnapshot snapshotJobInfo(JobInfo jobInfo) {
    JobInfoSnapshot.Builder builder =
        JobInfoSnapshot.newBuilder()
            .setJobId(jobInfo.locator().getId())
            .setJobName(jobInfo.locator().getName())
            .setDeviceType(jobInfo.type().getDevice())
            .setDriverType(jobInfo.type().getDriver())
            .addAllDecoratorTypes(jobInfo.type().getDecoratorList())
            .putAllParams(jobInfo.params().getAll())
            .putAllProperties(jobInfo.properties().getAll());

    for (Map.Entry<String, String> entry : jobInfo.files().getAll().entries()) {
      builder.addFiles(
          FileEntry.newBuilder().setTag(entry.getKey()).setPath(entry.getValue()).build());
    }

    return builder.build();
  }

  /** Serializes a {@link Device} instance to {@link DeviceInfoSnapshot}. */
  public static DeviceInfoSnapshot snapshotDeviceInfo(@Nullable Device device) {
    DeviceInfoSnapshot.Builder builder = DeviceInfoSnapshot.newBuilder();
    if (device != null) {
      if (device.getDeviceId() != null) {
        builder.setDeviceId(device.getDeviceId());
      }
      if (device.getDeviceControlId() != null) {
        builder.setDeviceControlId(device.getDeviceControlId());
      }
      if (device.getProperties() != null) {
        builder.putAllProperties(device.getProperties());
      }
      if (device.getDriverTypes() != null) {
        builder.addAllSupportedDrivers(device.getDriverTypes());
      }
      if (device.getDecoratorTypes() != null) {
        builder.addAllSupportedDecorators(device.getDecoratorTypes());
      }
      if (device.getDeviceTypes() != null) {
        builder.addAllSupportedDeviceTypes(device.getDeviceTypes());
      }

      if (device.getDimensions() != null) {
        for (StrPair dimension : device.getDimensions()) {
          if (dimension != null) {
            builder.addDimensions(
                DimensionEntry.newBuilder()
                    .setName(dimension.getName())
                    .setValue(dimension.getValue())
                    .build());
          }
        }
      }
    }
    return builder.build();
  }

  private TestInfoSnapshotter() {}
}
