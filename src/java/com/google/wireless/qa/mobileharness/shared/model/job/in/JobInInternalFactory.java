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

package com.google.wireless.qa.mobileharness.shared.model.job.in;

import com.google.devtools.mobileharness.service.moss.proto.Slg.SubDeviceSpecsProto;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import java.util.Collection;

/**
 * The factory to help create instances under this package. It's only used internally to support
 * making job/test data models persistent.
 */
public final class JobInInternalFactory {

  private JobInInternalFactory() {}

  /**
   * Creates a {@link Dimensions} instance by the given {@link Timing} and {@link
   * com.google.devtools.mobileharness.api.model.job.in.Dimensions}.
   */
  public static Dimensions createDimensions(
      Timing timing, com.google.devtools.mobileharness.api.model.job.in.Dimensions newDimensions) {
    return new Dimensions(timing, newDimensions);
  }

  /**
   * Creates a {@link Params} instance by the given {@link
   * com.google.devtools.mobileharness.api.model.job.in.Params}.
   */
  public static Params createParams(
      com.google.devtools.mobileharness.api.model.job.in.Params newParams) {
    return new Params(newParams);
  }

  /**
   * Creates a {@link ScopedSpecs} instance by the given global {@link Params}, job {@link Timing}
   * and the string representation of a {@link ScopedSpecs}.
   */
  public static ScopedSpecs createScopedSpecs(
      Params globalParams, Timing timing, String scopedSpecsJsonString) {
    return new ScopedSpecs(globalParams, timing, scopedSpecsJsonString);
  }

  /**
   * Creates a {@link SubDeviceSpecs} instance by the given {@link Timing}, {@link Params},
   * recovered collection of {@link SubDeviceSpec}s and the {@link SubDeviceSpecsProto}.
   */
  public static SubDeviceSpecs createSubDeviceSpecs(
      Timing timing,
      Params params,
      Collection<SubDeviceSpec> subDeviceSpecs,
      SubDeviceSpecsProto subDeviceSpecsProto) {
    return new SubDeviceSpecs(timing, params, subDeviceSpecs, subDeviceSpecsProto);
  }
}
