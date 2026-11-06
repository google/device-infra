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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ConfigurableDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import java.util.Optional;

/** Deployment curation and validation policy for batch device dimension configuration. */
public interface BatchDeviceConfigPolicy {

  /** Curated dimensions exposed directly in the Search Page Configuration menu. */
  ImmutableList<ConfigurableDimension> getCuratedDimensions();

  /** Whether operators are permitted to configure arbitrary custom dimension keys. */
  boolean allowCustomDimensions();

  /**
   * Whether fleet dimension key and value discovery reads {@code DeviceConfig}s rather than
   * querying {@code LabInfoService} dimension indexes.
   */
  boolean discoverFleetDimensionsFromDeviceConfigs();

  /** Default configurable dimension keys included in Step 1 for {@code dimensionList}. */
  default ImmutableSet<String> getDefaultConfigurableKeys(DimensionList dimensionList) {
    if (dimensionList != DimensionList.DIMENSION_LIST_SUPPORTED
        && dimensionList != DimensionList.DIMENSION_LIST_REQUIRED) {
      return ImmutableSet.of();
    }
    return getCuratedDimensions().stream()
        .filter(d -> d.getDimensionList() == dimensionList)
        .map(ConfigurableDimension::getDimensionName)
        .collect(toImmutableSet());
  }

  /** Lowercase OmniLab system-detected dimension names that cannot be configured manually. */
  ImmutableSet<String> getSystemDetectedDimensionNames();

  /** Sorted OmniLab system-detected dimension names returned to Step 1 clients. */
  default ImmutableList<String> getSortedSystemDetectedDimensionNames() {
    return ImmutableList.sortedCopyOf(getSystemDetectedDimensionNames());
  }

  /**
   * Validates {@code (dimensionName, dimensionList)} against deployment curation rules, or returns
   * empty when the target is permitted.
   */
  default Optional<String> checkPolicyDimensionTarget(
      String dimensionName, DimensionList dimensionList) {
    if (!allowCustomDimensions()
        && !getDefaultConfigurableKeys(dimensionList).contains(dimensionName)) {
      return Optional.of(
          String.format(
              "Dimension \"%s\" in %s is not configurable.", dimensionName, dimensionList));
    }
    return Optional.empty();
  }
}
