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

package com.google.devtools.mobileharness.infra.ats.console.result.report;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportParser.MoblyReportInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.TestRunfilesUtil;
import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MoblyReportParserTest {

  private static final String MOBLY_TEST_SUMMARY_FILE =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/mobly/pass/test_summary.yaml");

  private static final String RESULT_ATTR_FILE =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/pass/result_attrs.textproto");

  private static final String BUILD_ATTR_FILE =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/pass/build_attrs.textproto");

  private static final String MODULE_RESULT_FILE =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/pass/ats_module_run_result.textproto");

  private static final String DEVICE_BUILD_FINGERPRINT =
      "google/bramble/bramble:UpsideDownCake/UP1A.220722.002/8859461:userdebug/dev-keys";

  @Inject private MoblyReportParser moblyReportParser;

  @Before
  public void setUp() {
    Guice.createInjector().injectMembers(this);
  }

  @Test
  public void parseMoblyTestResult() throws Exception {
    Optional<Result> res =
        moblyReportParser.parseMoblyTestResult(
            MoblyReportInfo.of(
                "mobly-package-name",
                "module-abi",
                "module-parameter",
                Path.of(MOBLY_TEST_SUMMARY_FILE),
                Path.of(RESULT_ATTR_FILE),
                DEVICE_BUILD_FINGERPRINT,
                Path.of(BUILD_ATTR_FILE),
                Path.of(MODULE_RESULT_FILE)));

    assertThat(res).isPresent();

    Result report = res.get();
    assertThat(report.getAttributeList()).hasSize(2);
    assertThat(report.getBuild().getBuildFingerprint()).isEqualTo(DEVICE_BUILD_FINGERPRINT);
    assertThat(report.getBuild().getAttributeList()).hasSize(2);

    assertThat(report.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(1)
                .setFailed(2)
                .setModulesDone(1)
                .setModulesTotal(1)
                .build());

    assertThat(report.getModuleInfoList()).hasSize(1);
    assertThat(report.getModuleInfo(0))
        .isEqualTo(
            Module.newBuilder()
                .setName("mobly-package-name[module-parameter]")
                .setIsNonTfModule(true)
                .setAbi("module-abi")
                .setDone(true)
                .setTotalTests(4)
                .setPassed(1)
                .setFailedTests(2)
                .setRuntimeMillis(6046L)
                .addTestCase(
                    TestCase.newBuilder()
                        .setName("HelloWorldTest1")
                        .addTest(
                            ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("test_hello_world1_1"))
                        .addTest(
                            ReportProto.Test.newBuilder()
                                .setResult("fail")
                                .setName("test_hello_world1_2")
                                .setFailure(
                                    TestFailure.newBuilder()
                                        .setMsg("Traceback (most recent call last):")
                                        .setStackTrace(
                                            StackTrace.newBuilder()
                                                .setContent(
                                                    "Traceback (most recent call last):\n"
                                                        + "  File \"mobly/base_test.py\", line 818,"
                                                        + " in exec_one_test\n")))))
                .addTestCase(
                    TestCase.newBuilder()
                        .setName("HelloWorldTest2")
                        .addTest(
                            ReportProto.Test.newBuilder()
                                .setResult("SKIPPED")
                                .setSkipped(true)
                                .setName("test_hello_world2_1"))
                        .addTest(
                            ReportProto.Test.newBuilder()
                                .setResult("fail")
                                .setName("test_hello_world2_2")))
                .build());
  }
}
