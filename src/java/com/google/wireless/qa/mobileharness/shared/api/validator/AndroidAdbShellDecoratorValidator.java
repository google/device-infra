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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.qa.mobileharness.shared.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidAdbShellDecoratorSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Validator for the {@code AndroidAdbShellDecorator} driver decorator. */
public class AndroidAdbShellDecoratorValidator extends BaseValidator
    implements SpecConfigable<AndroidAdbShellDecoratorSpec> {
  private static final ImmutableSet<String> ASYNC_DECORATORS =
      ImmutableSet.of("AndroidHdVideoDecorator", "AndroidSystemHealthMemoryDecorator");
  private static final ImmutableList<String> ILLEGAL_KEYWORDS =
      ImmutableList.of("adb", "shell", "reboot", "uninstall");
  private static final ImmutableList<String> ILLEGAL_MNM_ADDITIONAL_KEYWORDS =
      ImmutableList.of("root");

  private static final Pattern ILLEGAL_REGEX =
      Pattern.compile("(^|\\s)(" + Joiner.on('|').join(ILLEGAL_KEYWORDS) + ")($|\\s)");
  private static final Pattern ILLEGAL_MNM_ADDITIONAL_REGEX =
      Pattern.compile(
          "(^|\\s)(" + Joiner.on('|').join(ILLEGAL_MNM_ADDITIONAL_KEYWORDS) + ")($|\\s)");

  @Override
  public void validateEnv(Device device) throws MobileHarnessException, InterruptedException {
    super.validateEnv(device);
  }

  @Override
  public List<String> validateJob(JobInfo job) throws InterruptedException {
    ImmutableList.Builder<String> errors = ImmutableList.builder();

    List<AndroidAdbShellDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidAdbShellDecorator"));
    } catch (com.google.devtools.mobileharness.api.model.error.MobileHarnessException e) {
      errors.add(e.getMessage());
      return errors.build();
    }

    // Checks the properties.
    for (AndroidAdbShellDecoratorSpec spec : specs) {
      List<String> commands = new ArrayList<>(2);
      if (spec.hasAdbShellBeforeTest()) {
        commands.add(spec.getAdbShellBeforeTest());
      }
      if (spec.hasAdbShellAfterTest()) {
        commands.add(spec.getAdbShellAfterTest());
      }
      if (commands.isEmpty()) {
        errors.add(
            String.format(
                "You should set either %s or %s or both if you want to use "
                    + "AndroidAdbShellDecorator",
                spec.getAdbShellBeforeTest(), spec.getAdbShellAfterTest()));
      }

      boolean isInMnMLab = isInMnMLab(job);
      for (String command : commands) {
        if (ILLEGAL_REGEX.matcher(command).find()) {
          errors.add(
              "You should not add any of "
                  + ILLEGAL_KEYWORDS
                  + " in your commands when using "
                  + "AndroidAdbShellDecorator");
          break;
        }

        if (isInMnMLab && ILLEGAL_MNM_ADDITIONAL_REGEX.matcher(command).find()) {
          errors.add(
              "You should not add any of "
                  + ILLEGAL_MNM_ADDITIONAL_KEYWORDS
                  + " in your commands when using "
                  + "AndroidAdbShellDecorator");
          break;
        }
      }
    }

    // AndroidAdbShellDecorator will restart `adbd` and break async adb command so make sure it's
    // executed before async decorators.
    if (isDecoratorOrderWrong(job)) {
      errors.add(
          "AndroidAdbShellDecorator should be executed before async decorators: "
              + ASYNC_DECORATORS
              + ". If you use decorators argument in mobile_test, it should be defined before "
              + "async decorators; If you use deprecated type argument, it should be defined after "
              + "async decorators.");
    }

    return errors.build();
  }

  private boolean isInMnMLab(JobInfo job) {

    if (job.dimensions().getAll() != null) {
      for (String key : job.dimensions().getAll().keySet()) {
        if (key.equals(Dimension.Name.POOL.name())
            && job.dimensions().getAll().get(key).equals(Dimension.Value.POOL_SHARED)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isDecoratorOrderWrong(JobInfo job) {
    if (job.type() == null) {
      return false;
    }

    boolean foundAndroidAdbShellDecorator = false;
    for (String decorator : job.type().getDecoratorList()) {
      if (decorator.equals("AndroidAdbShellDecorator")) {
        foundAndroidAdbShellDecorator = true;
      }

      if (foundAndroidAdbShellDecorator && isAsyncDecorator(decorator)) {
        return true;
      }
    }
    return false;
  }

  private boolean isAsyncDecorator(String decorator) {
    return ASYNC_DECORATORS.contains(decorator);
  }
}
