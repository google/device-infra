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

package com.google.devtools.mobileharness.platform.android.labtestsupport.plugin;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.client.api.event.JobStartEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.io.File;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AndroidLabTestSupportResolvePluginTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TemporaryFolder folder = new TemporaryFolder();
  @Mock ResUtil resUtil;

  private AndroidLabTestSupportResolvePlugin plugin;
  private JobInfo jobInfo;

  @Before
  public void setUp() {
    plugin = new AndroidLabTestSupportResolvePlugin(resUtil);
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("locator"))
            .setType(JobType.getDefaultInstance())
            .build();
  }

  @Test
  public void onJobStart_resolveLabTestSupportApk() throws Exception {
    String labTestSupportApkPath = prepareFakeLabTestSupportApk(folder).toString();
    when(resUtil.getResourceFile(
            AndroidLabTestSupportResolvePlugin.class,
            AndroidLabTestSupportResolvePlugin.LAB_TEST_SUPPORT_APK_RES_PATH))
        .thenReturn(labTestSupportApkPath);
    JobStartEvent event = new JobStartEvent(jobInfo);

    plugin.onJobStart(event);

    assertThat(jobInfo.files().get(AndroidLabTestSupportResolvePlugin.TAG_LAB_TEST_SUPPORT_APK))
        .containsExactly(labTestSupportApkPath);
    assertThat(
            jobInfo
                .params()
                .get(
                    String.format(
                        "file_accessor_%s",
                        AndroidLabTestSupportResolvePlugin.TAG_LAB_TEST_SUPPORT_APK)))
        .isEqualTo("lab");
  }

  @Test
  public void onJobStart_failedToResolveLabTestSupportApk_throwSkipTestException()
      throws Exception {
    when(resUtil.getResourceFile(
            AndroidLabTestSupportResolvePlugin.class,
            AndroidLabTestSupportResolvePlugin.LAB_TEST_SUPPORT_APK_RES_PATH))
        .thenThrow(
            new MobileHarnessException(
                BasicErrorId.JAR_RES_COPY_ERROR, "An I/O error occurred when copying resource"));
    JobStartEvent event = new JobStartEvent(jobInfo);

    SkipTestException exception =
        assertThrows(SkipTestException.class, () -> plugin.onJobStart(event));

    assertThat(exception.errorId())
        .isEqualTo(AndroidErrorId.ANDROID_LAB_TEST_SUPPORT_RESOLVE_PLUGIN_RESOLVE_LTS_ERROR);
    assertThat(exception).hasMessageThat().contains("Failed to resolve LabTestSupport APK");
  }

  private Path prepareFakeLabTestSupportApk(TemporaryFolder temporaryFolder) throws Exception {
    File tmpDir = temporaryFolder.newFolder("tmp-dir");
    File labTestSupportApk = new File(tmpDir, "labtestsupport.apk");
    labTestSupportApk.createNewFile();
    return labTestSupportApk.toPath();
  }
}
