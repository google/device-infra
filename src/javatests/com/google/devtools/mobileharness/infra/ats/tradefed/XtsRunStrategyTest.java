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

package com.google.devtools.mobileharness.infra.ats.tradefed;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsChunkedTestCasesRestorer;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsCommandUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.TradefedTestDriverSpec;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class XtsRunStrategyTest {

  private static final String XTS_TYPE = "cts";
  private static final String ANDROID_XTS_ZIP = "/path/to/android-chunked-cts.zip";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final SetFlags flags = new SetFlags();

  @Mock private LocalFileUtil localFileUtil;
  @Mock private ResUtil resUtil;
  @Mock private SystemUtil systemUtil;
  @Mock private XtsCommandUtil xtsCommandUtil;
  @Mock private XtsChunkedTestCasesRestorer xtsChunkedTestCasesRestorer;

  private TestInfo testInfo;
  private XtsRunStrategy xtsRunStrategy;

  @Before
  public void setUp() throws Exception {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("AndroidRealDevice")
                    .setDriver("TradefedTest")
                    .build())
            .build();
    testInfo = jobInfo.tests().add("test_id", "test_name");
    xtsRunStrategy =
        new XtsRunStrategy(
            localFileUtil,
            resUtil,
            systemUtil,
            Clock.fixed(Instant.ofEpochMilli(1000L), ZoneId.systemDefault()),
            XTS_TYPE,
            xtsCommandUtil,
            xtsChunkedTestCasesRestorer);
  }

  @Test
  public void getXtsRootDir_xtsRootDirInSpec_doesNotRestore() throws Exception {
    TradefedTestDriverSpec spec =
        TradefedTestDriverSpec.newBuilder().setXtsRootDir("/path/to/xts_root_dir").build();

    Path xtsRootDir = xtsRunStrategy.getXtsRootDir(spec, testInfo);

    assertThat(xtsRootDir).isEqualTo(Path.of("/path/to/xts_root_dir"));
    // The client which set xts_root_dir has already restored the test cases if needed.
    verify(xtsChunkedTestCasesRestorer, never()).restoreTestCases(anyString(), anyString());
  }

  @Test
  public void getXtsRootDir_androidXtsZipInSpec_unzipsAndRestores() throws Exception {
    TradefedTestDriverSpec spec =
        TradefedTestDriverSpec.newBuilder().setAndroidXtsZip(ANDROID_XTS_ZIP).build();
    String expectedUnzippedPath =
        PathUtil.join(testInfo.getTmpFileDir(), ANDROID_XTS_ZIP.replace('.', '_') + "_unzipped");

    Path xtsRootDir = xtsRunStrategy.getXtsRootDir(spec, testInfo);

    assertThat(xtsRootDir).isEqualTo(Path.of(expectedUnzippedPath));
    verify(localFileUtil)
        .unzipFile(ANDROID_XTS_ZIP, expectedUnzippedPath, Duration.ofHours(2L), null);
    verify(xtsChunkedTestCasesRestorer).restoreTestCases(expectedUnzippedPath, XTS_TYPE);
  }

  @Test
  public void getXtsRootDir_restoreFailed_propagatesRestoreError() throws Exception {
    TradefedTestDriverSpec spec =
        TradefedTestDriverSpec.newBuilder().setAndroidXtsZip(ANDROID_XTS_ZIP).build();
    doThrow(
            new MobileHarnessException(
                AndroidErrorId.XTS_RESTORE_CHUNKED_TEST_CASES_ERROR, "restore failed"))
        .when(xtsChunkedTestCasesRestorer)
        .restoreTestCases(anyString(), anyString());

    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class, () -> xtsRunStrategy.getXtsRootDir(spec, testInfo));

    // The restore error must not be masked as an unzip failure.
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.XTS_RESTORE_CHUNKED_TEST_CASES_ERROR);
  }

  @Test
  public void getXtsRootDir_neitherXtsRootDirNorZip_throws() throws Exception {
    MobileHarnessException exception =
        assertThrows(
            MobileHarnessException.class,
            () ->
                xtsRunStrategy.getXtsRootDir(
                    TradefedTestDriverSpec.getDefaultInstance(), testInfo));

    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.XTS_TRADEFED_GET_XTS_ROOT_DIR_ERROR);
    verify(localFileUtil, never()).unzipFile(anyString(), anyString(), any(Duration.class), any());
  }
}
