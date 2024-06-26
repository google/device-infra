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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import com.google.devtools.mobileharness.infra.ats.console.util.TestRunfilesUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.tradefed.TestRecordWriter;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.File;
import java.nio.file.Path;
import javax.inject.Inject;
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
public final class CompatibilityReportCreatorTest {

  private static final String CTS_TEST_RESULT_XML =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/xml/report_creator_cts_test_result.xml");

  private static final String CTS_NON_TF_TEST_RESULT_XML =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/xml/report_creator_cts_non_tf_test_result.xml");

  private static final Splitter LINE_SPLITTER = Splitter.onPattern("\r\n|\n|\r");

  private final LocalFileUtil realLocalFileUtil = new LocalFileUtil();

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Bind @Mock private TestRecordWriter testRecordWriter;
  @Inject private CompatibilityReportParser reportParser;

  private CompatibilityReportCreator reportCreator;

  @Before
  public void setUp() {
    Guice.createInjector(new TestModule(), BoundFieldModule.of(this)).injectMembers(this);
    reportCreator = new CompatibilityReportCreator(realLocalFileUtil, testRecordWriter);
  }

  @Test
  public void writeReportToXml() throws Exception {
    Result report = reportParser.parse(Path.of(CTS_TEST_RESULT_XML), /* shallow= */ false).get();

    File xmlResultDir = temporaryFolder.newFolder("xml_result");

    reportCreator.writeReportToXml(report, xmlResultDir);

    assertThat(
            replaceLineBreak(
                realLocalFileUtil
                    .readFile(
                        xmlResultDir
                            .toPath()
                            .resolve(CompatibilityReportCreator.TEST_RESULT_FILE_NAME))
                    .trim()))
        .isEqualTo(
            replaceLineBreak(realLocalFileUtil.readFile(Path.of(CTS_TEST_RESULT_XML)).trim()));
  }

  @Test
  public void writeReportWithNonTfModuleToXml_escapeXmlContent() throws Exception {
    Module module =
        Module.newBuilder()
            .setName("NonTfModule1")
            .setIsNonTfModule(true)
            .setAbi("arm64-v8a")
            .setDone(true)
            .setTotalTests(1)
            .setPassed(0)
            .setFailedTests(1)
            .setRuntimeMillis(6046L)
            .addTestCase(
                TestCase.newBuilder()
                    .setName("android.cts.DummyFoo1Test")
                    .addTest(
                        ReportProto.Test.newBuilder()
                            .setResult("fail")
                            .setName("test_hello_world1_1")
                            .setFailure(
                                TestFailure.newBuilder()
                                    .setMsg(
                                        "AssertionError: X coordinates should be the same"
                                            + " expected:<-25.0> but was:<15.0>")
                                    .setStackTrace(
                                        StackTrace.newBuilder()
                                            .setContent(
                                                "AssertionError: X coordinates should be the same"
                                                    + " expected:<-25.0> but was:<15.0>\n"
                                                    + " Traceback (most recent call last):\n"
                                                    + "   File \"mobly/base_test.py\", line 818, in"
                                                    + " exec_one_test\n")))))
            .build();
    Result report = Result.newBuilder().addModuleInfo(module).build();
    File xmlResultDir = temporaryFolder.newFolder("xml_result");

    reportCreator.writeReportToXml(report, xmlResultDir);

    assertThat(
            replaceLineBreak(
                realLocalFileUtil
                    .readFile(
                        xmlResultDir
                            .toPath()
                            .resolve(CompatibilityReportCreator.TEST_RESULT_FILE_NAME))
                    .trim()))
        .isEqualTo(
            replaceLineBreak(
                realLocalFileUtil.readFile(Path.of(CTS_NON_TF_TEST_RESULT_XML)).trim()));
  }

  @Test
  public void createReport() throws Exception {
    Result report = reportParser.parse(Path.of(CTS_TEST_RESULT_XML), /* shallow= */ false).get();

    File xmlResultDir = temporaryFolder.newFolder("xml_result");

    reportCreator.createReport(
        report, xmlResultDir.toPath(), /* testRecord= */ null, /* includeHtmlInZip= */ false);

    assertThat(
            realLocalFileUtil.listFilePaths(xmlResultDir.toPath(), false).stream()
                .map(p -> p.getFileName().toString()))
        .containsExactlyElementsIn(
            ImmutableList.<String>builder()
                .addAll(CompatibilityReportCreator.RESULT_RESOURCES)
                .add(CompatibilityReportCreator.TEST_RESULT_FILE_NAME)
                .add(CompatibilityReportCreator.HTML_REPORT_NAME)
                .add("checksum-suite.data")
                .add(CompatibilityReportCreator.TEST_RESULT_PB_FILE_NAME)
                .add(CompatibilityReportCreator.FAILURE_REPORT_NAME)
                .build());

    assertThat(
            realLocalFileUtil.listFilePaths(xmlResultDir.toPath().getParent(), false).stream()
                .map(p -> p.getFileName().toString()))
        .containsExactly("xml_result.zip");
  }

  private static String replaceLineBreak(String str) {
    return Joiner.on("\n").join(LINE_SPLITTER.omitEmptyStrings().splitToList(str));
  }
}
