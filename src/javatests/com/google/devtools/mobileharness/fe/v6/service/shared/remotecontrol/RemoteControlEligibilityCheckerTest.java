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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceProxyType;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import com.google.inject.Guice;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RemoteControlEligibilityCheckerTest {

  @Inject private RemoteControlEligibilityChecker checker;

  @Before
  public void setUp() {
    Guice.createInjector().injectMembers(this);
  }

  @Test
  public void checkEligibility_deviceBusy_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.BUSY)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.DEVICE_NOT_IDLE);
    assertThat(result.reasonMessage()).hasValue("Not IDLE");
  }

  @Test
  public void checkEligibility_noAcidDriver_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("OtherDriver"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.ACID_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("No AcidRemoteDriver");
  }

  @Test
  public void checkEligibility_multiSelectNoAndroidReal_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(true)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("UsbDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "USB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("Not AndroidRealDevice");
  }

  @Test
  public void checkEligibility_macosHostSingleSelect_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(false)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("host_os", "Mac OS X", "communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.HOST_OS_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("Mac OS not supported");
  }

  @Test
  public void checkEligibility_noAcidDimension_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setDimensions(ImmutableMap.of("host_os", "Linux"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.ACID_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("No eligible Acid dimension");
  }

  @Test
  public void checkEligibility_abnormalTestbedSingleSelect_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(false)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AbnormalTestbedDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("Device type not supported");
  }

  @Test
  public void checkEligibility_failedDeviceSingleSelect_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(false)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("FailedDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("Device type not supported");
  }

  @Test
  public void checkEligibility_macosHostMultiSelect_returnsEligible() {
    // Mac OS check should be skipped in multi-selection mode.
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(true)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("host_os", "Mac OS X", "communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
  }

  @Test
  public void checkEligibility_failedDeviceMultiSelect_returnsEligible() {
    // Ineligible types check should be skipped in multi-selection mode if AndroidRealDevice is
    // present.
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsMultipleSelection(true)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice", "FailedDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
  }

  @Test
  public void checkEligibility_validAndroidDevice_returnsEligibleWithProxies() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB", "sdk_version", "30"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes())
        .containsExactly(
            DeviceProxyType.ADB_AND_VIDEO, DeviceProxyType.ADB_ONLY, DeviceProxyType.USB_IP);
  }

  @Test
  public void checkEligibility_validAndroidDeviceOldSdk_returnsEligibleWithLimitedProxies() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB", "sdk_version", "19"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes())
        .containsExactly(DeviceProxyType.ADB_ONLY, DeviceProxyType.USB_IP);
  }

  @Test
  public void checkEligibility_moretoSupportOnly_returnsEligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsSubDevice(true)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("UsbDevice"))
            .setDimensions(ImmutableMap.of("device_supports_moreto", "true"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).containsExactly(DeviceProxyType.USB_IP);
  }

  @Test
  public void checkEligibility_failedDevice_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("FailedDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("Device type not supported");
  }

  @Test
  public void checkEligibility_testbedWithCommCapableSubDevice_returnsEligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("TestbedDevice"))
            .setHasCommSubDevice(true)
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes())
        .containsExactly(DeviceProxyType.DEVICE_PROXY_TYPE_UNSPECIFIED);
  }

  @Test
  public void checkEligibility_testbedNoCommCapableSubDevice_returnsIneligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("TestbedDevice"))
            .setHasCommSubDevice(false)
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isFalse();
    assertThat(result.reasonCode()).hasValue(IneligibilityReasonCode.ACID_NOT_SUPPORTED);
    assertThat(result.reasonMessage()).hasValue("No eligible Acid dimension");
  }

  @Test
  public void checkEligibility_subDeviceWithMoreto_returnsEligible() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setIsSubDevice(true)
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("UsbDevice"))
            .setDimensions(ImmutableMap.of("device_supports_moreto", "true"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).containsExactly(DeviceProxyType.USB_IP);
  }

  @Test
  public void checkEligibility_commTypeUsb_returnsEligibleWithUsbIp() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("UsbDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "USB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).containsExactly(DeviceProxyType.USB_IP);
  }

  @Test
  public void checkEligibility_commTypeSsh_returnsEligibleWithSsh() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("EmbeddedLinuxDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "SSH"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).containsExactly(DeviceProxyType.SSH);
  }

  @Test
  public void checkEligibility_androidRealDeviceNonAdbCommType_addsAdbAndVideo() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "USB", "sdk_version", "30"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.ADB_AND_VIDEO);
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.USB_IP);
  }

  @Test
  public void checkEligibility_adbCommTypeOtherDevice_addsAdbProxies() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("EmbeddedLinuxDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB", "sdk_version", "30"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.ADB_ONLY);
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.ADB_AND_VIDEO);
  }

  @Test
  public void checkEligibility_usbCommTypeOtherDevice_addsUsbProxy() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("EmbeddedLinuxDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "USB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.USB_IP);
  }

  @Test
  public void checkEligibility_testbedWithCommType_returnsSpecificProxies() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("TestbedDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.ADB_ONLY);
    assertThat(result.supportedProxyTypes())
        .doesNotContain(DeviceProxyType.DEVICE_PROXY_TYPE_UNSPECIFIED);
  }

  @Test
  public void checkEligibility_sshCommTypeNonSshDevice_addsSshProxy() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "SSH"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.SSH);
  }

  @Test
  public void checkEligibility_androidDeviceMissingSdkVersion_addsAdbAndVideo() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.ADB_AND_VIDEO);
  }

  @Test
  public void checkEligibility_androidDeviceMalformedSdkVersion_returnsEligibleWithoutVideo() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB", "sdk_version", "invalid"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).doesNotContain(DeviceProxyType.ADB_AND_VIDEO);
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.ADB_ONLY);
  }

  @Test
  public void checkEligibility_androidDeviceMinSdkVersion_addsAdbAndVideo() {
    RemoteControlEligibilityContext context =
        RemoteControlEligibilityContext.builder()
            .setDeviceStatus(DeviceStatus.IDLE)
            .setDrivers(ImmutableSet.of("AcidRemoteDriver"))
            .setTypes(ImmutableSet.of("AndroidRealDevice"))
            .setDimensions(ImmutableMap.of("communication_type", "ADB", "sdk_version", "21"))
            .build();

    RemoteControlEligibilityResult result = checker.checkEligibility(context);

    assertThat(result.isEligible()).isTrue();
    assertThat(result.supportedProxyTypes()).contains(DeviceProxyType.ADB_AND_VIDEO);
  }
}
