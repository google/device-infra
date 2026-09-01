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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.PrepareDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.PrepareDeviceResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import io.grpc.Status;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class PrepareDeviceHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private PrepareDeviceActionHelper prepareDeviceActionHelper;

  @Inject private PrepareDeviceHandler prepareDeviceHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void prepareDevice_withUser_success() throws Exception {
    PrepareDeviceRequest request = PrepareDeviceRequest.newBuilder().setId("device_id").build();
    PrepareDeviceResponse expectedResponse = PrepareDeviceResponse.getDefaultInstance();

    when(prepareDeviceActionHelper.prepareDevice(request, UniverseScope.SELF, "user"))
        .thenReturn(immediateFuture(expectedResponse));

    PrepareDeviceResponse response =
        prepareDeviceHandler.prepareDevice(request, UniverseScope.SELF, Optional.of("user")).get();

    assertThat(response).isEqualTo(expectedResponse);
    verify(prepareDeviceActionHelper).prepareDevice(request, UniverseScope.SELF, "user");
  }

  @Test
  public void prepareDevice_withoutUser_success() throws Exception {
    PrepareDeviceRequest request = PrepareDeviceRequest.newBuilder().setId("device_id").build();
    PrepareDeviceResponse expectedResponse = PrepareDeviceResponse.getDefaultInstance();

    when(prepareDeviceActionHelper.prepareDevice(request, UniverseScope.SELF, ""))
        .thenReturn(immediateFuture(expectedResponse));

    PrepareDeviceResponse response =
        prepareDeviceHandler.prepareDevice(request, UniverseScope.SELF, Optional.empty()).get();

    assertThat(response).isEqualTo(expectedResponse);
    verify(prepareDeviceActionHelper).prepareDevice(request, UniverseScope.SELF, "");
  }

  @Test
  public void prepareDevice_helperError_propagates() {
    PrepareDeviceRequest request = PrepareDeviceRequest.newBuilder().setId("device_id").build();

    when(prepareDeviceActionHelper.prepareDevice(request, UniverseScope.SELF, "user"))
        .thenReturn(
            immediateFailedFuture(FeServiceException.permissionDenied("Permission denied")));

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () ->
                prepareDeviceHandler
                    .prepareDevice(request, UniverseScope.SELF, Optional.of("user"))
                    .get());

    assertThat(thrown).hasCauseThat().isInstanceOf(FeServiceException.class);
    FeServiceException cause = (FeServiceException) thrown.getCause();
    assertThat(cause.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
  }
}
