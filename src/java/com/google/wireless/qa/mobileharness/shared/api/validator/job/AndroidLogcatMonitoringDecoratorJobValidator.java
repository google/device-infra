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
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLogcatMonitoringDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLogcatMonitoringDecoratorSpec.DeviceEventConfig;
import java.util.List;

/** {@link JobValidator} for AndroidLogcatMonitoringDecorator. */
public class AndroidLogcatMonitoringDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidLogcatMonitoringDecoratorSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public ImmutableList<String> validate(JobInfo job) throws InterruptedException {
    ImmutableList.Builder<String> errorsBuilder = ImmutableList.builder();
    job.log().atInfo().alsoTo(logger).log("--- Running AndroidRoboTestJobValidator ---");
    List<AndroidLogcatMonitoringDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidLogcatMonitoringDecorator"));
    } catch (MobileHarnessException ex) {
      if (ex.getMessage() != null) {
        errorsBuilder.add(ex.getMessage());
      } else {
        errorsBuilder.add("Error getting combined spec: " + ex.getErrorId());
      }
      return errorsBuilder.build();
    }
    for (AndroidLogcatMonitoringDecoratorSpec spec : specs) {
      if (spec.getReportAsFailurePackagesList().isEmpty()) {
        errorsBuilder.add(
            "report_as_failure_packages list is empty. Either add package to monitor or remove the "
                + "decorator from the job config.");
      }

      for (DeviceEventConfig eventConfig : spec.getDeviceEventConfigList()) {
        if (eventConfig.getEventName().isEmpty()) {
          errorsBuilder.add("event_name cannot be empty in deviceEventConfig.");
        }
        if (eventConfig.getTag().isEmpty()) {
          errorsBuilder.add(
              "tag cannot be empty in deviceEventConfig with name " + eventConfig.getEventName());
        }
        if (eventConfig.getLineRegex().isEmpty()) {
          errorsBuilder.add(
              "line_regex cannot be empty in deviceEventConfig with name "
                  + eventConfig.getEventName());
        }
      }
    }
    return errorsBuilder.build();
  }
}
