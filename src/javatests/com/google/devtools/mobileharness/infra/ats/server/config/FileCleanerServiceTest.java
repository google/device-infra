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

package com.google.devtools.mobileharness.infra.ats.server.config;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.server.config.store.AtsConfigStore;
import com.google.devtools.mobileharness.infra.ats.server.config.store.InMemoryAtsConfigStore;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerConfig;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerCriterion;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerOperation;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerPolicy;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerServiceGrpc;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerServiceGrpc.FileCleanerServiceBlockingStub;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerSettings;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.GetFileCleanerSettingsRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.NameValuePair;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.ResetFileCleanerSettingsRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.UpdateFileCleanerSettingsRequest;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FileCleanerServiceTest {

  private static final FileCleanerPolicy POLICY =
      FileCleanerPolicy.newBuilder()
          .setName("policy")
          .setTarget(FileCleanerPolicy.Target.FILE)
          .setOperation(
              FileCleanerOperation.newBuilder()
                  .setType(FileCleanerOperation.Type.DELETE)
                  .addParams(NameValuePair.newBuilder().setName("name").setValue("value")))
          .addCriteria(
              FileCleanerCriterion.newBuilder().setType(FileCleanerCriterion.Type.NAME_MATCH))
          .build();
  private static final FileCleanerSettings SETTINGS =
      FileCleanerSettings.newBuilder()
          .addPolicies(POLICY)
          .addConfigs(
              FileCleanerConfig.newBuilder()
                  .setName("config")
                  .addDirectories("/dir")
                  .addPolicyNames("policy"))
          .build();

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
  @Rule public final SetFlags flags = new SetFlags();

  @Before
  public void setUp() {
    flags.set("ats_storage_path", "/storage");
  }

  @Test
  public void getFileCleanerSettings_noStoredSettings_returnsDefaultSettings() throws Exception {
    FileCleanerServiceBlockingStub stub = createStub(new InMemoryAtsConfigStore());

    FileCleanerSettings settings =
        stub.getFileCleanerSettings(GetFileCleanerSettingsRequest.getDefaultInstance());

    assertThat(settings.getPoliciesList().stream().map(FileCleanerPolicy::getName))
        .containsExactly(
            "Archive directories not modified", "Delete directories not modified and accessed")
        .inOrder();
    assertThat(settings.getConfigsList())
        .containsExactly(
            FileCleanerConfig.newBuilder()
                .setName("Clean up test results")
                .setDescription("Clean up test results")
                .addDirectories("/storage/app_default_bucket/test_runs")
                .addPolicyNames("Archive directories not modified")
                .build(),
            FileCleanerConfig.newBuilder()
                .setName("Clean up test work files")
                .setDescription("Clean up test work files")
                .addDirectories("/storage/tmp")
                .addPolicyNames("Delete directories not modified and accessed")
                .build())
        .inOrder();
  }

  @Test
  public void updateFileCleanerSettings_storesSettings() throws Exception {
    FileCleanerServiceBlockingStub stub = createStub(new InMemoryAtsConfigStore());

    FileCleanerSettings updatedSettings = stub.updateFileCleanerSettings(updateRequest(SETTINGS));

    assertThat(updatedSettings).isEqualTo(SETTINGS);
    assertThat(stub.getFileCleanerSettings(GetFileCleanerSettingsRequest.getDefaultInstance()))
        .isEqualTo(SETTINGS);
  }

  @Test
  public void updateFileCleanerSettings_removesNamesOfUnknownPolicies() throws Exception {
    FileCleanerServiceBlockingStub stub = createStub(new InMemoryAtsConfigStore());
    FileCleanerSettings.Builder settings = SETTINGS.toBuilder();
    settings.getConfigsBuilder(0).addPolicyNames("unknown_policy");

    FileCleanerSettings updatedSettings =
        stub.updateFileCleanerSettings(updateRequest(settings.build()));

    assertThat(updatedSettings).isEqualTo(SETTINGS);
  }

  @Test
  public void updateFileCleanerSettings_missingRequiredField_failsWithInvalidArgument()
      throws Exception {
    FileCleanerServiceBlockingStub stub = createStub(new InMemoryAtsConfigStore());
    UpdateFileCleanerSettingsRequest request =
        updateRequest(
            FileCleanerSettings.newBuilder().addPolicies(POLICY.toBuilder().clearName()).build());

    StatusRuntimeException e =
        assertThrows(StatusRuntimeException.class, () -> stub.updateFileCleanerSettings(request));

    assertThat(e.getStatus().getCode()).isEqualTo(Code.INVALID_ARGUMENT);
    assertThat(e.getStatus().getDescription())
        .isEqualTo("Message FileCleanerPolicy is missing required field name");
    // The stored settings are not changed.
    assertThat(
            stub.getFileCleanerSettings(GetFileCleanerSettingsRequest.getDefaultInstance())
                .getPoliciesList())
        .hasSize(2);
  }

  @Test
  public void updateFileCleanerSettings_unspecifiedEnumValue_failsWithInvalidArgument()
      throws Exception {
    FileCleanerServiceBlockingStub stub = createStub(new InMemoryAtsConfigStore());
    FileCleanerPolicy.Builder policy = POLICY.toBuilder();
    policy.getOperationBuilder().setType(FileCleanerOperation.Type.TYPE_UNSPECIFIED);
    UpdateFileCleanerSettingsRequest request =
        updateRequest(FileCleanerSettings.newBuilder().addPolicies(policy).build());

    StatusRuntimeException e =
        assertThrows(StatusRuntimeException.class, () -> stub.updateFileCleanerSettings(request));

    assertThat(e.getStatus().getCode()).isEqualTo(Code.INVALID_ARGUMENT);
    assertThat(e.getStatus().getDescription())
        .isEqualTo("Message FileCleanerOperation is missing required field type");
  }

  @Test
  public void resetFileCleanerSettings_returnsOldSettingsAndRestoresDefaultSettings()
      throws Exception {
    FileCleanerServiceBlockingStub stub = createStub(new InMemoryAtsConfigStore());
    FileCleanerSettings defaultSettings =
        stub.getFileCleanerSettings(GetFileCleanerSettingsRequest.getDefaultInstance());
    stub.updateFileCleanerSettings(updateRequest(SETTINGS));

    FileCleanerSettings oldSettings =
        stub.resetFileCleanerSettings(ResetFileCleanerSettingsRequest.getDefaultInstance());

    assertThat(oldSettings).isEqualTo(SETTINGS);
    assertThat(stub.getFileCleanerSettings(GetFileCleanerSettingsRequest.getDefaultInstance()))
        .isEqualTo(defaultSettings);
  }

  @Test
  public void getFileCleanerSettings_storeError_failsWithInternal() throws Exception {
    AtsConfigStore store = mock(AtsConfigStore.class);
    when(store.get(any(), any()))
        .thenThrow(new MobileHarnessException(BasicErrorId.DATABASE_TABLE_READ_ERROR, "error"));
    FileCleanerServiceBlockingStub stub = createStub(store);

    StatusRuntimeException e =
        assertThrows(
            StatusRuntimeException.class,
            () -> stub.getFileCleanerSettings(GetFileCleanerSettingsRequest.getDefaultInstance()));

    assertThat(e.getStatus().getCode()).isEqualTo(Code.INTERNAL);
  }

  private static UpdateFileCleanerSettingsRequest updateRequest(FileCleanerSettings settings) {
    return UpdateFileCleanerSettingsRequest.newBuilder().setSettings(settings).build();
  }

  /** Starts an in-process server with the service on the given store and returns a stub of it. */
  private FileCleanerServiceBlockingStub createStub(AtsConfigStore store) throws Exception {
    String serverName = InProcessServerBuilder.generateName();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new FileCleanerService(store))
            .build()
            .start());
    return FileCleanerServiceGrpc.newBlockingStub(
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
  }
}
