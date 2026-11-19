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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceError;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceErrorsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetDeviceErrorsResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
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
public final class GetDeviceErrorsHandlerTest {

  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();

  @Inject private GetDeviceErrorsHandler handler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void getDeviceErrors_success() throws Exception {
    DeviceError error = DeviceError.getDefaultInstance();
    when(labInfoProvider.getDeviceErrorsAsync(
            eq(
                LabInfoServiceProto.GetDeviceErrorsRequest.newBuilder()
                    .setDeviceId("device-1")
                    .setHostName("host-1")
                    .build()),
            eq(UNIVERSE)))
        .thenReturn(
            immediateFuture(
                LabInfoServiceProto.GetDeviceErrorsResponse.newBuilder()
                    .addDeviceErrors(error)
                    .build()));

    GetDeviceErrorsResponse response =
        handler
            .getDeviceErrors(
                GetDeviceErrorsRequest.newBuilder().setId("device-1").setHostName("host-1").build(),
                UNIVERSE)
            .get();

    assertThat(response)
        .isEqualTo(GetDeviceErrorsResponse.newBuilder().addDeviceErrors(error).build());
  }
}
