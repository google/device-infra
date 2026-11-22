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
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/**
 * Defines the capabilities and scope of the Config Service based on the current environment and
 * target universe.
 */
public class ConfigServiceCapability {

  private final Environment environment;
  private final UniverseScope universe;

  @AssistedInject
  ConfigServiceCapability(Environment environment, @Assisted UniverseScope universe) {
    this.environment = environment;
    this.universe = universe;
  }

  // TODO: Consolidate with FeatureManager.isConfigurationFeatureEnabled() which
  // contains similar universe-based availability logic.
  /** Returns true if the configuration service is available for the current context. */
  public boolean isConfigServiceAvailable() {
    if (environment.isGoogleInternal()) {
      return universe instanceof UniverseScope.SelfUniverse;
    }

    if (!Flags.feConnectToConfigServer.get()) {
      return false;
    }

    // OSS/ATS only supports the default context (empty universe).
    return universe instanceof UniverseScope.SelfUniverse;
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
              "Configuration operations are not currently supported for universe '%s'.",
              universe instanceof UniverseScope.RoutedUniverse routed
                  ? routed.atsControllerId()
                  : "self"));
    }

    if (!Flags.feConnectToConfigServer.getNonNull()) {
      throw new UnsupportedOperationException("Configuration service is currently disabled.");
    }

    throw new UnsupportedOperationException(
        "Configuration operations are not currently supported.");
  }

  /** Calculates UI status for host configuration components. */
  public HostConfigUiStatus calculateHostUiStatus() {
    boolean isAts = environment.isAts();

    return HostConfigUiStatus.newBuilder()
        .setHostAdmins(visibleAndEditable(!isAts))
        .setDeviceConfigMode(visibleAndEditable(!isAts))
        .setDeviceConfig(
            HostDeviceConfigUiStatus.newBuilder()
                .setSectionStatus(visibleAndEditable(true))
                .setSubSections(calculateHostDeviceSubSectionsUiStatus(isAts))
                .build())
        .setHostProperties(
            HostPropertiesUiStatus.newBuilder()
                .setSectionStatus(visibleAndEditable(!isAts))
                .build())
        .setDeviceDiscovery(visibleAndEditable(!isAts))
        .build();
  }

  /** Calculates UI status for device configuration components. */
  public DeviceConfigUiStatus calculateDeviceUiStatus() {
    boolean isAts = environment.isAts();
    return DeviceConfigUiStatus.newBuilder()
        .setPermissions(visibleAndEditable(!isAts))
        .setWifi(visibleAndEditable(!isAts))
        .setDimensions(visibleAndEditable(true))
        .setSettings(visibleAndEditable(!isAts))
        .build();
  }

  private static DeviceConfigUiStatus calculateHostDeviceSubSectionsUiStatus(boolean isAts) {
    return DeviceConfigUiStatus.newBuilder()
        .setPermissions(visibleAndEditable(!isAts))
        .setWifi(visibleAndEditable(true))
        .setDimensions(visibleAndEditable(!isAts))
        .setSettings(visibleAndEditable(!isAts))
        .build();
  }

  private static PartStatus visibleAndEditable(boolean enabled) {
    return PartStatus.newBuilder()
        .setVisible(enabled)
        .setEditability(Editability.newBuilder().setEditable(enabled).build())
        .build();
  }
}
