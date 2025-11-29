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

package com.google.devtools.mobileharness.infra.master.rpc.stub.grpc;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link LabInfoGrpcStub}. */
@RunWith(JUnit4.class)
public final class LabInfoGrpcStubTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LabInfoGrpcStub.BlockingInterface blockingInterface;
  @Mock private LabInfoGrpcStub.FutureInterface futureInterface;

  private LabInfoGrpcStub labInfoGrpcStub;

  @Before
  public void setUp() {
    labInfoGrpcStub = new LabInfoGrpcStub(blockingInterface, futureInterface);
  }

  @Test
  public void getLabInfo_success() throws Exception {
    GetLabInfoResponse response = GetLabInfoResponse.getDefaultInstance();
    when(blockingInterface.getLabInfo(any(GetLabInfoRequest.class))).thenReturn(response);

    assertThat(labInfoGrpcStub.getLabInfo(GetLabInfoRequest.getDefaultInstance()))
        .isEqualTo(response);
  }

  @Test
  public void getLabInfoAsync_success() throws Exception {
    GetLabInfoResponse expectedResponse = GetLabInfoResponse.getDefaultInstance();
    when(futureInterface.getLabInfo(any(GetLabInfoRequest.class)))
        .thenReturn(immediateFuture(expectedResponse));

    ListenableFuture<GetLabInfoResponse> actualFuture =
        labInfoGrpcStub.getLabInfoAsync(GetLabInfoRequest.getDefaultInstance());

    // Assert that the future completes within a timeout and returns the expected value
    GetLabInfoResponse actualResponse = actualFuture.get(5, TimeUnit.SECONDS); // Added timeout
    assertThat(actualResponse).isEqualTo(expectedResponse);
  }
}
