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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.mobileharness.api.model.proto.Device.DeviceStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.DeviceProxyType;
import com.google.devtools.mobileharness.fe.v6.service.proto.host.IneligibilityReasonCode;
import com.google.devtools.mobileharness.fe.v6.service.shared.auth.GroupMembershipProvider;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Checker for remote control eligibility.
 *
 * <p>Consolidates business rules for checking whether a device (physical or sub-device) is eligible
 * for remote control.
 */
@Singleton
public class RemoteControlEligibilityChecker {

  private final GroupMembershipProvider groupMembershipProvider;
  private final ListeningExecutorService executor;

  @Inject
  RemoteControlEligibilityChecker(
      GroupMembershipProvider groupMembershipProvider, ListeningExecutorService executor) {
    this.groupMembershipProvider = groupMembershipProvider;
    this.executor = executor;
  }

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

  public ListenableFuture<RemoteControlEligibilityResult> checkEligibility(
      RemoteControlEligibilityContext context) {
    RemoteControlEligibilityResult technicalResult = checkTechnicalEligibility(context);

    // 1. If the username is empty, we return the technical result directly. This is used for cases
    // like GetDeviceHeaderInfo and GetHostDeviceSummaries.
    // 2. If the technical result is ineligible, return it directly.
    if (context.username().isEmpty() || !technicalResult.isEligible()) {
      return immediateFuture(technicalResult);
    }

    // Calculate supported proxy types.
    RemoteControlEligibilityResult resultWithProxies =
        technicalResult.toBuilder()
            .setSupportedProxyTypes(
                calculateSupportedProxies(context.types(), context.dimensions()))
            .build();

    // Permission and Candidate Calculation (Async).
    // We calculate this even if technical check failed to ensure PERMISSION_DENIED
    // takes precedence or candidates are populated for debugging.
    return Futures.transform(
        calculateRunAsCandidates(context),
        candidates -> {
          RemoteControlEligibilityResult.Builder resultBuilder =
              resultWithProxies.toBuilder().setRunAsCandidates(candidates);

          // If no candidates are found, return ineligible with PERMISSION_DENIED.
          // This takes precedence over technical ineligibility.
          if (candidates.isEmpty()) {
            return resultBuilder
                .setIsEligible(false)
                .setReasonCode(IneligibilityReasonCode.PERMISSION_DENIED)
                .setReasonMessage("Permission denied")
                .build();
          }

          return resultBuilder.build();
        },
        executor);
  }

  public RemoteControlEligibilityResult checkTechnicalEligibility(
      RemoteControlEligibilityContext context) {
    RemoteControlEligibilityResult.Builder resultBuilder =
        RemoteControlEligibilityResult.builder().setIsEligible(true);

    // 1. Check Device (or Parent) Status.
    if (context.deviceStatus() != DeviceStatus.IDLE) {
      return resultBuilder
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.DEVICE_NOT_IDLE)
          .setReasonMessage("Not IDLE")
          .build();
    }

    // 2. Check AcidRemoteDriver.
    if (!context.drivers().contains(DRIVER_ACID_REMOTE_DRIVER)) {
      return resultBuilder
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.ACID_NOT_SUPPORTED)
          .setReasonMessage("No AcidRemoteDriver")
          .build();
    }

    ImmutableSet<String> types = context.types();
    ImmutableMap<String, String> dimensions = context.dimensions();

    // 3. Multiple selection constraints.
    if (context.isMultipleSelection()
        && (!types.contains(TYPE_ANDROID_REAL_DEVICE) || types.contains(TYPE_TESTBED_DEVICE))) {
      return resultBuilder
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
      return resultBuilder
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.HOST_OS_NOT_SUPPORTED)
          .setReasonMessage("Mac OS not supported")
          .build();
    }

    // 5. Ineligible types check.
    if (!context.isMultipleSelection()
        && (types.contains(TYPE_ABNORMAL_TESTBED_DEVICE) || types.contains(TYPE_FAILED_DEVICE))) {
      return resultBuilder
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.DEVICE_TYPE_NOT_SUPPORTED)
          .setReasonMessage("Device type not supported")
          .build();
    }

    // 6. Generic Acid support check.
    if (!hasEligibleAcidDimension(context)) {
      return resultBuilder
          .setIsEligible(false)
          .setReasonCode(IneligibilityReasonCode.ACID_NOT_SUPPORTED)
          .setReasonMessage("No eligible Acid dimension")
          .build();
    }

    return resultBuilder.build();
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

    // 1. Process Device Types using the lookup table (Functional style).
    deviceTypes.stream()
        .map(TYPE_TO_PROXIES::get)
        .filter(Objects::nonNull)
        .forEach(proxies::addAll);

    // 2. sdk_version dimension check.
    boolean sdkSupported = isSdkVersionSupported(dimensions);
    if (sdkSupported && deviceTypes.contains(TYPE_ANDROID_REAL_DEVICE)) {
      proxies.add(DeviceProxyType.ADB_AND_VIDEO);
    }

    String commType =
        dimensions.getOrDefault(DIMENSION_COMMUNICATION_TYPE, "").toUpperCase(Locale.ROOT);

    // 3. Communication type dimension check.
    if (commType.contains("ADB")) {
      proxies.addAll(ImmutableList.of(DeviceProxyType.ADB_AND_VIDEO, DeviceProxyType.ADB_ONLY));
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
      return false;
    }
    try {
      return Integer.parseInt(sdkVersionStr) >= ACID_MIN_SDK_VERSION;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private ListenableFuture<ImmutableList<String>> calculateRunAsCandidates(
      RemoteControlEligibilityContext context) {
    String username = context.username();
    ImmutableList<String> ownersAndExecutors = context.ownersAndExecutors();

    if (username.isEmpty()) {
      return immediateFuture(ImmutableList.of());
    }

    if (ownersAndExecutors.isEmpty()) {
      return immediateFuture(ImmutableList.of(username));
    }

    // Deduplicate candidates to minimize RPC calls.
    ImmutableSet<String> uniqueCandidates = ImmutableSet.copyOf(ownersAndExecutors);

    return Futures.transform(
        Futures.allAsList(
            uniqueCandidates.stream()
                .map(
                    candidate ->
                        candidate.equals(username)
                            ? immediateFuture(username)
                            : Futures.transform(
                                groupMembershipProvider.isMemberOfAny(
                                    username, ImmutableList.of(candidate)),
                                isMember -> isMember ? candidate : null,
                                directExecutor()))
                .collect(toImmutableList())),
        results -> results.stream().filter(Objects::nonNull).collect(toImmutableList()),
        executor);
  }
}
