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

package com.google.devtools.mobileharness.infra.ats.console.result.incremental;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static java.util.function.Function.identity;

import com.android.tradefed.build.proto.BuildInformation;
import com.android.tradefed.config.proto.ConfigurationDescription.Metadata;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.result.proto.TestRecordProto.ChildReference;
import com.android.tradefed.result.proto.TestRecordProto.DebugInfo;
import com.android.tradefed.result.proto.TestRecordProto.DebugInfoContext;
import com.android.tradefed.result.proto.TestRecordProto.FailureStatus;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord.SkipReason;
import com.android.tradefed.result.proto.TestRecordProto.TestStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.result.incremental.IncrementalResultConverter.HostEnvironment;
import com.google.devtools.mobileharness.infra.ats.console.result.incremental.IncrementalResultConverter.InvocationPlan;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Metric;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Reason;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Result;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.StackTrace;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Summary;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestCase;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.TestFailure;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportCreator;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportModule;
import com.google.devtools.mobileharness.infra.ats.console.result.report.CompatibilityReportParser;
import com.google.devtools.mobileharness.infra.ats.console.result.xml.XmlConstants;
import com.google.devtools.mobileharness.infra.ats.console.util.TestRunfilesUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.shared.util.inject.CommonModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.Timestamp;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class IncrementalResultConverterTest {

  private static final String GOLDEN_COMPLETE_RUN_INVOCATION =
      "result/incremental/testdata/complete_run/" + XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME;
  private static final String TEST_FINGERPRINT =
      "google/husky/husky:16/BP2A.250605.031/12999999:user/release-keys";

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private final IncrementalResultConverter converter = new IncrementalResultConverter();

  private enum InvalidUnitsRunCase {
    MISSING(null),
    NON_NUMERIC("not_a_number"),
    NEGATIVE("-1");

    @Nullable final String rawValue;

    InvalidUnitsRunCase(@Nullable String rawValue) {
      this.rawValue = rawValue;
    }
  }

  private enum MalformedPlannedModuleCase {
    EMPTY_ENTRY(""),
    MISSING_COLON("missing_colon"),
    EMPTY_MODULE_ID(":2"),
    BLANK_MODULE_ID("   :2"),
    NON_NUMERIC_COUNT("arm64-v8a ModBadInt:abc"),
    EMPTY_COUNT("arm64-v8a ModEmptyCount:"),
    ZERO_COUNT("arm64-v8a ModZero:0"),
    NEGATIVE_COUNT("arm64-v8a ModNegative:-2");

    final String rawEntry;

    MalformedPlannedModuleCase(String rawEntry) {
      this.rawEntry = rawEntry;
    }
  }

  @Test
  public void parseInvocation_goldenCompleteRun_unpacksContextAndPlannedModules() throws Exception {
    Path storeDir = getGoldenCompleteRunDir();

    Optional<InvocationPlan> plan = converter.parseInvocation(storeDir);

    assertThat(plan).isPresent();
    assertThat(plan.get().context().getTestTag()).isEqualTo("cts");
    assertThat(plan.get().context().getNameBuildInfoMap()).containsKey("DEFAULT_DEVICE");
    assertThat(plan.get().context().getMetadataList())
        .contains(
            Metadata.newBuilder()
                .setKey("command_line_args")
                .addValue("cts -m CtsMediaTestCases")
                .build());
    assertThat(plan.get().plannedCountsByModule())
        .containsExactly("arm64-v8a CtsMediaTestCases", 1);
  }

  @Test
  public void parseInvocation_plannedModulesFormats_preservesOrderSumsDuplicatesAndSkipsInvalid()
      throws Exception {
    Path storeDir = tempFolder.newFolder("planned_formats").toPath();
    Context context =
        Context.newBuilder()
            .setTestTag("cts")
            .addMetadata(
                Metadata.newBuilder()
                    .setKey(XtsConstants.PLANNED_MODULES_ATTR)
                    .addValue("arm64-v8a CtsMediaTestCases:2")
                    .addValue("CtsGestureTestCases:1")
                    .addValue("arm64-v8a CtsAppSecurityHostTestCases[instant]:2")
                    .addValue("x86_64 CtsCustom:SubModule[param:v1]:4")
                    .addValue("")
                    .addValue("missing_colon")
                    .addValue(":3")
                    .addValue("   :2")
                    .addValue("arm64-v8a ModBadInt:abc")
                    .addValue("arm64-v8a ModEmptyCount:")
                    .addValue("arm64-v8a ModZero:0")
                    .addValue("arm64-v8a ModNegative:-2"))
            .addMetadata(
                Metadata.newBuilder()
                    .setKey(XtsConstants.PLANNED_MODULES_ATTR)
                    .addValue("arm64-v8a CtsMediaTestCases:3"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-formats")
            .setDescription(Any.pack(context))
            .build());

    Optional<InvocationPlan> plan = converter.parseInvocation(storeDir);

    assertThat(plan).isPresent();
    assertThat(plan.get().plannedCountsByModule())
        .containsExactly(
            "arm64-v8a CtsMediaTestCases",
            5,
            "CtsGestureTestCases",
            1,
            "arm64-v8a CtsAppSecurityHostTestCases[instant]",
            2,
            "x86_64 CtsCustom:SubModule[param:v1]",
            4)
        .inOrder();
  }

  @Test
  public void parseInvocation_missingDescription_returnsDefaultContextAndEmptyMap()
      throws Exception {
    Path noPlanDir = tempFolder.newFolder("no_plan").toPath();
    writeDelimitedRecord(
        noPlanDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder().setTestRecordId("inv-no-desc").build());

    Optional<InvocationPlan> emptyPlan = converter.parseInvocation(noPlanDir);

    assertThat(emptyPlan).isPresent();
    assertThat(emptyPlan.get().context()).isEqualToDefaultInstance();
    assertThat(emptyPlan.get().plannedCountsByModule()).isEmpty();
  }

  @Test
  public void parseInvocation_absentOrStagingInvocationFile_returnsEmpty() throws Exception {
    Path emptyDir = tempFolder.newFolder("empty_store").toPath();
    Path nonExistentDir = tempFolder.getRoot().toPath().resolve("does_not_exist");
    Path tmpOnlyDir = tempFolder.newFolder("tmp_only_store").toPath();
    writeDelimitedRecord(
        tmpOnlyDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME + ".tmp"),
        TestRecord.newBuilder()
            .setTestRecordId("inv-tmp")
            .setDescription(Any.pack(Context.newBuilder().setTestTag("cts").build()))
            .build());
    Path dirNamedInvocationPb = tempFolder.newFolder("dir_named_invocation").toPath();
    Files.createDirectory(
        dirNamedInvocationPb.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME));

    assertThat(converter.parseInvocation(emptyDir)).isEmpty();
    assertThat(converter.parseInvocation(nonExistentDir)).isEmpty();
    assertThat(converter.parseInvocation(tmpOnlyDir)).isEmpty();
    assertThat(converter.parseInvocation(dirNamedInvocationPb)).isEmpty();
  }

  @Test
  public void parseInvocation_corruptOrEmptyProtoFile_returnsEmpty() throws Exception {
    Path corruptDir = tempFolder.newFolder("corrupt_proto").toPath();
    Files.write(
        corruptDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        new byte[] {0x0A, 0x05, 0x01});
    Path emptyFileDir = tempFolder.newFolder("empty_proto_file").toPath();
    Files.write(emptyFileDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME), new byte[0]);

    assertThat(converter.parseInvocation(corruptDir)).isEmpty();
    assertThat(converter.parseInvocation(emptyFileDir)).isEmpty();
  }

  @Test
  public void parseInvocation_invalidOrCorruptDescription_returnsDefaultContextAndEmptyMap()
      throws Exception {
    Path wrongTypeDir = tempFolder.newFolder("invalid_any_type").toPath();
    Metadata wrongMessage = Metadata.newBuilder().setKey("wrong").build();
    writeDelimitedRecord(
        wrongTypeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-bad-any")
            .setDescription(Any.pack(wrongMessage))
            .build());

    Path corruptBytesDir = tempFolder.newFolder("corrupt_context_bytes").toPath();
    Any corruptContextAny =
        Any.newBuilder()
            .setTypeUrl("type.googleapis.com/tradefed.invoker.Context")
            .setValue(ByteString.copyFrom(new byte[] {0x00}))
            .build();
    writeDelimitedRecord(
        corruptBytesDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-corrupt-ctx")
            .setDescription(corruptContextAny)
            .build());

    Optional<InvocationPlan> wrongTypePlan = converter.parseInvocation(wrongTypeDir);
    Optional<InvocationPlan> corruptBytesPlan = converter.parseInvocation(corruptBytesDir);

    assertThat(wrongTypePlan).isPresent();
    assertThat(wrongTypePlan.get().context()).isEqualToDefaultInstance();
    assertThat(wrongTypePlan.get().plannedCountsByModule()).isEmpty();
    assertThat(corruptBytesPlan).isPresent();
    assertThat(corruptBytesPlan.get().context()).isEqualToDefaultInstance();
    assertThat(corruptBytesPlan.get().plannedCountsByModule()).isEmpty();
  }

  @Test
  public void convert_goldenCompleteRun_convertsAllFieldsAndRoundTripsXml() throws Exception {
    Path storeDir = getGoldenCompleteRunDir();
    ImmutableMap<String, String> fixedProps =
        ImmutableMap.of(
            "os.name", "Linux",
            "os.version", "6.6.0",
            "os.arch", "amd64",
            "java.vendor", "Google",
            "java.version", "17.0.10");
    IncrementalResultConverter hermeticConverter =
        new IncrementalResultConverter(
            new HostEnvironment(
                ZoneOffset.UTC,
                () -> "test-host.example.com",
                key -> fixedProps.getOrDefault(key, "")));

    Result result = hermeticConverter.convert(storeDir).orElseThrow();

    assertThat(result.getBuild().getBuildFingerprint())
        .isEqualTo("google/husky/husky:16/BP2A.250605.031/12999999:user/release-keys");
    assertThat(result.getBuild().getBuildFingerprintUnaltered())
        .isEqualTo("google/husky/husky:16/BP2A.250605.031/12999999:user/release-keys");
    assertThat(result.getBuild().getBuildVendorFingerprint())
        .isEqualTo("google/husky/husky:16/BP2A.250605.031/12999999:vendor/release-keys");
    assertThat(result.getBuild().getAttributeList())
        .containsExactly(
            Attribute.newBuilder().setKey("adb_version").setValue("1.0.41").build(),
            Attribute.newBuilder().setKey("csr_default").setValue("csr_payload").build(),
            Attribute.newBuilder()
                .setKey("device_kernel_info")
                .setValue("Linux localhost 6.1.99-android15")
                .build(),
            Attribute.newBuilder()
                .setKey("keymint.root_of_trust.verified_boot_hash")
                .setValue("deadbeef1234")
                .build(),
            Attribute.newBuilder()
                .setKey("system_img_info")
                .setValue("google/husky/husky:16/system")
                .build(),
            Attribute.newBuilder()
                .setKey("vendor_img_info")
                .setValue("google/husky/husky:16/vendor")
                .build(),
            Attribute.newBuilder().setKey("build_abis").setValue("arm64-v8a,armeabi-v7a").build(),
            Attribute.newBuilder()
                .setKey("build_fingerprint")
                .setValue("google/husky/husky:16/BP2A.250605.031/12999999:user/release-keys")
                .build(),
            Attribute.newBuilder()
                .setKey("build_fingerprint_unaltered")
                .setValue("google/husky/husky:16/BP2A.250605.031/12999999:user/release-keys")
                .build(),
            Attribute.newBuilder().setKey("build_model").setValue("Pixel 8 Pro").build(),
            Attribute.newBuilder().setKey("build_serial").setValue("39011FDJH000AA").build(),
            Attribute.newBuilder()
                .setKey("build_vendor_fingerprint")
                .setValue("google/husky/husky:16/BP2A.250605.031/12999999:vendor/release-keys")
                .build())
        .inOrder();

    ImmutableMap<String, String> resultAttrs = toAttrMap(result.getAttributeList());
    assertThat(resultAttrs).containsEntry(XmlConstants.START_TIME_ATTR, "1700000000000");
    assertThat(resultAttrs).containsEntry(XmlConstants.END_TIME_ATTR, "1700000008000");
    assertThat(resultAttrs)
        .containsEntry(XmlConstants.START_DISPLAY_TIME_ATTR, "Tue Nov 14 22:13:20 UTC 2023");
    assertThat(resultAttrs)
        .containsEntry(XmlConstants.END_DISPLAY_TIME_ATTR, "Tue Nov 14 22:13:28 UTC 2023");
    assertThat(resultAttrs)
        .containsEntry(XmlConstants.COMMAND_LINE_ARGS, "cts -m CtsMediaTestCases");
    assertThat(resultAttrs).containsEntry(XmlConstants.DEVICES_ATTR, "39011FDJH000AA");
    assertThat(resultAttrs).containsEntry(XmlConstants.SUITE_NAME_ATTR, "CTS");
    assertThat(resultAttrs).containsEntry(XmlConstants.SUITE_VARIANT_ATTR, "CTS");
    assertThat(resultAttrs).containsEntry(XmlConstants.SUITE_VERSION_ATTR, "16_r1");
    assertThat(resultAttrs).containsEntry(XmlConstants.SUITE_PLAN_ATTR, "cts");
    assertThat(resultAttrs).containsEntry(XmlConstants.SUITE_BUILD_ATTR, "12345678");
    assertThat(resultAttrs).containsEntry(XmlConstants.SUITE_REPORT_VERSION_ATTR, "5.0");
    assertThat(resultAttrs).containsEntry(XmlConstants.HOST_NAME_ATTR, "test-host.example.com");
    assertThat(resultAttrs).containsEntry(XmlConstants.OS_NAME_ATTR, "Linux");
    assertThat(resultAttrs).containsEntry(XmlConstants.JAVA_VERSION_ATTR, "17.0.10");

    assertThat(result.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(1)
                .setFailed(1)
                .setWarning(0)
                .setModulesDone(1)
                .setModulesTotal(1)
                .build());

    assertThat(result.getModuleInfoList()).hasSize(1);
    Module module = result.getModuleInfo(0);
    assertThat(module.getName()).isEqualTo("CtsMediaTestCases");
    assertThat(module.getAbi()).isEqualTo("arm64-v8a");
    assertThat(module.getDone()).isTrue();
    assertThat(module.getRuntimeMillis()).isEqualTo(4250L);
    assertThat(module.getPrepTimeMillis()).isEqualTo(350L);
    assertThat(module.getTeardownTimeMillis()).isEqualTo(150L);
    assertThat(module.getPassed()).isEqualTo(1);
    assertThat(module.getFailedTests()).isEqualTo(1);
    assertThat(module.getTotalTests()).isEqualTo(4);
    assertThat(module.hasReason()).isFalse();

    TestCase audioCase = module.getTestCase(0);
    assertThat(audioCase.getName()).isEqualTo("android.media.cts.AudioTrackTest");
    assertThat(audioCase.getTest(0).getName()).isEqualTo("testPlayStream");
    assertThat(audioCase.getTest(0).getResult()).isEqualTo("pass");
    assertThat(audioCase.getTest(0).getMetricList())
        .containsExactly(
            Metric.newBuilder().setKey("single_str").setContent("ok&lt;tag&gt;&amp;val").build(),
            Metric.newBuilder().setKey("single_int").setContent("42").build(),
            Metric.newBuilder().setKey("single_double").setContent("3.5").build(),
            Metric.newBuilder().setKey("str_list").setContent("a,b&amp;c").build(),
            Metric.newBuilder().setKey("num_list").setContent("10,20,30").build(),
            Metric.newBuilder().setKey("dbl_list").setContent("1.25,2.5").build())
        .inOrder();

    assertThat(audioCase.getTest(1).getName()).isEqualTo("testBufferUnderrun");
    assertThat(audioCase.getTest(1).getResult()).isEqualTo("fail");
    assertThat(audioCase.getTest(1).getFailure())
        .isEqualTo(
            TestFailure.newBuilder()
                .setMsg(
                    "java.lang.AssertionError: expected:&lt;10&gt; &amp; &lt;20&gt; but"
                        + " was:&lt;5&gt;")
                .setStackTrace(
                    StackTrace.newBuilder()
                        .setContent(
                            "java.lang.AssertionError: expected:&lt;10&gt; &amp; &lt;20&gt; but"
                                + " was:&lt;5&gt;\n"
                                + "\tat org.junit.Assert.fail(Assert.java:89)\n"
                                + "\tat android.media.cts.AudioTrackTest.testBufferUnderrun"
                                + "(AudioTrackTest.java:142)"))
                .build());

    TestCase videoCase = module.getTestCase(1);
    assertThat(videoCase.getTest(0).getResult()).isEqualTo("IGNORED");
    assertThat(videoCase.getTest(0).getSkipped()).isTrue();
    assertThat(videoCase.getTest(1).getResult()).isEqualTo("ASSUMPTION_FAILURE");
    assertThat(videoCase.getTest(1).getFailure().getMsg())
        .isEqualTo("org.junit.AssumptionViolatedException: HDR &lt;10+&gt; not supported");

    Result reparsed = roundTripXml(result);
    Result expectedAfterXmlRoundTrip =
        result.toBuilder()
            .setModuleInfo(
                0, module.toBuilder().clearPrepTimeMillis().clearTeardownTimeMillis().build())
            .build();
    assertThat(reparsed).isEqualTo(expectedAfterXmlRoundTrip);
  }

  @Test
  public void convert_shardedAndHostsideModules_sortsNumericallyMergesRetriesAndKeepsPlanOrder()
      throws Exception {
    Path storeDir = tempFolder.newFolder("sharded_and_hostside").toPath();
    String modNet = "arm64-v8a CtsNetTestCases[instant]";
    String modHost = "CtsHostsideTestCases";

    Context invCtx =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes("SUITE_NAME", "CTS")
                    .putAttributes("SUITE_PLAN", "cts-on-gsi")
                    .putAttributes("cts:build_fingerprint", TEST_FINGERPRINT)
                    .putAttributes("cts:build_serial", "CAIMAN01")
                    .build())
            .addMetadata(
                Metadata.newBuilder()
                    .setKey(XtsConstants.PLANNED_MODULES_ATTR)
                    .addValue(modNet + ":3")
                    .addValue(modHost + ":1"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder().setTestRecordId("inv-s").setDescription(Any.pack(invCtx)).build());

    // Omit module-abi to also verify ABI inference alongside base module-name + '[instant]'.
    Context netCtx =
        Context.newBuilder()
            .setTestTag("cts")
            .addMetadata(Metadata.newBuilder().setKey("module-id").addValue(modNet))
            .addMetadata(Metadata.newBuilder().setKey("module-name").addValue("CtsNetTestCases"))
            .addMetadata(
                Metadata.newBuilder().setKey(XtsConstants.MODULE_UNITS_RUN_ATTR).addValue("1"))
            .build();

    TestRecord skippedByDemotion =
        TestRecord.newBuilder()
            .setTestRecordId("android.net.cts.ClassATest#testDemoted")
            .setStatus(TestStatus.PASS)
            .setSkipReason(
                SkipReason.newBuilder().setReason("Demoted by SkipManager").setTrigger("DEMOTION"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsNetTestCases_instant__0.pb"),
        newModuleRecord(
            modNet,
            netCtx,
            newRunRecord(
                modNet,
                /* startSec= */ 10L,
                /* endSec= */ 10L,
                /* prepMs= */ 100L,
                /* teardownMs= */ 50L,
                ImmutableList.of(
                    failedTest("android.net.cts.ClassATest#testFlaky", "attempt0 fail"),
                    passedTest("android.net.cts.ClassATest#testPassFirst"),
                    skippedByDemotion)),
            // Intra-shard auto-retry TestRun repeats the shard's identical PREP_TIME/TEARDOWN_TIME.
            newRunRecord(
                modNet,
                /* startSec= */ 10L,
                /* endSec= */ 11L,
                /* prepMs= */ 100L,
                /* teardownMs= */ 50L,
                ImmutableList.of(
                    failedTest("android.net.cts.ClassATest#testFlaky", "initial fail")))));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsNetTestCases_instant__2.pb"),
        newModuleRecord(
            modNet,
            netCtx,
            newRunRecord(
                modNet,
                /* startSec= */ 12L,
                /* endSec= */ 14L,
                /* prepMs= */ 200L,
                /* teardownMs= */ 75L,
                ImmutableList.of(
                    passedTest("android.net.cts.ClassATest#testFlaky"),
                    failedTest("android.net.cts.ClassATest#testPassFirst", "later fail ignored"),
                    passedTest("android.net.cts.ClassBTest#testFromShard2")))));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsNetTestCases_instant__10.pb"),
        newModuleRecord(
            modNet,
            netCtx,
            newRunRecord(
                modNet,
                /* startSec= */ 15L,
                /* endSec= */ 16L,
                /* prepMs= */ 120L,
                /* teardownMs= */ 30L,
                ImmutableList.of(passedTest("android.net.cts.ClassCTest#testFromShard10")))));

    // 'C' < 'a' on disk, so plan order must place modNet first and non-ABI modHost second.
    Context hostCtx = newModuleCtx(modHost, "", modHost, "1");
    writeDelimitedRecord(
        storeDir.resolve("CtsHostsideTestCases_0.pb"),
        newModuleRecord(
            modHost,
            hostCtx,
            newRunRecord(modHost, passedTest("android.host.cts.HostTest#testHostPing"))));

    Result result = converter.convert(storeDir).orElseThrow();

    assertThat(toAttrMap(result.getAttributeList()))
        .containsEntry(XmlConstants.SUITE_VARIANT_ATTR, "CTS_ON_GSI");
    assertThat(result.getModuleInfoList()).hasSize(2);

    Module netModule = result.getModuleInfo(0);
    assertThat(netModule.getAbi()).isEqualTo("arm64-v8a");
    assertThat(netModule.getName()).isEqualTo("CtsNetTestCases[instant]");
    assertThat(netModule.getDone()).isTrue();
    assertThat(netModule.getRuntimeMillis()).isEqualTo(4000L);
    assertThat(netModule.getPrepTimeMillis()).isEqualTo(420L);
    assertThat(netModule.getTeardownTimeMillis()).isEqualTo(155L);
    assertThat(netModule.getPassed()).isEqualTo(4);
    assertThat(netModule.getFailedTests()).isEqualTo(0);
    assertThat(netModule.getTotalTests()).isEqualTo(5);
    assertThat(netModule.getTestCase(0).getTest(2).getName()).isEqualTo("testDemoted");
    assertThat(netModule.getTestCase(0).getTest(2).getResult()).isEqualTo("SKIPPED");
    assertThat(netModule.getTestCase(0).getTest(2).getSkipped()).isTrue();
    assertThat(netModule.getTestCaseList().stream().map(TestCase::getName))
        .containsExactly(
            "android.net.cts.ClassATest",
            "android.net.cts.ClassBTest",
            "android.net.cts.ClassCTest")
        .inOrder();

    Module hostModule = result.getModuleInfo(1);
    assertThat(hostModule.getName()).isEqualTo("CtsHostsideTestCases");
    assertThat(hostModule.hasAbi()).isFalse();
    assertThat(hostModule.getDone()).isTrue();
  }

  @Test
  public void convert_buildInfoFallback_recoversTargetPreparerAttrsFromModulesAndComplete()
      throws Exception {
    Path storeDir = tempFolder.newFolder("build_info_fallback").toPath();
    String modId = "arm64-v8a CtsSensorTestCases";
    Context invCtx =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes("SUITE_NAME", "CTS")
                    .putAttributes("cts:build_fingerprint", "   ")
                    .putAttributes("device_kernel_info", "")
                    .build())
            .addMetadata(
                Metadata.newBuilder()
                    .setKey(XtsConstants.PLANNED_MODULES_ATTR)
                    .addValue(modId + ":2"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-fb")
            .setStartTime(Timestamp.newBuilder().setSeconds(30L))
            .setDescription(Any.pack(invCtx))
            .build());

    Context shard0Ctx =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes(
                        "cts:build_fingerprint",
                        "google/tokay/tokay:16/BP2A/8888:user/release-keys")
                    .putAttributes("cts:build_serial", "TOKAY001")
                    .putAttributes("device_kernel_info", "Linux localhost 6.6-tokay")
                    .build())
            .addMetadata(Metadata.newBuilder().setKey("module-id").addValue(modId))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSensorTestCases_0.pb"),
        newModuleRecord(
            modId, shard0Ctx, newRunRecord(modId, passedTest("android.cts.SensorTest#testAccel"))));

    Context shard1Ctx =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes("cts:build_serial", "TOKAY002")
                    .putAttributes("system_img_info", "google/tokay/tokay:16/system")
                    .build())
            .addMetadata(Metadata.newBuilder().setKey("module-id").addValue(modId))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSensorTestCases_1.pb"),
        newModuleRecord(
            modId, shard1Ctx, newRunRecord(modId, passedTest("android.cts.SensorTest#testGyro"))));

    Context completeCtx =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes(
                        "cts:build_vendor_fingerprint",
                        "google/tokay/tokay:16/BP2A/8888:vendor/release-keys")
                    .build())
            .addMetadata(Metadata.newBuilder().setKey("adb_version").addValue("1.0.42"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_COMPLETE_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-fb")
            .setStatus(TestStatus.PASS)
            .setEndTime(Timestamp.newBuilder().setSeconds(39L))
            .setDescription(Any.pack(completeCtx))
            .build());

    Result result = converter.convert(storeDir).orElseThrow();

    assertThat(result.getBuild().getBuildFingerprint())
        .isEqualTo("google/tokay/tokay:16/BP2A/8888:user/release-keys");
    assertThat(result.getBuild().getBuildVendorFingerprint())
        .isEqualTo("google/tokay/tokay:16/BP2A/8888:vendor/release-keys");
    ImmutableMap<String, String> buildAttrs = toAttrMap(result.getBuild().getAttributeList());
    assertThat(buildAttrs).containsEntry("device_kernel_info", "Linux localhost 6.6-tokay");
    assertThat(buildAttrs).containsEntry("system_img_info", "google/tokay/tokay:16/system");
    assertThat(buildAttrs).containsEntry("adb_version", "1.0.42");
    ImmutableMap<String, String> resultAttrs = toAttrMap(result.getAttributeList());
    assertThat(resultAttrs).containsEntry(XmlConstants.DEVICES_ATTR, "TOKAY001,TOKAY002");
    assertThat(resultAttrs).containsEntry(XmlConstants.END_TIME_ATTR, "39000");
  }

  @Test
  public void convert_sanitizesColonsAndExcludesNonAllowlistedBuildAttributes() throws Exception {
    Path storeDir = tempFolder.newFolder("build_attr_allowlist").toPath();
    BuildInformation.BuildInfo buildInfo =
        BuildInformation.BuildInfo.newBuilder()
            .putAttributes(
                "cts:build_fingerprint",
                "google/husky/husky:16/BP2A.250605.031/12999999:user/release-keys")
            .putAttributes("cts:build_serial", "39011FDJH000AA")
            .putAttributes("cts:csr_strongbox:v2", "csr_value")
            .putAttributes("device_kernel_info", "Linux localhost 6.1")
            .putAttributes("arbitrary_build_info_key", "should_be_dropped")
            .build();
    Context invContext =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo("DEFAULT_DEVICE", buildInfo)
            .addMetadata(Metadata.newBuilder().setKey("adb_version").addValue("1.0.41"))
            .addMetadata(
                Metadata.newBuilder().setKey("cts:build_product").addValue("husky_from_meta"))
            .addMetadata(
                Metadata.newBuilder()
                    .setKey("java_classpath")
                    .addValue("/tmp/jar1.jar:/tmp/jar2.jar"))
            .addMetadata(
                Metadata.newBuilder()
                    .setKey("cts:unallowed_meta_key")
                    .addValue("should_be_dropped"))
            .addMetadata(
                Metadata.newBuilder()
                    .setKey(XtsConstants.PLANNED_MODULES_ATTR)
                    .addValue("arm64-v8a CtsAllowlistMod:1"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-allowlist")
            .setStartTime(Timestamp.newBuilder().setSeconds(50L))
            .setEndTime(Timestamp.newBuilder().setSeconds(55L))
            .setDescription(Any.pack(invContext))
            .build());

    Result result = converter.convert(storeDir).orElseThrow();

    assertThat(result.getBuild().getAttributeList())
        .containsExactly(
            Attribute.newBuilder().setKey("adb_version").setValue("1.0.41").build(),
            Attribute.newBuilder().setKey("csr_strongbox_v2").setValue("csr_value").build(),
            Attribute.newBuilder()
                .setKey("device_kernel_info")
                .setValue("Linux localhost 6.1")
                .build(),
            Attribute.newBuilder()
                .setKey("build_fingerprint")
                .setValue("google/husky/husky:16/BP2A.250605.031/12999999:user/release-keys")
                .build(),
            Attribute.newBuilder().setKey("build_product").setValue("husky_from_meta").build(),
            Attribute.newBuilder().setKey("build_serial").setValue("39011FDJH000AA").build())
        .inOrder();
  }

  @Test
  public void convert_ignoresLeftoverTmpAndBaselinePbAndReturnsEmptyWhenNoRecords()
      throws Exception {
    Path goldenDir = getGoldenCompleteRunDir();
    Result expected = converter.convert(goldenDir).orElseThrow();

    Path pollutedStore = tempFolder.newFolder("polluted_store").toPath();
    copyDirectoryFiles(goldenDir, pollutedStore);
    byte[] tornBytes = new byte[] {0x0A, (byte) 0xFF, 0x01};
    Files.write(pollutedStore.resolve("invocation.pb.tmp"), tornBytes);
    Files.write(pollutedStore.resolve("arm64-v8a_CtsMediaTestCases_1.pb.tmp"), tornBytes);
    Files.write(pollutedStore.resolve("complete.pb.tmp"), tornBytes);
    Files.write(
        pollutedStore.resolve(XtsConstants.INCREMENTAL_BASELINE_FILE_NAME),
        Result.getDefaultInstance().toByteArray());

    assertThat(converter.convert(pollutedStore)).hasValue(expected);

    Path emptyDir = tempFolder.newFolder("empty_d2").toPath();
    Path nonExistentDir = tempFolder.getRoot().toPath().resolve("missing_d2_dir");
    Path tmpAndBaselineOnly = tempFolder.newFolder("tmp_and_baseline_only").toPath();
    Files.write(tmpAndBaselineOnly.resolve("invocation.pb.tmp"), tornBytes);
    Files.write(
        tmpAndBaselineOnly.resolve(XtsConstants.INCREMENTAL_BASELINE_FILE_NAME),
        Result.getDefaultInstance().toByteArray());

    assertThat(converter.convert(emptyDir)).isEmpty();
    assertThat(converter.convert(nonExistentDir)).isEmpty();
    assertThat(converter.convert(tmpAndBaselineOnly)).isEmpty();
  }

  @Test
  public void convert_emptyCompletePb_doesNotMarkRunCleanlyClosed() throws Exception {
    Path storeDir = tempFolder.newFolder("empty_complete_pb").toPath();
    String modId = "arm64-v8a CtsMod";
    writeInvocationPb(storeDir, modId + ":1");
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsMod_0.pb"),
        newModuleRecord(
            modId,
            newModuleCtx(modId, "arm64-v8a", "CtsMod", /* unitsRun= */ null),
            newRunRecord(modId, passedTest("android.cts.ModTest#testOne"))));
    Files.write(storeDir.resolve(XtsConstants.INCREMENTAL_COMPLETE_FILE_NAME), new byte[0]);

    Result result = converter.convert(storeDir).orElseThrow();

    assertThat(result.getModuleInfo(0).getDone()).isFalse();
    assertThat(result.getModuleInfo(0).getPassed()).isEqualTo(1);
  }

  @Test
  public void convert_hashTruncatedFilenameAndMinimalInvocation_recoversModuleMetadata()
      throws Exception {
    Path storeDir = tempFolder.newFolder("truncated_and_fallback").toPath();
    String longModuleName = "CtsVeryLongParameterizedModuleName_" + "x".repeat(200) + "[instant]";
    String longModuleId = "arm64-v8a " + longModuleName;
    String truncatedPrefix =
        ("arm64-v8a_" + longModuleName).replaceAll("[^A-Za-z0-9._-]", "_").substring(0, 200)
            + "_"
            + Integer.toHexString(longModuleId.hashCode());

    // Minimal invocation.pb carrying only planned-modules falls back to module-level BuildInfo and
    // metadata (serial, suite_name, suite_plan).
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-minimal")
            .setDescription(
                Any.pack(
                    Context.newBuilder()
                        .setTestTag("cts")
                        .addMetadata(
                            Metadata.newBuilder()
                                .setKey(XtsConstants.PLANNED_MODULES_ATTR)
                                .addValue(longModuleId + ":1"))
                        .build()))
            .build());

    Context fallbackModCtx =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes("cts:build_fingerprint", TEST_FINGERPRINT)
                    .build())
            .addMetadata(Metadata.newBuilder().setKey("module-id").addValue(longModuleId))
            .addMetadata(Metadata.newBuilder().setKey("serial").addValue("SER_META_1"))
            .addMetadata(Metadata.newBuilder().setKey("suite_name").addValue("CTS"))
            .addMetadata(Metadata.newBuilder().setKey("suite_plan").addValue("cts"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve(truncatedPrefix + "_0.pb"),
        TestRecord.newBuilder()
            .addChildren(
                ChildReference.newBuilder()
                    .setInlineTestRecord(
                        newRunRecord(
                            longModuleId, passedTest("android.long.cts.LongTest#testOne"))))
            .setDescription(Any.pack(fallbackModCtx))
            .build());

    Result result = converter.convert(storeDir).orElseThrow();

    ImmutableMap<String, String> attrs = toAttrMap(result.getAttributeList());
    assertThat(attrs).containsEntry(XmlConstants.DEVICES_ATTR, "SER_META_1");
    assertThat(attrs).containsEntry(XmlConstants.SUITE_NAME_ATTR, "CTS");
    Module module = result.getModuleInfo(0);
    assertThat(module.getAbi()).isEqualTo("arm64-v8a");
    assertThat(module.getName()).isEqualTo(longModuleName);
    assertThat(module.getDone()).isFalse();
    assertThat(module.getPassed()).isEqualTo(1);
  }

  @Test
  public void convert_perShardRuntimeFallbackAndMalformedRecords_skipsInvalidEntries()
      throws Exception {
    Path storeDir = tempFolder.newFolder("runtime_and_malformed").toPath();
    String modId = "arm64-v8a CtsRuntimeMod";
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-runtime")
            .setDescription(
                Any.pack(
                    Context.newBuilder()
                        .setTestTag("cts")
                        .addMetadata(
                            Metadata.newBuilder()
                                .setKey(XtsConstants.PLANNED_MODULES_ATTR)
                                .addValue(modId + ":2"))
                        .build()))
            .build());
    Context modCtx =
        newModuleCtx(modId, "arm64-v8a", "CtsRuntimeMod", "1").toBuilder()
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes("cts:build_fingerprint", TEST_FINGERPRINT)
                    .build())
            .build();

    TestRecord runWithoutTimestamps =
        TestRecord.newBuilder()
            .setTestRecordId(modId)
            .setParentTestRecordId(modId)
            .addChildren(
                ChildReference.newBuilder()
                    .setInlineTestRecord(passedTest("android.cts.RuntimeTest#testOne")))
            .addChildren(
                ChildReference.newBuilder()
                    .setInlineTestRecord(
                        TestRecord.newBuilder()
                            .setTestRecordId("MalformedNoHash")
                            .setStatus(TestStatus.PASS)))
            .addChildren(
                ChildReference.newBuilder()
                    .setInlineTestRecord(
                        TestRecord.newBuilder()
                            .setTestRecordId("android.cts.RuntimeTest#testEmptyDbg")
                            .setStatus(TestStatus.FAIL)
                            .setDebugInfo(DebugInfo.getDefaultInstance())))
            .setStatus(TestStatus.PASS)
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsRuntimeMod_0.pb"),
        TestRecord.newBuilder()
            .setTestRecordId(modId)
            .addChildren(ChildReference.newBuilder().setInlineTestRecord(runWithoutTimestamps))
            .setStartTime(Timestamp.newBuilder().setSeconds(100L))
            .setEndTime(Timestamp.newBuilder().setSeconds(100L).setNanos(750_000_000))
            .setDescription(Any.pack(modCtx))
            .build());

    // Orphan record without ID, Long-overflow numeric suffix, and corrupt .pb file are skipped.
    writeDelimitedRecord(
        storeDir.resolve("orphan_999999999999999999999999999.pb"),
        TestRecord.newBuilder()
            .setDescription(Any.pack(Context.newBuilder().setTestTag("cts").build()))
            .build());
    Files.write(storeDir.resolve("corrupt_shard_2.pb"), new byte[] {0x0A, 0x09, 0x01});

    TestRecord runWithTimestamps =
        newRunRecord(
            modId,
            /* startSec= */ 110L,
            /* endSec= */ 111L,
            /* prepMs= */ 0L,
            /* teardownMs= */ 0L,
            ImmutableList.of(passedTest("android.cts.RuntimeTest#testTwo")));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsRuntimeMod_1.pb"),
        TestRecord.newBuilder()
            .setTestRecordId(modId)
            .addChildren(ChildReference.newBuilder().setInlineTestRecord(runWithTimestamps))
            .setStartTime(Timestamp.newBuilder().setSeconds(109L))
            .setEndTime(Timestamp.newBuilder().setSeconds(112L))
            .setDescription(Any.pack(modCtx))
            .build());

    Result result = converter.convert(storeDir).orElseThrow();

    Module module = result.getModuleInfo(0);
    assertThat(module.getRuntimeMillis()).isEqualTo(1750L);
    assertThat(module.getTotalTests()).isEqualTo(3);
  }

  @Test
  public void convert_stackTraceTruncation_truncatesBeforeXmlEscaping() throws Exception {
    Path storeDir = tempFolder.newFolder("stack_truncation").toPath();
    String modId = "arm64-v8a CtsStackMod";
    int maxStack = 1024 * 1024;
    String hugeSingleLine = "E".repeat(maxStack - 1) + "<trailing";

    TestRecord runRecord =
        TestRecord.newBuilder()
            .setTestRecordId(modId)
            .setParentTestRecordId(modId)
            .addChildren(
                ChildReference.newBuilder()
                    .setInlineTestRecord(
                        failedTest("android.cts.StackTest#testSingleLine", hugeSingleLine)))
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage(hugeSingleLine))
            .setStartTime(Timestamp.newBuilder().setSeconds(100L))
            .setEndTime(Timestamp.newBuilder().setSeconds(101L))
            .build();
    Context ctx = newModuleCtx(modId, "arm64-v8a", "CtsStackMod", "1");
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-trunc")
            .setStartTime(Timestamp.newBuilder().setSeconds(100L))
            .setEndTime(Timestamp.newBuilder().setSeconds(102L))
            .setDescription(
                Any.pack(
                    Context.newBuilder()
                        .setTestTag("cts")
                        .putNameBuildInfo(
                            "DEFAULT_DEVICE",
                            BuildInformation.BuildInfo.newBuilder()
                                .putAttributes("cts:build_fingerprint", TEST_FINGERPRINT)
                                .build())
                        .addMetadata(
                            Metadata.newBuilder()
                                .setKey(XtsConstants.PLANNED_MODULES_ATTR)
                                .addValue(modId + ":1"))
                        .build()))
            .build());
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsStackMod_0.pb"), newModuleRecord(modId, ctx, runRecord));

    Result result = converter.convert(storeDir).orElseThrow();

    Module module = result.getModuleInfo(0);
    assertThat(module.getReason().getMsg()).endsWith("E&lt;");
    assertThat(module.getReason().getMsg()).hasLength(maxStack + 3);
    TestFailure failure = module.getTestCase(0).getTest(0).getFailure();
    assertThat(failure.getMsg()).endsWith("E&lt;");
    assertThat(failure.getMsg()).hasLength(maxStack + 3);
    assertThat(failure.getStackTrace().getContent()).endsWith("E&lt;");
  }

  @Test
  public void convert_missingPlannedModulesOrBuildInfo_dropsInvalidStoresAndSupportsSkipDeviceInfo()
      throws Exception {
    Path noBuildDir = tempFolder.newFolder("missing_build_info").toPath();
    String modId = "arm64-v8a CtsNoBuildMod";
    Context invWithoutFingerprint =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes("cts:build_fingerprint", "")
                    .build())
            .addMetadata(
                Metadata.newBuilder()
                    .setKey(XtsConstants.PLANNED_MODULES_ATTR)
                    .addValue(modId + ":1"))
            .build();
    writeDelimitedRecord(
        noBuildDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-no-fp")
            .setDescription(Any.pack(invWithoutFingerprint))
            .build());
    writeDelimitedRecord(
        noBuildDir.resolve("arm64-v8a_CtsNoBuildMod_0.pb"),
        newModuleRecord(
            modId,
            newModuleCtx(modId, "arm64-v8a", "CtsNoBuildMod", /* unitsRun= */ null),
            newRunRecord(modId, passedTest("android.cts.NoBuildTest#testOne"))));

    assertThat(converter.convert(noBuildDir)).isEmpty();
    Result skippedDeviceInfoResult =
        converter.convert(noBuildDir, /* skipDeviceInfo= */ true).orElseThrow();
    assertThat(skippedDeviceInfoResult.getModuleInfoList()).hasSize(1);
    assertThat(skippedDeviceInfoResult.getModuleInfo(0).getPassed()).isEqualTo(1);

    // Valid invocation.pb + unaltered fingerprint on complete.pb recovers the run.
    Path recoveredDir = tempFolder.newFolder("recovered_from_complete").toPath();
    copyDirectoryFiles(noBuildDir, recoveredDir);
    Context completeWithUnalteredFp =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes("cts:build_fingerprint_unaltered", TEST_FINGERPRINT)
                    .build())
            .build();
    writeDelimitedRecord(
        recoveredDir.resolve(XtsConstants.INCREMENTAL_COMPLETE_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-complete-fp")
            .setStatus(TestStatus.PASS)
            .setDescription(Any.pack(completeWithUnalteredFp))
            .build());

    Result recoveredResult = converter.convert(recoveredDir).orElseThrow();
    assertThat(recoveredResult.getBuild().getBuildFingerprintUnaltered())
        .isEqualTo(TEST_FINGERPRINT);
    assertThat(recoveredResult.getModuleInfo(0).getDone()).isTrue();
    assertThat(recoveredResult.getModuleInfo(0).getPassed()).isEqualTo(1);

    // Corrupt Context bytes inside complete.pb's Any prevent cleanlyClosed from marking unitless
    // modules done=true.
    Path corruptCompleteAnyDir = tempFolder.newFolder("corrupt_complete_any_bytes").toPath();
    writeInvocationPb(corruptCompleteAnyDir, modId + ":1");
    Files.copy(
        noBuildDir.resolve("arm64-v8a_CtsNoBuildMod_0.pb"),
        corruptCompleteAnyDir.resolve("arm64-v8a_CtsNoBuildMod_0.pb"));
    writeDelimitedRecord(
        corruptCompleteAnyDir.resolve(XtsConstants.INCREMENTAL_COMPLETE_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-complete-corrupt-ctx")
            .setStatus(TestStatus.PASS)
            .setDescription(
                Any.newBuilder()
                    .setTypeUrl("type.googleapis.com/" + Context.getDescriptor().getFullName())
                    .setValue(ByteString.copyFrom(new byte[] {0x0A, 0x09, 0x01}))
                    .build())
            .build());
    assertThat(converter.convert(corruptCompleteAnyDir).orElseThrow().getModuleInfo(0).getDone())
        .isFalse();

    // Corrupt Context bytes on a 0-TestRun module record must neither trigger controller-skip
    // classification (done=true, skipped=true) nor allow cleanlyClosed to mark sibling unitless
    // modules done=true.
    Path corruptModuleAnyDir = tempFolder.newFolder("corrupt_module_any_bytes").toPath();
    String zeroRunCorruptMod = "arm64-v8a CtsZeroRunCorruptCtxMod";
    writeInvocationPb(corruptModuleAnyDir, modId + ":1", zeroRunCorruptMod + ":1");
    Files.copy(
        noBuildDir.resolve("arm64-v8a_CtsNoBuildMod_0.pb"),
        corruptModuleAnyDir.resolve("arm64-v8a_CtsNoBuildMod_0.pb"));
    writeDelimitedRecord(
        corruptModuleAnyDir.resolve("arm64-v8a_CtsZeroRunCorruptCtxMod_0.pb"),
        TestRecord.newBuilder()
            .setTestRecordId(zeroRunCorruptMod)
            .setStatus(TestStatus.PASS)
            .setDescription(
                Any.newBuilder()
                    .setTypeUrl("type.googleapis.com/" + Context.getDescriptor().getFullName())
                    .setValue(ByteString.copyFrom(new byte[] {0x0A, 0x09, 0x01}))
                    .build())
            .build());
    writeCleanCompletePb(corruptModuleAnyDir);
    Result corruptModuleAnyResult = converter.convert(corruptModuleAnyDir).orElseThrow();
    assertThat(corruptModuleAnyResult.getModuleInfo(0).getDone()).isFalse();
    assertThat(corruptModuleAnyResult.getModuleInfo(1).getDone()).isFalse();
    assertThat(corruptModuleAnyResult.getModuleInfo(1).getSkipped()).isFalse();

    // Missing, corrupt, or planless invocation.pb in any non-empty job store drops the whole
    // Result (even with complete.pb, BuildInfo, skipDeviceInfo=true, or a valid sibling job).
    Path corruptInvDir = tempFolder.newFolder("corrupt_inv_dropped").toPath();
    copyDirectoryFiles(recoveredDir, corruptInvDir);
    Files.write(
        corruptInvDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        new byte[] {0x0A, 0x05, 0x01});
    assertThat(converter.convert(corruptInvDir)).isEmpty();
    assertThat(converter.convert(corruptInvDir, /* skipDeviceInfo= */ true)).isEmpty();

    Path multiJobWithBrokenSibling = tempFolder.newFolder("multi_job_broken_sibling").toPath();
    Path validStaticDir = Files.createDirectory(multiJobWithBrokenSibling.resolve("STATIC_XTS"));
    copyDirectoryFiles(recoveredDir, validStaticDir);
    Path planlessMctsDir = Files.createDirectory(multiJobWithBrokenSibling.resolve("MCTS"));
    writeDelimitedRecord(
        planlessMctsDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-mcts-no-plan")
            .setDescription(Any.pack(completeWithUnalteredFp))
            .build());
    assertThat(converter.convert(multiJobWithBrokenSibling)).isEmpty();

    Path multiJobMissingInv = tempFolder.newFolder("multi_job_missing_inv").toPath();
    copyDirectoryFiles(
        recoveredDir, Files.createDirectory(multiJobMissingInv.resolve("STATIC_XTS")));
    Path missingInvMctsDir = Files.createDirectory(multiJobMissingInv.resolve("MCTS"));
    Files.copy(
        noBuildDir.resolve("arm64-v8a_CtsNoBuildMod_0.pb"),
        missingInvMctsDir.resolve("arm64-v8a_CtsNoBuildMod_0.pb"));
    assertThat(converter.convert(multiJobMissingInv)).isEmpty();

    Path multiJobInvalidRoot = tempFolder.newFolder("multi_job_invalid_root").toPath();
    copyDirectoryFiles(
        recoveredDir, Files.createDirectory(multiJobInvalidRoot.resolve("STATIC_XTS")));
    Files.copy(
        noBuildDir.resolve("arm64-v8a_CtsNoBuildMod_0.pb"),
        multiJobInvalidRoot.resolve("stray_root_mod_0.pb"));
    assertThat(converter.convert(multiJobInvalidRoot)).isEmpty();
  }

  @Test
  public void convert_dynamicShardedFullUnitDrainWithoutCompletePb_setsDoneTrue() throws Exception {
    Path storeDir = tempFolder.newFolder("dynamic_unit_drain_done").toPath();
    String modDrain = "arm64-v8a CtsDrainMod";
    String modSplit = "arm64-v8a CtsSplitUnitsMod";
    String modFilteredEmpty = "arm64-v8a CtsFilteredEmptyMod";
    writeInvocationPb(storeDir, modDrain + ":4", modSplit + ":4", modFilteredEmpty + ":2");

    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsDrainMod_0.pb"),
        newModuleRecord(
            modDrain,
            newModuleCtx(modDrain, "arm64-v8a", "CtsDrainMod", "4"),
            newRunRecord(
                modDrain,
                passedTest("android.cts.DrainTest#testPass"),
                failedTest("android.cts.DrainTest#testFail", "boom"))));

    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSplitUnitsMod_0.pb"),
        newModuleRecord(
            modSplit,
            newModuleCtx(modSplit, "arm64-v8a", "CtsSplitUnitsMod", "2"),
            newRunRecord(modSplit, passedTest("android.cts.SplitTest#testOne"))));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSplitUnitsMod_1.pb"),
        newModuleRecord(
            modSplit,
            newModuleCtx(modSplit, "arm64-v8a", "CtsSplitUnitsMod", "2"),
            newRunRecord(modSplit, passedTest("android.cts.SplitTest#testTwo"))));

    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsFilteredEmptyMod_0.pb"),
        TestRecord.newBuilder()
            .setTestRecordId(modFilteredEmpty)
            .setStatus(TestStatus.PASS)
            .setStartTime(Timestamp.newBuilder().setSeconds(20L))
            .setEndTime(Timestamp.newBuilder().setSeconds(22L))
            .setDescription(
                Any.pack(newModuleCtx(modFilteredEmpty, "arm64-v8a", "CtsFilteredEmptyMod", "2")))
            .build());

    Result result = converter.convert(storeDir).orElseThrow();

    assertThat(result.getModuleInfo(0).getDone()).isTrue();
    assertThat(result.getModuleInfo(1).getDone()).isTrue();
    Module filteredEmpty = result.getModuleInfo(2);
    assertThat(filteredEmpty.getDone()).isTrue();
    assertThat(filteredEmpty.getSkipped()).isFalse();
    assertThat(filteredEmpty.getRuntimeMillis()).isEqualTo(2000L);
    assertThat(result.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(3)
                .setFailed(1)
                .setWarning(0)
                .setModulesDone(3)
                .setModulesTotal(3)
                .build());
  }

  @Test
  public void convert_intraModuleAutoRetry_preservesSameAttemptAndPostRetryFailures()
      throws Exception {
    Path storeDir = tempFolder.newFolder("intra_module_auto_retry").toPath();
    String modSameAttemptBackfill = "arm64-v8a CtsSameAttemptBackfillMod";
    String modPostRetryBackfill = "arm64-v8a CtsPostRetryBackfillMod";
    String modCrashThenCleanSameAttempt = "arm64-v8a CtsCrashThenCleanSameAttemptMod";
    writeInvocationPb(
        storeDir,
        modSameAttemptBackfill + ":1",
        modPostRetryBackfill + ":1",
        modCrashThenCleanSameAttempt + ":1");

    // Two TestRuns sharing attempt_id=0 where the second carries backfill/teardown DebugInfo must
    // still mark the module done=false.
    TestRecord backfillRun0 =
        newRunRecord(modSameAttemptBackfill, passedTest("android.cts.BackfillTest#testOne"));
    TestRecord backfillRun1 =
        TestRecord.newBuilder()
            .setTestRecordId(modSameAttemptBackfill)
            .setAttemptId(0L)
            .setStatus(TestStatus.FAIL)
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage("Teardown device lost"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSameAttemptBackfillMod_0.pb"),
        newModuleRecord(
            modSameAttemptBackfill,
            newModuleCtx(modSameAttemptBackfill, "arm64-v8a", "CtsSameAttemptBackfillMod", "1"),
            backfillRun0,
            backfillRun1));

    // A synthetic attempt_id=0 teardown backfill arriving AFTER a clean attempt_id=1 retry must
    // not be masked by attempt_id=1.
    TestRecord postRetryAttempt0 =
        newRunRecord(
                modPostRetryBackfill,
                failedTest("android.cts.PostRetryTest#testOne", "initial fail"))
            .toBuilder()
            .setAttemptId(0L)
            .build();
    TestRecord postRetryAttempt1 =
        newRunRecord(modPostRetryBackfill, passedTest("android.cts.PostRetryTest#testOne"))
            .toBuilder()
            .setAttemptId(1L)
            .build();
    TestRecord postRetryTeardownFail =
        TestRecord.newBuilder()
            .setTestRecordId(modPostRetryBackfill)
            .setAttemptId(0L)
            .setStatus(TestStatus.FAIL)
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage("Post-retry teardown lost"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsPostRetryBackfillMod_0.pb"),
        newModuleRecord(
            modPostRetryBackfill,
            newModuleCtx(modPostRetryBackfill, "arm64-v8a", "CtsPostRetryBackfillMod", "1"),
            postRetryAttempt0,
            postRetryAttempt1,
            postRetryTeardownFail));

    // A crashed attempt_id=0 TestRun followed by another attempt_id=0 TestRun without DebugInfo
    // must start a separate run sequence rather than clearing the first runner's crash.
    TestRecord crashedFirstSameAttempt =
        newRunRecord(
                modCrashThenCleanSameAttempt,
                failedTest("android.cts.SameAttemptTest#testOne", "runner0 crash"))
            .toBuilder()
            .setAttemptId(0L)
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage("Initial runner crash"))
            .build();
    TestRecord cleanSecondSameAttempt =
        newRunRecord(
                modCrashThenCleanSameAttempt, passedTest("android.cts.SameAttemptTest#testTwo"))
            .toBuilder()
            .setAttemptId(0L)
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsCrashThenCleanSameAttemptMod_0.pb"),
        newModuleRecord(
            modCrashThenCleanSameAttempt,
            newModuleCtx(
                modCrashThenCleanSameAttempt, "arm64-v8a", "CtsCrashThenCleanSameAttemptMod", "1"),
            crashedFirstSameAttempt,
            cleanSecondSameAttempt));

    Result result = converter.convert(storeDir).orElseThrow();

    Module backfilled = result.getModuleInfo(0);
    assertThat(backfilled.getDone()).isFalse();
    assertThat(backfilled.getReason().getMsg()).isEqualTo("Teardown device lost");

    Module postRetryBackfilled = result.getModuleInfo(1);
    assertThat(postRetryBackfilled.getDone()).isFalse();
    assertThat(postRetryBackfilled.getPassed()).isEqualTo(1);
    assertThat(postRetryBackfilled.getReason().getMsg()).isEqualTo("Post-retry teardown lost");

    Module crashThenClean = result.getModuleInfo(2);
    assertThat(crashThenClean.getDone()).isFalse();
    assertThat(crashThenClean.getReason().getMsg()).isEqualTo("Initial runner crash");
  }

  @Test
  public void convert_unitShortfall_setsDoneFalseAndPreservesPassedTests() throws Exception {
    Path storeDir = tempFolder.newFolder("unit_shortfalls").toPath();
    String modPartial = "arm64-v8a CtsPartialMod";
    String modAutoRetry = "arm64-v8a CtsRetryShortfallMod";
    String modZeroTestRuns = "arm64-v8a CtsPartialZeroTestRunsMod";
    String modRetryCrashShortfall = "arm64-v8a CtsRetryCrashShortfallMod";
    writeInvocationPb(
        storeDir,
        modPartial + ":4",
        modAutoRetry + ":2",
        modZeroTestRuns + ":2",
        modRetryCrashShortfall + ":2");

    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsPartialMod_0.pb"),
        newModuleRecord(
            modPartial,
            newModuleCtx(modPartial, "arm64-v8a", "CtsPartialMod", "2"),
            newRunRecord(
                modPartial,
                passedTest("android.cts.PartialTest#testPass"),
                failedTest("android.cts.PartialTest#testFail", "err"))));

    // 2 TestRuns for 1 completed unit (< 2 planned), with Attempt 0's resolved DebugInfo NOT
    // leaking into the incomplete module's <Reason>.
    TestRecord runAttempt0 =
        newRunRecord(modAutoRetry, failedTest("android.cts.RetryTest#testFlaky", "try0"))
            .toBuilder()
            .setAttemptId(0L)
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage("Resolved attempt 0 error"))
            .build();
    TestRecord runAttempt1 =
        newRunRecord(modAutoRetry, passedTest("android.cts.RetryTest#testFlaky")).toBuilder()
            .setAttemptId(1L)
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsRetryShortfallMod_0.pb"),
        newModuleRecord(
            modAutoRetry,
            newModuleCtx(modAutoRetry, "arm64-v8a", "CtsRetryShortfallMod", "1"),
            runAttempt0,
            runAttempt1));

    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsPartialZeroTestRunsMod_0.pb"),
        newModuleRecord(
            modZeroTestRuns,
            newModuleCtx(modZeroTestRuns, "arm64-v8a", "CtsPartialZeroTestRunsMod", "1")));

    // Attempt 0 finished with a test failure (no run DebugInfo) and Attempt 1 crashed mid-retry
    // before remaining shards ran -> fallbackDebugInfo populates <Reason>.
    TestRecord cleanAttempt0 =
        newRunRecord(
                modRetryCrashShortfall,
                failedTest("android.cts.RetryCrashTest#testOne", "attempt 0 test fail"))
            .toBuilder()
            .setAttemptId(0L)
            .build();
    TestRecord crashedAttempt1 =
        TestRecord.newBuilder()
            .setTestRecordId(modRetryCrashShortfall)
            .setAttemptId(1L)
            .setStatus(TestStatus.FAIL)
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage("Device lost during auto-retry"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsRetryCrashShortfallMod_0.pb"),
        newModuleRecord(
            modRetryCrashShortfall,
            newModuleCtx(modRetryCrashShortfall, "arm64-v8a", "CtsRetryCrashShortfallMod", "1"),
            cleanAttempt0,
            crashedAttempt1));
    writeCleanCompletePb(storeDir);

    Result result = converter.convert(storeDir).orElseThrow();

    Module partial = result.getModuleInfo(0);
    assertThat(partial.getDone()).isFalse();
    assertThat(partial.getSkipped()).isFalse();
    assertThat(partial.getPassed()).isEqualTo(1);
    assertThat(partial.getFailedTests()).isEqualTo(1);
    assertThat(partial.getReason().getMsg())
        .isEqualTo("Run was incomplete. Some tests might not have finished.");

    Module retried = result.getModuleInfo(1);
    assertThat(retried.getDone()).isFalse();
    assertThat(retried.getPassed()).isEqualTo(1);
    assertThat(retried.getFailedTests()).isEqualTo(0);
    assertThat(retried.getReason().getMsg())
        .isEqualTo("Run was incomplete. Some tests might not have finished.");

    Module zeroRuns = result.getModuleInfo(2);
    assertThat(zeroRuns.getDone()).isFalse();
    assertThat(zeroRuns.getSkipped()).isFalse();

    Module retryCrashShortfall = result.getModuleInfo(3);
    assertThat(retryCrashShortfall.getDone()).isFalse();
    assertThat(retryCrashShortfall.getReason().getMsg()).isEqualTo("Device lost during auto-retry");

    assertThat(result.getSummary().getModulesDone()).isEqualTo(0);
    assertThat(result.getSummary().getModulesTotal()).isEqualTo(4);
  }

  @Test
  public void convert_debugInfoOverridesFullUnitsRunAndSetupFailure_setsDoneFalse()
      throws Exception {
    Path storeDir = tempFolder.newFolder("debug_info_override").toPath();
    String modRunCrash = "arm64-v8a CtsRunCrashMod";
    String modSetupFail = "arm64-v8a CtsSetupFailMod";
    writeInvocationPb(storeDir, modRunCrash + ":2", modSetupFail + ":1");

    TestRecord crashedRun =
        TestRecord.newBuilder()
            .setTestRecordId(modRunCrash)
            .setParentTestRecordId(modRunCrash)
            .addChildren(
                ChildReference.newBuilder()
                    .setInlineTestRecord(passedTest("android.cts.CrashTest#testBeforeCrash")))
            .setStatus(TestStatus.FAIL)
            .setDebugInfo(
                DebugInfo.newBuilder()
                    .setErrorMessage("Instrumentation crashed mid-run")
                    .setFailureStatus(FailureStatus.INFRA_FAILURE)
                    .setDebugInfoContext(
                        DebugInfoContext.newBuilder()
                            .setErrorName("INSTRUMENTATION_CRASH")
                            .setErrorCode(520100)))
            .setStartTime(Timestamp.newBuilder().setSeconds(20L))
            .setEndTime(Timestamp.newBuilder().setSeconds(21L))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsRunCrashMod_0.pb"),
        newModuleRecord(
            modRunCrash,
            newModuleCtx(modRunCrash, "arm64-v8a", "CtsRunCrashMod", "2"),
            crashedRun));

    TestRecord setupFailRun =
        TestRecord.newBuilder()
            .setTestRecordId(modSetupFail)
            .setParentTestRecordId(modSetupFail)
            .setNumExpectedChildren(0)
            .setStatus(TestStatus.FAIL)
            .setDebugInfo(
                DebugInfo.newBuilder()
                    .setErrorMessage("TargetSetupError: failed to install APK")
                    .setFailureStatus(FailureStatus.NOT_EXECUTED)
                    .setDebugInfoContext(
                        DebugInfoContext.newBuilder()
                            .setErrorName("MODULE_DID_NOT_EXECUTE")
                            .setErrorCode(505003)))
            .setStartTime(Timestamp.newBuilder().setSeconds(22L))
            .setEndTime(Timestamp.newBuilder().setSeconds(22L))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSetupFailMod_0.pb"),
        newModuleRecord(
            modSetupFail,
            newModuleCtx(modSetupFail, "arm64-v8a", "CtsSetupFailMod", "0"),
            setupFailRun));
    writeCleanCompletePb(storeDir);

    Result result = converter.convert(storeDir).orElseThrow();

    Module crashMod = result.getModuleInfo(0);
    assertThat(crashMod.getDone()).isFalse();
    assertThat(crashMod.getPassed()).isEqualTo(1);
    assertThat(crashMod.getReason())
        .isEqualTo(
            Reason.newBuilder()
                .setMsg("Instrumentation crashed mid-run")
                .setErrorName("INSTRUMENTATION_CRASH")
                .setErrorCode("520100")
                .build());

    Module setupMod = result.getModuleInfo(1);
    assertThat(setupMod.getDone()).isFalse();
    assertThat(setupMod.getSkipped()).isFalse();
    assertThat(setupMod.getReason())
        .isEqualTo(
            Reason.newBuilder()
                .setMsg("TargetSetupError: failed to install APK")
                .setErrorName("MODULE_DID_NOT_EXECUTE")
                .setErrorCode("505003")
                .build());
  }

  @Test
  public void convert_plannedButUnseenModules_synthesizesDoneFalse(
      @TestParameter boolean hasCleanCompletePb) throws Exception {
    Path storeDir = tempFolder.newFolder("planned_unseen_" + hasCleanCompletePb).toPath();
    String modExec = "arm64-v8a CtsExecutedMod";
    String modUnseenAbi = "arm64-v8a CtsUnseenAbiMod";
    String modUnseenHost = "CtsUnseenHostMod";
    writeInvocationPb(storeDir, modExec + ":1", modUnseenAbi + ":2", modUnseenHost + ":1");
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsExecutedMod_0.pb"),
        newModuleRecord(
            modExec,
            newModuleCtx(modExec, "arm64-v8a", "CtsExecutedMod", "1"),
            newRunRecord(modExec, passedTest("android.cts.ExecTest#testRan"))));
    if (hasCleanCompletePb) {
      writeCleanCompletePb(storeDir);
    }

    Result result = converter.convert(storeDir).orElseThrow();

    assertThat(result.getModuleInfo(0).getDone()).isTrue();
    assertThat(result.getModuleInfo(1).getAbi()).isEqualTo("arm64-v8a");
    assertThat(result.getModuleInfo(1).getName()).isEqualTo("CtsUnseenAbiMod");
    assertThat(result.getModuleInfo(1).getDone()).isFalse();
    assertThat(result.getModuleInfo(1).hasReason()).isFalse();
    assertThat(result.getModuleInfo(2).hasAbi()).isFalse();
    assertThat(result.getModuleInfo(2).getName()).isEqualTo("CtsUnseenHostMod");
    assertThat(result.getModuleInfo(2).getDone()).isFalse();
    assertThat(result.getSummary().getModulesDone()).isEqualTo(1);
    assertThat(result.getSummary().getModulesTotal()).isEqualTo(3);
  }

  @Test
  public void convert_controllerSkippedAndSystemCheckers_precedesUnitSumAndIgnoresCheckers()
      throws Exception {
    Path storeDir = tempFolder.newFolder("controller_skipped").toPath();
    String modSkip = "arm64-v8a CtsSkippedMod";
    String modSkipWithCheckers = "arm64-v8a CtsSkippedWithCheckersMod";
    String modNormalWithCheckers = "arm64-v8a CtsNormalWithCheckersMod";
    String standaloneChecker = "arm64-v8a PreModuleCheckerSample";
    writeInvocationPb(
        storeDir,
        modSkip + ":2",
        modSkipWithCheckers + ":2",
        modNormalWithCheckers + ":1",
        standaloneChecker + ":1");

    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSkippedMod_0.pb"),
        newModuleRecord(
            modSkip,
            withModuleSkipped(
                newModuleCtx(modSkip, "arm64-v8a", "CtsSkippedMod", "0"),
                "Skipped by module controller")));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSkippedMod_1.pb"),
        newModuleRecord(
            modSkip,
            withModuleSkipped(
                newModuleCtx(modSkip, "arm64-v8a", "CtsSkippedMod", "0"),
                "Skipped by module controller")));

    TestRecord preChk =
        TestRecord.newBuilder()
            .setTestRecordId("PreModuleChecker_" + modSkipWithCheckers)
            .setStatus(TestStatus.PASS)
            .build();
    TestRecord postChkWithDbg =
        TestRecord.newBuilder()
            .setTestRecordId("PostModuleChecker_" + modNormalWithCheckers)
            .setStatus(TestStatus.FAIL)
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage("Checker failed"))
            .setStartTime(Timestamp.newBuilder().setSeconds(30L))
            .setEndTime(Timestamp.newBuilder().setSeconds(35L))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSkippedWithCheckersMod_0.pb"),
        newModuleRecord(
            modSkipWithCheckers,
            withModuleSkipped(
                newModuleCtx(modSkipWithCheckers, "arm64-v8a", "CtsSkippedWithCheckersMod", "0"),
                "Skipped by module controller"),
            preChk));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSkippedWithCheckersMod_1.pb"),
        newModuleRecord(
            modSkipWithCheckers,
            withModuleSkipped(
                newModuleCtx(modSkipWithCheckers, "arm64-v8a", "CtsSkippedWithCheckersMod", "0"),
                "Skipped by module controller"),
            preChk));

    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsNormalWithCheckersMod_0.pb"),
        newModuleRecord(
            modNormalWithCheckers,
            newModuleCtx(modNormalWithCheckers, "arm64-v8a", "CtsNormalWithCheckersMod", "1"),
            newRunRecord(modNormalWithCheckers, passedTest("android.cts.NormTest#testPass")),
            postChkWithDbg));

    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_PreModuleCheckerSample_0.pb"),
        newModuleRecord(
            standaloneChecker,
            newModuleCtx(standaloneChecker, "arm64-v8a", "PreModuleCheckerSample", "1"),
            newRunRecord(standaloneChecker, passedTest("com.android.Checker#testCheck"))));

    Result result = converter.convert(storeDir).orElseThrow();

    assertThat(result.getModuleInfo(0).getDone()).isTrue();
    assertThat(result.getModuleInfo(0).getSkipped()).isTrue();
    assertThat(result.getModuleInfo(0).getReason().getMsg())
        .isEqualTo("Module CtsSkippedMod is skipped by module controller.");
    assertThat(result.getModuleInfo(1).getDone()).isTrue();
    assertThat(result.getModuleInfo(1).getSkipped()).isTrue();

    Module normalMod = result.getModuleInfo(2);
    assertThat(normalMod.getDone()).isTrue();
    assertThat(normalMod.getSkipped()).isFalse();
    assertThat(normalMod.getRuntimeMillis()).isEqualTo(1000L);

    assertThat(result.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(1)
                .setFailed(0)
                .setWarning(0)
                .setModulesDone(1)
                .setModulesTotal(1)
                .build());

    Path reportDir = tempFolder.newFolder("skipped_report").toPath();
    Result xmlFromDisk = writeReportAndParseXml(result, reportDir);
    assertThat(
            Result.parseFrom(
                Files.readAllBytes(reportDir.resolve("test_result.pb")),
                ExtensionRegistryLite.getEmptyRegistry()))
        .isEqualTo(result);
    assertThat(xmlFromDisk.getModuleInfoList()).containsExactly(normalMod, result.getModuleInfo(3));
  }

  @Test
  public void convert_interruptedShardedModuleWithSingleZeroUnitShard_marksIncompleteNotSkipped()
      throws Exception {
    String modId = "arm64-v8a CtsDrainedShardMod";
    Path crashedDir = tempFolder.newFolder("drained_shard_crashed").toPath();
    writeInvocationPb(crashedDir, modId + ":2");
    writeDelimitedRecord(
        crashedDir.resolve("arm64-v8a_CtsDrainedShardMod_0.pb"),
        newModuleRecord(modId, newModuleCtx(modId, "arm64-v8a", "CtsDrainedShardMod", "0")));

    Path cleanPreflightDir = tempFolder.newFolder("preflight_clean").toPath();
    copyDirectoryFiles(crashedDir, cleanPreflightDir);
    writeCleanCompletePb(cleanPreflightDir);

    Result crashedResult = converter.convert(crashedDir).orElseThrow();
    Result cleanResult = converter.convert(cleanPreflightDir).orElseThrow();

    Module crashedModule = crashedResult.getModuleInfo(0);
    assertThat(crashedModule.getDone()).isFalse();
    assertThat(crashedModule.getSkipped()).isFalse();
    assertThat(crashedModule.getReason().getMsg())
        .isEqualTo("Run was incomplete. Some tests might not have finished.");
    assertThat(crashedResult.getSummary().getModulesDone()).isEqualTo(0);
    assertThat(crashedResult.getSummary().getModulesTotal()).isEqualTo(1);

    Module cleanModule = cleanResult.getModuleInfo(0);
    assertThat(cleanModule.getDone()).isTrue();
    assertThat(cleanModule.getSkipped()).isTrue();
    assertThat(cleanResult.getSummary().getModulesTotal()).isEqualTo(0);
  }

  @Test
  public void convert_moduleAbiAndNameWithPathTraversal_sanitizesForHtmlReport() throws Exception {
    Path storeDir = tempFolder.newFolder("traversal_mod").toPath();
    String rawModId = "../../evil_abi ../CtsTraversalMod";
    writeInvocationPb(storeDir, rawModId + ":1", "..\\unseen_abi ..\\CtsUnseenTraversal:1");
    writeDelimitedRecord(
        storeDir.resolve("evil_0.pb"),
        newModuleRecord(
            rawModId,
            newModuleCtx(rawModId, "../../evil_abi", "../CtsTraversalMod\0/sub", "1"),
            newRunRecord(rawModId, passedTest("android.cts.SafeTest#testOne"))));

    Result result = converter.convert(storeDir).orElseThrow();

    Module executedMod = result.getModuleInfo(0);
    assertThat(executedMod.getAbi()).isEqualTo("______evil_abi");
    assertThat(executedMod.getName()).isEqualTo("___CtsTraversalMod__sub");

    Module unseenMod = result.getModuleInfo(1);
    assertThat(unseenMod.getAbi()).isEqualTo("___unseen_abi");
    assertThat(unseenMod.getName()).isEqualTo("___CtsUnseenTraversal");

    Path outerDir = tempFolder.newFolder("report_container").toPath();
    Path reportDir = Files.createDirectory(outerDir.resolve("inner_report"));
    Result parsedXml = writeReportAndParseXml(result, reportDir);
    assertThat(parsedXml.getModuleInfoList()).hasSize(2);
    try (var outerEntries = Files.list(outerDir)) {
      assertThat(outerEntries.map(entry -> entry.getFileName().toString()).toList())
          .containsExactly("inner_report", "inner_report.zip");
    }
    assertThat(Files.isDirectory(reportDir.resolve("module_reports"))).isTrue();
  }

  @Test
  public void convert_missingOrInvalidUnitsRun_fallsBackToCleanCompletePb(
      @TestParameter InvalidUnitsRunCase unitsRunCase) throws Exception {
    String modId = "arm64-v8a CtsLegacyMod";
    Path baseDir = tempFolder.newFolder("units_base_" + unitsRunCase.name()).toPath();
    writeInvocationPb(baseDir, modId + ":1");
    writeDelimitedRecord(
        baseDir.resolve("arm64-v8a_CtsLegacyMod_0.pb"),
        newModuleRecord(
            modId,
            newModuleCtx(modId, "arm64-v8a", "CtsLegacyMod", unitsRunCase.rawValue),
            newRunRecord(modId, passedTest("android.cts.LegacyTest#testPass"))));

    Path dirtyDir = tempFolder.newFolder("units_dirty_" + unitsRunCase.name()).toPath();
    copyDirectoryFiles(baseDir, dirtyDir);
    writeDelimitedRecord(
        dirtyDir.resolve(XtsConstants.INCREMENTAL_COMPLETE_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-dirty")
            .setStatus(TestStatus.FAIL)
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage("Invocation aborted"))
            .setDescription(Any.pack(Context.newBuilder().setTestTag("cts").build()))
            .build());

    Path cleanDir = tempFolder.newFolder("units_clean_" + unitsRunCase.name()).toPath();
    copyDirectoryFiles(baseDir, cleanDir);
    writeCleanCompletePb(cleanDir);

    Result withoutComplete = converter.convert(baseDir).orElseThrow();
    Result withDirtyComplete = converter.convert(dirtyDir).orElseThrow();
    Result withCleanComplete = converter.convert(cleanDir).orElseThrow();

    assertThat(withoutComplete.getModuleInfo(0).getDone()).isFalse();
    assertThat(withoutComplete.getModuleInfo(0).getPassed()).isEqualTo(1);
    assertThat(withDirtyComplete.getModuleInfo(0).getDone()).isFalse();
    assertThat(withCleanComplete.getModuleInfo(0).getDone()).isTrue();
  }

  @Test
  public void convert_unplannedExtraModules_requireCleanCompletePbForDoneTrue() throws Exception {
    Path withoutCompleteDir = tempFolder.newFolder("unplanned_without_complete").toPath();
    String plannedModId = "arm64-v8a CtsPlannedMod";
    String extraModId = "arm64-v8a CtsExtraMod";
    String extraSkippedModId = "arm64-v8a CtsExtraSkippedMod";
    writeInvocationPb(withoutCompleteDir, plannedModId + ":1");
    writeDelimitedRecord(
        withoutCompleteDir.resolve("arm64-v8a_CtsPlannedMod_0.pb"),
        newModuleRecord(
            plannedModId,
            newModuleCtx(plannedModId, "arm64-v8a", "CtsPlannedMod", "1"),
            newRunRecord(plannedModId, passedTest("android.cts.PlannedTest#testOne"))));
    writeDelimitedRecord(
        withoutCompleteDir.resolve("arm64-v8a_CtsExtraMod_0.pb"),
        newModuleRecord(
            extraModId,
            newModuleCtx(extraModId, "arm64-v8a", "CtsExtraMod", /* unitsRun= */ null),
            newRunRecord(extraModId, passedTest("android.cts.ExtraTest#testOne"))));
    writeDelimitedRecord(
        withoutCompleteDir.resolve("arm64-v8a_CtsExtraSkippedMod_0.pb"),
        newModuleRecord(
            extraSkippedModId,
            newModuleCtx(extraSkippedModId, "arm64-v8a", "CtsExtraSkippedMod", "0")));

    Path withCompleteDir = tempFolder.newFolder("unplanned_with_complete").toPath();
    copyDirectoryFiles(withoutCompleteDir, withCompleteDir);
    // Descriptionless complete.pb without DebugInfo still marks the run cleanly closed.
    writeDelimitedRecord(
        withCompleteDir.resolve(XtsConstants.INCREMENTAL_COMPLETE_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-complete-no-desc")
            .setStatus(TestStatus.PASS)
            .build());

    Path corruptShardDir = tempFolder.newFolder("unplanned_with_corrupt_shard").toPath();
    copyDirectoryFiles(withCompleteDir, corruptShardDir);
    Files.write(
        corruptShardDir.resolve("arm64-v8a_CtsCorruptShard_0.pb"), new byte[] {0x0A, 0x05, 0x01});

    Result withoutComplete = converter.convert(withoutCompleteDir).orElseThrow();
    Result withComplete = converter.convert(withCompleteDir).orElseThrow();
    Result withCorruptShard = converter.convert(corruptShardDir).orElseThrow();

    assertThat(withoutComplete.getModuleInfo(0).getDone()).isTrue();
    assertThat(withoutComplete.getModuleInfo(1).getDone()).isFalse();
    assertThat(withoutComplete.getModuleInfo(2).getDone()).isFalse();
    assertThat(withoutComplete.getModuleInfo(2).getSkipped()).isFalse();
    assertThat(withComplete.getModuleInfo(0).getDone()).isTrue();
    assertThat(withComplete.getModuleInfo(1).getDone()).isTrue();
    assertThat(withComplete.getModuleInfo(2).getDone()).isTrue();
    assertThat(withComplete.getModuleInfo(2).getSkipped()).isTrue();
    assertThat(withCorruptShard.getModuleInfo(0).getDone()).isTrue();
    assertThat(withCorruptShard.getModuleInfo(1).getDone()).isFalse();
    assertThat(withCorruptShard.getModuleInfo(2).getDone()).isFalse();
    assertThat(withCorruptShard.getModuleInfo(2).getSkipped()).isFalse();
  }

  @Test
  public void convert_multiJobSession_mergesSharedModulesAndIgnoresEmptyJob() throws Exception {
    Path sessionStoreDir = tempFolder.newFolder("multi_job_session").toPath();
    // Create in reverse lexicographical order so the test catches missing directory sorting.
    Path staticDir = Files.createDirectory(sessionStoreDir.resolve("STATIC_XTS"));
    Path mctsDir = Files.createDirectory(sessionStoreDir.resolve("MCTS"));
    Files.createDirectory(sessionStoreDir.resolve("EMPTY_JOB"));

    String sharedCrashedMod = "arm64-v8a CtsSharedCrashedMod";
    String sharedCompletedMod = "arm64-v8a CtsSharedCompletedMod";
    String staticOnlySkippedMod = "arm64-v8a CtsStaticSkippedMod";
    String mctsOnlyMod = "arm64-v8a MctsOnlyMod";

    writeInvocationPb(
        mctsDir, mctsOnlyMod + ":1", sharedCrashedMod + ":2", sharedCompletedMod + ":2");
    writeDelimitedRecord(
        mctsDir.resolve("arm64-v8a_MctsOnlyMod_0.pb"),
        newModuleRecord(
            mctsOnlyMod,
            newModuleCtx(mctsOnlyMod, "arm64-v8a", "MctsOnlyMod", "1"),
            newRunRecord(mctsOnlyMod, passedTest("android.mcts.OnlyTest#testMcts"))));
    writeDelimitedRecord(
        mctsDir.resolve("arm64-v8a_CtsSharedCrashedMod_0.pb"),
        newModuleRecord(
            sharedCrashedMod,
            newModuleCtx(sharedCrashedMod, "arm64-v8a", "CtsSharedCrashedMod", "0")));
    writeDelimitedRecord(
        mctsDir.resolve("arm64-v8a_CtsSharedCompletedMod_0.pb"),
        newModuleRecord(
            sharedCompletedMod,
            newModuleCtx(sharedCompletedMod, "arm64-v8a", "CtsSharedCompletedMod", "0")));
    writeCleanCompletePb(mctsDir);

    writeInvocationPb(
        staticDir, sharedCrashedMod + ":2", sharedCompletedMod + ":2", staticOnlySkippedMod + ":2");
    writeDelimitedRecord(
        staticDir.resolve("arm64-v8a_CtsSharedCompletedMod_0.pb"),
        newModuleRecord(
            sharedCompletedMod,
            newModuleCtx(sharedCompletedMod, "arm64-v8a", "CtsSharedCompletedMod", "2"),
            newRunRecord(
                sharedCompletedMod,
                passedTest("android.cts.SharedTest#testOne"),
                passedTest("android.cts.SharedTest#testTwo"))));
    writeDelimitedRecord(
        staticDir.resolve("arm64-v8a_CtsStaticSkippedMod_0.pb"),
        newModuleRecord(
            staticOnlySkippedMod,
            withModuleSkipped(
                newModuleCtx(staticOnlySkippedMod, "arm64-v8a", "CtsStaticSkippedMod", "0"),
                "Skipped by module controller")));
    writeDelimitedRecord(
        staticDir.resolve("arm64-v8a_CtsStaticSkippedMod_1.pb"),
        newModuleRecord(
            staticOnlySkippedMod,
            withModuleSkipped(
                newModuleCtx(staticOnlySkippedMod, "arm64-v8a", "CtsStaticSkippedMod", "0"),
                "Skipped by module controller")));

    Result result = converter.convert(sessionStoreDir).orElseThrow();

    ImmutableMap<String, Module> modulesByName =
        result.getModuleInfoList().stream().collect(toImmutableMap(Module::getName, identity()));
    assertThat(modulesByName.keySet())
        .containsExactly(
            "MctsOnlyMod", "CtsSharedCrashedMod", "CtsSharedCompletedMod", "CtsStaticSkippedMod")
        .inOrder();

    Module crashedShared = modulesByName.get("CtsSharedCrashedMod");
    assertThat(crashedShared.getDone()).isFalse();
    assertThat(crashedShared.getSkipped()).isFalse();

    Module completedShared = modulesByName.get("CtsSharedCompletedMod");
    assertThat(completedShared.getDone()).isTrue();
    assertThat(completedShared.getSkipped()).isFalse();
    assertThat(completedShared.getPassed()).isEqualTo(2);

    Module staticSkipped = modulesByName.get("CtsStaticSkippedMod");
    assertThat(staticSkipped.getDone()).isTrue();
    assertThat(staticSkipped.getSkipped()).isTrue();

    Module mctsModule = modulesByName.get("MctsOnlyMod");
    assertThat(mctsModule.getDone()).isTrue();

    assertThat(result.getSummary())
        .isEqualTo(
            Summary.newBuilder()
                .setPassed(3)
                .setFailed(0)
                .setWarning(0)
                .setModulesDone(2)
                .setModulesTotal(3)
                .build());
  }

  @Test
  public void convert_unannotatedZeroUnitModule_requiresCleanCompletePbEvenWhenRecordCountMatches()
      throws Exception {
    String modSingle = "arm64-v8a CtsSingleUnitInterruptedMod";
    String modSharded = "arm64-v8a CtsShardedInterruptedMod";
    Path unclosedDir = tempFolder.newFolder("unannotated_zero_unit_unclosed").toPath();
    writeInvocationPb(unclosedDir, modSingle + ":1", modSharded + ":2");
    writeDelimitedRecord(
        unclosedDir.resolve("arm64-v8a_CtsSingleUnitInterruptedMod_0.pb"),
        newModuleRecord(
            modSingle, newModuleCtx(modSingle, "arm64-v8a", "CtsSingleUnitInterruptedMod", "0")));
    writeDelimitedRecord(
        unclosedDir.resolve("arm64-v8a_CtsShardedInterruptedMod_0.pb"),
        newModuleRecord(
            modSharded, newModuleCtx(modSharded, "arm64-v8a", "CtsShardedInterruptedMod", "0")));
    writeDelimitedRecord(
        unclosedDir.resolve("arm64-v8a_CtsShardedInterruptedMod_1.pb"),
        newModuleRecord(
            modSharded, newModuleCtx(modSharded, "arm64-v8a", "CtsShardedInterruptedMod", "0")));

    Path cleanDir = tempFolder.newFolder("unannotated_zero_unit_clean").toPath();
    copyDirectoryFiles(unclosedDir, cleanDir);
    writeCleanCompletePb(cleanDir);

    Result unclosedResult = converter.convert(unclosedDir).orElseThrow();
    Result cleanResult = converter.convert(cleanDir).orElseThrow();

    Module unclosedSingle = unclosedResult.getModuleInfo(0);
    assertThat(unclosedSingle.getDone()).isFalse();
    assertThat(unclosedSingle.getSkipped()).isFalse();
    assertThat(unclosedSingle.getReason().getMsg())
        .isEqualTo("Run was incomplete. Some tests might not have finished.");
    Module unclosedSharded = unclosedResult.getModuleInfo(1);
    assertThat(unclosedSharded.getDone()).isFalse();
    assertThat(unclosedSharded.getSkipped()).isFalse();
    assertThat(unclosedResult.getSummary().getModulesDone()).isEqualTo(0);
    assertThat(unclosedResult.getSummary().getModulesTotal()).isEqualTo(2);

    Module cleanSingle = cleanResult.getModuleInfo(0);
    assertThat(cleanSingle.getDone()).isTrue();
    assertThat(cleanSingle.getSkipped()).isTrue();
    assertThat(cleanSingle.getReason().getMsg())
        .isEqualTo("Module CtsSingleUnitInterruptedMod is skipped by module controller.");
    Module cleanSharded = cleanResult.getModuleInfo(1);
    assertThat(cleanSharded.getDone()).isTrue();
    assertThat(cleanSharded.getSkipped()).isTrue();
    assertThat(cleanResult.getSummary().getModulesDone()).isEqualTo(0);
    assertThat(cleanResult.getSummary().getModulesTotal()).isEqualTo(0);
  }

  @Test
  public void convert_explicitModuleSkippedMetadata_marksDoneSkippedWithoutCompletePbAndGuards()
      throws Exception {
    Path storeDir = tempFolder.newFolder("explicit_module_skipped").toPath();
    String modPreflight = "arm64-v8a CtsPreflightSkippedMod";
    String modBlankSkip = "arm64-v8a CtsBlankSkippedAttrMod";
    String modEmptySkip = "arm64-v8a CtsEmptySkippedAttrMod";
    String modSkipWithDbg = "arm64-v8a CtsSkippedWithDebugInfoMod";
    String modSkipWithRun = "arm64-v8a CtsSkippedWithRunMod";
    String modSkipNoUnits = "arm64-v8a CtsSkippedMissingUnitsMod";
    writeInvocationPb(
        storeDir,
        modPreflight + ":3",
        modBlankSkip + ":1",
        modEmptySkip + ":1",
        modSkipWithDbg + ":1",
        modSkipWithRun + ":1",
        modSkipNoUnits + ":1");

    // Single record for plannedUnits=3 (e.g., preflight-check clearing remaining shards).
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsPreflightSkippedMod_0.pb"),
        newModuleRecord(
            modPreflight,
            withModuleSkipped(
                newModuleCtx(modPreflight, "arm64-v8a", "CtsPreflightSkippedMod", "0"),
                "Module skipped by pre-flight check: 0 runnable test cases after filtering.")));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsBlankSkippedAttrMod_0.pb"),
        newModuleRecord(
            modBlankSkip,
            withModuleSkipped(
                newModuleCtx(modBlankSkip, "arm64-v8a", "CtsBlankSkippedAttrMod", "0"), "   ")));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsEmptySkippedAttrMod_0.pb"),
        newModuleRecord(
            modEmptySkip,
            withModuleSkipped(
                newModuleCtx(modEmptySkip, "arm64-v8a", "CtsEmptySkippedAttrMod", "0"), "")));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSkippedWithDebugInfoMod_0.pb"),
        newModuleRecord(
                modSkipWithDbg,
                withModuleSkipped(
                    newModuleCtx(modSkipWithDbg, "arm64-v8a", "CtsSkippedWithDebugInfoMod", "0"),
                    "Skipped by module controller"))
            .toBuilder()
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage("Setup failed before skip"))
            .build());
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSkippedWithRunMod_0.pb"),
        newModuleRecord(
            modSkipWithRun,
            withModuleSkipped(
                newModuleCtx(modSkipWithRun, "arm64-v8a", "CtsSkippedWithRunMod", "0"),
                "Skipped by module controller"),
            newRunRecord(modSkipWithRun, passedTest("android.cts.SkipRunTest#testOne"))));
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsSkippedMissingUnitsMod_0.pb"),
        newModuleRecord(
            modSkipNoUnits,
            withModuleSkipped(
                newModuleCtx(
                    modSkipNoUnits, "arm64-v8a", "CtsSkippedMissingUnitsMod", /* unitsRun= */ null),
                "Skipped by module controller")));

    Result result = converter.convert(storeDir).orElseThrow();
    ImmutableMap<String, Module> modules = modulesByName(result);

    Module preflight = modules.get("CtsPreflightSkippedMod");
    assertThat(preflight.getDone()).isTrue();
    assertThat(preflight.getSkipped()).isTrue();
    assertThat(preflight.getReason().getMsg())
        .isEqualTo("Module CtsPreflightSkippedMod is skipped by module controller.");

    Module blankSkip = modules.get("CtsBlankSkippedAttrMod");
    assertThat(blankSkip.getDone()).isFalse();
    assertThat(blankSkip.getSkipped()).isFalse();

    Module emptySkip = modules.get("CtsEmptySkippedAttrMod");
    assertThat(emptySkip.getDone()).isFalse();
    assertThat(emptySkip.getSkipped()).isFalse();

    Module skipWithDbg = modules.get("CtsSkippedWithDebugInfoMod");
    assertThat(skipWithDbg.getDone()).isFalse();
    assertThat(skipWithDbg.getSkipped()).isFalse();
    assertThat(skipWithDbg.getReason().getMsg()).isEqualTo("Setup failed before skip");

    Module skipWithRun = modules.get("CtsSkippedWithRunMod");
    assertThat(skipWithRun.getDone()).isFalse();
    assertThat(skipWithRun.getSkipped()).isFalse();
    assertThat(skipWithRun.getPassed()).isEqualTo(1);

    Module skipNoUnits = modules.get("CtsSkippedMissingUnitsMod");
    assertThat(skipNoUnits.getDone()).isFalse();
    assertThat(skipNoUnits.getSkipped()).isFalse();

    assertThat(result.getSummary().getModulesDone()).isEqualTo(0);
    assertThat(result.getSummary().getModulesTotal()).isEqualTo(5);
  }

  @Test
  public void convert_nonInlineChildReferences_failClosedAtModuleAndRunLevels() throws Exception {
    Path storeDir = tempFolder.newFolder("non_inline_child_refs").toPath();
    String modNonInlineRunSkipped = "arm64-v8a CtsNonInlineRunSkippedMod";
    String modNonInlineRunFullUnits = "arm64-v8a CtsNonInlineRunFullUnitsMod";
    String modNonInlineTestFullUnits = "arm64-v8a CtsNonInlineTestFullUnitsMod";
    String modNonInlineRunUnitless = "arm64-v8a CtsNonInlineRunUnitlessMod";
    String modHealthySibling = "arm64-v8a CtsHealthySiblingMod";
    writeInvocationPb(
        storeDir,
        modNonInlineRunSkipped + ":1",
        modNonInlineRunFullUnits + ":1",
        modNonInlineTestFullUnits + ":1",
        modNonInlineRunUnitless + ":1",
        modHealthySibling + ":1");

    // Module-to-run non-inline ChildReference prevents controller-skip classification even with
    // module-skipped metadata and clean complete.pb.
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsNonInlineRunSkippedMod_0.pb"),
        newModuleRecord(
                modNonInlineRunSkipped,
                withModuleSkipped(
                    newModuleCtx(
                        modNonInlineRunSkipped, "arm64-v8a", "CtsNonInlineRunSkippedMod", "0"),
                    "Skipped by module controller"))
            .toBuilder()
            .addChildren(ChildReference.newBuilder().setTestRecordId("external-run-ref"))
            .build());

    // Module-to-run non-inline ChildReference alongside an inline run forces done=false even when
    // planned units are met and complete.pb is clean.
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsNonInlineRunFullUnitsMod_0.pb"),
        newModuleRecord(
                modNonInlineRunFullUnits,
                newModuleCtx(
                    modNonInlineRunFullUnits, "arm64-v8a", "CtsNonInlineRunFullUnitsMod", "1"),
                newRunRecord(
                    modNonInlineRunFullUnits, passedTest("android.cts.RunRefTest#testInline")))
            .toBuilder()
            .addChildren(ChildReference.newBuilder().setTestRecordId("external-run-ref-2"))
            .build());

    // Run-to-test-case non-inline ChildReference forces done=false while preserving inline tests.
    TestRecord runWithNonInlineTest =
        newRunRecord(modNonInlineTestFullUnits, passedTest("android.cts.TestRefTest#testInline"))
            .toBuilder()
            .addChildren(
                ChildReference.newBuilder().setTestRecordId("android.cts.TestRefTest#testExternal"))
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsNonInlineTestFullUnitsMod_0.pb"),
        newModuleRecord(
            modNonInlineTestFullUnits,
            newModuleCtx(
                modNonInlineTestFullUnits, "arm64-v8a", "CtsNonInlineTestFullUnitsMod", "1"),
            runWithNonInlineTest));

    // Unitless module with non-inline run reference stays done=false even with clean complete.pb.
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsNonInlineRunUnitlessMod_0.pb"),
        newModuleRecord(
                modNonInlineRunUnitless,
                newModuleCtx(
                    modNonInlineRunUnitless,
                    "arm64-v8a",
                    "CtsNonInlineRunUnitlessMod",
                    /* unitsRun= */ null))
            .toBuilder()
            .addChildren(ChildReference.newBuilder().setTestRecordId("external-unitless-run"))
            .build());

    // Healthy sibling in the same job remains done=true.
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsHealthySiblingMod_0.pb"),
        newModuleRecord(
            modHealthySibling,
            newModuleCtx(modHealthySibling, "arm64-v8a", "CtsHealthySiblingMod", "1"),
            newRunRecord(modHealthySibling, passedTest("android.cts.HealthyTest#testOk"))));
    writeCleanCompletePb(storeDir);

    Result result = converter.convert(storeDir).orElseThrow();
    ImmutableMap<String, Module> modules = modulesByName(result);

    Module nonInlineSkipped = modules.get("CtsNonInlineRunSkippedMod");
    assertThat(nonInlineSkipped.getDone()).isFalse();
    assertThat(nonInlineSkipped.getSkipped()).isFalse();
    assertThat(nonInlineSkipped.getReason().getMsg())
        .isEqualTo("Run was incomplete. Some tests might not have finished.");

    Module nonInlineRunFull = modules.get("CtsNonInlineRunFullUnitsMod");
    assertThat(nonInlineRunFull.getDone()).isFalse();
    assertThat(nonInlineRunFull.getSkipped()).isFalse();
    assertThat(nonInlineRunFull.getPassed()).isEqualTo(1);
    assertThat(nonInlineRunFull.getReason().getMsg())
        .isEqualTo("Run was incomplete. Some tests might not have finished.");

    Module nonInlineTestFull = modules.get("CtsNonInlineTestFullUnitsMod");
    assertThat(nonInlineTestFull.getDone()).isFalse();
    assertThat(nonInlineTestFull.getSkipped()).isFalse();
    assertThat(nonInlineTestFull.getPassed()).isEqualTo(1);
    assertThat(nonInlineTestFull.getReason().getMsg())
        .isEqualTo("Run was incomplete. Some tests might not have finished.");

    Module nonInlineUnitless = modules.get("CtsNonInlineRunUnitlessMod");
    assertThat(nonInlineUnitless.getDone()).isFalse();
    assertThat(nonInlineUnitless.getSkipped()).isFalse();

    Module healthySibling = modules.get("CtsHealthySiblingMod");
    assertThat(healthySibling.getDone()).isTrue();
    assertThat(healthySibling.getPassed()).isEqualTo(1);

    assertThat(result.getSummary().getModulesDone()).isEqualTo(1);
    assertThat(result.getSummary().getModulesTotal()).isEqualTo(5);
  }

  @Test
  public void convert_nonInlineChildReferenceInOneMultiJobShard_propagatesDoneFalseAcrossJobs()
      throws Exception {
    Path sessionDir = tempFolder.newFolder("multi_job_non_inline_child").toPath();
    Path mctsDir = Files.createDirectory(sessionDir.resolve("MCTS"));
    Path staticDir = Files.createDirectory(sessionDir.resolve("STATIC_XTS"));
    String sharedMod = "arm64-v8a CtsSharedNonInlineMod";

    writeInvocationPb(mctsDir, sharedMod + ":1");
    writeDelimitedRecord(
        mctsDir.resolve("arm64-v8a_CtsSharedNonInlineMod_0.pb"),
        newModuleRecord(
            sharedMod,
            newModuleCtx(sharedMod, "arm64-v8a", "CtsSharedNonInlineMod", "1"),
            newRunRecord(sharedMod, passedTest("android.cts.SharedTest#testMctsPass"))));
    writeCleanCompletePb(mctsDir);

    writeInvocationPb(staticDir, sharedMod + ":1");
    TestRecord runWithNonInlineTest =
        newRunRecord(sharedMod, passedTest("android.cts.SharedTest#testStaticPass")).toBuilder()
            .addChildren(
                ChildReference.newBuilder().setTestRecordId("android.cts.SharedTest#testExternal"))
            .build();
    writeDelimitedRecord(
        staticDir.resolve("arm64-v8a_CtsSharedNonInlineMod_0.pb"),
        newModuleRecord(
            sharedMod,
            newModuleCtx(sharedMod, "arm64-v8a", "CtsSharedNonInlineMod", "1"),
            runWithNonInlineTest));
    writeCleanCompletePb(staticDir);

    Result result = converter.convert(sessionDir).orElseThrow();

    Module merged = result.getModuleInfo(0);
    assertThat(merged.getDone()).isFalse();
    assertThat(merged.getSkipped()).isFalse();
    assertThat(merged.getPassed()).isEqualTo(2);
  }

  @Test
  public void convert_unexpectedAnyOnModuleRecord_preventsCleanlyClosedForUnitlessAndZeroUnit()
      throws Exception {
    Any unexpectedAny = Any.pack(Metadata.newBuilder().setKey("unexpected").build());
    Path badModuleAnyDir = tempFolder.newFolder("unexpected_any_module").toPath();
    String modUnitless = "arm64-v8a CtsUnitlessMod";
    String modWrongAny = "arm64-v8a CtsWrongAnyMod";
    String modZeroUnit = "arm64-v8a CtsZeroUnitMod";
    String modFullUnits = "arm64-v8a CtsFullUnitsMod";
    writeInvocationPb(
        badModuleAnyDir,
        modUnitless + ":1",
        modWrongAny + ":1",
        modZeroUnit + ":1",
        modFullUnits + ":1");
    writeDelimitedRecord(
        badModuleAnyDir.resolve("arm64-v8a_CtsUnitlessMod_0.pb"),
        newModuleRecord(
            modUnitless,
            newModuleCtx(modUnitless, "arm64-v8a", "CtsUnitlessMod", /* unitsRun= */ null),
            newRunRecord(modUnitless, passedTest("android.cts.UnitlessTest#testOne"))));
    writeDelimitedRecord(
        badModuleAnyDir.resolve("arm64-v8a_CtsWrongAnyMod_0.pb"),
        TestRecord.newBuilder()
            .setTestRecordId(modWrongAny)
            .setStatus(TestStatus.PASS)
            .setDescription(unexpectedAny)
            .build());
    writeDelimitedRecord(
        badModuleAnyDir.resolve("arm64-v8a_CtsZeroUnitMod_0.pb"),
        newModuleRecord(
            modZeroUnit, newModuleCtx(modZeroUnit, "arm64-v8a", "CtsZeroUnitMod", "0")));
    writeDelimitedRecord(
        badModuleAnyDir.resolve("arm64-v8a_CtsFullUnitsMod_0.pb"),
        newModuleRecord(
            modFullUnits,
            newModuleCtx(modFullUnits, "arm64-v8a", "CtsFullUnitsMod", "1"),
            newRunRecord(modFullUnits, passedTest("android.cts.FullUnitsTest#testOne"))));
    writeCleanCompletePb(badModuleAnyDir);

    ImmutableMap<String, Module> modules =
        modulesByName(converter.convert(badModuleAnyDir).orElseThrow());

    assertThat(modules.get("CtsUnitlessMod").getDone()).isFalse();
    assertThat(modules.get("CtsWrongAnyMod").getDone()).isFalse();
    assertThat(modules.get("CtsWrongAnyMod").getSkipped()).isFalse();
    assertThat(modules.get("CtsZeroUnitMod").getDone()).isFalse();
    assertThat(modules.get("CtsZeroUnitMod").getSkipped()).isFalse();
    assertThat(modules.get("CtsFullUnitsMod").getDone()).isTrue();
  }

  @Test
  public void convert_unexpectedAnyOnCompletePb_preventsCleanlyClosedWhileExplicitSkipPasses()
      throws Exception {
    Any unexpectedAny = Any.pack(Metadata.newBuilder().setKey("unexpected").build());
    Path badCompleteAnyDir = tempFolder.newFolder("unexpected_any_complete").toPath();
    String modUnitless = "arm64-v8a CtsUnitlessMod";
    String modZeroUnit = "arm64-v8a CtsZeroUnitMod";
    String modExplicitSkip = "arm64-v8a CtsExplicitSkipMod";
    writeInvocationPb(
        badCompleteAnyDir, modUnitless + ":1", modZeroUnit + ":1", modExplicitSkip + ":1");
    writeDelimitedRecord(
        badCompleteAnyDir.resolve("arm64-v8a_CtsUnitlessMod_0.pb"),
        newModuleRecord(
            modUnitless,
            newModuleCtx(modUnitless, "arm64-v8a", "CtsUnitlessMod", /* unitsRun= */ null),
            newRunRecord(modUnitless, passedTest("android.cts.UnitlessTest#testOne"))));
    writeDelimitedRecord(
        badCompleteAnyDir.resolve("arm64-v8a_CtsZeroUnitMod_0.pb"),
        newModuleRecord(
            modZeroUnit, newModuleCtx(modZeroUnit, "arm64-v8a", "CtsZeroUnitMod", "0")));
    writeDelimitedRecord(
        badCompleteAnyDir.resolve("arm64-v8a_CtsExplicitSkipMod_0.pb"),
        newModuleRecord(
            modExplicitSkip,
            withModuleSkipped(
                newModuleCtx(modExplicitSkip, "arm64-v8a", "CtsExplicitSkipMod", "0"),
                "Skipped by module controller")));
    writeDelimitedRecord(
        badCompleteAnyDir.resolve(XtsConstants.INCREMENTAL_COMPLETE_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-complete-bad-any")
            .setStatus(TestStatus.PASS)
            .setDescription(unexpectedAny)
            .build());

    ImmutableMap<String, Module> modules =
        modulesByName(converter.convert(badCompleteAnyDir).orElseThrow());

    assertThat(modules.get("CtsUnitlessMod").getDone()).isFalse();
    assertThat(modules.get("CtsZeroUnitMod").getDone()).isFalse();
    assertThat(modules.get("CtsZeroUnitMod").getSkipped()).isFalse();
    assertThat(modules.get("CtsExplicitSkipMod").getDone()).isTrue();
    assertThat(modules.get("CtsExplicitSkipMod").getSkipped()).isTrue();
  }

  @Test
  public void convert_unexpectedAnyOnInvocationPb_rejectsStoreWhileParseInvocationReturnsDefault()
      throws Exception {
    Any unexpectedAny = Any.pack(Metadata.newBuilder().setKey("unexpected").build());
    Path badInvocationAnyDir = tempFolder.newFolder("unexpected_any_invocation").toPath();
    writeDelimitedRecord(
        badInvocationAnyDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-bad-any")
            .setDescription(unexpectedAny)
            .build());

    assertThat(converter.convert(badInvocationAnyDir)).isEmpty();
    assertThat(converter.convert(badInvocationAnyDir, /* skipDeviceInfo= */ true)).isEmpty();
    Optional<InvocationPlan> parsedPlan = converter.parseInvocation(badInvocationAnyDir);
    assertThat(parsedPlan).isPresent();
    assertThat(parsedPlan.get().context()).isEqualToDefaultInstance();
    assertThat(parsedPlan.get().plannedCountsByModule()).isEmpty();
  }

  @Test
  public void convert_duplicatePlannedModulesOverflow_rejectsStoreWhileParseInvocationKeepsFirst()
      throws Exception {
    Path storeDir = tempFolder.newFolder("planned_modules_overflow").toPath();
    String modId = "arm64-v8a CtsOverflowMod";
    writeInvocationPb(storeDir, modId + ":2000000000", modId + ":2000000000");
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsOverflowMod_0.pb"),
        newModuleRecord(
            modId,
            newModuleCtx(modId, "arm64-v8a", "CtsOverflowMod", "1"),
            newRunRecord(modId, passedTest("android.cts.OverflowTest#testOne"))));
    writeCleanCompletePb(storeDir);

    assertThat(converter.convert(storeDir)).isEmpty();
    assertThat(converter.convert(storeDir, /* skipDeviceInfo= */ true)).isEmpty();

    Optional<InvocationPlan> parsedPlan = converter.parseInvocation(storeDir);
    assertThat(parsedPlan).isPresent();
    assertThat(parsedPlan.get().plannedCountsByModule()).containsExactly(modId, 2_000_000_000);
  }

  @Test
  public void convert_partiallyMalformedPlannedModules_rejectsStoreWhileParseInvocationSkipsInvalid(
      @TestParameter MalformedPlannedModuleCase malformedCase) throws Exception {
    Path storeDir = tempFolder.newFolder("partial_malformed_plan_" + malformedCase.name()).toPath();
    String validMod = "arm64-v8a CtsValidMod";
    writeInvocationPb(storeDir, validMod + ":1", malformedCase.rawEntry);
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsValidMod_0.pb"),
        newModuleRecord(
            validMod,
            newModuleCtx(validMod, "arm64-v8a", "CtsValidMod", "1"),
            newRunRecord(validMod, passedTest("android.cts.ValidTest#testOne"))));
    writeCleanCompletePb(storeDir);

    assertThat(converter.convert(storeDir)).isEmpty();
    assertThat(converter.convert(storeDir, /* skipDeviceInfo= */ true)).isEmpty();

    Optional<InvocationPlan> parsedPlan = converter.parseInvocation(storeDir);
    assertThat(parsedPlan).isPresent();
    assertThat(parsedPlan.get().plannedCountsByModule()).containsExactly(validMod, 1);
  }

  @Test
  public void convert_partiallyMalformedPlannedModulesInSiblingJob_rejectsSession()
      throws Exception {
    Path sessionDir = tempFolder.newFolder("multi_job_partial_malformed_plan").toPath();
    Path validDir = Files.createDirectory(sessionDir.resolve("STATIC_XTS"));
    Path malformedDir = Files.createDirectory(sessionDir.resolve("MCTS"));
    String validMod = "arm64-v8a CtsValidMod";
    String mctsMod = "arm64-v8a MctsValidMod";

    writeInvocationPb(validDir, validMod + ":1");
    writeDelimitedRecord(
        validDir.resolve("arm64-v8a_CtsValidMod_0.pb"),
        newModuleRecord(
            validMod,
            newModuleCtx(validMod, "arm64-v8a", "CtsValidMod", "1"),
            newRunRecord(validMod, passedTest("android.cts.ValidTest#testOne"))));
    writeCleanCompletePb(validDir);

    writeInvocationPb(malformedDir, mctsMod + ":1", "arm64-v8a CorruptedPlanEntry:bad");
    writeDelimitedRecord(
        malformedDir.resolve("arm64-v8a_MctsValidMod_0.pb"),
        newModuleRecord(
            mctsMod,
            newModuleCtx(mctsMod, "arm64-v8a", "MctsValidMod", "1"),
            newRunRecord(mctsMod, passedTest("android.mcts.ValidTest#testOne"))));
    writeCleanCompletePb(malformedDir);

    assertThat(converter.convert(sessionDir)).isEmpty();
  }

  private Result roundTripXml(Result result) throws Exception {
    return writeReportAndParseXml(result, tempFolder.newFolder().toPath());
  }

  private static Result writeReportAndParseXml(Result result, Path reportDir) throws Exception {
    Injector injector =
        Guice.createInjector(
            new CompatibilityReportModule(),
            new CommonModule(ImmutableList.of(), ImmutableMap.of(), ImmutableMap.of()));
    injector
        .getInstance(CompatibilityReportCreator.class)
        .createReport(
            /* originalReport= */ result,
            /* userfacingReport= */ result,
            reportDir,
            /* testRecord= */ null,
            /* includeHtmlInZip= */ false,
            /* testPlan= */ "cts",
            /* testReportProperties= */ ImmutableMap.of("has_tf_module", "true"),
            /* extraFilesOrDirsToZip= */ ImmutableList.of());
    return injector
        .getInstance(CompatibilityReportParser.class)
        .parse(reportDir.resolve("test_result.xml"), /* shallow= */ false)
        .orElseThrow();
  }

  private static Path getGoldenCompleteRunDir() {
    return Path.of(TestRunfilesUtil.getRunfilesLocation(GOLDEN_COMPLETE_RUN_INVOCATION))
        .getParent();
  }

  private static void writeInvocationPb(Path storeDir, String... plannedEntries)
      throws IOException {
    Metadata.Builder planned = Metadata.newBuilder().setKey(XtsConstants.PLANNED_MODULES_ATTR);
    for (String entry : plannedEntries) {
      planned.addValue(entry);
    }
    Context invCtx =
        Context.newBuilder()
            .setTestTag("cts")
            .putNameBuildInfo(
                "DEFAULT_DEVICE",
                BuildInformation.BuildInfo.newBuilder()
                    .putAttributes("cts:build_fingerprint", TEST_FINGERPRINT)
                    .build())
            .addMetadata(planned)
            .build();
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-test")
            .setDescription(Any.pack(invCtx))
            .build());
  }

  private static void writeCleanCompletePb(Path storeDir) throws IOException {
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_COMPLETE_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-test")
            .setStatus(TestStatus.PASS)
            .setDescription(Any.pack(Context.newBuilder().setTestTag("cts").build()))
            .build());
  }

  private static Context newModuleCtx(
      String moduleId, String abi, String name, @Nullable String unitsRun) {
    Context.Builder builder =
        Context.newBuilder()
            .setTestTag("cts")
            .addMetadata(Metadata.newBuilder().setKey("module-id").addValue(moduleId))
            .addMetadata(Metadata.newBuilder().setKey("module-name").addValue(name));
    if (!abi.isEmpty()) {
      builder.addMetadata(Metadata.newBuilder().setKey("module-abi").addValue(abi));
    }
    if (unitsRun != null) {
      builder.addMetadata(
          Metadata.newBuilder().setKey(XtsConstants.MODULE_UNITS_RUN_ATTR).addValue(unitsRun));
    }
    return builder.build();
  }

  private static Context withModuleSkipped(Context moduleCtx, String skipReason) {
    return moduleCtx.toBuilder()
        .addMetadata(Metadata.newBuilder().setKey("module-skipped").addValue(skipReason))
        .build();
  }

  private static TestRecord newModuleRecord(String moduleId, Context ctx, TestRecord... runs) {
    TestRecord.Builder builder =
        TestRecord.newBuilder()
            .setTestRecordId(moduleId)
            .setStatus(TestStatus.PASS)
            .setDescription(Any.pack(ctx));
    for (TestRecord run : runs) {
      builder.addChildren(ChildReference.newBuilder().setInlineTestRecord(run));
    }
    return builder.build();
  }

  private static TestRecord newRunRecord(String moduleId, TestRecord... tests) {
    return newRunRecord(
        moduleId,
        /* startSec= */ 10L,
        /* endSec= */ 11L,
        /* prepMs= */ 0L,
        /* teardownMs= */ 0L,
        ImmutableList.copyOf(tests));
  }

  private static TestRecord newRunRecord(
      String moduleId,
      long startSec,
      long endSec,
      long prepMs,
      long teardownMs,
      List<TestRecord> tests) {
    TestRecord.Builder builder =
        TestRecord.newBuilder()
            .setTestRecordId(moduleId)
            .setParentTestRecordId(moduleId)
            .setNumExpectedChildren(tests.size())
            .setStatus(TestStatus.PASS)
            .setStartTime(Timestamp.newBuilder().setSeconds(startSec))
            .setEndTime(Timestamp.newBuilder().setSeconds(endSec));
    if (prepMs > 0) {
      builder.putMetrics(
          "PREP_TIME",
          MetricMeasurement.Metric.newBuilder()
              .setMeasurements(Measurements.newBuilder().setSingleInt(prepMs))
              .build());
    }
    if (teardownMs > 0) {
      builder.putMetrics(
          "TEARDOWN_TIME",
          MetricMeasurement.Metric.newBuilder()
              .setMeasurements(Measurements.newBuilder().setSingleInt(teardownMs))
              .build());
    }
    for (TestRecord test : tests) {
      builder.addChildren(ChildReference.newBuilder().setInlineTestRecord(test));
    }
    return builder.build();
  }

  private static TestRecord passedTest(String id) {
    return newTestRecord(id, TestStatus.PASS, /* stackTrace= */ null);
  }

  private static TestRecord failedTest(String id, String stackTrace) {
    return newTestRecord(id, TestStatus.FAIL, stackTrace);
  }

  private static TestRecord newTestRecord(
      String id, TestStatus status, @Nullable String stackTrace) {
    TestRecord.Builder builder = TestRecord.newBuilder().setTestRecordId(id).setStatus(status);
    if (stackTrace != null) {
      builder.setDebugInfo(DebugInfo.newBuilder().setErrorMessage(stackTrace).setTrace(stackTrace));
    }
    return builder.build();
  }

  private static ImmutableMap<String, String> toAttrMap(List<Attribute> attributes) {
    return attributes.stream().collect(toImmutableMap(Attribute::getKey, Attribute::getValue));
  }

  private static ImmutableMap<String, Module> modulesByName(Result result) {
    return result.getModuleInfoList().stream().collect(toImmutableMap(Module::getName, m -> m));
  }

  private static void copyDirectoryFiles(Path sourceDir, Path targetDir) throws IOException {
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(sourceDir)) {
      for (Path entry : entries) {
        if (Files.isRegularFile(entry)) {
          Files.copy(entry, targetDir.resolve(entry.getFileName()));
        }
      }
    }
  }

  private static void writeDelimitedRecord(Path targetFile, TestRecord record) throws IOException {
    try (OutputStream out = Files.newOutputStream(targetFile)) {
      record.writeDelimitedTo(out);
    }
  }
}
