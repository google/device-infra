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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Abstract {@link TestbedConfig} base class that defines common methods for all TestbedConfig
 * implementations.
 *
 * <p>Note that the implementation below may result in many TestbedConfigs that are not equal but
 * have the same hash.
 */
public abstract class BaseTestbedConfig implements TestbedConfig {

  private final String name;

  // Use a ImmutableMap to preserve device insertion order. This is so that when a user calls
  // getDevices() the order of entries is deterministic and same as the insertion order.
  private final ImmutableMap<SubDeviceKey, SubDeviceInfo> devices;

  private final ImmutableMap<String, String> dimensions;
  private final ImmutableMap<String, Object> properties;

  private static final Yaml yaml = createYaml();

  protected BaseTestbedConfig(
      String name,
      Map<SubDeviceKey, SubDeviceInfo> devices,
      Map<String, String> dimensions,
      Map<String, Object> properties)
      throws MobileHarnessException {
    this.name = name;
    this.devices = ImmutableMap.copyOf(devices);
    this.dimensions = ImmutableMap.copyOf(dimensions);
    this.properties = ImmutableMap.copyOf(properties);
    validate();
  }

  @Override
  public final String getName() {
    return name;
  }

  @Override
  public final ImmutableMap<SubDeviceKey, SubDeviceInfo> getDevices() {
    return devices;
  }

  @Override
  public final ImmutableMap<String, String> getDimensions() {
    return dimensions;
  }

  @Override
  public final ImmutableMap<String, Object> getProperties() {
    return properties;
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof TestbedConfig)) {
      return false;
    }
    TestbedConfig otherConfig = (TestbedConfig) other;
    return getName().equals(otherConfig.getName())
        && getDimensions().equals(otherConfig.getDimensions())
        && getProperties().equals(otherConfig.getProperties())
        && compareDevices(getDevices(), otherConfig.getDevices());
  }

  @Override
  public final int hashCode() {
    return Objects.hash(getName(), getDevices(), getDimensions(), getProperties());
  }

  @Override
  public final String toString() {
    return toYaml();
  }

  @Override
  public final String toYaml() {
    return yaml.dump(this);
  }

  /** Returns true iff first and second contain the same entries in the same order. */
  private static boolean compareDevices(
      ImmutableMap<SubDeviceKey, SubDeviceInfo> first,
      ImmutableMap<SubDeviceKey, SubDeviceInfo> second) {
    if (first.size() != second.size()) {
      return false;
    }
    ImmutableList<Entry<SubDeviceKey, SubDeviceInfo>> firstEntries = first.entrySet().asList();
    ImmutableList<Entry<SubDeviceKey, SubDeviceInfo>> secondEntries = second.entrySet().asList();
    return firstEntries.equals(secondEntries);
  }

  /**
   * Validates required fields of TestbedConfig (see go/mh-testbeds-static).
   *
   * <p>In particular, this means that the id is not an empty string, that the config defines at
   * least one subdevice, and that no subdevice has the same id as the testbed. Subclasses of
   * BaseTestbedConfig should call {@code validate()} at the end of or after construction.
   */
  protected final void validate() throws MobileHarnessException {
    ImmutableMap<SubDeviceKey, SubDeviceInfo> devices = getDevices();
    String testbedId = getName();
    if (Strings.isNullOrEmpty(testbedId)) {
      throw new MobileHarnessException(
          ExtErrorId.TESTBED_CONFIG_TESTBED_ID_NOT_EXIST_ERROR,
          "Invalid TestbedConfig -- ID cannot be an empty string: " + this);
    }
    if (devices.isEmpty()) {
      throw new MobileHarnessException(
          ExtErrorId.TESTBED_CONFIG_SUBDEVICE_NOT_EXIST_ERROR,
          "Invalid TestbedConfig -- Testbed needs at least one subdevice: " + this);
    }
    for (SubDeviceInfo subDeviceInfo : devices.values()) {
      if (subDeviceInfo.getId().equals(testbedId)) {
        throw new MobileHarnessException(
            ExtErrorId.TESTBED_CONFIG_TESTBED_SUBDEVICE_SAME_NAME_ERROR,
            "Invalid TestbedConfig -- Subdevices and testbed cannot have the same IDs: " + this);
      }
    }
  }

  private static Yaml createYaml() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    return new Yaml(
        new SafeConstructor(new LoaderOptions()), new TestbedConfigYamlRepresenter(), options);
  }
}
