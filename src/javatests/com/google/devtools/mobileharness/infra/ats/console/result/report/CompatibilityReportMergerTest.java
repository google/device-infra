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
import static org.junit.Assert.assertThrows;

import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.BuildInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger.ParseResult;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportMerger.TradefedResultBundle;
import com.google.devtools.mobileharness.infra.ats.console.result.report.MoblyReportParser.MoblyReportInfo;
import com.google.devtools.mobileharness.infra.ats.console.util.TestRunfilesUtil;
import com.google.inject.Guice;
import com.google.protobuf.TextFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompatibilityReportMergerTest {

  private static final String CTS_TEST_RESULT_XML =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/xml/cts_test_result.xml");

  private static final String CTS_TEST_RESULT_XML_2 =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/xml/cts_test_result_2.xml");

  private static final String CTS_TEST_RESULT_XML_3 =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/xml/cts_test_result_3.xml");

  private static final String CTS_TEST_RECORD_PB_FILE =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/xml/cts_test_record.pb");

  private static final String MOBLY_TEST_SUMMARY_FILE_1 =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/mobly/pass/test_summary.yaml");

  private static final String MOBLY_RESULT_ATTR_FILE_1 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/pass/result_attrs.textproto");

  private static final String MOBLY_BUILD_ATTR_FILE_1 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/pass/build_attrs.textproto");

  private static final String MOBLY_TEST_SUMMARY_FILE_2 =
      TestRunfilesUtil.getRunfilesLocation("result/report/testdata/mobly/fail/test_summary.yaml");

  private static final String MOBLY_RESULT_ATTR_FILE_2 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/fail/result_attrs.textproto");

  private static final String MOBLY_BUILD_ATTR_FILE_2 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/fail/build_attrs.textproto");

  private static final String MODULE_RESULT_FILE_1 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/pass/ats_module_run_result.textproto");

  private static final String MODULE_RESULT_FILE_2 =
      TestRunfilesUtil.getRunfilesLocation(
          "result/report/testdata/mobly/fail/ats_module_run_result.textproto");

  private static final String DEVICE_BUILD_FINGERPRINT =
      "google/bramble/bramble:UpsideDownCake/UP1A.220722.002/8859461:userdebug/dev-keys";

  @Inject private CompatibilityReportMerger reportMerger;

  @Before
  public void setUp() {
    Guice.createInjector(new TestModule()).injectMembers(this);
  }

  @Test
  public void generateTestRecordMetricsMap_success() throws Exception {
    Map<String, Map<String, Metric>> metricsMap = new LinkedHashMap<>();
    TestRecord testRecord =
        TextFormat.parse(Files.readString(Path.of(CTS_TEST_RECORD_PB_FILE)), TestRecord.class);
    CompatibilityReportMerger.generateTestRecordMetricsMap(testRecord, metricsMap);

    assertThat(metricsMap).hasSize(2);
    assertThat(metricsMap.get("arm64-v8a Module1"))
        .containsExactly(
            "PREP_TIME", createBasicMetric(123456), "TEARDOWN_TIME", createBasicMetric(654321));
    assertThat(metricsMap.get("arm64-v8a Module2"))
        .containsExactly(
            "PREP_TIME", createBasicMetric(321), "TEARDOWN_TIME", createBasicMetric(123));
  }

  @Test
  public void insertMetadataFromTestRecord_success() throws Exception {
    List<ParseResult> res =
        reportMerger.parseResultBundles(
            ImmutableList.of(
                TradefedResultBundle.of(Path.of(CTS_TEST_RESULT_XML), Optional.empty())));
    Result report = res.get(0).report().get();
    TestRecord testRecord =
        TextFormat.parse(Files.readString(Path.of(CTS_TEST_RECORD_PB_FILE)), TestRecord.class);

    Result updatedReport =
        CompatibilityReportMerger.insertMetadataFromTestRecord(report, testRecord);

    assertThat(updatedReport.getModuleInfoList().get(0).getPrepTimeMillis()).isEqualTo(123456);
    assertThat(updatedReport.getModuleInfoList().get(0).getTeardownTimeMillis()).isEqualTo(654321);
    assertThat(updatedReport.getModuleInfoList().get(1).getPrepTimeMillis()).isEqualTo(321);
    assertThat(updatedReport.getModuleInfoList().get(1).getTeardownTimeMillis()).isEqualTo(123);
  }

  @Test
  public void parseXmlReports() throws Exception {
    List<ParseResult> res =
        reportMerger.parseResultBundles(
            ImmutableList.of(
                TradefedResultBundle.of(Path.of(CTS_TEST_RESULT_XML), Optional.empty()),
                TradefedResultBundle.of(Path.of(CTS_TEST_RESULT_XML_2), Optional.empty())));

    assertThat(res).hasSize(2);
    assertThat(res.get(0).report().get().getModuleInfoList()).hasSize(2);
    assertThat(res.get(1).report().get().getModuleInfoList()).hasSize(3);
  }

  @Test
  public void mergeXmlReports() throws Exception {
    Optional<Result> res =
        reportMerger.mergeXmlReports(
            ImmutableList.of(Path.of(CTS_TEST_RESULT_XML_2), Path.of(CTS_TEST_RESULT_XML)));

    assertThat(res).isPresent();
    Result result = res.get();

    assertThat(result.getAttributeList())
        .containsExactly(
            Attribute.newBuilder().setKey("start").setValue("1678951330449").build(),
            Attribute.newBuilder().setKey("end").setValue("1680772548260").build(),
            Attribute.newBuilder()
                .setKey("start_display")
                .setValue("Thu Mar 16 00:22:10 PDT 2023")
                .build(),
            Attribute.newBuilder()
                .setKey("end_display")
                .setValue("Thu Apr 06 17:15:48 CST 2023")
                .build(),
            Attribute.newBuilder().setKey("devices").setValue("12241FDD4002Z6").build());
    assertThat(result.getBuild())
        .isEqualTo(
            BuildInfo.newBuilder()
                .setBuildFingerprint(
                    "google/bramble/bramble:UpsideDownCake/UP1A.220722.002/8859461:userdebug/dev-keys")
                .addAttribute(
                    Attribute.newBuilder()
                        .setKey("device_kernel_info")
                        .setValue(
                            "Linux localhost 5.10.149-android13-4-693040-g6422af733678-ab9739629 #1"
                                + " SMP PREEMPT Fri Mar 10 01:44:38 UTC 2023 aarch64 Toybox"))
                .addAttribute(
                    Attribute.newBuilder()
                        .setKey("build_fingerprint")
                        .setValue(
                            "google/bramble/bramble:UpsideDownCake/UP1A.220722.002/8859461:userdebug/dev-keys"))
                .build());
    assertThat(result.getRunHistory().getRunCount()).isEqualTo(4);
    assertThat(result.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(20)
                .setFailed(2)
                .setModulesDone(3)
                .setModulesTotal(3)
                .build());
    assertThat(result.getModuleInfoCount()).isEqualTo(3);

    // Assert module mergion
    Module module1 = result.getModuleInfoList().get(0);
    assertThat(module1.getName()).isEqualTo("Module1");
    assertThat(module1.getRuntimeMillis()).isEqualTo(7495 + 7495);
    assertThat(module1.getPassed()).isEqualTo(6);
    assertThat(module1.getTotalTests()).isEqualTo(8);
    assertThat(module1.getTestCaseCount()).isEqualTo(2);
    assertThat(module1.getTestCase(0).getTestCount()).isEqualTo(4);
    assertThat(module1.getTestCase(1).getTestCount()).isEqualTo(4);
  }

  @Test
  public void mergeXmlReports_differentBuildFingerprintFound_throwException() throws Exception {
    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        reportMerger.mergeXmlReports(
                            ImmutableList.of(
                                Path.of(CTS_TEST_RESULT_XML_2), Path.of(CTS_TEST_RESULT_XML_3))))
                .getErrorId())
        .isEqualTo(ExtErrorId.REPORT_MERGER_DIFF_DEVICE_BUILD_FINGERPRINT_FOUND);
  }

  @Test
  public void parseMoblyReports() throws Exception {
    List<ParseResult> res =
        reportMerger.parseMoblyReports(
            ImmutableList.of(
                MoblyReportInfo.of(
                    "mobly-package-1",
                    "module-abi",
                    "module-parameter",
                    Path.of(MOBLY_TEST_SUMMARY_FILE_1),
                    Path.of(MOBLY_RESULT_ATTR_FILE_1),
                    DEVICE_BUILD_FINGERPRINT,
                    Path.of(MOBLY_BUILD_ATTR_FILE_1),
                    Path.of(MODULE_RESULT_FILE_1)),
                MoblyReportInfo.of(
                    "mobly-package-2",
                    "module-abi",
                    "module-parameter",
                    Path.of(MOBLY_TEST_SUMMARY_FILE_2),
                    Path.of(MOBLY_RESULT_ATTR_FILE_2),
                    DEVICE_BUILD_FINGERPRINT,
                    Path.of(MOBLY_BUILD_ATTR_FILE_2),
                    Path.of(MODULE_RESULT_FILE_2))));

    assertThat(res).hasSize(2);
    assertThat(res.get(0).report().get().getModuleInfoList()).hasSize(1);
    assertThat(res.get(1).report().get().getModuleInfoList()).hasSize(1);
  }

  @Test
  public void mergeMoblyReports() throws Exception {
    Optional<Result> res =
        reportMerger.mergeMoblyReports(
            ImmutableList.of(
                MoblyReportInfo.of(
                    "mobly-package-1",
                    "module-abi",
                    "module-parameter",
                    Path.of(MOBLY_TEST_SUMMARY_FILE_1),
                    Path.of(MOBLY_RESULT_ATTR_FILE_1),
                    DEVICE_BUILD_FINGERPRINT,
                    Path.of(MOBLY_BUILD_ATTR_FILE_1),
                    Path.of(MODULE_RESULT_FILE_1)),
                MoblyReportInfo.of(
                    "mobly-package-2",
                    "module-abi",
                    "module-parameter",
                    Path.of(MOBLY_TEST_SUMMARY_FILE_2),
                    Path.of(MOBLY_RESULT_ATTR_FILE_2),
                    DEVICE_BUILD_FINGERPRINT,
                    Path.of(MOBLY_BUILD_ATTR_FILE_2),
                    Path.of(MODULE_RESULT_FILE_2))));

    assertThat(res).isPresent();
    Result result = res.get();

    assertThat(result.getAttributeList())
        .containsExactly(
            Attribute.newBuilder()
                .setKey("result_attr1_key")
                .setValue("result_attr1_value")
                .build(),
            Attribute.newBuilder()
                .setKey("result_attr2_key")
                .setValue("result_attr2_value")
                .build(),
            Attribute.newBuilder().setKey("start").setValue("").build(),
            Attribute.newBuilder().setKey("end").setValue("").build(),
            Attribute.newBuilder().setKey("start_display").setValue("").build(),
            Attribute.newBuilder().setKey("end_display").setValue("").build(),
            Attribute.newBuilder().setKey("devices").setValue("").build());
    assertThat(result.getBuild())
        .isEqualTo(
            BuildInfo.newBuilder()
                .setBuildFingerprint(DEVICE_BUILD_FINGERPRINT)
                .addAttribute(
                    Attribute.newBuilder().setKey("build_attr1_key").setValue("build_attr1_value"))
                .addAttribute(
                    Attribute.newBuilder().setKey("build_attr2_key").setValue("build_attr2_value"))
                .build());
    assertThat(result.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(1)
                .setFailed(3)
                .setModulesDone(2)
                .setModulesTotal(2)
                .build());
    assertThat(result.getModuleInfoCount()).isEqualTo(2);
  }

  private Metric createBasicMetric(long value) {
    return Metric.newBuilder()
        .setMeasurements(Measurements.newBuilder().setSingleInt(value))
        .build();
  }
}
