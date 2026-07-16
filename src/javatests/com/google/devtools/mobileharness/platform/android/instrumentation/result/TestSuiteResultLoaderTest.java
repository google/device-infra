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

package com.google.devtools.mobileharness.platform.android.instrumentation.result;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestStatus;
import com.google.devtools.mobileharness.platform.android.instrumentation.result.proto.TestSuiteResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit test for {@link TestSuiteResultLoader}. */
@RunWith(JUnit4.class)
public final class TestSuiteResultLoaderTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private LocalFileUtil realLocalFileUtil;
  private TestInfo testInfo;
  private TestSuiteResultLoader loader;

  @Before
  public void setUp() throws Exception {
    realLocalFileUtil = new LocalFileUtil();
    JobSetting jobSetting =
        JobSetting.newBuilder().setGenFileDir(tempFolder.getRoot().getAbsolutePath()).build();
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(JobType.newBuilder().setDriver("AndroidInstrumentation").build())
            .setSetting(jobSetting)
            .build();
    testInfo = jobInfo.tests().add("test_name");
    loader = new TestSuiteResultLoader(realLocalFileUtil);
  }

  @Test
  public void loadTestResult_resultFileDoesNotExist_returnsEmpty() throws Exception {
    Optional<TestSuiteResult> result = loader.loadTestResult(testInfo);

    assertThat(result).isEmpty();
  }

  @Test
  public void loadTestResult_resultFileExists_returnsParsedResult() throws Exception {
    String genFileDir = testInfo.getGenFileDir();
    TestSuiteResult testSuiteResult =
        TestSuiteResult.newBuilder().setTestStatus(TestStatus.PASSED).build();
    byte[] testSuiteResultBytes = testSuiteResult.toByteArray();

    String testSuiteResultPbPath = PathUtil.join(genFileDir, "instrument_test_result.pb");
    realLocalFileUtil.writeToFile(testSuiteResultPbPath, testSuiteResultBytes);

    Optional<TestSuiteResult> result = loader.loadTestResult(testInfo);

    assertThat(result).isPresent();
    assertThat(result.get().getTestStatus()).isEqualTo(TestStatus.PASSED);
  }

  @Test
  public void loadTestResult_invalidProtoBytes_returnsEmpty() throws Exception {
    String genFileDir = testInfo.getGenFileDir();
    String testSuiteResultPbPath = PathUtil.join(genFileDir, "instrument_test_result.pb");
    realLocalFileUtil.writeToFile(testSuiteResultPbPath, new byte[] {(byte) 0xFF, (byte) 0xFF});

    Optional<TestSuiteResult> result = loader.loadTestResult(testInfo);

    assertThat(result).isEmpty();
  }
}
