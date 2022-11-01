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

package com.google.devtools.mobileharness.api.model.lab.in;

import com.google.devtools.mobileharness.infra.controller.device.config.ApiConfig;
import javax.annotation.Nullable;

/** Factory for creating {@link CompositeDimensions}. */
public final class DimensionsFactory {

  public static CompositeDimensions createCompositeDimensions(
      String deviceId,
      @Nullable ApiConfig apiConfig,
      LocalDimensions otherSourceSupportedLocalDimensions,
      LocalDimensions otherSourceRequiredLocalDimensions) {
    return new CompositeDimensions(
        createDimensions(
            deviceId, apiConfig, otherSourceSupportedLocalDimensions, /* required= */ false),
        createDimensions(
            deviceId, apiConfig, otherSourceRequiredLocalDimensions, /* required= */ true));
  }

  private static Dimensions createDimensions(
      String deviceId,
      @Nullable ApiConfig apiConfig,
      @Nullable LocalDimensions otherSourceLocalDimensions,
      boolean required) {
    return new LocalConfigurableDimensions(
        apiConfig, otherSourceLocalDimensions, deviceId, required);
  }

  private DimensionsFactory() {}
}
