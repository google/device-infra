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

import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.service.moss.proto.Slg.ResultProto;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import com.google.wireless.qa.mobileharness.shared.model.job.out.JobOutInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Result;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;

/**
 * Utility class to help convert a {@link Result} class to a {@link ResultProto} in forward and
 * backward.
 */
final class ResultConverter {

  private ResultConverter() {}

  /**
   * Gets a {@link Result} by the given {@link Timing}, {@link Params} and {@link ResultProto}.
   * Note: the params should be restored first in able to invoke this method.
   */
  static Result fromProto(Timing timing, Params params, ResultProto resultProto) {
    return JobOutInternalFactory.createResult(timing, params, resultProto);
  }

  /**
   * Gets a {@link ResultProto} by the given {@link Result}. The cause to the failure result is also
   * persistent if presented.
   */
  static ResultProto toProto(Result result) {
    ResultTypeWithCause resultTypeWithCause = result.toNewResult().get();
    ResultProto.Builder resultProto =
        ResultProto.newBuilder().setResult(resultTypeWithCause.type());
    resultTypeWithCause.cause().ifPresent(resultProto::setCause);
    return resultProto.build();
  }
}
