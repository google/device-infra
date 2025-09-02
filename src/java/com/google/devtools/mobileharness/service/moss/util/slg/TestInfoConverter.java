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

package com.google.devtools.mobileharness.service.moss.util.slg;

import com.google.devtools.mobileharness.api.model.job.out.JobOutInternalFactory;
import com.google.devtools.mobileharness.api.model.job.out.Warnings;
import com.google.devtools.mobileharness.api.model.proto.Test.TestStatus;
import com.google.devtools.mobileharness.service.moss.proto.Slg.FilesProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.TestExtraInfo;
import com.google.devtools.mobileharness.service.moss.proto.Slg.TestInfoProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.TestScheduleUnitProto;
import com.google.wireless.qa.mobileharness.shared.model.job.JobHelper;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Log;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.RemoteFiles;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Status;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.stream.Collectors;

/**
 * Utility class to convert a {@link TestInfo} to a {@link TestInfoProto} in forward and backward.
 */
public final class TestInfoConverter {

  private TestInfoConverter() {}

  /**
   * Gets a {@link TestInfo} by the given {@link JobInfo} it belongs to, the given {@link TestInfo}
   * it belongs to if it's a sub-test, and the {@link TestInfoProto}.
   */
  public static TestInfo fromProto(
      boolean resumeFiles, JobInfo jobInfo, TestInfo parentTestInfo, TestInfoProto testInfoProto) {
    TestLocator testLocator =
        new TestLocator(
            com.google.devtools.mobileharness.api.model.job.TestLocator.of(
                testInfoProto.getTestScheduleUnit().getTestLocator()));
    Timing timing = TimingConverter.fromProto(testInfoProto.getTestScheduleUnit().getTiming());
    Files files =
        FilesConverter.fromProto(
            timing.toNewTiming(),
            resumeFiles ? testInfoProto.getFiles() : FilesProto.getDefaultInstance());
    RemoteFiles remoteGenFiles =
        RemoteFilesConverter.fromProto(timing, testInfoProto.getRemoteGenFiles());

    TestStatus testStatus = testInfoProto.getStatus();

    // Change RUNNING status to NEW so that the allocator will try to get test allocation again to
    // further resume the test.
    if (testStatus == TestStatus.RUNNING) {
      testStatus = TestStatus.NEW;
    }
    Status status = StatusConverter.fromProto(timing, testStatus);

    Result result = ResultConverter.fromProto(timing, jobInfo.params(), testInfoProto.getResult());
    Log log = new Log(timing);
    Properties properties = PropertiesConverter.fromProto(timing, testInfoProto.getProperties());
    Warnings warnings =
        JobOutInternalFactory.createWarnings(
            log, timing.toNewTiming(), testInfoProto.getErrorList());
    TestInfo testInfo =
        JobInternalFactory.createTestInfo(
            testLocator,
            timing,
            jobInfo,
            parentTestInfo,
            files,
            remoteGenFiles,
            status,
            result,
            log,
            properties,
            warnings);
    if (testInfoProto.getSubTestInfoCount() > 0) {
      JobHelper.addTests(
          testInfo.subTests(),
          testInfoProto.getSubTestInfoList().stream()
              .map(
                  subTestInfoProto ->
                      TestInfoConverter.fromProto(resumeFiles, jobInfo, testInfo, subTestInfoProto))
              .collect(Collectors.toList()));
    }
    return testInfo;
  }

  /** Gets a {@link TestInfoProto} by the given {@link TestInfo}. */
  public static TestInfoProto toProto(TestInfo testInfo) {
    return TestInfoProto.newBuilder()
        .setTestScheduleUnit(
            TestScheduleUnitProto.newBuilder()
                .setTestLocator(testInfo.locator().toNewTestLocator().toProto())
                .setTiming(TimingConverter.toProto(testInfo.timing())))
        .setFiles(FilesConverter.toProto(testInfo.files()))
        .setRemoteGenFiles(RemoteFilesConverter.toProto(testInfo.remoteGenFiles()))
        .setStatus(StatusConverter.toProto(testInfo.status()))
        .setResult(ResultConverter.toProto(testInfo.resultWithCause()))
        .setProperties(PropertiesConverter.toProto(testInfo.properties()))
        .addAllError(testInfo.warnings().getAll())
        .addAllSubTestInfo(
            testInfo.subTests().getAll().values().stream()
                .map(TestInfoConverter::toProto)
                .collect(Collectors.toList()))
        .setExtraInfo(TestExtraInfo.newBuilder().setUser(testInfo.jobInfo().jobUser().getRunAs()))
        .build();
  }
}
