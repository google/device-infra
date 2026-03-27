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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceProxyType;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Checker for remote control eligibility.
 *
 * <p>Consolidates business rules for checking whether a device (physical or sub-device) is eligible
 * for remote control.
 */
@Singleton
public class RemoteControlEligibilityChecker {

  private static final int ACID_MIN_SDK_VERSION = 21;

  // Device Types
  private static final String TYPE_NEST_FCT_DEVICE = "NestFctDevice";
  private static final String TYPE_USB_DEVICE = "UsbDevice";
  private static final String TYPE_EMBEDDED_LINUX_DEVICE = "EmbeddedLinuxDevice";
  private static final String TYPE_ANDROID_REAL_DEVICE = "AndroidRealDevice";
  private static final String TYPE_ANDROID_LOCAL_EMULATOR = "AndroidLocalEmulator";
  private static final String TYPE_ANDROID_FASTBOOT_DEVICE = "AndroidFastbootDevice";
  private static final String TYPE_VIDEO_DEVICE = "VideoDevice";
  private static final String TYPE_TESTBED_DEVICE = "TestbedDevice";
  private static final String TYPE_ABNORMAL_TESTBED_DEVICE = "AbnormalTestbedDevice";
  private static final String TYPE_FAILED_DEVICE = "FailedDevice";

  // Drivers and Dimensions
  private static final String DRIVER_ACID_REMOTE_DRIVER = "AcidRemoteDriver";
  private static final String DIMENSION_COMMUNICATION_TYPE = "communication_type";
  private static final String DIMENSION_HOST_OS = "host_os";
  private static final String DIMENSION_SDK_VERSION = "sdk_version";
  private static final String DIMENSION_DEVICE_SUPPORTS_MORETO = "device_supports_moreto";

  /** Static mapping for device types to their default supported proxy types. */
  private static final ImmutableMultimap<String, DeviceProxyType> TYPE_TO_PROXIES =
      ImmutableMultimap.<String, DeviceProxyType>builder()
          .putAll(TYPE_NEST_FCT_DEVICE, DeviceProxyType.ADB_ONLY, DeviceProxyType.USB_IP)
          .put(TYPE_USB_DEVICE, DeviceProxyType.USB_IP)
          .put(TYPE_ANDROID_FASTBOOT_DEVICE, DeviceProxyType.USB_IP)
          .put(TYPE_EMBEDDED_LINUX_DEVICE, DeviceProxyType.SSH)
          .putAll(TYPE_ANDROID_REAL_DEVICE, DeviceProxyType.ADB_ONLY, DeviceProxyType.USB_IP)
          .putAll(
              TYPE_ANDROID_LOCAL_EMULATOR, DeviceProxyType.ADB_AND_VIDEO, DeviceProxyType.ADB_ONLY)
          .put(TYPE_VIDEO_DEVICE, DeviceProxyType.VIDEO)
          .build();

  private static final ImmutableList<DeviceProxyType> ADB_PROXIES =
      ImmutableList.of(DeviceProxyType.ADB_ONLY);

  public RemoteControlEligibilityResult checkEligibility(RemoteControlEligibilityContext context) {
    RemoteControlEligibilityResult.Builder result = RemoteControlEligibilityResult.builder();

    // 1. Check Device (or Parent) Status.
    if (context.deviceStatus() != DeviceStatus.IDLE) {
      return result
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.DEVICE_NOT_IDLE)
          .setReasonMessage("Not IDLE")
          .build();
    }

