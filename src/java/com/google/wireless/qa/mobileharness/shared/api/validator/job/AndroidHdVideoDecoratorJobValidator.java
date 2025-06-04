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

package com.google.wireless.qa.mobileharness.shared.api.validator.job;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidHdVideoSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Validator for the {@code AndroidHdVideoDecorator}. */
public class AndroidHdVideoDecoratorJobValidator implements JobValidator {

  /** Regex for video_size. */
  @VisibleForTesting static final String VIDEO_SIZE_REGEX = "^[1-9][0-9]{0,3}x[1-9][0-9]{0,3}$";

  @CanIgnoreReturnValue
  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();
    String videoSize = job.params().get(AndroidHdVideoSpec.PARAM_VIDEO_SIZE, null);
    JobType type = job.type();
    if (videoSize != null && !videoSize.matches(VIDEO_SIZE_REGEX)) {
      errors.add(
          "video_size must be width x height. width & height should be valid 4 digit number. e.g. "
              + "1280x720");
    }
    if (job.params().has(AndroidHdVideoSpec.PARAM_SCREENRECORD_TIME_LIMIT_SECONDS)) {
      Duration timeLimit =
          Duration.ofSeconds(
              job.params().getLong(AndroidHdVideoSpec.PARAM_SCREENRECORD_TIME_LIMIT_SECONDS, 0L));
      if (!timeLimit.isZero()
          && timeLimit.minusMillis(AndroidHdVideoSpec.OVERLAP_RECORDING_TIME_MS).isNegative()) {
        errors.add(
            String.format(
                "screenrecord_time_limit_seconds must be larger than %s. ",
                Duration.ofMillis(AndroidHdVideoSpec.OVERLAP_RECORDING_TIME_MS)));
      }
    }
    boolean hasMetAndroidHdVideoDecorator = false;
    if (type.getDecoratorList().contains("AndroidMonsoonDecorator")) {
      errors.add(
          "AndroidHdVideoDecorator can not work with AndroidMonsoonDecorator, please change it to "
              + "AndroidMonsoonVideoDecorator.");
    }
    for (String decoratorName : type.getDecoratorList()) {
      if (decoratorName.equals("AndroidHdVideoDecorator")) {
        hasMetAndroidHdVideoDecorator = true;
      } else if (decoratorName.equals("AndroidFilePullerDecorator")
          && !hasMetAndroidHdVideoDecorator) {
        errors.add("AndroidHdVideoDecorator must be put after AndroidFilePullerDecorator.");
        break;
      }
    }
    return errors;
  }
}
