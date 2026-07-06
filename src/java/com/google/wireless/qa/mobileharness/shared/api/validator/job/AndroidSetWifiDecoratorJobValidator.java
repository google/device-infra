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
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidSetWifiDecoratorSpec;
import java.util.ArrayList;
import java.util.List;

/** Job validator for the {@code AndroidSetWifiDecorator} driver decorator. */
public class AndroidSetWifiDecoratorJobValidator
    implements JobValidator, SpecConfigable<AndroidSetWifiDecoratorSpec> {

  @Override
  public List<String> validate(JobInfo job) throws InterruptedException {
    List<String> errors = new ArrayList<>();
    List<AndroidSetWifiDecoratorSpec> specs;
    try {
      specs =
          job.combinedSpecForDevices(
              this,
              subDeviceSpec ->
                  subDeviceSpec.decorators().getAll().contains("AndroidSetWifiDecorator"));
    } catch (MobileHarnessException ex) {
      errors.add("Error getting combined spec: " + ex.getErrorId());
      return errors;
    }
    for (AndroidSetWifiDecoratorSpec spec : specs) {
      boolean useDefaultSsid = spec.getUseDefaultSsid();
      boolean wifiSsidOptional = spec.getWifiSsidOptional();

      if (useDefaultSsid) {
        if (!spec.getWifiSsid().isEmpty()) {
          errors.add("Please leave wifi_ssid empty when use_default_ssid is true.");
        }
      } else {
        if (!wifiSsidOptional && spec.getWifiSsid().isEmpty()) {
          errors.add("Param \"wifi_ssid\" is not found or empty.");
        }
      }
      if (spec.hasWifiRetryNum()) {
        int retryNum = spec.getWifiRetryNum();
        if (retryNum < 0 || retryNum > 5) {
          errors.add("Please set wifi_retry_num within [0, 5].");
        }
      }
    }
    return errors;
  }
}
