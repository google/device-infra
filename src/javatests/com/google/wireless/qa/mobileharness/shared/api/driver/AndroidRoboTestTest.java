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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec.ControllerEndpoint;
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
public class AndroidRoboTestTest {

  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private Adb adb;
  @Mock private Aapt aapt;

  private JobInfo jobInfo;
  private Device device;
  private Path genFilesDir;

  @Before
  public void setUp() throws Exception {
    device = new NoOpDevice("device_name");
    genFilesDir = temporaryFolder.newFolder().toPath();
    jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setSetting(JobSetting.newBuilder().setGenFileDir(genFilesDir.toString()).build())
            .setType(
                JobType.newBuilder().setDevice("device_type").setDriver("AndroidRoboTest").build())
            .build();

    when(adb.getAdbServerHost()).thenReturn("localhost");
    when(adb.getAdbServerPort()).thenReturn(5037);
    when(adb.getAdbPath()).thenReturn("adb");
    when(aapt.getAaptPath()).thenReturn("aapt");
  }

  @Test
  public void run_pass() throws Exception {
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setCrawlerApk("/path/to/crawler.apk")
            .setCrawlerStubApk("/path/to/stub.apk")
            .setAppPackageId("com.some.app")
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);
    TestInfo testInfo = jobInfo.tests().add("fake test");
    AndroidRoboTest roboTest = createAndroidRoboTest(testInfo);

    roboTest.run(testInfo);
  }

  private AndroidRoboTest createAndroidRoboTest(TestInfo testInfo) {
    return new AndroidRoboTest(device, testInfo, adb, aapt);
  }
}
