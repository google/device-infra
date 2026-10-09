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

import static com.google.wireless.qa.mobileharness.shared.api.spec.AndroidFlashstationDecoratorSpec.TAG_TOOL_FILE;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.testrunner.step.android.DeviceInitializationStep;
import com.google.wireless.qa.mobileharness.shared.api.validator.util.AndroidDecoratorValidatorUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidFlashstationDecoratorSpec;
import java.util.List;

/** Job validator for the {@code AndroidFlashstationDecorator} driver decorator. */
public class AndroidFlashstationDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidFlashstationDecoratorSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  @SuppressWarnings("deprecation") // Validates deprecated use_mh_flashstation_key_if_exists field.
  public List<String> validate(JobInfo job) throws InterruptedException {
    ImmutableList.Builder<String> errors = ImmutableList.builder();

    List<AndroidFlashstationDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidFlashstationDecorator"));
    } catch (MobileHarnessException e) {
      return errors.add(e.getMessage()).build();
    }

    for (AndroidFlashstationDecoratorSpec spec : specs) {
      String buildId = spec.getBuildId();
      String branch = spec.getBranch();
      String autoota = spec.getAutootaGroup();

      if (spec.getDisableFlashstationFlash()) {
        logger.atInfo().log("Flashstation flash is disabled, skipping validation.");
        continue;
      }

      if (Strings.isNullOrEmpty(buildId)
          && Strings.isNullOrEmpty(branch)
          && Strings.isNullOrEmpty(autoota)) {
        errors.add(
            "At least one of the params build_id, branch or autoota_group must be specified");
      }

      try {
        job.files().getSingle(TAG_TOOL_FILE);
      } catch (MobileHarnessException e) {
        errors.add(
            String.format("The file %s must be specified. %s", TAG_TOOL_FILE, e.getMessage()));
      }

      if (spec.getUseMhFlashstationKeyIfExists()) {
        errors.add(
            "The shared Mobile Harness flashstation key (use_mh_flashstation_key_if_exists=true) "
                + "is no longer supported and has been disabled to ensure test stability. "
                + "Please migrate to a team-owned key.");
      }

      errors
          .addAll(
              AndroidDecoratorValidatorUtil.validateDecoratorsOrder(
                  job,
                  "AndroidFlashstationDecorator",
                  ImmutableSet.of("AndroidHdVideoDecorator", "AndroidSdVideoDecorator")))
          .addAll(
              AndroidDecoratorValidatorUtil.validateTimeoutWithinRange(
                  job,
                  DeviceInitializationStep.PARAM_DEVICE_READY_TIMEOUT_SEC,
                  SECONDS,
                  DeviceInitializationStep.MIN_DEVICE_READY_TIMEOUT,
                  DeviceInitializationStep.MAX_DEVICE_READY_TIMEOUT));

      if (spec.hasMinFlashTimeSec()) {
        long minFlashTime = spec.getMinFlashTimeSec();
        if (minFlashTime <= 0) {
          errors.add(
              "The value of param min_flash_time_sec must be a long number larger than zero: "
                  + minFlashTime);
        }
      }

      if (spec.getSkipReboot() && job.type().getDecoratorList().size() > 1) {
        errors.add(
            "The param skip_reboot is only supported when it's the only decorator in the list.");
      }
    }

    return errors.build();
  }
}