    // 2. Check AcidRemoteDriver.
    if (!context.drivers().contains(DRIVER_ACID_REMOTE_DRIVER)) {
      return result
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.ACID_NOT_SUPPORTED)
          .setReasonMessage("No AcidRemoteDriver")
          .build();
    }

    ImmutableSet<String> types = context.types();
    ImmutableMap<String, String> dimensions = context.dimensions();

    // 3. Multiple selection constraints.
    if (context.isMultipleSelection() && !types.contains(TYPE_ANDROID_REAL_DEVICE)) {
      return result
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED)
          .setReasonMessage("Not AndroidRealDevice")
          .build();
    }

    // 4. Host OS check.
    String hostOs = dimensions.get(DIMENSION_HOST_OS);
    if (!context.isMultipleSelection()
        && hostOs != null
        && Ascii.toLowerCase(hostOs).contains("mac os")) {
      return result
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.HOST_OS_NOT_SUPPORTED)
          .setReasonMessage("Mac OS not supported")
          .build();
    }

    // 5. Ineligible types check.
    if (!context.isMultipleSelection()
        && (types.contains(TYPE_ABNORMAL_TESTBED_DEVICE) || types.contains(TYPE_FAILED_DEVICE))) {
      return result
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED)
          .setReasonMessage("Device type not supported")
          .build();
    }
    // 6. Generic Acid support check.
    if (!hasEligibleAcidDimension(context)) {
      return result
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.ACID_NOT_SUPPORTED)
          .setReasonMessage("No eligible Acid dimension")
          .build();
    }

    // 7. Success - Calculate proxies.
    return result
        .setIsEligible(true)
        .setSupportedProxyTypes(calculateSupportedProxies(types, dimensions))
        .build();
  }

  private boolean hasEligibleAcidDimension(RemoteControlEligibilityContext context) {
    ImmutableSet<String> types = context.types();
    ImmutableMap<String, String> dimensions = context.dimensions();

    // For sub-device, check communication_type dimension or device_supports_moreto dimension.
    if (context.isSubDevice()) {
      return dimensions.containsKey(DIMENSION_COMMUNICATION_TYPE)
          || dimensions
              .getOrDefault(DIMENSION_DEVICE_SUPPORTS_MORETO, "false")
              .equalsIgnoreCase("true");
    }

    // For testbed device, check communication_type and sub-device communication_type dimension.
    if (types.contains(TYPE_TESTBED_DEVICE)) {
      return dimensions.containsKey(DIMENSION_COMMUNICATION_TYPE) || context.hasCommSubDevice();
    }

    return dimensions.containsKey(DIMENSION_COMMUNICATION_TYPE);
  }

  private ImmutableList<DeviceProxyType> calculateSupportedProxies(
      ImmutableSet<String> deviceTypes, Map<String, String> dimensions) {
    EnumSet<DeviceProxyType> proxies = EnumSet.noneOf(DeviceProxyType.class);

    for (String type : deviceTypes) {
      proxies.addAll(TYPE_TO_PROXIES.get(type));
    }

    boolean sdkSupported = isSdkVersionSupported(dimensions);
    if (deviceTypes.contains(TYPE_ANDROID_REAL_DEVICE) && sdkSupported) {
      proxies.add(DeviceProxyType.ADB_AND_VIDEO);
    }

    String commType =
        dimensions.getOrDefault(DIMENSION_COMMUNICATION_TYPE, "").toUpperCase(Locale.ROOT);
    if (commType.contains("ADB")) {
      proxies.addAll(ADB_PROXIES);
      if (sdkSupported) {
        proxies.add(DeviceProxyType.ADB_AND_VIDEO);
      }
    }
    if (commType.contains("USB")) {
      proxies.add(DeviceProxyType.USB_IP);
    }
    if (commType.contains("SSH")) {
      proxies.add(DeviceProxyType.SSH);
    }

    if (proxies.isEmpty() && deviceTypes.contains(TYPE_TESTBED_DEVICE)) {
      return ImmutableList.of(DeviceProxyType.DEVICE_PROXY_TYPE_UNSPECIFIED);
    }

    return ImmutableList.copyOf(proxies);
  }

  private boolean isSdkVersionSupported(Map<String, String> dimensions) {
    String sdkVersionStr = dimensions.get(DIMENSION_SDK_VERSION);
    if (sdkVersionStr == null) {
      return true;
    }
    try {
      return Integer.parseInt(sdkVersionStr) >= ACID_MIN_SDK_VERSION;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
