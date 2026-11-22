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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.server.config.store.AtsConfigStore;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerConfig;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerCriterion;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerOperation;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerPolicy;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerServiceGrpc.FileCleanerServiceImplBase;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.FileCleanerSettings;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.GetFileCleanerSettingsRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.NameValuePair;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.ResetFileCleanerSettingsRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.config.UpdateFileCleanerSettingsRequest;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** gRPC service for managing the file cleaner settings of ATS server. */
@Singleton
final class FileCleanerService extends FileCleanerServiceImplBase {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Kind and ID of the entity which holds the settings in the store. */
  private static final String ENTITY_KIND = "FILE_CLEANER_SETTINGS";

  private static final String ENTITY_ID = "default";

  /** Name of the bucket directory of test outputs under the storage path. */
  private static final String DEFAULT_BUCKET_NAME = "app_default_bucket";

  /** Logic of a method of the service. */
  private interface Method<ResponseT> {
    ResponseT execute() throws MobileHarnessException;
  }

  private final AtsConfigStore store;

  @Inject
  FileCleanerService(AtsConfigStore store) {
    this.store = store;
  }

  @Override
  public void getFileCleanerSettings(
      GetFileCleanerSettingsRequest request, StreamObserver<FileCleanerSettings> responseObserver) {
    invoke(responseObserver, this::getSettings);
  }

  @Override
  public void updateFileCleanerSettings(
      UpdateFileCleanerSettingsRequest request,
      StreamObserver<FileCleanerSettings> responseObserver) {
    invoke(responseObserver, () -> updateSettings(request.getSettings()));
  }

  @Override
  public void resetFileCleanerSettings(
      ResetFileCleanerSettingsRequest request,
      StreamObserver<FileCleanerSettings> responseObserver) {
    invoke(responseObserver, this::resetSettings);
  }

  /** Returns the stored settings, or the default ones if no settings are stored. */
  private FileCleanerSettings getSettings() throws MobileHarnessException {
    Optional<ByteString> data = store.get(ENTITY_KIND, ENTITY_ID);
    if (data.isEmpty()) {
      return createDefaultSettings();
    }
    try {
      return FileCleanerSettings.parseFrom(
          data.get(), ProtoExtensionRegistry.getGeneratedRegistry());
    } catch (InvalidProtocolBufferException e) {
      throw new MobileHarnessException(
          BasicErrorId.DATABASE_TABLE_READ_PROTO_PARSE_ERROR,
          "Failed to parse the stored file cleaner settings",
          e);
    }
  }

  /**
   * Stores the settings and returns what was stored, which is the given settings without the policy
   * names of configs that refer to no policy.
   */
  private FileCleanerSettings updateSettings(FileCleanerSettings settings)
      throws MobileHarnessException {
    validateSettings(settings);

    ImmutableSet<String> policyNames =
        settings.getPoliciesList().stream()
            .map(FileCleanerPolicy::getName)
            .collect(toImmutableSet());
    FileCleanerSettings newSettings =
        settings.toBuilder()
            .clearConfigs()
            .addAllConfigs(
                settings.getConfigsList().stream()
                    .map(config ->
                        config.toBuilder()
                            .clearPolicyNames()
                            .addAllPolicyNames(
                                config.getPolicyNamesList().stream()
                                    .filter(policyNames::contains)
                                    .collect(toImmutableList()))
                            .build())
                    .collect(toImmutableList()))
            .build();

    store.put(ENTITY_KIND, ENTITY_ID, newSettings.toByteString());
    return newSettings;
  }

  /** Deletes the stored settings and returns the settings as they were before the deletion. */
  private FileCleanerSettings resetSettings() throws MobileHarnessException {
    FileCleanerSettings settings = getSettings();
    store.delete(ENTITY_KIND, ENTITY_ID);
    return settings;
  }

