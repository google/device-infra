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

package com.google.devtools.mobileharness.fe.v6.service.search.pull;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Map;

/** Raw dimension data pulled from a data source for one on-demand long-tail dimension. */
@AutoValue
public abstract class DimensionOverlayRaw {

  /**
   * Scoped identity of a device in an on-demand dimension overlay, disambiguating devices that
   * share a {@code deviceId} across hosts or universes.
   */
  public record DeviceKey(UniverseScope universe, String hostName, String deviceId) {
    public static DeviceKey ofUuid(String deviceId) {
      return new DeviceKey(UniverseScope.SELF, "", deviceId);
    }
  }

  /** The namespaced key id (e.g. "dimension::carrier"). */
  public abstract String keyId();

  /** Map from scoped {@link DeviceKey} to the list of display-cased values for this dimension. */
  public abstract ImmutableMap<DeviceKey, ImmutableList<String>> deviceValues();

  /**
   * Map from bare device UUID to the list of display-cased values for this dimension (last-seen
   * wins when multiple hosts share a UUID), for single-universe callers and tests.
   */
  @Memoized
  public ImmutableMap<String, ImmutableList<String>> uuidToValues() {
    return deviceValues().entrySet().stream()
        .collect(
            toImmutableMap(
                entry -> entry.getKey().deviceId(),
                Map.Entry::getValue,
                (first, second) -> second));
  }

  public static DimensionOverlayRaw createForDevices(
      String keyId, ImmutableMap<DeviceKey, ImmutableList<String>> deviceValues) {
    return new AutoValue_DimensionOverlayRaw(keyId, deviceValues);
  }

  public static DimensionOverlayRaw create(
      String keyId, ImmutableMap<String, ImmutableList<String>> uuidToValues) {
    ImmutableMap<DeviceKey, ImmutableList<String>> deviceValues =
        uuidToValues.entrySet().stream()
            .collect(
                toImmutableMap(
                    entry -> DeviceKey.ofUuid(entry.getKey()),
                    Map.Entry::getValue,
                    (first, second) -> second));
    return new AutoValue_DimensionOverlayRaw(keyId, deviceValues);
  }
}
