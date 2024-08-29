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

package com.google.devtools.mobileharness.api.model.job.in;

import com.google.devtools.mobileharness.api.model.job.out.TouchableTiming;
import com.google.devtools.mobileharness.service.moss.proto.Slg.DimensionsProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.FilesProto;
import com.google.devtools.mobileharness.service.moss.proto.Slg.ParamsProto;

/**
 * The factory to help create instances under this package. It's only used internally to support
 * making job/test data models persistent.
 */
public final class JobInInternalFactory {

  private JobInInternalFactory() {}

  /** Creates a {@link Dimensions} instance by the given {@link DimensionsProto}. */
  public static Dimensions createDimensions(DimensionsProto dimensionsProto) {
    return new Dimensions(dimensionsProto);
  }

  /**
   * Creates a {@link Params} instance by the given {@link TouchableTiming} and {@link ParamsProto}.
   */
  public static Params createParams(TouchableTiming touchableTiming, ParamsProto paramsProto) {
    return new Params(touchableTiming, paramsProto);
  }

  /**
   * Creates a {@link Files} instance by the given {@link TouchableTiming} and {@link FilesProto}.
   */
  public static Files createFiles(TouchableTiming touchableTiming, FilesProto filesProto) {
    return new Files(touchableTiming, null, filesProto);
  }
}
