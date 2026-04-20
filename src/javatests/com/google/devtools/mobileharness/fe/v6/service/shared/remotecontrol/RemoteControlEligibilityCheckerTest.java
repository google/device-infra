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

package com.google.devtools.mobileharness.fe.v6.service.shared.remotecontrol;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceProxyType;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RemoteControlEligibilityCheckerTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private GroupMembershipProvider groupMembershipProvider;

  private final ListeningExecutorService executor = MoreExecutors.newDirectExecutorService();
  private RemoteControlEligibilityChecker checker;

  @Before
  public void setUp() {
    checker = new RemoteControlEligibilityChecker(groupMembershipProvider, executor);
  }

  @Test
  public void checkEligibility_deviceBusy_returnsIneligible() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .setUsername("user")
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.DEVICE_NOT_IDLE);
    assertThat(result.reasonMessage()).hasValue("Not IDLE");
  }

  @Test
  public void checkEligibility_noAcidDriver_returnsIneligible() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("OtherDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .setUsername("user")
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.ACID_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("No AcidRemoteDriver");
  }

  @Test
  public void checkEligibility_multiSelectNoAndroidReal_returnsIneligible() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(true)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("UsbDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "USB"))
            .setUsername("user")
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED);
  }

  @Test
  public void checkEligibility_macosHostSingleSelect_returnsIneligible() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(false)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("host_os", "Mac OS X", "communication_type", "ADB"))
            .setUsername("user")
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.HOST_OS_NOT_SUPPORTED);
  }

  @Test
  public void checkEligibility_validAndroidDevice_returnsEligibleWithProxies() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB", "sdk_version", "30"))
            .setUsername("user")
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes())
        .containsExactly(
            DeviceProxyType.ADB_AND_VIDEO, DeviceProxyType.ADB_ONLY, DeviceProxyType.USB_IP);
  }

  @Test
  public void checkEligibility_permissionDenied_returnsIneligible() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setUsername("user")
            .setOwnersAndExecutors(ImmutableList.of("owner", "executor"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    when(groupMembershipProvider.isMemberOfAny(anyString(), any()))
        .thenReturn(Futures.immediateFuture(false));

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.PERMISSION_DENIED);
    assertThat(result.runAsCandidates()).isEmpty();
  }

  @Test
  public void checkEligibility_authorizedByGroup_returnsEligible() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setUsername("user")
            .setOwnersAndExecutors(ImmutableList.of("group1"))
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    when(groupMembershipProvider.isMemberOfAny(eq("user"), eq(ImmutableList.of("group1"))))
        .thenReturn(Futures.immediateFuture(true));

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isTrue();
    assertThat(result.runAsCandidates()).containsExactly("group1");
  }

  @Test
  public void checkEligibility_technicalFailureButAuthorized_populatesCandidates()
      throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setUsername("user")
            .setOwnersAndExecutors(ImmutableList.of("user"))
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.DEVICE_NOT_IDLE);
    assertThat(result.runAsCandidates()).isEmpty();
  }

  @Test
  public void checkEligibility_noPermissionsDefined_returnsEligibleForUser() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setUsername("user")
            .setOwnersAndExecutors(ImmutableList.of())
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isTrue();
    assertThat(result.runAsCandidates()).containsExactly("user");
  }

  @Test
  public void checkEligibility_noUsername_returnsTechnicalEligible() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setUsername("")
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isTrue();
    assertThat(result.runAsCandidates()).isEmpty();
  }

  @Test
  public void checkTechnicalEligibility_validDevice_returnsEligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkTechnicalEligibility(context);

    assertThat(result.isEligible()).isTrue();
  }

  @Test
  public void checkTechnicalEligibility_noEligibleAcidDimension_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of())
            .build();

    RemoteControlEligibilityResult result = checker.checkTechnicalEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.ACID_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("No eligible Acid dimension");
  }

  @Test
  public void checkTechnicalEligibility_testbedDeviceWithCommSubDevice_returnsEligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("TestbedDevice"))
            .setDimensions(ImmutableMap.of())
            .setHasCommSubDevice(true)
            .build();

    RemoteControlEligibilityResult result = checker.checkTechnicalEligibility(context);

    assertThat(result.isEligible()).isTrue();
  }

  @Test
  public void checkEligibility_emptyOwnersAndExecutors_returnsEligibleWithUser() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .setUsername("user")
            .setOwnersAndExecutors(ImmutableList.of())
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isTrue();
    assertThat(result.runAsCandidates()).containsExactly("user");
  }

  @Test
  public void calculateSupportedProxies_nonAndroidDeviceWithAdbComm_addsAdbProxies()
      throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("UsbDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .setUsername("user")
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.supportedProxyTypes())
        .containsExactly(
            DeviceProxyType.USB_IP, DeviceProxyType.ADB_AND_VIDEO, DeviceProxyType.ADB_ONLY);
  }

  @Test
  public void checkEligibility_multiSelectTestbedAndAndroidReal_returnsIneligible()
      throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(true)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice", "TestbedDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .setUsername("user")
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED);
  }

  @Test
  @SuppressWarnings("DoNotMockAutoValue")
  public void checkEligibility_usernameBecomesEmpty_returnsEmptyCandidates() throws Exception {
    RemoteControlEligibilityContext mockContext = mock(RemoteControlEligibilityContext.class);
    when(mockContext.username()).thenReturn("user", "");
    when(mockContext.deviceStatus()).thenReturn(DeviceStatus.IDLE);
    when(mockContext.drivers()).thenReturn(ImmutableSet.of("AcidRemoteDriver"));
    when(mockContext.types()).thenReturn(ImmutableSet.of("AndroidRealDevice"));
    when(mockContext.dimensions()).thenReturn(ImmutableMap.of("communication_type", "ADB"));
    when(mockContext.ownersAndExecutors()).thenReturn(ImmutableList.of());
    when(mockContext.isMultipleSelection()).thenReturn(false);
    when(mockContext.isSubDevice()).thenReturn(false);
    when(mockContext.hasCommSubDevice()).thenReturn(false);

    RemoteControlEligibilityResult result = checker.checkEligibility(mockContext).get();

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.PERMISSION_DENIED);
    assertThat(result.runAsCandidates()).isEmpty();
  }

  @Test
  public void calculateSupportedProxies_androidDeviceMissingSdkVersion_doesNotAddAdbAndVideo()
      throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "USB"))
            .setUsername("user")
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.supportedProxyTypes())
        .containsExactly(DeviceProxyType.ADB_ONLY, DeviceProxyType.USB_IP);
  }

  @Test
  public void checkEligibility_nonEmptyOwners_userIsMember_addsOwnerAsCandidate() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .setUsername("user")
            .setOwnersAndExecutors(ImmutableList.of("owner1"))
            .build();

    when(groupMembershipProvider.isMemberOfAny("user", ImmutableList.of("owner1")))
        .thenReturn(Futures.immediateFuture(true));

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isTrue();
    assertThat(result.runAsCandidates()).containsExactly("owner1");
  }

  @Test
  public void checkEligibility_multiSelectAndroidRealOnly_returnsEligible() throws Exception {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(true)
            .setUsername("user")
            .setOwnersAndExecutors(ImmutableList.of())
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context).get();

    assertThat(result.isEligible()).isTrue();
  }
}
