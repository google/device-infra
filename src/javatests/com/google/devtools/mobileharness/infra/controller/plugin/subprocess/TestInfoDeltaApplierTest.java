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

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.controller.plugin.proto.ExceptionDetail;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.FileEntry;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.RunTestEventResponse;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TestInfoDeltaApplierTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private TestInfo testInfo;
  private String outputFilePath;

  @Before
  public void setUp() throws Exception {
    outputFilePath = folder.newFile("out.txt").getAbsolutePath();
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_123", "sample_job"))
            .setType(
                JobType.newBuilder().setDevice("AndroidRealDevice").setDriver("NoOpDriver").build())
            .build();
    testInfo = jobInfo.tests().add("test_456", "sample_test");
    testInfo.properties().add("existing_key", "existing_val");
    testInfo.status().set(TestStatus.RUNNING);
    testInfo.result().set(TestResult.UNKNOWN);
  }

  @Test
  public void applyDelta_updatesPropertiesAndStatusAndResult() throws Exception {
    RunTestEventResponse response =
        RunTestEventResponse.newBuilder()
            .putAddedProperties("new_prop_k", "new_prop_v")
            .addRemovedPropertyKeys("existing_key")
            .setOverrideStatus(TestStatus.DONE.name())
            .setOverrideResult(TestResult.FAIL.name())
            .addAddedFiles(FileEntry.newBuilder().setTag("output_tag").setPath(outputFilePath))
            .addLogRecords("Worker log message 1")
            .addWarnings(
                ExceptionDetail.newBuilder()
                    .setExceptionClass("java.lang.IllegalArgumentException")
                    .setMessage("Bad argument")
                    .setStackTrace("trace..."))
            .build();

    TestInfoDeltaApplier.applyDelta(testInfo, response);

    assertThat(testInfo.properties().getAll()).containsEntry("new_prop_k", "new_prop_v");
    assertThat(testInfo.properties().has("existing_key")).isFalse();
    assertThat(testInfo.status().get()).isEqualTo(TestStatus.DONE);
    assertThat(testInfo.result().get()).isEqualTo(TestResult.FAIL);
    assertThat(testInfo.files().get("output_tag")).contains(outputFilePath);
    assertThat(testInfo.warnings().getAll()).isNotEmpty();
  }
}
