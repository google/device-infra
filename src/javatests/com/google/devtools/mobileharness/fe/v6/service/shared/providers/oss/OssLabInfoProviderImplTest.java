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

package com.google.devtools.mobileharness.fe.v6.service.shared.providers.oss;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.infra.master.rpc.stub.LabInfoStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoRequest;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class OssLabInfoProviderImplTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LabInfoStub labInfoStub;

  private OssLabInfoProviderImpl ossLabInfoProvider;

  @Before
  public void setUp() {
    ossLabInfoProvider = new OssLabInfoProviderImpl(labInfoStub);
  }

  @Test
  public void getLabInfoAsync_success() throws Exception {
    GetLabInfoRequest request = GetLabInfoRequest.getDefaultInstance();
    GetLabInfoResponse expectedResponse = GetLabInfoResponse.getDefaultInstance();
    when(labInfoStub.getLabInfoAsync(request)).thenReturn(immediateFuture(expectedResponse));

    assertThat(ossLabInfoProvider.getLabInfoAsync(request, "any_universe").get())
        .isEqualTo(expectedResponse);
  }

  @Test
  public void getLabInfoAsync_stubFails() throws Exception {
    GetLabInfoRequest request = GetLabInfoRequest.getDefaultInstance();
    when(labInfoStub.getLabInfoAsync(request))
        .thenReturn(immediateFailedFuture(new RuntimeException("Stub call error")));

    assertThrows(
        ExecutionException.class,
        () -> ossLabInfoProvider.getLabInfoAsync(request, "any_universe").get());
  }
}
