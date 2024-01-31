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
import static org.mockito.Mockito.when;

import com.google.common.truth.Truth8;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.LoggedFile;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Metric;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Reason;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Run;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import com.google.devtools.mobileharness.infra.ats.console.util.TestRunfilesUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.stream.XMLInputFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class CompatibilityReportParserTest {

  private static final String CTS_TEST_RESULT_XML =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/xml/cts_test_result.xml");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock private LocalFileUtil localFileUtil;

  private CompatibilityReportParser reportParser;

  @Before
  public void setUp() throws Exception {
    reportParser = new CompatibilityReportParser(XMLInputFactory.newInstance());
  }

  @Test
  public void parse_ctsReportXml() throws Exception {
    Result result = reportParser.parse(Paths.get(CTS_TEST_RESULT_XML)).get();

    assertThat(result.getAttributeList())
        .containsExactly(
            Attribute.newBuilder().setKey("start").setValue("1678951330449").build(),
            Attribute.newBuilder().setKey("end").setValue("1678951395733").build(),
            Attribute.newBuilder()
                .setKey("start_display")
                .setValue("Thu Mar 16 00:22:10 PDT 2023")
                .build(),
            Attribute.newBuilder()
                .setKey("end_display")
                .setValue("Thu Mar 16 00:23:15 PDT 2023")
                .build(),
            Attribute.newBuilder()
                .setKey("command_line_args")
                .setValue("cts -s 12241FDD4002Z6")
                .build(),
            Attribute.newBuilder().setKey("devices").setValue("12241FDD4002Z6").build())
        .inOrder();

    assertThat(result.getBuild().getBuildFingerprint())
        .isEqualTo(
            "google/bramble/bramble:UpsideDownCake/UP1A.220722.002/8859461:userdebug/dev-keys");
    assertThat(result.getBuild().getAttributeList())
        .containsExactly(
            Attribute.newBuilder()
                .setKey("adb_version")
                .setValue("1.0.41 install path: /usr/bin/adb")
                .build(),
            Attribute.newBuilder()
                .setKey("command_line_args")
                .setValue("cts -s 12241FDD4002Z6")
                .build(),
            Attribute.newBuilder()
                .setKey("device_kernel_info")
                .setValue(
                    "Linux localhost 5.10.149-android13-4-693040-g6422af733678-ab9739629 #1 SMP"
                        + " PREEMPT Fri Mar 10 01:44:38 UTC 2023 aarch64 Toybox")
                .build(),
            Attribute.newBuilder().setKey("invocation-id").setValue("1").build(),
            Attribute.newBuilder().setKey("java_version").setValue("19.0.2").build(),
            Attribute.newBuilder()
                .setKey("build_fingerprint")
                .setValue(
                    "google/bramble/bramble:UpsideDownCake/UP1A.220722.002/8859461:userdebug/dev-keys")
                .build())
        .inOrder();

    assertThat(result.getRunHistory().getRunCount()).isEqualTo(2);
    assertThat(result.getRunHistory().getRun(0))
        .isEqualTo(
            Run.newBuilder()
                .setStartTimeMillis(1678951330449L)
                .setEndTimeMillis(1678951360449L)
                .setPassedTests(9)
                .setFailedTests(1)
                .setCommandLineArgs("cts -s 12241FDD4002Z6")
                .setHostName("myhostname")
                .build());

    assertThat(result.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(9)
                .setFailed(1)
                .setModulesDone(2)
                .setModulesTotal(2)
                .build());

    assertThat(result.getModuleInfoList())
        .containsExactly(
            Module.newBuilder()
                .setName("Module1")
                .setAbi("arm64-v8a")
                .setRuntimeMillis(7495L)
                .setDone(true)
                .setPassed(3)
                .setTotalTests(4)
                .setReason(
                    Reason.newBuilder()
                        .setMsg("Module1 has test failure")
                        .setErrorName("TEST FAILURE")
                        .setErrorCode("1"))
                .addTestCase(
                    TestCase.newBuilder()
                        .setName("android.cts.Dummy1Test")
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("fail")
                                .setName("testMethod1")
                                .setFailure(
                                    TestFailure.newBuilder()
                                        .setMsg("testMethod1 failed")
                                        .setErrorName("TEST FAILURE")
                                        .setErrorCode("1")
                                        .setStackTrace(
                                            StackTrace.newBuilder().setContent("Test Error")))
                                .setBugReport(
                                    LoggedFile.newBuilder()
                                        .setFileName("bugreport.zip")
                                        .setContent("BugReport content"))
                                .setLogcat(
                                    LoggedFile.newBuilder()
                                        .setFileName("logcat.txt")
                                        .setContent("Logcat content"))
                                .addScreenshot(
                                    LoggedFile.newBuilder()
                                        .setFileName("screenshot.jpeg")
                                        .setContent("Screenshot JPEG description"))
                                .addScreenshot(
                                    LoggedFile.newBuilder()
                                        .setFileName("screenshot.png")
                                        .setContent("Screenshot PNG description"))
                                .addMetric(
                                    Metric.newBuilder()
                                        .setKey("metric1")
                                        .setContent("Metric1 content"))
                                .addMetric(
                                    Metric.newBuilder()
                                        .setKey("metric2")
                                        .setContent("Metric2 content")))
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("testMethod2")))
                .addTestCase(
                    TestCase.newBuilder()
                        .setName("android.cts.Dummy2Test")
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("testMethod1"))
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("testMethod2")))
                .build(),
            Module.newBuilder()
                .setName("Module2")
                .setAbi("arm64-v8a")
                .setRuntimeMillis(7495L)
                .setDone(true)
                .setPassed(6)
                .setTotalTests(6)
                .addTestCase(
                    TestCase.newBuilder()
                        .setName("android.cts.Hello1Test")
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("testMethod1"))
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("testMethod2")))
                .addTestCase(
                    TestCase.newBuilder()
                        .setName("android.cts.Hello2Test")
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("testMethod1"))
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("testMethod2")))
                .addTestCase(
                    TestCase.newBuilder()
                        .setName("android.cts.Hello3Test")
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("testMethod1"))
                        .addTest(
                            com.google.devtools.mobileharness.infra.ats.console.result.proto
                                .ReportProto.Test.newBuilder()
                                .setResult("pass")
                                .setName("testMethod2")))
                .build());
  }

  @Test
  public void parse_nonExistentFile() throws Exception {
    reportParser = new CompatibilityReportParser(XMLInputFactory.newInstance(), localFileUtil);
    Path nonExistentXml = Paths.get("/path/to/non-existent-xml");
    when(localFileUtil.isFileExist(nonExistentXml)).thenReturn(false);

    Truth8.assertThat(reportParser.parse(nonExistentXml)).isEmpty();
  }
}