  /**
   * Checks that the required fields of the settings are present and that no enum field is set to
   * its unspecified value.
   *
   * @throws IllegalArgumentException if the settings are invalid
   */
  private static void validateSettings(FileCleanerSettings settings) {
    settings
        .getPoliciesList()
        .forEach(policy -> {
          checkRequiredField(policy.hasName(), "FileCleanerPolicy", "name");
          checkRequiredField(policy.hasOperation(), "FileCleanerPolicy", "operation");
          checkRequiredField(
              policy.getOperation().getType() != FileCleanerOperation.Type.TYPE_UNSPECIFIED,
              "FileCleanerOperation",
              "type");
          checkArgument(
              !policy.hasTarget()
                  || policy.getTarget() != FileCleanerPolicy.Target.TARGET_UNSPECIFIED,
              "Field target of message FileCleanerPolicy is unspecified");
          validateParams(policy.getOperation().getParamsList());
          policy
              .getCriteriaList()
              .forEach(criterion -> {
                checkRequiredField(
                    criterion.getType() != FileCleanerCriterion.Type.TYPE_UNSPECIFIED,
                    "FileCleanerCriterion",
                    "type");
                validateParams(criterion.getParamsList());
              });
        });
    settings
        .getConfigsList()
        .forEach(config -> checkRequiredField(config.hasName(), "FileCleanerConfig", "name"));
  }

  private static void validateParams(List<NameValuePair> params) {
    params.forEach(param -> checkRequiredField(param.hasName(), "NameValuePair", "name"));
  }

  private static void checkRequiredField(boolean present, String messageName, String fieldName) {
    checkArgument(present, "Message %s is missing required field %s", messageName, fieldName);
  }

  /** Creates the settings which are in effect when no settings are stored. */
  private static FileCleanerSettings createDefaultSettings() {
    String storagePath = Flags.atsStoragePath.getNonNull();
    NameValuePair ttl = NameValuePair.newBuilder().setName("ttl").setValue("7 days").build();
    FileCleanerPolicy archivePolicy =
        FileCleanerPolicy.newBuilder()
            .setName("Archive directories not modified")
            .setTarget(FileCleanerPolicy.Target.DIRECTORY)
            .setOperation(
                FileCleanerOperation.newBuilder().setType(FileCleanerOperation.Type.ARCHIVE))
            .addCriteria(
                FileCleanerCriterion.newBuilder()
                    .setType(FileCleanerCriterion.Type.LAST_MODIFIED_TIME)
                    .addParams(ttl))
            .build();
    FileCleanerPolicy deletePolicy =
        FileCleanerPolicy.newBuilder()
            .setName("Delete directories not modified and accessed")
            .setTarget(FileCleanerPolicy.Target.DIRECTORY)
            .setOperation(
                FileCleanerOperation.newBuilder().setType(FileCleanerOperation.Type.DELETE))
            .addCriteria(
                FileCleanerCriterion.newBuilder()
                    .setType(FileCleanerCriterion.Type.LAST_MODIFIED_TIME)
                    .addParams(ttl))
            .addCriteria(
                FileCleanerCriterion.newBuilder()
                    .setType(FileCleanerCriterion.Type.LAST_ACCESS_TIME)
                    .addParams(ttl))
            .build();
    return FileCleanerSettings.newBuilder()
        .addPolicies(archivePolicy)
        .addPolicies(deletePolicy)
        .addConfigs(
            FileCleanerConfig.newBuilder()
                .setName("Clean up test results")
                .setDescription("Clean up test results")
                .addDirectories(String.format("%s/%s/test_runs", storagePath, DEFAULT_BUCKET_NAME))
                .addPolicyNames(archivePolicy.getName()))
        .addConfigs(
            FileCleanerConfig.newBuilder()
                .setName("Clean up test work files")
                .setDescription("Clean up test work files")
                .addDirectories(String.format("%s/tmp", storagePath))
                .addPolicyNames(deletePolicy.getName()))
        .build();
  }

  /**
   * Runs the logic of a method and sends its response to the client.
   *
   * <p>A failure is sent as a canonical gRPC status: {@code INVALID_ARGUMENT} for an invalid
   * request, and {@code INTERNAL} for any other failure.
   */
  private static <ResponseT> void invoke(
      StreamObserver<ResponseT> responseObserver, Method<ResponseT> method) {
    ResponseT response;
    try {
      response = method.execute();
    } catch (IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT
              .withDescription(e.getMessage())
              .withCause(e)
              .asRuntimeException());
      return;
    } catch (MobileHarnessException | RuntimeException e) {
      logger.atWarning().withCause(e).log("Failed to handle a file cleaner settings request");
      responseObserver.onError(
          Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
      return;
    }
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
