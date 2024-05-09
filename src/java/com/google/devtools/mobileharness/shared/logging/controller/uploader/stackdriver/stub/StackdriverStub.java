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

package com.google.devtools.mobileharness.shared.logging.controller.uploader.stackdriver.stub;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcExceptionWithErrorId;
import com.google.devtools.common.metrics.stability.rpc.grpc.GrpcStubUtil;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.logging.annotation.Annotations.StackdriverSecretFileName;
import com.google.devtools.mobileharness.shared.util.comm.stub.ClientInterceptorFactory;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Inject;
import com.google.logging.v2.LoggingServiceV2Grpc;
import com.google.logging.v2.LoggingServiceV2Grpc.LoggingServiceV2BlockingStub;
import com.google.logging.v2.WriteLogEntriesRequest;
import com.google.logging.v2.WriteLogEntriesResponse;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Stub of Mobile Harness Stackdriver Logging. */
public class StackdriverStub {

  private static final String LOGGING_SERVICE_TARGET = "logging.googleapis.com";

  private static final ImmutableSet<String> WRITE_SCOPES =
      ImmutableSet.of("https://www.googleapis.com/auth/logging.write");

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private final String secretFileName;

  private final LocalFileUtil fileUtil;

  @VisibleForTesting volatile LoggingServiceV2BlockingStub loggingServiceV2BlockingStub;

  @Inject
  StackdriverStub(@StackdriverSecretFileName String secretFileName) {
    this(secretFileName, new LocalFileUtil());
  }

  StackdriverStub(String secretFileName, LocalFileUtil fileUtil) {
    this.secretFileName = secretFileName;
    this.fileUtil = fileUtil;
  }

  public void init() throws MobileHarnessException {
    checkState(!isInitialized.getAndSet(true), "StackdriverStub has already been initialized");
    String clientSecretFilePath = getCredentialFromFlag().orElse(null);
    checkNotNull(
        clientSecretFilePath, "Please provide the credential key via flag stackdriver_cred_file.");
    List<ClientInterceptor> interceptors = Lists.newArrayList();
    interceptors.add(ClientInterceptorFactory.authorityInterceptor(LOGGING_SERVICE_TARGET));
    interceptors.add(
        ClientInterceptorFactory.credentialInterceptor(
            new File(clientSecretFilePath), WRITE_SCOPES));

    this.loggingServiceV2BlockingStub =
        LoggingServiceV2Grpc.newBlockingStub(
            ManagedChannelBuilder.forTarget(LOGGING_SERVICE_TARGET)
                .intercept(interceptors)
                .build());
  }

  private Optional<String> getCredentialFromFlag() throws MobileHarnessException {
    String credentialFilePath = Flags.instance().stackdriverCredentialFile.get();
    if (credentialFilePath != null) {
      if (!fileUtil.isFileExist(credentialFilePath)) {
        throw new MobileHarnessException(
            InfraErrorId.LOGGER_STACKDRIVER_CLIENT_SECRET_FILE_ERROR,
            "Missing local credential file " + credentialFilePath);
      }
      return Optional.of(credentialFilePath);
    }
    return Optional.empty();
  }

  /** Writes the log entries to Stackdriver. */
  @CanIgnoreReturnValue
  public WriteLogEntriesResponse writeLogEntries(WriteLogEntriesRequest writeLogEntriesRequest)
      throws MobileHarnessException {
    checkNotNull(loggingServiceV2BlockingStub);
    try {
      return GrpcStubUtil.invoke(
          loggingServiceV2BlockingStub::writeLogEntries,
          writeLogEntriesRequest,
          InfraErrorId.LOGGER_STACKDRIVER_WRITE_RPC_ERROR,
          "Failed to call WriteLogEntries.");
    } catch (GrpcExceptionWithErrorId e) {
      throw new MobileHarnessException(
          InfraErrorId.LOGGER_STACKDRIVER_WRITE_RPC_ERROR, "Failed to call WriteLogEntries.", e);
    }
  }
}
