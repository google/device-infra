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

package com.google.devtools.mobileharness.fe.v6.service.config.handlers;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.mobileharness.fe.v6.service.config.util.ConfigServiceCapabilityFactory;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.ConfigurableDimension;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.DimensionList;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetConfigurableDimensionsRequest;
import com.google.devtools.mobileharness.fe.v6.service.proto.config.GetConfigurableDimensionsResponse;
import com.google.devtools.mobileharness.fe.v6.service.util.Environment;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Handler for the GetConfigurableDimensions RPC. */
@Singleton
public final class GetConfigurableDimensionsHandler {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableList<ConfigurableDimension> ATS_CONFIGURABLE_DIMENSIONS =
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

  private static final ImmutableList<ConfigurableDimension> INTERNAL_CONFIGURABLE_DIMENSIONS =
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

  private final ConfigServiceCapabilityFactory configServiceCapabilityFactory;
  private final Environment environment;

  @Inject
  GetConfigurableDimensionsHandler(
      ConfigServiceCapabilityFactory configServiceCapabilityFactory, Environment environment) {
    this.configServiceCapabilityFactory = configServiceCapabilityFactory;
    this.environment = environment;
  }

  public ListenableFuture<GetConfigurableDimensionsResponse> getConfigurableDimensions(
      GetConfigurableDimensionsRequest request, UniverseScope universe) {
    logger.atInfo().log("GetConfigurableDimensionsRequest: %s", request);

    if (!configServiceCapabilityFactory.create(universe).isConfigServiceAvailable()) {
      return immediateFuture(
          GetConfigurableDimensionsResponse.newBuilder()
              .setEnabled(false)
              .setAllowCustomDimensions(false)
              .build());
    }

    if (environment.isAts()) {
      return immediateFuture(
          GetConfigurableDimensionsResponse.newBuilder()
              .setEnabled(true)
              .addAllDimensions(ATS_CONFIGURABLE_DIMENSIONS)
              .setAllowCustomDimensions(false)
              .build());
    }

    return immediateFuture(
        GetConfigurableDimensionsResponse.newBuilder()
            .setEnabled(true)
            .addAllDimensions(INTERNAL_CONFIGURABLE_DIMENSIONS)
            .setAllowCustomDimensions(true)
            .build());
  }
}
