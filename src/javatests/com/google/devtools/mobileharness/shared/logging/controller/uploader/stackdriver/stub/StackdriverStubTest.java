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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.devtools.mobileharness.shared.util.comm.server.ServerBuilderFactory;
import com.google.devtools.mobileharness.shared.util.comm.stub.ChannelFactory;
import com.google.logging.v2.LoggingServiceV2Grpc;
import com.google.logging.v2.WriteLogEntriesRequest;
import com.google.logging.v2.WriteLogEntriesResponse;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link StackdriverStub}. */
@RunWith(JUnit4.class)
public class StackdriverStubTest {
  private static final String SECRET_FILE_NAME = "fake_secret.json";

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  @Test
  public void writeLogEntries_success() throws Exception {
    Server server =
        grpcCleanup.register(
            ServerBuilderFactory.createNettyServerBuilder(/* port= */ 0, /* localhost= */ true)
                .addService(getServiceImpl())
                .build());
    server.start();
    StackdriverStub stackdriverStub = new StackdriverStub(SECRET_FILE_NAME);
    stackdriverStub.loggingServiceV2BlockingStub =
        LoggingServiceV2Grpc.newBlockingStub(
            grpcCleanup.register(
                ChannelFactory.createChannel("localhost:" + server.getPort(), directExecutor())));
    assertThat(stackdriverStub.writeLogEntries(WriteLogEntriesRequest.getDefaultInstance()))
        .isEqualToDefaultInstance();
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
