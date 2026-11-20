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
import static org.junit.Assert.assertThrows;

import com.android.tradefed.build.proto.BuildInformation;
import com.android.tradefed.config.proto.ConfigurationDescription.Metadata;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.result.proto.TestRecordProto.ChildReference;
import com.android.tradefed.result.proto.TestRecordProto.DebugInfo;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord.SkipReason;
import com.android.tradefed.result.proto.TestRecordProto.TestStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.incremental.IncrementalResultConverter.HostEnvironment;
import com.google.devtools.mobileharness.infra.ats.console.result.incremental.IncrementalResultConverter.InvocationPlan;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Attribute;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Metric;
import com.google.devtools.mobileharness.infra.ats.console.result.proto.ReportProto.Module;
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
import com.google.protobuf.Timestamp;
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
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class IncrementalResultConverterTest {

  private static final String GOLDEN_COMPLETE_RUN_INVOCATION =
      "result/incremental/testdata/complete_run/" + XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME;

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private final IncrementalResultConverter converter = new IncrementalResultConverter();

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
  public void parseInvocation_plannedModulesFormats_preservesOrderSaturatesSumAndSkipsInvalid()
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
                    .addValue("arm64-v8a CtsHugeMod:" + Integer.MAX_VALUE)
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
                    .addValue("arm64-v8a CtsMediaTestCases:3")
                    .addValue("arm64-v8a CtsHugeMod:5"))
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
            4,
            "arm64-v8a CtsHugeMod",
            Integer.MAX_VALUE)
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
  public void parseInvocation_emptyOrCorruptProtoFile_throwsReadProtoFileError() throws Exception {
    Path zeroByteDir = tempFolder.newFolder("zero_byte_proto").toPath();
    Files.write(zeroByteDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME), new byte[0]);

    MobileHarnessException zeroByteThrown =
        assertThrows(MobileHarnessException.class, () -> converter.parseInvocation(zeroByteDir));
    assertThat(zeroByteThrown.getErrorId()).isEqualTo(ExtErrorId.TEST_RECORD_READ_PROTO_FILE_ERROR);

    Path corruptDir = tempFolder.newFolder("corrupt_proto").toPath();
    Files.write(
        corruptDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        new byte[] {0x0A, 0x05, 0x01});

    MobileHarnessException corruptThrown =
        assertThrows(MobileHarnessException.class, () -> converter.parseInvocation(corruptDir));
    assertThat(corruptThrown.getErrorId()).isEqualTo(ExtErrorId.TEST_RECORD_READ_PROTO_FILE_ERROR);
  }

  @Test
  public void parseInvocation_invalidAnyTypeUrl_throwsInvalidProtoContextError() throws Exception {
    Path storeDir = tempFolder.newFolder("invalid_any_type").toPath();
    Metadata wrongMessage = Metadata.newBuilder().setKey("wrong").build();
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-bad-any")
            .setDescription(Any.pack(wrongMessage))
            .build());

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> converter.parseInvocation(storeDir));

    assertThat(thrown.getErrorId()).isEqualTo(ExtErrorId.TEST_RECORD_INVALID_PROTO_CONTEXT);
  }

  @Test
  public void parseInvocation_corruptPackedContextBytes_throwsInvalidProtoContextError()
      throws Exception {
    Path storeDir = tempFolder.newFolder("corrupt_context_bytes").toPath();
    Any corruptContextAny =
        Any.newBuilder()
            .setTypeUrl("type.googleapis.com/tradefed.invoker.Context")
            .setValue(ByteString.copyFrom(new byte[] {0x00}))
            .build();
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-corrupt-ctx")
            .setDescription(corruptContextAny)
            .build());

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, () -> converter.parseInvocation(storeDir));

    assertThat(thrown.getErrorId()).isEqualTo(ExtErrorId.TEST_RECORD_INVALID_PROTO_CONTEXT);
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
            .build();

    TestRecord skippedByDemotion =
        TestRecord.newBuilder()
            .setTestRecordId("android.net.cts.ClassATest#testDemoted")
            .setStatus(TestStatus.PASS)
            .setSkipReason(
                SkipReason.newBuilder().setReason("Demoted by SkipManager").setTrigger("DEMOTION"))
            .build();
    TestRecord runAttempt0 =
        newRunRecord(
                modNet,
                /* startSec= */ 10L,
                /* endSec= */ 10L,
                /* prepMs= */ 100L,
                /* teardownMs= */ 50L,
                ImmutableList.of(
                    failedTest("android.net.cts.ClassATest#testFlaky", "attempt0 fail"),
                    failedTest("android.net.cts.ClassATest#testAssumedOnRetry", "attempt0 fail"),
                    passedTest("android.net.cts.ClassATest#testPassFirst"),
                    skippedByDemotion))
            .toBuilder()
            .setAttemptId(0L)
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage("Attempt 0 transient crash"))
            .build();
    TestRecord runAttempt1 =
        newRunRecord(
                modNet,
                /* startSec= */ 10L,
                /* endSec= */ 11L,
                /* prepMs= */ 100L,
                /* teardownMs= */ 50L,
                ImmutableList.of(
                    failedTest("android.net.cts.ClassATest#testFlaky", "initial fail"),
                    newTestRecord(
                        "android.net.cts.ClassATest#testAssumedOnRetry",
                        TestStatus.ASSUMPTION_FAILURE,
                        "assumed false on retry")))
            .toBuilder()
            .setAttemptId(1L)
            .build();
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsNetTestCases_instant__0.pb"),
        newModuleRecord(modNet, netCtx, runAttempt0, runAttempt1));
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
    Context hostCtx =
        Context.newBuilder()
            .setTestTag("cts")
            .addMetadata(Metadata.newBuilder().setKey("module-id").addValue(modHost))
            .addMetadata(Metadata.newBuilder().setKey("module-name").addValue(modHost))
            .build();
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
    assertThat(netModule.getTotalTests()).isEqualTo(6);
    assertThat(netModule.getTestCase(0).getTest(1).getName()).isEqualTo("testAssumedOnRetry");
    assertThat(netModule.getTestCase(0).getTest(1).getResult()).isEqualTo("ASSUMPTION_FAILURE");
    assertThat(netModule.getTestCase(0).getTest(3).getName()).isEqualTo("testDemoted");
    assertThat(netModule.getTestCase(0).getTest(3).getResult()).isEqualTo("SKIPPED");
    assertThat(netModule.getTestCase(0).getTest(3).getSkipped()).isTrue();
    assertThat(netModule.getTestCaseList().stream().map(TestCase::getName))
        .containsExactly(
            "android.net.cts.ClassATest",
            "android.net.cts.ClassBTest",
            "android.net.cts.ClassCTest")
        .inOrder();

    Module hostModule = result.getModuleInfo(1);
    assertThat(hostModule.getName()).isEqualTo("CtsHostsideTestCases");
    assertThat(hostModule.hasAbi()).isFalse();
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
                    .putAttributes("cts:build_fingerprint", "")
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
            .putAttributes("cts:build_bad space", "should_be_dropped")
            .putAttributes("cts:non_allowlisted_collector_prop", "should_be_dropped")
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
            .addMetadata(Metadata.newBuilder().setKey("shard_count").addValue("4"))
            .addMetadata(
                Metadata.newBuilder()
                    .setKey("39011FDJH000AA-battery-initial -> setup")
                    .addValue("98"))
            .addMetadata(
                Metadata.newBuilder()
                    .setKey("cts:unallowed_meta_key")
                    .addValue("should_be_dropped"))
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
  public void convert_hashTruncatedFilenameAndDescriptionlessInvocation_recoversModuleMetadata()
      throws Exception {
    Path storeDir = tempFolder.newFolder("truncated_and_fallback").toPath();
    String longModuleName = "CtsVeryLongParameterizedModuleName_" + "x".repeat(200) + "[instant]";
    String longModuleId = "arm64-v8a " + longModuleName;
    String truncatedPrefix =
        ("arm64-v8a_" + longModuleName).replaceAll("[^A-Za-z0-9._-]", "_").substring(0, 200)
            + "_"
            + Integer.toHexString(longModuleId.hashCode());

    // Descriptionless invocation.pb must still allow module-level fallback metadata recovery.
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder().setTestRecordId("inv-empty-desc").build());

    Context fallbackModCtx =
        Context.newBuilder()
            .setTestTag("cts")
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
    assertThat(module.getPassed()).isEqualTo(1);
  }

  @Test
  public void convert_perShardRuntimeFallbackAndMalformedRecords_skipsInvalidEntries()
      throws Exception {
    Path storeDir = tempFolder.newFolder("runtime_and_malformed").toPath();
    String modId = "arm64-v8a CtsRuntimeMod";
    Context modCtx =
        Context.newBuilder()
            .setTestTag("cts")
            .addMetadata(Metadata.newBuilder().setKey("module-id").addValue(modId))
            .build();

    // Shard 0: run lacks timestamps -> falls back to module span (750 ms); malformed test IDs and
    // out-of-range timestamps do not crash conversion.
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

    // Orphan record without ID and Long-overflow numeric suffix is ignored cleanly.
    writeDelimitedRecord(
        storeDir.resolve("orphan_999999999999999999999999999.pb"),
        TestRecord.newBuilder()
            .setStartTime(Timestamp.newBuilder().setSeconds(Long.MAX_VALUE))
            .setDescription(Any.pack(Context.newBuilder().setTestTag("cts").build()))
            .build());

    // Shard 1: run has timestamps (1000 ms) while module span is 3000 ms -> uses run's 1000 ms.
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
  public void convert_stackTraceTruncation_preservesXmlEntitiesAndUtf16Surrogates()
      throws Exception {
    Path storeDir = tempFolder.newFolder("stack_truncation").toPath();
    String modId = "arm64-v8a CtsStackMod";
    int maxStack = 1024 * 1024;

    String header = "AssertionError: first line <bad>\n";
    String surrogateStack =
        header + "x".repeat(maxStack - header.length() - 1) + "\uD83D\uDE00trailing";
    String hugeSingleLine = "E".repeat(maxStack - 1) + "<trailing";

    TestRecord runRecord =
        TestRecord.newBuilder()
            .setTestRecordId(modId)
            .setParentTestRecordId(modId)
            .addChildren(
                ChildReference.newBuilder()
                    .setInlineTestRecord(
                        failedTest("android.cts.StackTest#testSurrogateBoundary", surrogateStack)))
            .addChildren(
                ChildReference.newBuilder()
                    .setInlineTestRecord(
                        failedTest("android.cts.StackTest#testSingleLine", hugeSingleLine)))
            .setDebugInfo(DebugInfo.newBuilder().setErrorMessage(hugeSingleLine))
            .setStartTime(Timestamp.newBuilder().setSeconds(100L))
            .setEndTime(Timestamp.newBuilder().setSeconds(101L))
            .build();
    Context ctx =
        Context.newBuilder()
            .setTestTag("cts")
            .addMetadata(Metadata.newBuilder().setKey("module-id").addValue(modId))
            .build();
    writeDelimitedRecord(
        storeDir.resolve(XtsConstants.INCREMENTAL_INVOCATION_FILE_NAME),
        TestRecord.newBuilder()
            .setTestRecordId("inv-trunc")
            .setStartTime(Timestamp.newBuilder().setSeconds(100L))
            .setEndTime(Timestamp.newBuilder().setSeconds(102L))
            .setDescription(Any.pack(Context.newBuilder().setTestTag("cts").build()))
            .build());
    writeDelimitedRecord(
        storeDir.resolve("arm64-v8a_CtsStackMod_0.pb"), newModuleRecord(modId, ctx, runRecord));

    Result result = converter.convert(storeDir).orElseThrow();

    Module module = result.getModuleInfo(0);
    assertThat(module.getReason().getMsg()).endsWith("E&lt;");
    assertThat(module.getReason().getMsg()).hasLength(maxStack + 3);
    TestFailure surrogateFailure = module.getTestCase(0).getTest(0).getFailure();
    assertThat(surrogateFailure.getMsg()).isEqualTo("AssertionError: first line &lt;bad&gt;");
    String content = surrogateFailure.getStackTrace().getContent();
    assertThat(Character.isHighSurrogate(content.charAt(content.length() - 1))).isFalse();
    assertThat(module.getTestCase(0).getTest(1).getFailure().getMsg()).endsWith("E&lt;");
    assertThat(module.getTestCase(0).getTest(1).getFailure().getMsg()).hasLength(maxStack + 3);
    assertThat(roundTripXml(result)).isEqualTo(result);
  }

  private Result roundTripXml(Result result) throws Exception {
    Injector injector =
        Guice.createInjector(
            new CompatibilityReportModule(),
            new CommonModule(ImmutableList.of(), ImmutableMap.of(), ImmutableMap.of()));
    Path reportDir = tempFolder.newFolder().toPath();
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
