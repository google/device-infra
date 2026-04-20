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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceCompositeDimension;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceFeature;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceLocator;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceList;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabData;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabQueryResult.LabView;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.DeviceDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceType;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.SubDeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CheckRemoteControlEligibilityRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.CheckRemoteControlEligibilityResponse;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceProxyType;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.EligibilityStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import com.google.devtools.mobileharness.fe.v6.service.shared.SubDeviceInfoListFactory;
import com.google.devtools.mobileharness.fe.v6.service.shared.providers.LabInfoProvider;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityChecker;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityContext;
import com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol.RemoteControlEligibilityResult;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.labinfo.proto.LabInfoServiceProto.GetLabInfoResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class CheckRemoteControlEligibilityHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  private CheckRemoteControlEligibilityHandler handler;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();
  private static final String DEVICE_ID_1 = "device_id_1";
  private static final String DEVICE_ID_2 = "device_id_2";
  private static final DeviceInfo DEVICE_INFO_1 =
      DeviceInfo.newBuilder()
          .setDeviceLocator(DeviceLocator.newBuilder().setId(DEVICE_ID_1))
          .setDeviceStatus(DeviceStatus.IDLE)
          .build();
  private static final DeviceInfo DEVICE_INFO_2 =
      DeviceInfo.newBuilder()
          .setDeviceLocator(DeviceLocator.newBuilder().setId(DEVICE_ID_2))
          .setDeviceStatus(DeviceStatus.IDLE)
          .build();

  @Mock private LabInfoProvider labInfoProvider;
  @Mock private SubDeviceInfoListFactory subDeviceInfoListFactory;
  @Mock private RemoteControlEligibilityChecker remoteControlEligibilityChecker;
  private final ListeningExecutorService executor = MoreExecutors.newDirectExecutorService();

  @Before
  public void setUp() {
    handler =
        new CheckRemoteControlEligibilityHandler(
            labInfoProvider, remoteControlEligibilityChecker, subDeviceInfoListFactory, executor);
    when(subDeviceInfoListFactory.create(any())).thenReturn(ImmutableList.of());
  }

  @Test
  public void checkRemoteControlEligibility_noTargets_returnsDefaultInstance() throws Exception {
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder().setHostName("test_host").build();

    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(Futures.immediateFuture(GetLabInfoResponse.getDefaultInstance()));

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response).isEqualToDefaultInstance();
    verify(labInfoProvider, never()).getLabInfoAsync(any(), any(UniverseScope.class));
  }

  @Test
  public void checkRemoteControlEligibility_noUsername_returnsPermissionDenied() throws Exception {
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.empty(), UNIVERSE).get();

    assertThat(response)
        .isEqualTo(
            CheckRemoteControlEligibilityResponse.newBuilder()
                .setStatus(EligibilityStatus.BLOCK_ALL_PERMISSION_DENIED)
                .build());
  }

  @Test
  public void checkRemoteControlEligibility_deviceNotFound() throws Exception {
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of());
    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getResultsList()).hasSize(1);
    assertThat(response.getResults(0).getIsEligible()).isFalse();
    assertThat(response.getResults(0).getIneligibilityReason().getCode())
        .isEqualTo(IneligibilityReasonCode.DEVICE_NOT_FOUND);
    assertThat(response.getStatus()).isEqualTo(EligibilityStatus.BLOCK_DEVICES_INELIGIBLE);
  }

  @Test
  public void checkRemoteControlEligibility_multipleDevicesEligible_isMultipleTrueInContext()
      throws Exception {
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_2))
            .build();
    setUpLabInfoResponse(ImmutableList.of(DEVICE_INFO_1, DEVICE_INFO_2));
    setUpEligibilityChecker(
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user"))
            .setSupportedProxyTypes(ImmutableList.of(DeviceProxyType.ADB_ONLY))
            .build());

    handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    ArgumentCaptor<RemoteControlEligibilityContext> captor =
        ArgumentCaptor.forClass(RemoteControlEligibilityContext.class);
    verify(remoteControlEligibilityChecker, atLeastOnce()).checkEligibility(captor.capture());
    assertThat(captor.getValue().isMultipleSelection()).isTrue();
  }

  @Test
  public void checkRemoteControlEligibility_subDeviceEligible() throws Exception {
    String subDeviceId = "sub_device_id";
    when(subDeviceInfoListFactory.create(any()))
        .thenReturn(ImmutableList.of(SubDeviceInfo.newBuilder().setId(subDeviceId).build()));
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1)
                    .setSubDeviceId(subDeviceId))
            .build();
    setUpLabInfoResponse(ImmutableList.of(DEVICE_INFO_1));
    setUpEligibilityChecker(
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user"))
            .setSupportedProxyTypes(ImmutableList.of(DeviceProxyType.ADB_ONLY))
            .build());

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getResultsList()).hasSize(1);
    assertThat(response.getResults(0).getIsEligible()).isTrue();
    assertThat(response.getResults(0).getDeviceId()).isEqualTo(subDeviceId);
    ArgumentCaptor<RemoteControlEligibilityContext> captor =
        ArgumentCaptor.forClass(RemoteControlEligibilityContext.class);
    verify(remoteControlEligibilityChecker, atLeastOnce()).checkEligibility(captor.capture());
    assertThat(captor.getValue().isSubDevice()).isTrue();
  }

  @Test
  public void checkRemoteControlEligibility_subDeviceNotFound() throws Exception {
    String subDeviceId = "sub_device_id";
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1)
                    .setSubDeviceId(subDeviceId))
            .build();
    setUpLabInfoResponse(ImmutableList.of(DEVICE_INFO_1));

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getResultsList()).hasSize(1);
    assertThat(response.getResults(0).getIsEligible()).isFalse();
    assertThat(response.getResults(0).getIneligibilityReason().getCode())
        .isEqualTo(IneligibilityReasonCode.DEVICE_NOT_FOUND);
    assertThat(response.getResults(0).getIneligibilityReason().getMessage())
        .contains("SubDevice sub_device_id of device device_id_1 not found");
  }

  @Test
  public void
      checkRemoteControlEligibility_testbedDeviceWithCommSubDevice_setsHasCommSubDeviceTrue()
          throws Exception {
    String subDeviceId = "sub_device_id";
    SubDeviceInfo subDeviceInfo =
        SubDeviceInfo.newBuilder()
            .setId(subDeviceId)
            .addDimensions(
                DeviceDimension.newBuilder().setName("communication_type").setValue("ADB"))
            .build();
    when(subDeviceInfoListFactory.create(any())).thenReturn(ImmutableList.of(subDeviceInfo));

    DeviceInfo testbedDevice =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("TestbedDevice"))
            .build();

    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of(testbedDevice));
    setUpEligibilityChecker(
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user"))
            .build());

    handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    ArgumentCaptor<RemoteControlEligibilityContext> captor =
        ArgumentCaptor.forClass(RemoteControlEligibilityContext.class);
    verify(remoteControlEligibilityChecker, atLeastOnce()).checkEligibility(captor.capture());

    List<RemoteControlEligibilityContext> contexts = captor.getAllValues();
    Optional<RemoteControlEligibilityContext> testbedContext =
        contexts.stream().filter(c -> !c.isSubDevice()).findFirst();

    assertThat(testbedContext.isPresent()).isTrue();
    assertThat(testbedContext.get().hasCommSubDevice()).isTrue();
  }

  @Test
  public void
      checkRemoteControlEligibility_testbedDeviceWithoutCommSubDevice_setsHasCommSubDeviceFalse()
          throws Exception {
    String subDeviceId = "sub_device_id";
    SubDeviceInfo subDeviceInfo = SubDeviceInfo.newBuilder().setId(subDeviceId).build();
    when(subDeviceInfoListFactory.create(any())).thenReturn(ImmutableList.of(subDeviceInfo));

    DeviceInfo testbedDevice =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("TestbedDevice"))
            .build();

    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of(testbedDevice));
    setUpEligibilityChecker(
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user"))
            .build());

    handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    ArgumentCaptor<RemoteControlEligibilityContext> captor =
        ArgumentCaptor.forClass(RemoteControlEligibilityContext.class);
    verify(remoteControlEligibilityChecker, atLeastOnce()).checkEligibility(captor.capture());

    List<RemoteControlEligibilityContext> contexts = captor.getAllValues();
    Optional<RemoteControlEligibilityContext> testbedContext =
        contexts.stream().filter(c -> !c.isSubDevice()).findFirst();

    assertThat(testbedContext.isPresent()).isTrue();
    assertThat(testbedContext.get().hasCommSubDevice()).isFalse();
  }

  @Test
  public void checkRemoteControlEligibility_nonTestbedDevice_setsHasCommSubDeviceFalse()
      throws Exception {
    DeviceInfo androidDevice =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("AndroidRealDevice"))
            .build();

    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of(androidDevice));
    setUpEligibilityChecker(
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user"))
            .build());

    handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    ArgumentCaptor<RemoteControlEligibilityContext> captor =
        ArgumentCaptor.forClass(RemoteControlEligibilityContext.class);
    verify(remoteControlEligibilityChecker, atLeastOnce()).checkEligibility(captor.capture());

    List<RemoteControlEligibilityContext> contexts = captor.getAllValues();
    Optional<RemoteControlEligibilityContext> deviceContext =
        contexts.stream().filter(c -> c.types().contains("AndroidRealDevice")).findFirst();

    assertThat(deviceContext.isPresent()).isTrue();
    assertThat(deviceContext.get().hasCommSubDevice()).isFalse();
  }

  @Test
  public void checkRemoteControlEligibility_testbedDeviceEligible_returnsSubDeviceResults()
      throws Exception {
    when(subDeviceInfoListFactory.create(any()))
        .thenReturn(ImmutableList.of(SubDeviceInfo.newBuilder().setId("sub1").build()));
    DeviceInfo testbedDevice =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("TestbedDevice"))
            .build();
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of(testbedDevice));
    RemoteControlEligibilityResult eligibleResult =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user"))
            .build();

    RemoteControlEligibilityResult ineligibleResult =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(false)
            .setReasonCode(IneligibilityReasonCode.ACID_NOT_SUPPORTED)
            .build();

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && !c.isMultipleSelection())))
        .thenReturn(Futures.immediateFuture(eligibleResult));

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && c.isMultipleSelection())))
        .thenReturn(Futures.immediateFuture(ineligibleResult));

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getResultsList()).hasSize(1);
    assertThat(response.getResults(0).getIsEligible()).isTrue();
    assertThat(response.getResults(0).getSubDeviceResultsList()).hasSize(1);
    assertThat(response.getResults(0).getSubDeviceResults(0).getDeviceId()).isEqualTo("sub1");
  }

  @Test
  public void checkRemoteControlEligibility_testbedDeviceIneligible_doesNotReturnSubDeviceResults()
      throws Exception {
    when(subDeviceInfoListFactory.create(any()))
        .thenReturn(ImmutableList.of(SubDeviceInfo.newBuilder().setId("sub1").build()));
    DeviceInfo testbedDevice =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("TestbedDevice"))
            .build();
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of(testbedDevice));
    setUpEligibilityChecker(
        RemoteControlEligibilityResult.builder()
            .setIsEligible(false)
            .setReasonCode(IneligibilityReasonCode.ACID_NOT_SUPPORTED)
            .build());

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getResultsList()).hasSize(1);
    assertThat(response.getResults(0).getIsEligible()).isFalse();
    assertThat(response.getResults(0).getSubDeviceResultsList()).isEmpty();
  }

  @Test
  public void checkRemoteControlEligibility_testbedDeviceWithNonAndroidSubDevice_subDeviceEligible()
      throws Exception {
    SubDeviceInfo subDeviceInfo =
        SubDeviceInfo.newBuilder()
            .setId("sub1")
            .addTypes(DeviceType.newBuilder().setType("UsbDevice"))
            .build();
    when(subDeviceInfoListFactory.create(any())).thenReturn(ImmutableList.of(subDeviceInfo));
    DeviceInfo testbedDevice =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("TestbedDevice"))
            .build();
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of(testbedDevice));

    RemoteControlEligibilityResult eligibleResult =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user"))
            .build();

    RemoteControlEligibilityResult ineligibleResult =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(false)
            .setReasonCode(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED)
            .build();

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && !c.isSubDevice())))
        .thenReturn(Futures.immediateFuture(eligibleResult));

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && c.isSubDevice() && !c.isMultipleSelection())))
        .thenReturn(Futures.immediateFuture(eligibleResult));

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && c.isSubDevice() && c.isMultipleSelection())))
        .thenReturn(Futures.immediateFuture(ineligibleResult));

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getResultsList()).hasSize(1);
    assertThat(response.getResults(0).getSubDeviceResultsList()).hasSize(1);
    assertThat(response.getResults(0).getSubDeviceResults(0).getIsEligible()).isTrue();
  }

  @Test
  public void checkRemoteControlEligibility_ineligibleDevice_returnsIneligibilityReason()
      throws Exception {
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of(DEVICE_INFO_1));
    setUpEligibilityChecker(
        RemoteControlEligibilityResult.builder()
            .setIsEligible(false)
            .setReasonCode(IneligibilityReasonCode.ACID_NOT_SUPPORTED)
            .setReasonMessage("No AcidRemoteDriver")
            .build());

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getResultsList()).hasSize(1);
    assertThat(response.getResults(0).getIsEligible()).isFalse();
    assertThat(response.getResults(0).getIneligibilityReason().getCode())
        .isEqualTo(IneligibilityReasonCode.ACID_NOT_SUPPORTED);
    assertThat(response.getResults(0).getIneligibilityReason().getMessage())
        .isEqualTo("No AcidRemoteDriver");
  }

  @Test
  public void checkRemoteControlEligibility_eligibleDevice_doesNotReturnIneligibilityReason()
      throws Exception {
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of(DEVICE_INFO_1));
    setUpEligibilityChecker(
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user"))
            .build());

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getResultsList()).hasSize(1);
    assertThat(response.getResults(0).getIsEligible()).isTrue();
    assertThat(response.getResults(0).hasIneligibilityReason()).isFalse();
  }

  @Test
  public void
      checkRemoteControlEligibility_testbedDeviceWithIneligibleSubDevice_returnsSubDeviceIneligibilityReason()
          throws Exception {
    SubDeviceInfo subDeviceInfo = SubDeviceInfo.newBuilder().setId("sub1").build();
    when(subDeviceInfoListFactory.create(any())).thenReturn(ImmutableList.of(subDeviceInfo));
    DeviceInfo testbedDevice =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(DeviceFeature.newBuilder().addType("TestbedDevice"))
            .build();
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .build();
    setUpLabInfoResponse(ImmutableList.of(testbedDevice));

    RemoteControlEligibilityResult eligibleResult =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user"))
            .build();

    RemoteControlEligibilityResult ineligibleResult =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(false)
            .setReasonCode(IneligibilityReasonCode.ACID_NOT_SUPPORTED)
            .setReasonMessage("No AcidRemoteDriver")
            .build();

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && !c.isSubDevice())))
        .thenReturn(Futures.immediateFuture(eligibleResult));

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && c.isSubDevice())))
        .thenReturn(Futures.immediateFuture(ineligibleResult));

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getResultsList()).hasSize(1);
    assertThat(response.getResults(0).getSubDeviceResultsList()).hasSize(1);
    assertThat(response.getResults(0).getSubDeviceResults(0).getIsEligible()).isFalse();
    assertThat(response.getResults(0).getSubDeviceResults(0).getIneligibilityReason().getCode())
        .isEqualTo(IneligibilityReasonCode.ACID_NOT_SUPPORTED);
    assertThat(response.getResults(0).getSubDeviceResults(0).getIneligibilityReason().getMessage())
        .isEqualTo("No AcidRemoteDriver");
  }

  @Test
  public void
      checkRemoteControlEligibility_allDevicesPermissionDenied_returnsBlockAllPermissionDenied()
          throws Exception {
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_2))
            .build();

    com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension idDim1 =
        com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension.newBuilder()
            .setName("id")
            .setValue(DEVICE_ID_1)
            .build();
    DeviceInfo deviceInfo1 =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder().addSupportedDimension(idDim1)))
            .build();

    com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension idDim2 =
        com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension.newBuilder()
            .setName("id")
            .setValue(DEVICE_ID_2)
            .build();
    DeviceInfo deviceInfo2 =
        DEVICE_INFO_2.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder().addSupportedDimension(idDim2)))
            .build();

    setUpLabInfoResponse(ImmutableList.of(deviceInfo1, deviceInfo2));

    RemoteControlEligibilityResult permissionDeniedResult =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(false)
            .setReasonCode(IneligibilityReasonCode.PERMISSION_DENIED)
            .build();

    setUpEligibilityChecker(permissionDeniedResult);

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getStatus()).isEqualTo(EligibilityStatus.BLOCK_ALL_PERMISSION_DENIED);
  }

  @Test
  public void checkRemoteControlEligibility_noCommonProxies_returnsBlockNoCommonProxy()
      throws Exception {
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_2))
            .build();

    com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension idDim1 =
        com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension.newBuilder()
            .setName("id")
            .setValue(DEVICE_ID_1)
            .build();
    DeviceInfo deviceInfo1 =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder().addSupportedDimension(idDim1)))
            .build();

    com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension idDim2 =
        com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension.newBuilder()
            .setName("id")
            .setValue(DEVICE_ID_2)
            .build();
    DeviceInfo deviceInfo2 =
        DEVICE_INFO_2.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder().addSupportedDimension(idDim2)))
            .build();

    setUpLabInfoResponse(ImmutableList.of(deviceInfo1, deviceInfo2));

    RemoteControlEligibilityResult result1 =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setSupportedProxyTypes(ImmutableList.of(DeviceProxyType.ADB_ONLY))
            .build();

    RemoteControlEligibilityResult result2 =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setSupportedProxyTypes(ImmutableList.of(DeviceProxyType.VIDEO))
            .build();

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && Objects.equals(c.dimensions().get("id"), DEVICE_ID_1))))
        .thenReturn(Futures.immediateFuture(result1));

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && Objects.equals(c.dimensions().get("id"), DEVICE_ID_2))))
        .thenReturn(Futures.immediateFuture(result2));

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getStatus()).isEqualTo(EligibilityStatus.BLOCK_NO_COMMON_PROXY);
  }

  @Test
  public void
      checkRemoteControlEligibility_mixOfEligibleAndPermissionDenied_returnsReadyWithCommonCandidates()
          throws Exception {
    CheckRemoteControlEligibilityRequest request =
        CheckRemoteControlEligibilityRequest.newBuilder()
            .setHostName("test_host")
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_1))
            .addTargets(
                CheckRemoteControlEligibilityRequest.DeviceTarget.newBuilder()
                    .setDeviceId(DEVICE_ID_2))
            .build();

    com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension idDim1 =
        com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension.newBuilder()
            .setName("id")
            .setValue(DEVICE_ID_1)
            .build();
    DeviceInfo deviceInfo1 =
        DEVICE_INFO_1.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder().addSupportedDimension(idDim1)))
            .build();

    com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension idDim2 =
        com.google.devtools.mobileharness.api.model.proto.Device.DeviceDimension.newBuilder()
            .setName("id")
            .setValue(DEVICE_ID_2)
            .build();
    DeviceInfo deviceInfo2 =
        DEVICE_INFO_2.toBuilder()
            .setDeviceFeature(
                DeviceFeature.newBuilder()
                    .setCompositeDimension(
                        DeviceCompositeDimension.newBuilder().addSupportedDimension(idDim2)))
            .build();

    setUpLabInfoResponse(ImmutableList.of(deviceInfo1, deviceInfo2));

    RemoteControlEligibilityResult result1 =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(true)
            .setRunAsCandidates(ImmutableList.of("user1", "user2"))
            .setSupportedProxyTypes(ImmutableList.of(DeviceProxyType.ADB_ONLY))
            .build();

    RemoteControlEligibilityResult result2 =
        RemoteControlEligibilityResult.builder()
            .setIsEligible(false)
            .setReasonCode(IneligibilityReasonCode.PERMISSION_DENIED)
            .setRunAsCandidates(ImmutableList.of("user3"))
            .setSupportedProxyTypes(ImmutableList.of(DeviceProxyType.ADB_ONLY))
            .build();

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && Objects.equals(c.dimensions().get("id"), DEVICE_ID_1))))
        .thenReturn(Futures.immediateFuture(result1));

    when(remoteControlEligibilityChecker.checkEligibility(
            argThat(c -> c != null && Objects.equals(c.dimensions().get("id"), DEVICE_ID_2))))
        .thenReturn(Futures.immediateFuture(result2));

    CheckRemoteControlEligibilityResponse response =
        handler.checkRemoteControlEligibility(request, Optional.of("user"), UNIVERSE).get();

    assertThat(response.getStatus()).isEqualTo(EligibilityStatus.READY);
    assertThat(response.getSessionOptions().getCommonRunAsCandidatesList())
        .containsExactly("user1", "user2");
  }

  private void setUpLabInfoResponse(ImmutableList<DeviceInfo> deviceInfos) {
    when(labInfoProvider.getLabInfoAsync(any(), any(UniverseScope.class)))
        .thenReturn(
            Futures.immediateFuture(
                GetLabInfoResponse.newBuilder()
                    .setLabQueryResult(
                        LabQueryProto.LabQueryResult.newBuilder()
                            .setLabView(
                                LabView.newBuilder()
                                    .addLabData(
                                        LabData.newBuilder()
                                            .setDeviceList(
                                                DeviceList.newBuilder()
                                                    .addAllDeviceInfo(deviceInfos)))))
                    .build()));
  }

  private void setUpEligibilityChecker(RemoteControlEligibilityResult result) {
    when(remoteControlEligibilityChecker.checkEligibility(any()))
        .thenReturn(Futures.immediateFuture(result));
  }
}
