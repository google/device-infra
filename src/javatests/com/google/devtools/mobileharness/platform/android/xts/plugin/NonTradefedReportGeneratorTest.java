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

package com.google.devtools.mobileharness.platform.android.xts.plugin;

import static com.google.devtools.mobileharness.platform.android.xts.plugin.NonTradefedReportGenerator.PARAM_RUN_CERTIFICATION_TEST_SUITE;
import static com.google.devtools.mobileharness.platform.android.xts.plugin.NonTradefedReportGenerator.PARAM_XTS_SUITE_INFO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CertificationSuiteInfoFactory;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportHelper;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbUtil;
import com.google.devtools.mobileharness.platform.android.xts.suite.SuiteCommon;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import java.nio.file.Paths;
import java.time.Clock;
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

/** Unit tests for {@link NonTradefedReportGenerator}. */
@RunWith(JUnit4.class)
public final class NonTradefedReportGeneratorTest {

  private static final String SERIAL = "363005dc750400ec";

  @Rule public final MockitoRule rule = MockitoJUnit.rule();

  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Adb adb;
  @Mock private AndroidAdbUtil androidAdbUtil;
  @Mock private LocalFileUtil localFileUtil;
  @Mock private MoblyReportHelper moblyReportHelper;
  @Mock private CertificationSuiteInfoFactory certificationSuiteInfoFactory;

  private Params params;

  private NonTradefedReportGenerator generator;

  @Before
  public void setup() {
    generator =
        new NonTradefedReportGenerator(
            Clock.system(ZoneId.systemDefault()),
            adb,
            androidAdbUtil,
            localFileUtil,
            certificationSuiteInfoFactory,
            moblyReportHelper);
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
            .setSuiteReportVersion(CertificationSuiteInfoFactory.SUITE_REPORT_VERSION)
            .build();
    params = new Params(null);
    params.add(PARAM_RUN_CERTIFICATION_TEST_SUITE, "true");
    params.add(
        PARAM_XTS_SUITE_INFO,
        String.format(
            "%s=CTS,%s=CTS,%s=,%s=,%s=0",
            SuiteCommon.SUITE_NAME,
            SuiteCommon.SUITE_VARIANT,
            SuiteCommon.SUITE_VERSION,
            SuiteCommon.SUITE_PLAN,
            SuiteCommon.SUITE_BUILD));

    when(jobInfo.params()).thenReturn(params);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.getGenFileDir()).thenReturn("/gen");
    when(androidAdbUtil.getProperty(SERIAL, ImmutableList.of("ro.build.fingerprint")))
        .thenReturn("");
    doCallRealMethod().when(certificationSuiteInfoFactory).createSuiteInfo(any());

    generator.createTestReport(
        testInfo, ImmutableList.of(SERIAL), Instant.ofEpochSecond(1), Instant.ofEpochSecond(10));

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
  public void postMoblyCommandExec_notRunCertificationTestSuite_skipGeneratingAttrFiles()
      throws Exception {
    params = new Params(null);
    params.add(PARAM_RUN_CERTIFICATION_TEST_SUITE, "false");

    when(jobInfo.params()).thenReturn(params);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.getGenFileDir()).thenReturn("/gen");

    generator.createTestReport(
        testInfo, ImmutableList.of(SERIAL), Instant.ofEpochSecond(1), Instant.ofEpochSecond(10));

    verify(certificationSuiteInfoFactory, never()).createSuiteInfo(any());
    verify(moblyReportHelper, never())
        .generateResultAttributesFile(any(), any(), any(), any(), any());
    verify(moblyReportHelper, never()).generateBuildAttributesFile(any(), any());
  }

  @Test
  public void postMoblyCommandExec_compositeDeviceHasNoSubDevices_skipGeneratingAttrFiles()
      throws Exception {
    params = new Params(null);
    params.add(PARAM_RUN_CERTIFICATION_TEST_SUITE, "true");

    when(jobInfo.params()).thenReturn(params);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(testInfo.getGenFileDir()).thenReturn("/gen");

    generator.createTestReport(
        testInfo, ImmutableList.of(), Instant.ofEpochSecond(1), Instant.ofEpochSecond(10));

    verify(certificationSuiteInfoFactory, never()).createSuiteInfo(any());
    verify(moblyReportHelper, never())
        .generateResultAttributesFile(any(), any(), any(), any(), any());
    verify(moblyReportHelper, never()).generateBuildAttributesFile(any(), any());
  }
}
