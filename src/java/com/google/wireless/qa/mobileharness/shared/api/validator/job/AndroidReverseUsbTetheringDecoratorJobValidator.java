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

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpecs;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidReverseUsbTetheringDecoratorSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Job validator for the {@code AndroidReverseUsbTetheringDecorator} driver decorator. */
public class AndroidReverseUsbTetheringDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidReverseUsbTetheringDecoratorSpec> {

  private static final String REVERSE_USB_TETHERING_DECORATOR =
      "AndroidReverseUsbTetheringDecorator";
  private static final String SET_WIFI_DECORATOR = "AndroidSetWifiDecorator";

  private static final Pattern VALID_CHECK_HOST_PATTERN =
      Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?$");

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();

    SubDeviceSpecs subDeviceSpecs = job.subDeviceSpecs();
    if (subDeviceSpecs != null) {
      for (SubDeviceSpec subDeviceSpec : subDeviceSpecs.getAllSubDevices()) {
        List<String> decorators = subDeviceSpec.decorators().getAll();
        if (decorators.contains(REVERSE_USB_TETHERING_DECORATOR)
            && decorators.contains(SET_WIFI_DECORATOR)) {
          errors.add(
              "AndroidReverseUsbTetheringDecorator and AndroidSetWifiDecorator cannot both be"
                  + " configured on the same device.");
        }
      }
    }

    List<AndroidReverseUsbTetheringDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains(REVERSE_USB_TETHERING_DECORATOR));
    } catch (MobileHarnessException ex) {
      errors.add("Error getting combined spec: " + ex.getErrorId());
      return errors;
    }

    for (AndroidReverseUsbTetheringDecoratorSpec spec : specs) {
      if (spec.getConnectivityTimeoutSec() <= 0) {
        errors.add("Please set connectivity_timeout_sec to a positive integer.");
      }
      if (spec.hasConnectivityCheckHost()
          && !spec.getConnectivityCheckHost().isEmpty()
          && !VALID_CHECK_HOST_PATTERN.matcher(spec.getConnectivityCheckHost()).matches()) {
        errors.add(
            "Invalid connectivity_check_host: only ASCII letters, digits, '.', and '-' are"
                + " allowed, and it must start and end with a letter or digit.");
      }
    }
    return errors;
  }
}
