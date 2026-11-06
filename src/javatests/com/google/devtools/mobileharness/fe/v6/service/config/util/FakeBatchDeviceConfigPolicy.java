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
import com.google.common.collect.Sets;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ConfigurableDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Switchable {@link BatchDeviceConfigPolicy} test double supporting both a custom-allowing policy
 * (with curated {@code pool}, {@code label}, and {@code cluster} plus {@code LabInfoService} index
 * discovery) and {@link OssBatchDeviceConfigPolicy}.
 */
public final class FakeBatchDeviceConfigPolicy implements BatchDeviceConfigPolicy {

  private static final OssBatchDeviceConfigPolicy OSS_POLICY = new OssBatchDeviceConfigPolicy();

  private static final ImmutableList<ConfigurableDimension> CUSTOM_CURATED_DIMENSIONS =
      ImmutableList.of(
          ConfigurableDimension.newBuilder()
              .setDimensionName("pool")
              .setDisplayName("Required Dimension: pool")
              .setDimensionList(DimensionList.DIMENSION_LIST_REQUIRED)
              .build(),
          ConfigurableDimension.newBuilder()
              .setDimensionName("label")
              .setDisplayName("label")
              .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
              .build(),
          ConfigurableDimension.newBuilder()
              .setDimensionName("cluster")
              .setDisplayName("cluster")
              .setDimensionList(DimensionList.DIMENSION_LIST_SUPPORTED)
              .build());

  private final Set<String> extraSystemDetectedDimensions = new HashSet<>();
  private boolean ossMode = false;

  public void useCustomAllowingPolicy() {
    ossMode = false;
    extraSystemDetectedDimensions.clear();
  }

  public void useOssPolicy() {
    ossMode = true;
    extraSystemDetectedDimensions.clear();
  }

  public void addExtraSystemDetectedDimension(String dimensionName) {
    extraSystemDetectedDimensions.add(dimensionName);
  }

  @Override
  public ImmutableList<ConfigurableDimension> getCuratedDimensions() {
    return ossMode ? OSS_POLICY.getCuratedDimensions() : CUSTOM_CURATED_DIMENSIONS;
  }

  @Override
  public boolean allowCustomDimensions() {
    return !ossMode;
  }

  @Override
  public boolean discoverFleetDimensionsFromDeviceConfigs() {
    return ossMode;
  }

  @Override
  public ImmutableSet<String> getSystemDetectedDimensionNames() {
    if (extraSystemDetectedDimensions.isEmpty()) {
      return BatchDeviceConfigUtil.COMMON_SYSTEM_DETECTED_DIMENSION_NAMES;
    }
    return ImmutableSet.copyOf(
        Sets.union(
            BatchDeviceConfigUtil.COMMON_SYSTEM_DETECTED_DIMENSION_NAMES,
            extraSystemDetectedDimensions));
  }

  @Override
  public Optional<String> checkPolicyDimensionTarget(
      String dimensionName, DimensionList dimensionList) {
    return ossMode
        ? OSS_POLICY.checkPolicyDimensionTarget(dimensionName, dimensionList)
        : BatchDeviceConfigPolicy.super.checkPolicyDimensionTarget(dimensionName, dimensionList);
  }
}
