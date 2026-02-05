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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstallArgs;
import com.google.devtools.mobileharness.platform.android.lightning.apkinstaller.ApkInstaller;
import com.google.wireless.qa.mobileharness.shared.api.device.AndroidDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
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
public class PreProcessorTest {
  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Mock private ApkInstaller apkInstaller;
  @Mock private Adb adb;
  @Mock private AndroidDevice device;
  private PreProcessor preProcessor;

  private String crawlerPath;
  private String stubPath;

  @Before
  public void setUp() throws Exception {
    crawlerPath = temporaryFolder.newFile("crawler.apk").toPath().toString();
    stubPath = temporaryFolder.newFile("stub.apk").toPath().toString();
    preProcessor = new PreProcessor(apkInstaller, adb);
    when(device.getDeviceId()).thenReturn("device_id");
  }

  @Test
  public void installApks_completesForApiLevel28() throws Exception {
    TestInfo testInfo = setUpJobInfo().tests().add("some test");
    when(device.getSdkVersion()).thenReturn(28);
    AndroidRoboTestSpec spec = AndroidRoboTestSpec.newBuilder().setCrawlerApk(crawlerPath).build();

    preProcessor.installApks(testInfo, device, spec, stubPath);

    verify(apkInstaller)
        .installApk(device, setupInstallable(spec.getCrawlerApk(), true), testInfo.log());
    verify(apkInstaller).installApk(device, setupInstallable(stubPath, true), testInfo.log());
    verify(adb).runShell("device_id", "dumpsys deviceidle whitelist +androidx.test.tools.crawler");
  }

  @Test
  public void installApks_completesForApiLevel27() throws Exception {
    TestInfo testInfo = setUpJobInfo().tests().add("some test");
    when(device.getSdkVersion()).thenReturn(27);
    AndroidRoboTestSpec spec = AndroidRoboTestSpec.newBuilder().setCrawlerApk(crawlerPath).build();

    preProcessor.installApks(testInfo, device, spec, stubPath);

    verifyNoInteractions(adb);
  }

  private JobInfo setUpJobInfo() {
    return JobInfo.newBuilder()
        .setLocator(new JobLocator("job_id", "job_name"))
        .setType(JobType.newBuilder().setDevice("device_type").setDriver("AndroidRoboTest").build())
        .build();
  }

  private ApkInstallArgs setupInstallable(String path, boolean grantRuntimePermissions) {
    return ApkInstallArgs.builder()
        .setApkPath(path)
        .setSkipIfCached(true)
        .setGrantPermissions(grantRuntimePermissions)
        .build();
  }
}
