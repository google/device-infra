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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static org.junit.Assert.assertThrows;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.comm.server.ServerBuilderFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.logging.v2.LoggingServiceV2Grpc;
import com.google.logging.v2.WriteLogEntriesRequest;
import com.google.logging.v2.WriteLogEntriesResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StackdriverStubTest {

  @Rule public final SetFlags flags = new SetFlags();

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  @Test
  public void writeLogEntries_success() throws Exception {
    Server server =
        grpcCleanup.register(
            ServerBuilderFactory.createNettyServerBuilder(/* port= */ 0, /* localhost= */ true)
                .addService(getServiceImpl())
                .build());
    server.start();
    StackdriverStub stackdriverStub =
        new StackdriverStub(
            new LocalFileUtil(),
            () ->
                GoogleCredentials.create(
                    new AccessToken("fake-token", /* expirationTime= */ null)));
    stackdriverStub.loggingServiceV2BlockingStub =
        LoggingServiceV2Grpc.newBlockingStub(
            grpcCleanup.register(
                ChannelFactory.createChannel("localhost:" + server.getPort(), directExecutor())));
    assertThat(stackdriverStub.writeLogEntries(WriteLogEntriesRequest.getDefaultInstance()))
        .isEqualToDefaultInstance();
  }

  @Test
  public void init_withoutCredFileFlag_usesScopedApplicationDefaultCredentials() throws Exception {
    ScopeRecordingCredentials credentials = new ScopeRecordingCredentials();
    StackdriverStub stackdriverStub = new StackdriverStub(new LocalFileUtil(), () -> credentials);

    stackdriverStub.init();

    assertThat(credentials.requestedScopes).isEqualTo(StackdriverStub.WRITE_SCOPES);
    assertThat(stackdriverStub.loggingServiceV2BlockingStub).isNotNull();
    grpcCleanup.register(
        (ManagedChannel) stackdriverStub.loggingServiceV2BlockingStub.getChannel());
  }

  @Test
  public void init_withoutCredFileFlagAndAdcFails_skipsUploadWithoutThrowing() throws Exception {
    flags.set("stackdriver_cred_file", "");
    StackdriverStub stackdriverStub =
        new StackdriverStub(
            new LocalFileUtil(),
            () -> {
              throw new IOException("no ADC found");
            });

    stackdriverStub.init();

    assertThat(stackdriverStub.loggingServiceV2BlockingStub).isNull();
    assertThat(stackdriverStub.writeLogEntries(WriteLogEntriesRequest.getDefaultInstance()))
        .isEqualToDefaultInstance();
  }

  @Test
  public void writeLogEntries_beforeInit_throwsIllegalStateException() {
    StackdriverStub stackdriverStub =
        new StackdriverStub(
            new LocalFileUtil(),
            () ->
                GoogleCredentials.create(
                    new AccessToken("fake-token", /* expirationTime= */ null)));

    assertThrows(
        IllegalStateException.class,
        () -> stackdriverStub.writeLogEntries(WriteLogEntriesRequest.getDefaultInstance()));
  }

  @Test
  public void writeLogEntries_afterFailedInit_throwsIllegalStateException() {
    flags.set("stackdriver_cred_file", "/nonexistent/stackdriver_cred.json");
    StackdriverStub stackdriverStub =
        new StackdriverStub(
            new LocalFileUtil(),
            () ->
                GoogleCredentials.create(
                    new AccessToken("fake-token", /* expirationTime= */ null)));
    assertThrows(MobileHarnessException.class, stackdriverStub::init);

    assertThrows(
        IllegalStateException.class,
        () -> stackdriverStub.writeLogEntries(WriteLogEntriesRequest.getDefaultInstance()));
  }

  @Test
  public void init_missingCredFile_throwsException() {
    flags.set("stackdriver_cred_file", "/nonexistent/stackdriver_cred.json");
    StackdriverStub stackdriverStub =
        new StackdriverStub(
            new LocalFileUtil(),
            () ->
                GoogleCredentials.create(
                    new AccessToken("fake-token", /* expirationTime= */ null)));

    MobileHarnessException thrown =
        assertThrows(MobileHarnessException.class, stackdriverStub::init);

    assertThat(thrown.getErrorId())
        .isEqualTo(InfraErrorId.LOGGER_STACKDRIVER_CLIENT_SECRET_FILE_ERROR);
  }

  private static final class ScopeRecordingCredentials extends GoogleCredentials {
    private ImmutableSet<String> requestedScopes = ImmutableSet.of();

    private ScopeRecordingCredentials() {
      super(new AccessToken("fake-token", /* expirationTime= */ null));
    }

    @Override
    public GoogleCredentials createScoped(Collection<String> scopes) {
      this.requestedScopes = ImmutableSet.copyOf(scopes);
      return this;
    }
  }

  private static LoggingServiceV2Grpc.LoggingServiceV2ImplBase getServiceImpl() {
    return new LoggingServiceV2Grpc.LoggingServiceV2ImplBase() {
      @Override
      public void writeLogEntries(
          WriteLogEntriesRequest request,
          StreamObserver<WriteLogEntriesResponse> responseStreamObserver) {
        responseStreamObserver.onNext(WriteLogEntriesResponse.getDefaultInstance());
        responseStreamObserver.onCompleted();
      }
    };
  }
}
