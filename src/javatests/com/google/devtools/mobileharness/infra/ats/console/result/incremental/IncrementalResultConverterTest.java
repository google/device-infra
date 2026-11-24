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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.Assert.assertThrows;

import com.android.tradefed.config.proto.ConfigurationDescription.Metadata;
import com.android.tradefed.invoker.proto.InvocationContext.Context;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.result.incremental.IncrementalResultConverter.InvocationPlan;
import com.google.devtools.mobileharness.infra.ats.console.util.TestRunfilesUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
    Path storeDir =
        Path.of(TestRunfilesUtil.getRunfilesLocation(GOLDEN_COMPLETE_RUN_INVOCATION)).getParent();

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
  public void parseInvocation_corruptProtoFile_throwsReadProtoFileError() throws Exception {
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

  private static void writeDelimitedRecord(Path targetFile, TestRecord record) throws IOException {
    try (OutputStream out = Files.newOutputStream(targetFile)) {
      record.writeDelimitedTo(out);
    }
  }
}
