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

package com.google.devtools.mobileharness.platform.android.appcrawler;

import static org.mockito.Mockito.verify;

import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class PostProcessorTest {
  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock private ApkInstaller apkInstaller;

  private PostProcessor postProcessor;

  @Before
  public void setUp() throws Exception {
    postProcessor = new PostProcessor(apkInstaller);
  }

  @Test
  public void uninstallApks_completes() throws Exception {
    TestInfo testInfo = setUpJobInfo().tests().add("some test");
    Device device = new NoOpDevice("device_name");

    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setCrawlerPackageId("androidx.test.tools.crawler")
            .setStubAppPackageId("androidx.test.tools.crawler.stubapp")
            .build();
    postProcessor.uninstallApks(testInfo, device, spec);

    verify(apkInstaller).uninstallApk(device, "androidx.test.tools.crawler", true, testInfo.log());
    verify(apkInstaller)
        .uninstallApk(device, "androidx.test.tools.crawler.stubapp", true, testInfo.log());
  }

  private JobInfo setUpJobInfo() {
    return JobInfo.newBuilder()
        .setLocator(new JobLocator("job_id", "job_name"))
        .setType(JobType.newBuilder().setDevice("device_type").setDriver("AndroidRoboTest").build())
        .build();
  }
}
