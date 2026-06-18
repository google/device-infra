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
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto;
import com.google.devtools.mobileharness.fe.v6.service.device.provider.DeviceOpsStubProvider;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.ListTroubleshootScriptsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.ListTroubleshootScriptsResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RunTroubleshootScriptRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RunTroubleshootScriptRequest.TroubleshootScript;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.RunTroubleshootScriptResponse;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.infra.lab.rpc.stub.DeviceOpsStub;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import com.google.wireless.qa.mobileharness.lab.proto.DeviceOpsServ;
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
public final class TroubleshootScriptHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private LabInfoProvider labInfoProvider;
  @Mock private DeviceOpsStubProvider deviceOpsStubProvider;
  @Mock private DeviceOpsStub deviceOpsStub;

  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  private TroubleshootScriptHandler handler;

  @Before
  public void setUp() {
    handler = new TroubleshootScriptHandler(labInfoProvider, deviceOpsStubProvider);
    // Default: no devices (empty response)
    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(GetLabInfoResponse.getDefaultInstance()));
  }

  @Test
  public void runTroubleshootScript_noDeviceBusy_success() throws Exception {
    RunTroubleshootScriptRequest request =
        RunTroubleshootScriptRequest.newBuilder()
            .setHostName("host")
            .setScript(TroubleshootScript.RESET_USB_HUB)
            .build();

    when(deviceOpsStubProvider.createStub(anyString(), any())).thenReturn(deviceOpsStub);
    when(deviceOpsStub.runTroubleshootScriptAsync(any()))
        .thenReturn(
            immediateFuture(
                DeviceOpsServ.RunTroubleshootScriptResponse.newBuilder()
                    .setExitCode(0)
                    .setStdout("stdout")
                    .setStderr("stderr")
                    .build()));

    RunTroubleshootScriptResponse response = handler.runTroubleshootScript(request, UNIVERSE).get();

    assertThat(response.getExitCode()).isEqualTo(0);
    assertThat(response.getStdout()).isEqualTo("stdout");
    assertThat(response.getStderr()).isEqualTo("stderr");
  }

  @Test
  public void runTroubleshootScript_unsupportedScript_fails() throws Exception {
    RunTroubleshootScriptRequest request =
        RunTroubleshootScriptRequest.newBuilder()
            .setHostName("host")
            .setScript(TroubleshootScript.UNKNOWN)
            .build();

    ExecutionException e =
        assertThrows(
            ExecutionException.class, () -> handler.runTroubleshootScript(request, UNIVERSE).get());
    assertThat(e).hasCauseThat().isInstanceOf(MobileHarnessException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("Unsupported troubleshoot script");
  }

  @Test
  public void listTroubleshootScripts_noDeviceBusy_allEnabled() throws Exception {
    ListTroubleshootScriptsRequest request =
        ListTroubleshootScriptsRequest.newBuilder().setHostName("host").build();

    ListTroubleshootScriptsResponse response =
        handler.listTroubleshootScripts(request, UNIVERSE).get();

    assertThat(response.getActionsCount()).isGreaterThan(0);
    assertThat(response.getActions(0).getEnabled()).isTrue();
  }

  @Test
  public void listTroubleshootScripts_deviceBusy_allDisabled() throws Exception {
    ListTroubleshootScriptsRequest request =
        ListTroubleshootScriptsRequest.newBuilder().setHostName("host").build();

    when(labInfoProvider.getLabInfoAsync(any(), any()))
        .thenReturn(immediateFuture(buildLabInfoWithBusyDevice()));

    ListTroubleshootScriptsResponse response =
        handler.listTroubleshootScripts(request, UNIVERSE).get();

    assertThat(response.getActionsCount()).isGreaterThan(0);
    assertThat(response.getActions(0).getEnabled()).isFalse();
    assertThat(response.getActions(0).getConstraintTooltip()).contains("BUSY");
  }

  @Test
  public void runTroubleshootScript_routedUniverse_fails() throws Exception {
    RunTroubleshootScriptRequest request =
        RunTroubleshootScriptRequest.newBuilder()
            .setHostName("host")
            .setScript(TroubleshootScript.RESET_USB_HUB)
            .build();

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () ->
                handler
                    .runTroubleshootScript(request, new UniverseScope.RoutedUniverse("partner"))
                    .get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("self universe");
  }

  @Test
  public void listTroubleshootScripts_routedUniverse_fails() throws Exception {
    ListTroubleshootScriptsRequest request =
        ListTroubleshootScriptsRequest.newBuilder().setHostName("host").build();

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () ->
                handler
                    .listTroubleshootScripts(request, new UniverseScope.RoutedUniverse("partner"))
                    .get());
    assertThat(e).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasCauseThat().hasMessageThat().contains("self universe");
  }

  private static GetLabInfoResponse buildLabInfoWithBusyDevice() {
    LabQueryProto.DeviceInfo busyDevice =
        LabQueryProto.DeviceInfo.newBuilder().setDeviceStatus(DeviceStatus.BUSY).build();
    LabQueryProto.LabData labData =
        LabQueryProto.LabData.newBuilder()
            .setDeviceList(LabQueryProto.DeviceList.newBuilder().addDeviceInfo(busyDevice))
            .build();
    LabQueryProto.LabQueryResult queryResult =
        LabQueryProto.LabQueryResult.newBuilder()
            .setLabView(LabQueryProto.LabQueryResult.LabView.newBuilder().addLabData(labData))
            .build();
    return GetLabInfoResponse.newBuilder().setLabQueryResult(queryResult).build();
  }
}
