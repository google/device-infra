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

import com.google.common.collect.ImmutableList;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.List;

/**
 * Validates whether a job contains the info required by the driver/decorator. Each job validator
 * class is associated with one driver/decorator class.
 */
public interface JobValidator {
  /**
   * Validates the job and tests information.
   *
   * <p>NOTE: the decorator list in JobInfo has reverse order with the list in mobile_test config.
   * Use "before" or "after" from the perspective of mobile_test, so that user won't get confused
   * with error messages.
   *
   * @param job the {@code JobInfo} to be validated
   * @return a list of error messages, or an <b>empty</b> list if no error found
   */
  default List<String> validate(JobInfo job) throws InterruptedException {
    // Does nothing.
    return ImmutableList.of();
  }
}
