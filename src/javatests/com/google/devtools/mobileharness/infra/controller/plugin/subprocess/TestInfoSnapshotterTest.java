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

import com.google.devtools.mobileharness.infra.controller.plugin.proto.DeviceInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.JobInfoSnapshot;
import com.google.devtools.mobileharness.infra.controller.plugin.proto.TestInfoSnapshot;
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
public final class TestInfoSnapshotterTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private JobInfo jobInfo;
  private TestInfo testInfo;
  private String jobFilePath;
  private String testFilePath;

  @Before
  public void setUp() throws Exception {
    jobFilePath = folder.newFile("job_file.txt").getAbsolutePath();
    testFilePath = folder.newFile("test_file.txt").getAbsolutePath();

    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_123", "sample_job"))
            .setType(
                JobType.newBuilder().setDevice("AndroidRealDevice").setDriver("NoOpDriver").build())
            .build();
    jobInfo.params().add("param_key", "param_val");
    jobInfo.properties().add("job_prop_key", "job_prop_val");
    jobInfo.files().add("job_file_tag", jobFilePath);

    testInfo = jobInfo.tests().add("test_456", "sample_test");
    testInfo.properties().add("test_prop_k1", "test_prop_v1");
    testInfo.status().set(TestStatus.RUNNING);
    testInfo.result().set(TestResult.UNKNOWN);
    testInfo.files().add("test_file_tag", testFilePath);
  }

  @Test
  public void snapshotTestInfo_success() {
    TestInfoSnapshot snapshot = TestInfoSnapshotter.snapshotTestInfo(testInfo);

    assertThat(snapshot.getTestId()).isEqualTo("test_456");
    assertThat(snapshot.getTestName()).isEqualTo("sample_test");
    assertThat(snapshot.getPropertiesMap()).containsEntry("test_prop_k1", "test_prop_v1");
    assertThat(snapshot.getStatus()).isEqualTo(TestStatus.RUNNING.name());
    assertThat(snapshot.getResult()).isEqualTo(TestResult.UNKNOWN.name());
    assertThat(snapshot.getInputFilesList()).hasSize(1);
    assertThat(snapshot.getInputFiles(0).getTag()).isEqualTo("test_file_tag");
    assertThat(snapshot.getInputFiles(0).getPath()).isEqualTo(testFilePath);
  }

  @Test
  public void snapshotJobInfo_success() {
    JobInfoSnapshot snapshot = TestInfoSnapshotter.snapshotJobInfo(jobInfo);

    assertThat(snapshot.getJobId()).isEqualTo("job_123");
    assertThat(snapshot.getJobName()).isEqualTo("sample_job");
    assertThat(snapshot.getParamsMap()).containsEntry("param_key", "param_val");
    assertThat(snapshot.getPropertiesMap()).containsEntry("job_prop_key", "job_prop_val");
    assertThat(snapshot.getFilesList()).hasSize(1);
    assertThat(snapshot.getFiles(0).getTag()).isEqualTo("job_file_tag");
    assertThat(snapshot.getFiles(0).getPath()).isEqualTo(jobFilePath);
  }

  @Test
  public void snapshotDeviceInfo_nullDevice() {
    DeviceInfoSnapshot snapshot = TestInfoSnapshotter.snapshotDeviceInfo(null);

    assertThat(snapshot.getDeviceId()).isEmpty();
    assertThat(snapshot.getDimensionsList()).isEmpty();
  }
}
