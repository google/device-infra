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

import static com.google.protobuf.util.JavaTimeConversions.toJavaDuration;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.ApplicationIds;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidTestLoopTestSpec;
import java.time.Duration;
import java.util.List;

/** {@link JobValidator} for AndroidTestLoopTest. */
public class AndroidTestLoopTestJobValidator
    implements JobValidator, SpecConfigable<AndroidTestLoopTestSpec> {

  @Override
  public List<String> validate(JobInfo jobInfo) throws InterruptedException {
    ImmutableList.Builder<String> errorsBuilder = ImmutableList.builder();
    AndroidTestLoopTestSpec spec;

    try {
      spec = jobInfo.combinedSpec(this);
    } catch (MobileHarnessException ex) {
      if (ex.getMessage() != null) {
        errorsBuilder.add("Error getting AndroidTestLoopTestSpec: " + ex.getMessage());
      } else {
        errorsBuilder.add("Error getting AndroidTestLoopTestSpec: " + ex.getErrorId());
      }
      return errorsBuilder.build();
    }

    String appPackage = spec.getAppPackageId();
    if (appPackage.isEmpty()) {
      errorsBuilder.add("App package must be configured but is empty.");
    } else if (!ApplicationIds.isValid(appPackage)) {
      errorsBuilder.add("App package is not a valid Android package name: " + appPackage);
    }

    String scenariosStr = spec.getScenarios();
    if (scenariosStr.isEmpty()) {
      errorsBuilder.add("Scenarios list must be configured but is empty.");
    } else {
      List<String> scenarios =
          Splitter.on(',').trimResults().omitEmptyStrings().splitToList(scenariosStr);
      if (scenarios.isEmpty()) {
        errorsBuilder.add("Scenarios list must contain at least one valid scenario.");
      } else {
        for (String scenario : scenarios) {
          if (!scenario.matches("^[0-9]+$")) {
            errorsBuilder.add("Scenario is not a valid number: '" + scenario + "'");
          }
        }
      }
    }

    if (spec.hasScenariosTimeout()) {
      Duration testLoopTimeout = toJavaDuration(spec.getScenariosTimeout());
      Duration mhTestTimeout = Duration.ofMillis(jobInfo.setting().getTimeout().getTestTimeoutMs());

      if (testLoopTimeout.isZero() || testLoopTimeout.isNegative()) {
        errorsBuilder.add("Test timeout must be greater than zero.");
      } else if (testLoopTimeout.compareTo(mhTestTimeout) > 0) {
        errorsBuilder.add(
            String.format(
                "Test timeout (%s) cannot be longer than overall MH test timeout (%s).",
                testLoopTimeout, mhTestTimeout));
      }
    }

    return errorsBuilder.build();
  }
}
