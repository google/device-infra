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

import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidRoboTest.ANDROID_ROBO_TEST_TEST_END_EPOCH_MS;
import static com.google.wireless.qa.mobileharness.shared.constant.PropertyName.Test.AndroidRoboTest.ANDROID_ROBO_TEST_TEST_START_EPOCH_MS;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.appcrawler.PostProcessor;
import com.google.devtools.mobileharness.platform.android.appcrawler.PreProcessor;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.testing.FakeCommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.device.NoOpDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.Job.TestResult;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec.ControllerEndpoint;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
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
  @Mock private Clock clock;
  @Mock private PreProcessor preProcessor;
  @Mock private ResUtil resUtil;
  @Mock private CommandExecutor commandExecutor;
  @Mock private CommandProcess commandProcess;
  @Mock private PostProcessor postProcessor;

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
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(1));
    when(resUtil.getExternalResourceFile(any())).thenReturn("cli.jar");
  }

  @Test
  public void run_pass() throws Exception {
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setCrawlerApk("/path/to/crawler.apk")
            .setCrawlerStubApk("/path/to/stub.apk")
            .setAppPackageId("com.some.app")
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .setCrawlTimeoutSecs(60)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);
    TestInfo testInfo = jobInfo.tests().add("fake test");
    AndroidRoboTest roboTest = createAndroidRoboTest(testInfo);
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(2)).thenReturn(Instant.ofEpochMilli(3));
    when(commandExecutor.start(any())).thenReturn(commandProcess);
    when(commandProcess.await(any())).thenReturn(FakeCommandResult.of("", "", 0));

    roboTest.run(testInfo);
    verify(preProcessor).installApks(testInfo, device, spec);

    resUtil.getExternalResourceFile(any());
    assertThat(testInfo.properties().get(ANDROID_ROBO_TEST_TEST_START_EPOCH_MS)).isEqualTo("2");

    verify(commandExecutor).start(any());
    verify(commandProcess).await(any());

    assertThat(testInfo.properties().get(ANDROID_ROBO_TEST_TEST_END_EPOCH_MS)).isEqualTo("3");
    assertThat(testInfo.result().get()).isEqualTo(TestResult.PASS);
    verify(postProcessor).uninstallApks(testInfo, device, spec);
  }

  @Test
  public void run_skip() throws Exception {
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setCrawlerApk("/path/to/crawler.apk")
            .setCrawlerStubApk("/path/to/stub.apk")
            .setAppPackageId("com.some.app")
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .setCrawlTimeoutSecs(60)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);
    TestInfo testInfo = jobInfo.tests().add("fake test");
    AndroidRoboTest roboTest = createAndroidRoboTest(testInfo);
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(2)).thenReturn(Instant.ofEpochMilli(3));
    when(commandExecutor.start(any())).thenReturn(commandProcess);
    when(commandProcess.await(any())).thenReturn(FakeCommandResult.of("", "", 1));

    roboTest.run(testInfo);

    assertThat(testInfo.result().get()).isEqualTo(TestResult.SKIP);
  }

  @Test
  public void run_errorAbsentExceptionDetail() throws Exception {
    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setCrawlerApk("/path/to/crawler.apk")
            .setCrawlerStubApk("/path/to/stub.apk")
            .setAppPackageId("com.some.app")
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .setCrawlTimeoutSecs(60)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);
    TestInfo testInfo = jobInfo.tests().add("fake test");
    AndroidRoboTest roboTest = createAndroidRoboTest(testInfo);
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(2)).thenReturn(Instant.ofEpochMilli(3));
    when(commandExecutor.start(any())).thenReturn(commandProcess);
    when(commandProcess.await(any())).thenReturn(FakeCommandResult.of("", "", 3));

    var exception = assertThrows(MobileHarnessException.class, () -> roboTest.run(testInfo));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_ROBO_TEST_MH_EXCEPTION_DETAIL_READ_ERROR);
  }

  @Test
  public void run_errorCliResourceAbsent() throws Exception {
    when(resUtil.getExternalResourceFile(any())).thenReturn("");

    AndroidRoboTestSpec spec =
        AndroidRoboTestSpec.newBuilder()
            .setCrawlerApk("/path/to/crawler.apk")
            .setCrawlerStubApk("/path/to/stub.apk")
            .setAppPackageId("com.some.app")
            .setControllerEndpoint(ControllerEndpoint.AUTOPUSH)
            .setCrawlTimeoutSecs(60)
            .build();
    jobInfo.scopedSpecs().add("AndroidRoboTestSpec", spec);
    TestInfo testInfo = jobInfo.tests().add("fake test");
    AndroidRoboTest roboTest = createAndroidRoboTest(testInfo);
    when(clock.instant()).thenReturn(Instant.ofEpochMilli(2)).thenReturn(Instant.ofEpochMilli(3));

    var exception = assertThrows(MobileHarnessException.class, () -> roboTest.run(testInfo));
    assertThat(exception.getErrorId())
        .isEqualTo(AndroidErrorId.ANDROID_ROBO_TEST_MH_ROBO_CLI_EXTRACTION_ERROR);
  }

  private AndroidRoboTest createAndroidRoboTest(TestInfo testInfo) {
    return new AndroidRoboTest(
        device, testInfo, adb, aapt, clock, preProcessor, resUtil, commandExecutor, postProcessor);
  }
}
