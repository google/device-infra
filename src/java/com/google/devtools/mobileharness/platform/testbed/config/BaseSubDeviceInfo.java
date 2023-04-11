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

package com.google.devtools.mobileharness.platform.testbed.config;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Abstract {@link SubDeviceInfo} base class that defines common methods for all SubDeviceInfo
 * implementations.
 */
public abstract class BaseSubDeviceInfo implements SubDeviceInfo {

  private final String id;
  private final Class<? extends Device> deviceType;
  private final ImmutableSet<String> deviceAliases;
  private final ImmutableMap<String, Object> properties;
  private final ImmutableMultimap<String, String> dimensions;

  protected BaseSubDeviceInfo(
      String id,
      Class<? extends Device> deviceType,
      Set<String> deviceAliases,
      Multimap<String, String> dimensions,
      Map<String, Object> properties)
      throws MobileHarnessException {
    // From go/mh-testbeds-static, a subdevice id cannot be null or empty.
    if (Strings.isNullOrEmpty(id)) {
      throw new MobileHarnessException(
          ExtErrorId.TESTBED_CONFIG_SUBDEVICE_ID_EMPTY_ERROR,
          "Invalid SubDeviceInfo -- ID cannot be an empty string: " + this);
    }
    this.id = id;
    this.deviceType = deviceType;
    this.deviceAliases = ImmutableSet.copyOf(deviceAliases);
    this.dimensions = ImmutableMultimap.copyOf(dimensions);
    this.properties = ImmutableMap.copyOf(properties);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Class<? extends Device> getDeviceType() {
    return deviceType;
  }

  @Override
  public ImmutableSet<String> getDeviceAliasType() {
    return deviceAliases;
  }

  @Override
  public ImmutableMultimap<String, String> getDimensions() {
    return dimensions;
  }

  @Override
  public ImmutableMap<String, Object> getProperties() {
    return properties;
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof SubDeviceInfo)) {
      return false;
    }
    SubDeviceInfo otherDevice = (SubDeviceInfo) other;
    return getId().equals(otherDevice.getId())
        && getDeviceType().equals(otherDevice.getDeviceType())
        && getDeviceAliasType().equals(otherDevice.getDeviceAliasType())
        && getDimensions().equals(otherDevice.getDimensions())
        && getProperties().equals(otherDevice.getProperties());
  }

  @Override
  public final int hashCode() {
    return Objects.hash(
        getId(), getDeviceType(), getDeviceAliasType(), getDimensions(), getProperties());
  }
}
