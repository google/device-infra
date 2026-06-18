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
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.GroupedDevices;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.DeviceView;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.TakeScreenshotResponse;
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
public final class TakeScreenshotHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private ScreenshotActionHelper screenshotActionHelper;
  @Bind @Mock private DevicePermissionChecker devicePermissionChecker;
  @Bind @Mock private LabInfoProvider labInfoProvider;
  @Bind private final ListeningExecutorService executor = MoreExecutors.newDirectExecutorService();

  @Inject private TakeScreenshotHandler takeScreenshotHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void takeScreenshot_success() throws Exception {
    TakeScreenshotRequest request = TakeScreenshotRequest.newBuilder().setId("device_id").build();
    TakeScreenshotResponse expectedResponse =
        TakeScreenshotResponse.newBuilder().setScreenshotUrl("http://gcs/path").build();

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
    when(screenshotActionHelper.takeScreenshot(request, UniverseScope.SELF))
        .thenReturn(immediateFuture(expectedResponse));

    TakeScreenshotResponse response =
        takeScreenshotHandler
            .takeScreenshot(request, UniverseScope.SELF, Optional.of("user"))
            .get();

    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  public void takeScreenshot_permissionDenied() throws Exception {
    TakeScreenshotRequest request = TakeScreenshotRequest.newBuilder().setId("device_id").build();

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

    TakeScreenshotResponse response =
        takeScreenshotHandler
            .takeScreenshot(request, UniverseScope.SELF, Optional.of("user"))
            .get();

    assertThat(response.getErrorType()).isEqualTo(IneligibilityReasonCode.PERMISSION_DENIED);
    assertThat(response.getErrorMessage()).contains("does not have permission");
  }

  @Test
  public void takeScreenshot_noUser() throws Exception {
    TakeScreenshotRequest request = TakeScreenshotRequest.newBuilder().setId("device_id").build();

    TakeScreenshotResponse response =
        takeScreenshotHandler.takeScreenshot(request, UniverseScope.SELF, Optional.empty()).get();

    assertThat(response.getErrorType()).isEqualTo(IneligibilityReasonCode.PERMISSION_DENIED);
    assertThat(response.getErrorMessage()).contains("User identity not found");
  }
}
