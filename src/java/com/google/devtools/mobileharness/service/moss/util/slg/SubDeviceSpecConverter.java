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

import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.DeviceRequirement;
import com.google.devtools.mobileharness.infra.controller.scheduler.model.job.in.JobInInternalFactory;
import com.google.devtools.mobileharness.service.moss.proto.Slg.SubDeviceSpecProto;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.in.ScopedSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;

/**
 * Utility class to convert a {@link SubDeviceSpec} to a {@link SubDeviceSpecProto} in forward and
 * backward.
 */
final class SubDeviceSpecConverter {

  private SubDeviceSpecConverter() {}

  /**
   * Gets a {@link SubDeviceSpec} by the given {@link Params}, {@link Timing} and {@link
   * SubDeviceSpecProto}. Note: it uses the public API {@link
   * SubDeviceSpec#createForTesting(DeviceRequirement, ScopedSpecs, Timing)} while it could be used
   * not only in tests.
   */
  static SubDeviceSpec fromProto(
      Params params, Timing timing, SubDeviceSpecProto subDeviceSpecProto) {
    return SubDeviceSpec.createForTesting(
        JobInInternalFactory.createDeviceRequirement(subDeviceSpecProto.getDeviceRequirement()),
        com.google.wireless.qa.mobileharness.shared.model.job.in.JobInInternalFactory
            .createScopedSpecs(params, timing, subDeviceSpecProto.getScopedSpecsJsonString()),
        timing);
  }

  /** Gets a {@link SubDeviceSpecProto} by the given {@link SubDeviceSpec}. */
  static SubDeviceSpecProto toProto(SubDeviceSpec subDeviceSpec) {
    return SubDeviceSpecProto.newBuilder()
        .setDeviceRequirement(subDeviceSpec.deviceRequirement().toProto())
        .setScopedSpecsJsonString(subDeviceSpec.scopedSpecs().toJsonString())
        .build();
  }
}
