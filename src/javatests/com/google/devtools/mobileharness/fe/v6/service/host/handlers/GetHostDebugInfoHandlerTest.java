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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.fe.v6.service.device.provider.DeviceOpsStubProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CommandResult;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDebugInfoRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.GetHostDebugInfoResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.DeviceOpsStub;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.util.Timestamps;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ;
import java.time.Instant;
import java.time.InstantSource;
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
public final class GetHostDebugInfoHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Bind @Mock private DeviceOpsStubProvider deviceOpsStubProvider;
  @Bind @Mock private InstantSource instantSource;

  @Mock private DeviceOpsStub deviceOpsStub;

  @Inject private GetHostDebugInfoHandler handler;

  private static final Instant NOW = Instant.parse("2026-06-02T10:00:00Z");

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

    when(instantSource.millis()).thenReturn(NOW.toEpochMilli());
    when(deviceOpsStubProvider.createStub(anyString(), any(UniverseScope.class)))
        .thenReturn(deviceOpsStub);
  }

  @Test
  public void getHostDebugInfo_selfUniverse_success() throws Exception {
    GetHostDebugInfoRequest request =
        GetHostDebugInfoRequest.newBuilder()
            .setHostName("test_host")
            .setUniverse("google_1p")
            .build();

    DeviceOpsServ.GetDeviceDebugInfoResponse labResponse =
        DeviceOpsServ.GetDeviceDebugInfoResponse.newBuilder()
            .addDeviceDebugInfo(
                DeviceOpsServ.CommandExecutionResult.newBuilder()
                    .setCommand("lsusb")
                    .setStdout("stdout_lsusb")
                    .setStderr("stderr_lsusb")
                    .build())
            .build();

    when(deviceOpsStub.getDeviceDebugInfoAsync(any(), eq(true)))
        .thenReturn(immediateFuture(labResponse));

    GetHostDebugInfoResponse response = handler.getHostDebugInfo(request, UniverseScope.SELF).get();

    GetHostDebugInfoResponse expectedResponse =
        GetHostDebugInfoResponse.newBuilder()
            .addResults(
                CommandResult.newBuilder()
                    .setCommand("lsusb")
                    .setStdout("stdout_lsusb")
                    .setStderr("stderr_lsusb")
                    .build())
            .setTimestamp(Timestamps.fromMillis(NOW.toEpochMilli()))
            .build();

    assertThat(response).isEqualTo(expectedResponse);

    verify(deviceOpsStubProvider).createStub("test_host", UniverseScope.SELF);
    verify(deviceOpsStub)
        .getDeviceDebugInfoAsync(
            DeviceOpsServ.GetDeviceDebugInfoRequest.newBuilder()
                .addCommand(DeviceOpsServ.GetDeviceDebugInfoRequest.GetDeviceDebugInfoCommand.ALL)
                .build(),
            /* useClientRpcAuthority= */ true);
  }

  @Test
  public void getHostDebugInfo_routedUniverse_throwsException() {
    GetHostDebugInfoRequest request =
        GetHostDebugInfoRequest.newBuilder().setHostName("test_host").setUniverse("other").build();

    UniverseScope universe = new UniverseScope.RoutedUniverse("other_controller");

    ExecutionException exception =
        assertThrows(
            ExecutionException.class, () -> handler.getHostDebugInfo(request, universe).get());
    assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
  }
}
