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
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidLabTestSupportSettingsDecoratorSpec;
import java.util.List;

/** Job validator for AndroidLabTestSupportSettingsDecorator. */
public class AndroidLabTestSupportSettingsDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidLabTestSupportSettingsDecoratorSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public ImmutableList<String> validate(JobInfo job) throws InterruptedException {
    ImmutableList.Builder<String> errorsBuilder = ImmutableList.builder();
    job.log()
        .atInfo()
        .alsoTo(logger)
        .log("--- Running AndroidLabTestSupportSettingsDecoratorJobValidator ---");
    List<AndroidLabTestSupportSettingsDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec
                      .decorators()
                      .getAll()
                      .contains("AndroidLabTestSupportSettingsDecorator"));
    } catch (MobileHarnessException ex) {
      if (ex.getMessage() != null) {
        errorsBuilder.add(ex.getMessage());
      } else {
        errorsBuilder.add("Error getting combined spec: " + ex.getErrorId());
      }
      return errorsBuilder.build();
    }
    for (var spec : specs) {
      if (!(spec.hasSignatureMasqueradingPackageId() == spec.hasSignatureFile())) {
        job.log()
            .atInfo()
            .alsoTo(logger)
            .log("AndroidLabTestSupportSettingsDecoratorSpec : %s", spec);
        errorsBuilder.add(
            "Signature masquerading needs both the signature_masquerading_package_id and"
                + " signature_file set.");
      }
    }
    return errorsBuilder.build();
  }
}
