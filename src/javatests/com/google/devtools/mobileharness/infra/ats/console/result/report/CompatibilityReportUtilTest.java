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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.BuildInfo;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Reason;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportUtil.TestCounts;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompatibilityReportUtilTest {

  @Test
  public void isAllowedBuildAttribute_matchesValidXmlNcNameAndAllowlist() {
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute("build_fingerprint")).isTrue();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute("csr_strongbox")).isTrue();
    assertThat(
            CompatibilityReportUtil.isAllowedBuildAttribute(
                "keymint_key_attestation.root_of_trust.verified_boot_hash"))
        .isTrue();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute(XmlConstants.ADB_VERSION_ATTR))
        .isTrue();
    assertThat(
            CompatibilityReportUtil.isAllowedBuildAttribute(XmlConstants.DEVICE_KERNEL_INFO_ATTR))
        .isTrue();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute(XmlConstants.SYSTEM_IMG_INFO_ATTR))
        .isTrue();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute(XmlConstants.VENDOR_IMG_INFO_ATTR))
        .isTrue();

    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute("")).isFalse();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute("command_line_args")).isFalse();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute("invocation-id")).isFalse();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute("root_of_trust.verified_boot_hash"))
        .isFalse();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute(".root_of_trust.verified_boot_hash"))
        .isFalse();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute("build_bad space")).isFalse();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute("build_bad=\"1\" injected"))
        .isFalse();
    assertThat(CompatibilityReportUtil.isAllowedBuildAttribute("csr_bad:namespace")).isFalse();
    assertThat(
            CompatibilityReportUtil.isAllowedBuildAttribute(
                "<tag>.root_of_trust.verified_boot_hash"))
        .isFalse();
  }

  @Test
  public void buildBuildInfo_trimsFingerprintsAndOrdersNonBuildBeforeBuildAttributes() {
    Map<String, String> attrs = new LinkedHashMap<>();
    attrs.put("build_product", "husky");
    attrs.put("build_fingerprint", "  google/husky/husky:16/BP2A:user/release-keys  ");
    attrs.put("build_fingerprint_unaltered", " google/husky/husky:16/ORIG:user/release-keys ");
    attrs.put("build_vendor_fingerprint", " google/husky/husky_vendor:16/VND:user/release-keys ");
    attrs.put("system_img_info", "sys-img");
    attrs.put("adb_version", "1.0.41");

    BuildInfo buildInfo = CompatibilityReportUtil.buildBuildInfo(attrs);

    assertThat(buildInfo.getBuildFingerprint())
        .isEqualTo("google/husky/husky:16/BP2A:user/release-keys");
    assertThat(buildInfo.getBuildFingerprintUnaltered())
        .isEqualTo("google/husky/husky:16/ORIG:user/release-keys");
    assertThat(buildInfo.getBuildVendorFingerprint())
        .isEqualTo("google/husky/husky_vendor:16/VND:user/release-keys");
    assertThat(buildInfo.getAttributeList())
        .containsExactly(
            Attribute.newBuilder().setKey("adb_version").setValue("1.0.41").build(),
            Attribute.newBuilder().setKey("system_img_info").setValue("sys-img").build(),
            Attribute.newBuilder()
                .setKey("build_fingerprint")
                .setValue("  google/husky/husky:16/BP2A:user/release-keys  ")
                .build(),
            Attribute.newBuilder()
                .setKey("build_fingerprint_unaltered")
                .setValue(" google/husky/husky:16/ORIG:user/release-keys ")
                .build(),
            Attribute.newBuilder().setKey("build_product").setValue("husky").build(),
            Attribute.newBuilder()
                .setKey("build_vendor_fingerprint")
                .setValue(" google/husky/husky_vendor:16/VND:user/release-keys ")
                .build())
        .inOrder();
  }

  @Test
  public void buildOrderedResultAttributes_pinsTimestampsFirstAndTrimsValuesWithoutMutatingInput() {
    Map<String, String> input = new LinkedHashMap<>();
    input.put("suite_name", " CTS ");
    input.put("end_display", " Thu Mar 16 00:23:15 UTC 2023 ");
    input.put("command_line_args", " cts -m CtsMedia ");
    input.put("start", " 100 ");
    input.put("end", " 200 ");

    ImmutableList<Attribute> ordered = CompatibilityReportUtil.buildOrderedResultAttributes(input);

    assertThat(ordered)
        .containsExactly(
            Attribute.newBuilder().setKey("start").setValue("100").build(),
            Attribute.newBuilder().setKey("end").setValue("200").build(),
            Attribute.newBuilder()
                .setKey("end_display")
                .setValue("Thu Mar 16 00:23:15 UTC 2023")
                .build(),
            Attribute.newBuilder().setKey("command_line_args").setValue("cts -m CtsMedia").build(),
            Attribute.newBuilder().setKey("suite_name").setValue("CTS").build())
        .inOrder();
    assertThat(input).hasSize(5);
  }

  @Test
  public void resolveSuiteVariant_mapsCtsOnGsiAndDefaultsOtherwise() {
    assertThat(CertificationSuiteInfoFactory.resolveSuiteVariant("cts-on-gsi", "CTS"))
        .isEqualTo("CTS_ON_GSI");
    assertThat(CertificationSuiteInfoFactory.resolveSuiteVariant("cts", "CTS")).isEqualTo("CTS");
  }

  @Test
  public void toSuiteAttributesMap_preservesExpectedAttributeOrder() {
    CertificationSuiteInfo suiteInfo =
        CertificationSuiteInfo.builder()
            .setSuiteName("CTS")
            .setSuiteVariant("CTS_ON_GSI")
            .setSuiteVersion("16_r1")
            .setSuitePlan("cts-on-gsi")
            .setSuiteBuild("12345")
            .setSuiteReportVersion(CertificationSuiteInfoFactory.SUITE_REPORT_VERSION)
            .build();

    assertThat(CompatibilityReportUtil.toSuiteAttributesMap(suiteInfo))
        .containsExactly(
            XmlConstants.SUITE_NAME_ATTR,
            "CTS",
            XmlConstants.SUITE_VARIANT_ATTR,
            "CTS_ON_GSI",
            XmlConstants.SUITE_VERSION_ATTR,
            "16_r1",
            XmlConstants.SUITE_PLAN_ATTR,
            "cts-on-gsi",
            XmlConstants.SUITE_BUILD_ATTR,
            "12345",
            XmlConstants.SUITE_REPORT_VERSION_ATTR,
            "5.0")
        .inOrder();
  }

  @Test
  public void buildHostAttributes_mapsHostAndSystemPropertiesInOrder() {
    ImmutableMap<String, String> sysProps =
        ImmutableMap.of(
            "os.name", "Linux",
            "os.version", "6.6",
            "os.arch", "amd64",
            "java.vendor", "Google",
            "java.version", "17.0.10");

    assertThat(
            CompatibilityReportUtil.buildHostAttributes(
                "host.example.com", key -> sysProps.getOrDefault(key, "")))
        .containsExactly(
            XmlConstants.HOST_NAME_ATTR,
            "host.example.com",
            XmlConstants.OS_NAME_ATTR,
            "Linux",
            XmlConstants.OS_VERSION_ATTR,
            "6.6",
            XmlConstants.OS_ARCH_ATTR,
            "amd64",
            XmlConstants.JAVA_VENDOR_ATTR,
            "Google",
            XmlConstants.JAVA_VERSION_ATTR,
            "17.0.10")
        .inOrder();
  }

  @Test
  public void pickPreferredTestAttempt_prefersFirstPassOtherwiseRetainsExisting() {
    ReportProto.Test pass1 =
        ReportProto.Test.newBuilder().setName("testA").setResult("pass").build();
    ReportProto.Test pass2 =
        ReportProto.Test.newBuilder()
            .setName("testA")
            .setResult("pass")
            .addMetric(ReportProto.Metric.newBuilder().setKey("attempt").setContent("2"))
            .build();
    ReportProto.Test fail1 =
        ReportProto.Test.newBuilder().setName("testA").setResult("fail").build();
    ReportProto.Test fail2 =
        ReportProto.Test.newBuilder().setName("testA").setResult("fail").setSkipped(true).build();

    assertThat(CompatibilityReportUtil.pickPreferredTestAttempt(fail1, pass1)).isEqualTo(pass1);
    assertThat(CompatibilityReportUtil.pickPreferredTestAttempt(pass1, pass2)).isEqualTo(pass1);
    assertThat(CompatibilityReportUtil.pickPreferredTestAttempt(fail1, fail2)).isEqualTo(fail1);
  }

  @Test
  public void countAndPopulateModuleTestCounts_aggregatesPassFailWarningAndTotal() {
    Module.Builder moduleBuilder =
        Module.newBuilder()
            .setName("CtsSample")
            .addTestCase(
                TestCase.newBuilder()
                    .setName("android.cts.SampleTest")
                    .addTest(ReportProto.Test.newBuilder().setName("t1").setResult("pass"))
                    .addTest(ReportProto.Test.newBuilder().setName("t2").setResult("fail"))
                    .addTest(ReportProto.Test.newBuilder().setName("t3").setResult("warning"))
                    .addTest(ReportProto.Test.newBuilder().setName("t4").setResult("IGNORED")));

    TestCounts counts = CompatibilityReportUtil.countModuleTests(moduleBuilder.getTestCaseList());
    assertThat(counts).isEqualTo(new TestCounts(1, 1, 1, 4));

    CompatibilityReportUtil.populateModuleTestCounts(moduleBuilder);
    Module built = moduleBuilder.build();
    assertThat(built.getPassed()).isEqualTo(1);
    assertThat(built.getFailedTests()).isEqualTo(1);
    assertThat(built.getWarningTests()).isEqualTo(1);
    assertThat(built.getTotalTests()).isEqualTo(4);
  }

  @Test
  public void isModuleCountedInSummary_excludesSkippedAndModuleCheckers() {
    assertThat(
            CompatibilityReportUtil.isModuleCountedInSummary(
                Module.newBuilder().setName("CtsDone").setDone(true).build()))
        .isTrue();
    assertThat(
            CompatibilityReportUtil.isModuleCountedInSummary(
                Module.newBuilder().setName("CtsIncomplete").setDone(false).build()))
        .isTrue();
    assertThat(
            CompatibilityReportUtil.isModuleCountedInSummary(
                Module.newBuilder().setName("CtsSkipped").setDone(true).setSkipped(true).build()))
        .isFalse();
    assertThat(
            CompatibilityReportUtil.isModuleCountedInSummary(
                Module.newBuilder().setName("PreModuleChecker_CtsDone").setDone(true).build()))
        .isFalse();
    assertThat(
            CompatibilityReportUtil.isModuleCountedInSummary(
                Module.newBuilder().setName("PostModuleChecker_CtsDone").setDone(true).build()))
        .isFalse();
  }

  @Test
  public void createSkippedModule_emptyAndPresentAbi_setsOptionalAbiOnlyWhenNonEmpty() {
    Module hostSkipped = CompatibilityReportUtil.createSkippedModule("", "HostMod", "Host skip");
    assertThat(hostSkipped.hasAbi()).isFalse();
    assertThat(hostSkipped.getDone()).isTrue();
    assertThat(hostSkipped.getSkipped()).isTrue();
    assertThat(hostSkipped.getReason()).isEqualTo(Reason.newBuilder().setMsg("Host skip").build());

    Module abiSkipped =
        CompatibilityReportUtil.createSkippedModule(
            "arm64-v8a",
            "CtsDevMod",
            CompatibilityReportUtil.formatControllerSkippedReason("CtsDevMod"));
    assertThat(abiSkipped.getAbi()).isEqualTo("arm64-v8a");
    assertThat(abiSkipped.getReason().getMsg())
        .isEqualTo("Module CtsDevMod is skipped by module controller.");
  }

  @Test
  public void truncateStackTrace_utf16SurrogateBoundary_doesNotSplitSurrogatePair() {
    int max = CompatibilityReportUtil.STACK_TRACE_MAX_SIZE;
    String exact = "a".repeat(max);
    assertThat(CompatibilityReportUtil.truncateStackTrace(exact)).hasLength(max);

    String asciiOverflow = "b".repeat(max + 10);
    assertThat(CompatibilityReportUtil.truncateStackTrace(asciiOverflow)).hasLength(max);

    String splitSurrogate = "c".repeat(max - 1) + "\uD83D\uDE00tail";
    String truncatedSurrogate = CompatibilityReportUtil.truncateStackTrace(splitSurrogate);
    assertThat(truncatedSurrogate).hasLength(max - 1);
    assertThat(
            Character.isHighSurrogate(truncatedSurrogate.charAt(truncatedSurrogate.length() - 1)))
        .isFalse();
  }
}
