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

package com.google.devtools.mobileharness.fe.v6.service.host;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.BatchPreflightLabServerReleaseRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.BatchPreflightLabServerReleaseResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.BatchReleaseLabServersRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.BatchReleaseLabServersResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.UpdatePassThroughFlagsResponse;
import io.grpc.stub.StreamObserver;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HostServiceGrpcImplTest {

  private HostServiceLogic logic;
  private ListeningExecutorService executor;
  private HostServiceGrpcImpl service;

  @Before
  public void setUp() {
    logic = mock(HostServiceLogic.class);
    executor = MoreExecutors.newDirectExecutorService();
    service = new HostServiceGrpcImpl(logic, executor);
  }

  @Test
  public void updatePassThroughFlags_callsLogic() {
    UpdatePassThroughFlagsRequest request = UpdatePassThroughFlagsRequest.getDefaultInstance();
    StreamObserver<UpdatePassThroughFlagsResponse> responseObserver =
        new StreamObserver<UpdatePassThroughFlagsResponse>() {
          @Override
          public void onNext(UpdatePassThroughFlagsResponse response) {}

          @Override
          public void onError(Throwable t) {}

          @Override
          public void onCompleted() {}
        };

    when(logic.updatePassThroughFlags(any()))
        .thenReturn(Futures.immediateFuture(UpdatePassThroughFlagsResponse.getDefaultInstance()));

    service.updatePassThroughFlags(request, responseObserver);

    verify(logic).updatePassThroughFlags(eq(request));
  }

  @Test
  public void batchPreflightLabServerRelease_callsLogic() {
    BatchPreflightLabServerReleaseRequest request =
        BatchPreflightLabServerReleaseRequest.getDefaultInstance();
    StreamObserver<BatchPreflightLabServerReleaseResponse> responseObserver =
        new StreamObserver<BatchPreflightLabServerReleaseResponse>() {
          @Override
          public void onNext(BatchPreflightLabServerReleaseResponse response) {}

          @Override
          public void onError(Throwable t) {}

          @Override
          public void onCompleted() {}
        };

    when(logic.batchPreflightLabServerRelease(any(), any()))
        .thenReturn(
            Futures.immediateFuture(BatchPreflightLabServerReleaseResponse.getDefaultInstance()));

    service.batchPreflightLabServerRelease(request, responseObserver);

    verify(logic).batchPreflightLabServerRelease(eq(request), eq(Optional.empty()));
  }

  @Test
  public void batchReleaseLabServers_callsLogic() {
    BatchReleaseLabServersRequest request = BatchReleaseLabServersRequest.getDefaultInstance();
    StreamObserver<BatchReleaseLabServersResponse> responseObserver =
        new StreamObserver<BatchReleaseLabServersResponse>() {
          @Override
          public void onNext(BatchReleaseLabServersResponse response) {}

          @Override
          public void onError(Throwable t) {}

          @Override
          public void onCompleted() {}
        };

    when(logic.batchReleaseLabServers(any(), any()))
        .thenReturn(Futures.immediateFuture(BatchReleaseLabServersResponse.getDefaultInstance()));

    service.batchReleaseLabServers(request, responseObserver);

    verify(logic).batchReleaseLabServers(eq(request), eq(Optional.empty()));
  }
}
