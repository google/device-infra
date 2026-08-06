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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.errors.FeServiceException;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.PrepareDeviceRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.PrepareDeviceResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.DeviceAccessResolver;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
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
  @Bind @Mock private DeviceAccessResolver deviceAccessResolver;
  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind private final ListeningExecutorService executor = newDirectExecutorService();

  @Inject private PrepareDeviceHandler prepareDeviceHandler;

  private static final DeviceInfo DEVICE_INFO = DeviceInfo.getDefaultInstance();

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  private void stubDeviceLookup() {
    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setDeviceView(
                        DeviceView.newBuilder()
                            .setGroupedDevices(
                                GroupedDevices.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder().addDeviceInfo(DEVICE_INFO)))))
            .build();
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(labInfoResponse));
  }

  @Test
  public void prepareDevice_success() throws Exception {
    PrepareDeviceRequest request = PrepareDeviceRequest.newBuilder().setId("device_id").build();
    PrepareDeviceResponse expectedResponse = PrepareDeviceResponse.getDefaultInstance();

    stubDeviceLookup();
    when(deviceAccessResolver.hasExecutePermission("user", DEVICE_INFO))
        .thenReturn(immediateFuture(true));
    when(prepareDeviceActionHelper.prepareDevice(request, UniverseScope.SELF, "user"))
        .thenReturn(immediateFuture(expectedResponse));

    PrepareDeviceResponse response =
        prepareDeviceHandler.prepareDevice(request, UniverseScope.SELF, Optional.of("user")).get();

    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  public void prepareDevice_permissionDenied() {
    PrepareDeviceRequest request = PrepareDeviceRequest.newBuilder().setId("device_id").build();

    stubDeviceLookup();
    when(deviceAccessResolver.hasExecutePermission("user", DEVICE_INFO))
        .thenReturn(immediateFuture(false));

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
    assertThat(cause).hasMessageThat().contains("prepare device");
  }

  @Test
  public void prepareDevice_noUser() {
    PrepareDeviceRequest request = PrepareDeviceRequest.newBuilder().setId("device_id").build();

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () ->
                prepareDeviceHandler
                    .prepareDevice(request, UniverseScope.SELF, Optional.empty())
                    .get());

    assertThat(thrown).hasCauseThat().isInstanceOf(FeServiceException.class);
    FeServiceException cause = (FeServiceException) thrown.getCause();
    assertThat(cause.getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    assertThat(cause).hasMessageThat().contains("User identity not found");
  }
}
