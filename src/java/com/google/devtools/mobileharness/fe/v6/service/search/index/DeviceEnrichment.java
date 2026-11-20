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

package com.google.devtools.mobileharness.fe.v6.service.search.index;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;

/**
 * Per-device enrichment that a data source layers on top of the LabInfo-derived device record.
 *
 * <p>This is a deployment-neutral data type: it carries no proto and no case-specific dependency,
 * so the shared {@link FleetIndexBuilder} can consume it in every deployment. Each data source maps
 * its own backend (for example a DeviceConfigService proto) into this shape and leaves absent
 * whatever it cannot supply.
 */
@AutoValue
public abstract class DeviceEnrichment {

  /**
   * Default WiFi SSID for the device. Absent when the config service is unavailable or the device
   * has no default WiFi configured.
   */
  public abstract Optional<String> wifiSsid();

  /**
   * Device config values keyed by canonical key id (for example {@code
   * device_config::max_consecutive_fail}), for keys that only the data source's own deployment
   * defines. The data source resolves these values, and the shared {@link FleetIndexBuilder}
   * indexes them as given without interpreting them.
   */
  public abstract ImmutableMap<String, ImmutableList<String>> configValues();

  /** Creates a new builder. */
  public static Builder builder() {
    return new AutoValue_DeviceEnrichment.Builder()
        .setWifiSsid(Optional.empty())
        .setConfigValues(ImmutableMap.of());
  }

  /** Builder for {@link DeviceEnrichment}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setWifiSsid(Optional<String> wifiSsid);

    public abstract Builder setConfigValues(
        ImmutableMap<String, ImmutableList<String>> configValues);

    public abstract DeviceEnrichment build();
  }
}
