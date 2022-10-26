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

package com.google.wireless.qa.mobileharness.shared.api.validator;

import com.google.common.collect.ImmutableList;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.List;

/** Base class for all validators. */
public abstract class BaseValidator implements Validator {

  @Override
  public void validateEnv(Device device) throws MobileHarnessException, InterruptedException {
    // Does nothing.
  }

  @Override
  public List<String> validateJob(JobInfo jobInfo) throws InterruptedException {
    // Does nothing.
    return ImmutableList.of();
  }
}
