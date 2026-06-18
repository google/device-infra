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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.GetLogcatResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.DevicePermissionChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.util.Optional;
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
public final class GetLogcatHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  private static final UniverseScope SELF_UNIVERSE = new UniverseScope.SelfUniverse();

  @Bind @Mock private LogcatActionHelper logcatActionHelper;
  @Bind @Mock private DevicePermissionChecker devicePermissionChecker;
  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind private final ListeningExecutorService executor = MoreExecutors.newDirectExecutorService();

  @Inject private GetLogcatHandler getLogcatHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void getLogcat_delegatesToHelper() throws Exception {
    GetLogcatRequest request = GetLogcatRequest.newBuilder().setId("deviceId").build();
    GetLogcatResponse expectedResponse =
        GetLogcatResponse.newBuilder().setLogUrl("http://log_url").build();

    DeviceInfo deviceInfo = DeviceInfo.newBuilder().build();
    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setDeviceView(
                        DeviceView.newBuilder()
                            .setGroupedDevices(
                                GroupedDevices.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder().addDeviceInfo(deviceInfo)))))
            .build();

    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(labInfoResponse));
    when(devicePermissionChecker.hasPermission("user", deviceInfo))
        .thenReturn(immediateFuture(true));
    when(logcatActionHelper.getLogcat(request, SELF_UNIVERSE))
        .thenReturn(immediateFuture(expectedResponse));

    assertThat(getLogcatHandler.getLogcat(request, SELF_UNIVERSE, Optional.of("user")).get())
        .isEqualTo(expectedResponse);
    verify(logcatActionHelper).getLogcat(request, SELF_UNIVERSE);
  }

  @Test
  public void getLogcat_permissionDenied() throws Exception {
    GetLogcatRequest request = GetLogcatRequest.newBuilder().setId("deviceId").build();

    DeviceInfo deviceInfo = DeviceInfo.newBuilder().build();
    GetLabInfoResponse labInfoResponse =
        GetLabInfoResponse.newBuilder()
            .setLabQueryResult(
                LabQueryResult.newBuilder()
                    .setDeviceView(
                        DeviceView.newBuilder()
                            .setGroupedDevices(
                                GroupedDevices.newBuilder()
                                    .setDeviceList(
                                        DeviceList.newBuilder().addDeviceInfo(deviceInfo)))))
            .build();

    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(labInfoResponse));
    when(devicePermissionChecker.hasPermission("user", deviceInfo))
        .thenReturn(immediateFuture(false));

    GetLogcatResponse response =
        getLogcatHandler.getLogcat(request, SELF_UNIVERSE, Optional.of("user")).get();

    assertThat(response.getErrorType()).isEqualTo(IneligibilityReasonCode.PERMISSION_DENIED);
    assertThat(response.getErrorMessage()).contains("does not have permission");
  }

  @Test
  public void getLogcat_noUser() throws Exception {
    GetLogcatRequest request = GetLogcatRequest.newBuilder().setId("deviceId").build();

    GetLogcatResponse response =
        getLogcatHandler.getLogcat(request, SELF_UNIVERSE, Optional.empty()).get();

    assertThat(response.getErrorType()).isEqualTo(IneligibilityReasonCode.PERMISSION_DENIED);
    assertThat(response.getErrorMessage()).contains("User identity not found");
  }
}
