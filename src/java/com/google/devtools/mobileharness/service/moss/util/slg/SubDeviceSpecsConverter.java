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

import com.google.devtools.mobileharness.service.moss.proto.Slg.SubDeviceSpecsProto;
import com.google.wireless.qa.mobileharness.shared.model.job.in.JobInInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.stream.Collectors;

/**
 * Utility class to convert a {@link SubDeviceSpecs} to a {@link SubDeviceSpecsProto} in forward and
 * backward.
 */
final class SubDeviceSpecsConverter {

  private SubDeviceSpecsConverter() {}

  /**
   * Gets a {@link SubDeviceSpecs} by the given {@link Timing}, {@link Params} and {@link
   * SubDeviceSpecsProto}.
   */
  static SubDeviceSpecs fromProto(
      Timing timing, Params params, SubDeviceSpecsProto subDeviceSpecsProto) {
    return JobInInternalFactory.createSubDeviceSpecs(
        timing,
        params,
        subDeviceSpecsProto.getSubDeviceSpecProtoList().stream()
            .map(
                subDeviceSpecProto ->
                    SubDeviceSpecConverter.fromProto(params, timing, subDeviceSpecProto))
            .collect(Collectors.toList()),
        subDeviceSpecsProto);
  }

  /** Gets a {@link SubDeviceSpecsProto} by the given {@link SubDeviceSpecs}. */
  static SubDeviceSpecsProto toProto(SubDeviceSpecs subDeviceSpecs) {
    return SubDeviceSpecsProto.newBuilder()
        .addAllSubDeviceSpecProto(
            subDeviceSpecs.getAllSubDevices().stream()
                .map(SubDeviceSpecConverter::toProto)
                .collect(Collectors.toList()))
        .addAllSharedDimensionName(subDeviceSpecs.getSharedDimensionNames())
        .addAllLatestDeviceIdFromAllocation(subDeviceSpecs.getLatestDeviceIdsFromAllocation())
        .build();
  }
}
