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

package com.google.devtools.mobileharness.service.moss.util.slg;

import com.google.devtools.mobileharness.service.moss.proto.Slg.DimensionsProto;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Dimensions;
import com.google.wireless.qa.mobileharness.shared.model.job.in.JobInInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;

/**
 * Utility class to convert a {@link Dimensions} to a {@link DimensionsProto} in forward and
 * backward.
 */
final class DimensionsConverter {

  private DimensionsConverter() {}

  /** Gets a {@link Dimensions} by the given {@link Timing} and {@link DimensionsProto}. */
  static Dimensions fromProto(Timing timing, DimensionsProto dimensionsProto) {
    return JobInInternalFactory.createDimensions(
        timing,
        com.google.devtools.mobileharness.api.model.job.in.JobInInternalFactory.createDimensions(
            dimensionsProto));
  }

  /** Gets a {@link DimensionsProto} by the given {@link Dimensions}. */
  static DimensionsProto toProto(Dimensions dimensions) {
    return DimensionsProto.newBuilder().putAllDimensions(dimensions.getAll()).build();
  }
}
