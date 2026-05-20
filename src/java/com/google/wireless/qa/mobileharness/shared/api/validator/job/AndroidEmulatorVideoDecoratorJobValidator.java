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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidEmulatorVideoDecoratorSpec;
import java.util.List;

/** {@link JobValidator} for AndroidEmulatorVideoDecorator */
public class AndroidEmulatorVideoDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidEmulatorVideoDecoratorSpec> {

  @Override
  public ImmutableList<String> validate(JobInfo job) throws InterruptedException {
    ImmutableList.Builder<String> errorsBuilder = ImmutableList.builder();
    List<AndroidEmulatorVideoDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidEmulatorVideoDecorator"));
    } catch (MobileHarnessException ex) {
      if (ex.getMessage() != null) {
        errorsBuilder.add(ex.getMessage());
      } else {
        errorsBuilder.add("Error getting combined spec: " + ex.getErrorId());
      }
      return errorsBuilder.build();
    }
    for (AndroidEmulatorVideoDecoratorSpec spec : specs) {
      if (spec.hasFps() && spec.getFps() < 0) {
        errorsBuilder.add("Invalid fps value. Negative value supplied: " + spec.getFps());
      }
      if (spec.hasBitRate() && (spec.getBitRate() < 100000 || spec.getBitRate() > 2500000)) {
        errorsBuilder.add(
            "Invalid bit_rate value. Should be between 100000 and 2500000. Supplied: "
                + spec.getBitRate());
      }
      if (spec.hasTimeLimitSecs()
          && (spec.getTimeLimitSecs() < 0 || spec.getTimeLimitSecs() > 900 /* 15 minutes */)) {
        errorsBuilder.add(
            "Invalid time_limit_secs value. Should be between 0 and 900 (15 minutes). Supplied: "
                + spec.getTimeLimitSecs());
      }
    }
    return errorsBuilder.build();
  }
}
