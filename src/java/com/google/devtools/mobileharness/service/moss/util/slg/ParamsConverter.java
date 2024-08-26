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

import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import com.google.devtools.mobileharness.service.moss.proto.Slg.ParamsProto;
import com.google.wireless.qa.mobileharness.shared.model.job.in.JobInInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;

/**
 * Utility class to help convert {@link Params} to a {@link ParamsProto} in forward and backward.
 */
final class ParamsConverter {

  private ParamsConverter() {}

  /** Gets a {@link Params} by the given {@link TouchableTiming} and {@link ParamsProto}. */
  static Params fromProto(TouchableTiming touchableTiming, ParamsProto paramsProto) {
    return JobInInternalFactory.createParams(
        com.google.devtools.mobileharness.api.model.job.in.JobInInternalFactory.createParams(
            touchableTiming, paramsProto));
  }

  /** Gets a {@link ParamsProto} by the given {@link Params}. */
  static ParamsProto toProto(Params params) {
    return ParamsProto.newBuilder().putAllParams(params.getAll()).build();
  }
}
