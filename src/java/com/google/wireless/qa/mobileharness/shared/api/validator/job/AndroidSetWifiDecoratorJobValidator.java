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

import com.google.common.base.Strings;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.spec.AndroidSetWifiDecoratorSpec;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;

/** Job validator for the {@code AndroidSetWifiDecorator} driver decorator. */
public class AndroidSetWifiDecoratorJobValidator implements JobValidator {

  @Override
  public List<String> validate(JobInfo job) {
    List<String> errors = new ArrayList<>();
    boolean useDefaultSsid =
        job.params().isTrue(AndroidSetWifiDecoratorSpec.PARAM_USE_DEFAULT_SSID);
    boolean wifiSsidOptional =
        job.params().getBool(AndroidSetWifiDecoratorSpec.PARAM_WIFI_SSID_OPTIONAL, false);

    if (useDefaultSsid) {
      if (!Strings.isNullOrEmpty(job.params().get(AndroidSetWifiDecoratorSpec.PARAM_WIFI_SSID))) {
        errors.add("Please leave wifi_ssid empty when use_default_ssid is true.");
      }
    } else {
      if (!wifiSsidOptional) {
        try {
          job.params().checkExist(AndroidSetWifiDecoratorSpec.PARAM_WIFI_SSID);
        } catch (MobileHarnessException e) {
          errors.add(e.getMessage());
        }
      }
    }
    if (job.params().has(AndroidSetWifiDecoratorSpec.PARAM_WIFI_RETRY_NUM)) {
      try {
        job.params().checkInt(AndroidSetWifiDecoratorSpec.PARAM_WIFI_RETRY_NUM, 0, 5);
      } catch (MobileHarnessException e) {
        errors.add("Please set wifi_retry_num within [0, 5].");
      }
    }
    return errors;
  }
}
