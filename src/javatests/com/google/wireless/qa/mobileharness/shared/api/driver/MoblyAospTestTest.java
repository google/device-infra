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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.atsconsole.result.report.CertificationSuiteInfo;
import com.google.devtools.atsconsole.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.atsconsole.result.report.CertificationSuiteInfoFactory.SuiteType;
import com.google.devtools.atsconsole.result.report.MoblyReportHelper;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.InstallMoblyTestDepsArgs;
import com.google.devtools.mobileharness.platform.testbed.mobly.util.MoblyAospTestSetupUtil;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.EmptyDevice;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link MoblyAospTest}. */
@RunWith(JUnit4.class)
public final class MoblyAospTestTest {

  private static final String SERIAL = "363005dc750400ec";
  private static final String PY_PKG_INDEX_URL = "https://python.package.index/url";

  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  @Mock private EmptyDevice emptyDevice;
  @Mock private CompositeDevice compositeDevice;
  @Mock private File configFile;
  @Mock private MoblyAospTestSetupUtil setupUtil;
  @Mock private MoblyReportHelper moblyReportHelper;
  @Mock private CertificationSuiteInfoFactory certificationSuiteInfoFactory;

  private Params params;

  @Test
  public void generateTestCommand_verifySetupUtilArgs() throws Exception {
    when(testInfo.getTmpFileDir()).thenReturn("/tmp");
    when(files.getSingle(MoblyAospTest.FILE_MOBLY_PKG)).thenReturn("sample_test.zip");
    when(jobInfo.files()).thenReturn(files);
    params = new Params(null);
    params.add(MoblyAospTest.PARAM_TEST_PATH, "sample_test.py");
    params.add(MoblyTest.TEST_SELECTOR_KEY, "test1 test2");
    params.add(MoblyAospTest.PARAM_PYTHON_VERSION, "3.10");
    params.add(MoblyAospTest.PARAM_PY_PKG_INDEX_URL, PY_PKG_INDEX_URL);
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(configFile.getPath()).thenReturn("config.yaml");
    InstallMoblyTestDepsArgs installMoblyTestDepsArgs =
        InstallMoblyTestDepsArgs.builder()
            .setDefaultTimeout(Duration.ofMinutes(30))
            .setIndexUrl(PY_PKG_INDEX_URL)
            .build();
    MoblyAospTest moblyAospTest =
        new MoblyAospTest(
            emptyDevice, testInfo, setupUtil, moblyReportHelper, certificationSuiteInfoFactory);

    var unused = moblyAospTest.generateTestCommand(testInfo, configFile, false);

    verify(setupUtil)
        .setupEnvAndGenerateTestCommand(
            Path.of("sample_test.zip"),
            Path.of("/tmp/mobly"),
            Path.of("/tmp/venv"),
            Path.of("config.yaml"),
            "sample_test.py",
            "test1 test2",
            "3.10",
            installMoblyTestDepsArgs);
  }

  @Test
  public void postMoblyCommandExec_verifyAttrFilesCreated() throws Exception {
    CertificationSuiteInfo certificationSuiteInfo =
        CertificationSuiteInfo.builder()
            .setSuiteName("CTS")
            .setSuiteVariant("CTS")
            .setSuiteVersion("")
            .setSuitePlan("")
            .setSuiteBuild("0")
            .setSuiteReportVersion("")
            .build();
    when(emptyDevice.getDeviceId()).thenReturn(SERIAL);
    when(testInfo.getGenFileDir()).thenReturn("/gen");
    params = new Params(null);
    params.add(MoblyAospTest.PARAM_CERTIFICATION_SUITE_TYPE, "cts");
    params.add(MoblyAospTest.PARAM_XTS_TEST_PLAN, "cts-plan");
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(certificationSuiteInfoFactory.createSuiteInfo(SuiteType.CTS, "cts-plan"))
        .thenReturn(certificationSuiteInfo);

    MoblyAospTest moblyAospTest =
        new MoblyAospTest(
            emptyDevice, testInfo, setupUtil, moblyReportHelper, certificationSuiteInfoFactory);

    moblyAospTest.postMoblyCommandExec(Instant.ofEpochSecond(1), Instant.ofEpochSecond(10));

    verify(moblyReportHelper)
        .generateResultAttributesFile(
            Instant.ofEpochSecond(1),
            Instant.ofEpochSecond(10),
            ImmutableList.of(SERIAL),
            certificationSuiteInfo,
            Paths.get("/gen"));
    verify(moblyReportHelper).generateBuildAttributesFile(SERIAL, Paths.get("/gen"));
  }

  @Test
  public void postMoblyCommandExec_noCertificationSuiteType_skipGeneratingAttrFiles()
      throws Exception {
    when(emptyDevice.getDeviceId()).thenReturn(SERIAL);
    when(testInfo.getGenFileDir()).thenReturn("/gen");
    params = new Params(null);
    params.add(MoblyAospTest.PARAM_CERTIFICATION_SUITE_TYPE, "");
    params.add(MoblyAospTest.PARAM_XTS_TEST_PLAN, "cts-plan");
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.jobInfo()).thenReturn(jobInfo);

    MoblyAospTest moblyAospTest =
        new MoblyAospTest(
            emptyDevice, testInfo, setupUtil, moblyReportHelper, certificationSuiteInfoFactory);

    moblyAospTest.postMoblyCommandExec(Instant.ofEpochSecond(1), Instant.ofEpochSecond(10));

    verify(certificationSuiteInfoFactory, never()).createSuiteInfo(any(), any());
    verify(moblyReportHelper, never())
        .generateResultAttributesFile(any(), any(), any(), any(), any());
    verify(moblyReportHelper, never()).generateBuildAttributesFile(any(), any());
  }

  @Test
  public void postMoblyCommandExec_compositeDeviceHasNoSubDevices_skipGeneratingAttrFiles()
      throws Exception {
    when(compositeDevice.getManagedDevices()).thenReturn(ImmutableSet.of());
    when(testInfo.getGenFileDir()).thenReturn("/gen");
    params = new Params(null);
    params.add(MoblyAospTest.PARAM_CERTIFICATION_SUITE_TYPE, "cts");
    params.add(MoblyAospTest.PARAM_XTS_TEST_PLAN, "cts-plan");
    when(jobInfo.params()).thenReturn(params);
    when(testInfo.jobInfo()).thenReturn(jobInfo);

    MoblyAospTest moblyAospTest =
        new MoblyAospTest(
            compositeDevice, testInfo, setupUtil, moblyReportHelper, certificationSuiteInfoFactory);

    moblyAospTest.postMoblyCommandExec(Instant.ofEpochSecond(1), Instant.ofEpochSecond(10));

    verify(certificationSuiteInfoFactory, never()).createSuiteInfo(any(), any());
    verify(moblyReportHelper, never())
        .generateResultAttributesFile(any(), any(), any(), any(), any());
    verify(moblyReportHelper, never()).generateBuildAttributesFile(any(), any());
  }
}
