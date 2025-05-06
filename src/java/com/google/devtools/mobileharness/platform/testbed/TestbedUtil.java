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

package com.google.devtools.mobileharness.platform.testbed;

import com.google.devtools.mobileharness.platform.testbed.config.SubDeviceInfo;
import com.google.devtools.mobileharness.platform.testbed.config.TestbedConfig;
import com.google.devtools.mobileharness.shared.util.base.ProtoExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.proto.Device.SubDeviceDimensions;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Helper class for interacting with the loaded testbeds. */
public class TestbedUtil {
  /**
   * Get all device ids within a collection of testbed configs that are for a certain device type.
   *
   * @param configs A collection of configs.
   * @param deviceType The type of device the id needs to belong to.
   * @return A set of all device ids for the given type.
   */
  public Set<String> getAllIdsOfType(
      Collection<TestbedConfig> configs, Class<? extends Device> deviceType) {
    Set<String> ids = new HashSet<>();

    for (TestbedConfig config : configs) {
      for (SubDeviceInfo subDeviceInfo : config.getDevices().values()) {
        if (subDeviceInfo.getDeviceType() == deviceType) {
          ids.add(subDeviceInfo.getId());
        }
      }
    }

    return ids;
  }

  /** Encodes the SubDeviceDimensions object into a byte array string. */
  public static String encodeSubDeviceDimensions(SubDeviceDimensions subDeviceDimensions) {
    return Base64.getEncoder().encodeToString(subDeviceDimensions.toByteArray());
  }

  /**
   * Decodes the byte array string for a SubDeviceDimensions object and returns the
   * SubDeviceDimension object. This method undos the encodeSubDeviceDimensions method above.
   *
   * @param byteArrStr the byte array string generated from the encodeSubDeviceDimensions method
   */
  public static SubDeviceDimensions decodeSubDeviceDimensionsStr(String byteArrStr)
      throws InvalidProtocolBufferException {
    return SubDeviceDimensions.parseFrom(
        Base64.getDecoder().decode(byteArrStr), ProtoExtensionRegistry.getGeneratedRegistry());
  }
}
