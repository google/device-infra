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

package com.google.devtools.mobileharness.fe.v6.service.config.util;

import com.google.devtools.mobileharness.fe.v6.service.proto.config.DeviceConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.Editability;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostDeviceConfigUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.HostPropertiesUiStatus;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.PartStatus;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * Defines the capabilities and scope of the Config Service based on the current environment and
 * target universe.
 */
public class ConfigServiceCapability {

  private final Environment environment;
  private final String universe;

  @AssistedInject
  ConfigServiceCapability(Environment environment, @Assisted String universe) {
    this.environment = environment;
    this.universe = universe;
  }

  /** Returns true if the configuration service is available for the current context. */
  public boolean isConfigServiceAvailable() {
    if (environment.isGoogleInternal()) {
      return universe.isEmpty() || universe.equals("google_1p");
    }

    if (!Flags.instance().feConnectToConfigServer.get()) {
      return false;
    }

    // OSS/ATS only supports the default context (empty universe).
    return universe.isEmpty();
  }

  /**
   * Checks if the configuration service is available and throws an {@link
   * UnsupportedOperationException} with an environment-aware message if not.
   */
  public void checkConfigServiceAvailability() {
    if (isConfigServiceAvailable()) {
      return;
    }

    if (environment.isGoogleInternal()) {
      throw new UnsupportedOperationException(
          String.format(
              "Configuration operations are not currently supported for universe '%s'.", universe));
    }

    if (!Flags.instance().feConnectToConfigServer.getNonNull()) {
      throw new UnsupportedOperationException("Configuration service is currently disabled.");
    }

    throw new UnsupportedOperationException(
        "Configuration operations are not currently supported.");
  }

  /** Calculates UI status for host configuration components. */
  public HostConfigUiStatus calculateHostUiStatus() {
    boolean isAts = environment.isAts();

    return HostConfigUiStatus.newBuilder()
        .setHostAdmins(
            PartStatus.newBuilder()
                .setVisible(!isAts)
                .setEditability(Editability.newBuilder().setEditable(!isAts).build())
                .build())
        .setDeviceConfigMode(
            PartStatus.newBuilder()
                .setVisible(true)
                .setEditability(Editability.newBuilder().setEditable(true).build())
                .build())
        .setDeviceConfig(
            HostDeviceConfigUiStatus.newBuilder()
                .setSectionStatus(
                    PartStatus.newBuilder()
                        .setVisible(true)
                        .setEditability(Editability.newBuilder().setEditable(true).build())
                        .build())
                .setSubSections(calculateDeviceUiStatus())
                .build())
        .setHostProperties(
            HostPropertiesUiStatus.newBuilder()
                .setSectionStatus(
                    PartStatus.newBuilder()
                        .setVisible(!isAts)
                        .setEditability(Editability.newBuilder().setEditable(!isAts).build())
                        .build())
                .build())
        .setDeviceDiscovery(
            PartStatus.newBuilder()
                .setVisible(!isAts)
                .setEditability(Editability.newBuilder().setEditable(!isAts).build())
                .build())
        .build();
  }

  /** Calculates UI status for device configuration components. */
  public DeviceConfigUiStatus calculateDeviceUiStatus() {
    boolean isAts = environment.isAts();
    return DeviceConfigUiStatus.newBuilder()
        .setPermissions(
            PartStatus.newBuilder()
                .setVisible(!isAts)
                .setEditability(Editability.newBuilder().setEditable(!isAts).build())
                .build())
        .setWifi(
            PartStatus.newBuilder()
                .setVisible(true)
                .setEditability(Editability.newBuilder().setEditable(true).build())
                .build())
        .setDimensions(
            PartStatus.newBuilder()
                .setVisible(true)
                .setEditability(Editability.newBuilder().setEditable(true).build())
                .build())
        .setSettings(
            PartStatus.newBuilder()
                .setVisible(!isAts)
                .setEditability(Editability.newBuilder().setEditable(!isAts).build())
                .build())
        .build();
  }
}
