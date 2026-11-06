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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ConfigurableDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** OSS and ATS deployment policy for batch device dimension configuration. */
@Singleton
public final class OssBatchDeviceConfigPolicy implements BatchDeviceConfigPolicy {

  private static final ImmutableList<ConfigurableDimension> CURATED_DIMENSIONS =
      ImmutableList.of(
          ConfigurableDimension.newBuilder()
              .setDimensionName("recovery")
              .setDisplayName("recovery")
              .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
              .build(),
          ConfigurableDimension.newBuilder()
              .setDimensionName("skip_suw_app")
              .setDisplayName("skip_suw_app")
              .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
              .build(),
          ConfigurableDimension.newBuilder()
              .setDimensionName("pool")
              .setDisplayName("Required Dimension: pool")
              .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
              .build(),
          ConfigurableDimension.newBuilder()
              .setDimensionName("persist_test_harness")
              .setDisplayName("Required Dimension: persist_test_harness")
              .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
              .build());

  private static final ImmutableList<String> SORTED_SYSTEM_DETECTED_DIMENSION_NAMES =
      ImmutableList.sortedCopyOf(BatchDeviceConfigUtil.COMMON_SYSTEM_DETECTED_DIMENSION_NAMES);

  @Inject
  public OssBatchDeviceConfigPolicy() {}

  @Override
  public ImmutableList<ConfigurableDimension> getCuratedDimensions() {
    return CURATED_DIMENSIONS;
  }

  @Override
  public boolean allowCustomDimensions() {
    return false;
  }

  @Override
  public boolean discoverFleetDimensionsFromDeviceConfigs() {
    return true;
  }

  @Override
  public ImmutableSet<String> getSystemDetectedDimensionNames() {
    return BatchDeviceConfigUtil.COMMON_SYSTEM_DETECTED_DIMENSION_NAMES;
  }

  @Override
  public ImmutableList<String> getSortedSystemDetectedDimensionNames() {
    return SORTED_SYSTEM_DETECTED_DIMENSION_NAMES;
  }

  @Override
  public Optional<String> checkPolicyDimensionTarget(
      String dimensionName, DimensionList dimensionList) {
    if (!getDefaultConfigurableKeys(dimensionList).contains(dimensionName)) {
      return Optional.of(
          String.format(
              "Dimension \"%s\" in %s is not configurable in ATS.", dimensionName, dimensionList));
    }
    return Optional.empty();
  }
}
