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

import com.google.devtools.mobileharness.service.moss.proto.Result.TimeDetail;
import com.google.wireless.qa.mobileharness.shared.model.job.out.JobOutInternalFactory;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;

/**
 * Utility class to help convert {@link Timing} classes to {@link TimeDetail}s in forward or
 * backward.
 */
final class TimingConverter {

  private TimingConverter() {}

  /** Gets a {@link Timing} from the given {@link TimeDetail}. */
  static Timing fromProto(TimeDetail timeDetail) {
    return JobOutInternalFactory.createTiming(
        com.google.devtools.mobileharness.api.model.job.out.JobOutInternalFactory
            .createTouchableTiming(timeDetail));
  }

  /** Gets a {@link TimeDetail} from a given {@link Timing}. */
  static TimeDetail toProto(Timing timing) {
    TimeDetail.Builder timeDetail =
        TimeDetail.newBuilder()
            .setCreateTimeMs(timing.getCreateTime().toEpochMilli())
            .setModifyTimeMs(timing.getModifyTime().toEpochMilli());
    if (timing.getStartTime() != null) {
      timeDetail.setStartTimeMs(timing.getStartTime().toEpochMilli());
    }
    return timeDetail.build();
  }
}
