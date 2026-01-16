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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.DeviceType;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.SubDeviceInfo;
import com.google.protobuf.ExtensionRegistry;
import com.google.wireless.qa.mobileharness.shared.api.spec.TestbedDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.Device.SubDeviceDimensions;
import java.util.Base64;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Factory for creating a list of {@link SubDeviceInfo} from device dimensions. */
@Singleton
final class SubDeviceInfoListFactory {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> ABNORMAL_TYPE_KEYWORDS =
      ImmutableSet.of(
          "DisconnectedDevice", "UnauthorizedDevice", "PreMaturedDevice", "DemotedDevice");

  @Inject
  SubDeviceInfoListFactory() {}

  /**
   * Creates a list of {@link SubDeviceInfo} by decoding testbed dimensions.
   *
   * @param dimensions A map containing all device dimensions.
   * @return A list of SubDeviceInfo if the device is a testbed and dimensions can be decoded,
   *     otherwise an empty list.
   */
  ImmutableList<SubDeviceInfo> create(ImmutableMap<String, String> dimensions) {
    Optional<String> encodedSubDeviceDimensions =
        Optional.ofNullable(dimensions.get(TestbedDeviceSpec.SUBDEVICE_DIMENSIONS_KEY));

    if (encodedSubDeviceDimensions.isPresent()) {
      try {
        byte[] decodedBytes = Base64.getDecoder().decode(encodedSubDeviceDimensions.get());
        SubDeviceDimensions subDeviceDimensions =
            SubDeviceDimensions.parseFrom(decodedBytes, ExtensionRegistry.getEmptyRegistry());

        return subDeviceDimensions.getSubDeviceDimensionList().stream()
            .map(this::createSubDeviceInfo)
            .collect(toImmutableList());

      } catch (Exception e) {
        // Log warning about decoding failure
        logger.atWarning().withCause(e).log("Failed to decode or parse subdevice dimensions.");
        return ImmutableList.of();
      }
    }
    return ImmutableList.of();
  }

  private SubDeviceInfo createSubDeviceInfo(SubDeviceDimensions.SubDeviceDimension subDevice) {
    SubDeviceInfo.Builder subDeviceInfoBuilder =
        SubDeviceInfo.newBuilder().setId(subDevice.getDeviceId());

    // Convert dimensions
    subDeviceInfoBuilder.addAllDimensions(
        subDevice.getDeviceDimensionList().stream()
            .map(
                strPair ->
                    DeviceDimension.newBuilder()
                        .setName(strPair.getName())
                        .setValue(strPair.getValue())
                        .build())
            .collect(toImmutableList()));

    // Extract types
    subDeviceInfoBuilder.addAllTypes(
        subDevice.getDeviceDimensionList().stream()
            .filter(strPair -> strPair.getName().equals(TestbedDeviceSpec.MH_DEVICE_TYPE_KEY))
            .map(
                strPair ->
                    DeviceType.newBuilder()
                        .setType(strPair.getValue())
                        .setIsAbnormal(
                            ABNORMAL_TYPE_KEYWORDS.stream().anyMatch(strPair.getValue()::contains))
                        .build())
            .collect(toImmutableList()));
    return subDeviceInfoBuilder.build();
  }
}
