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
import com.google.devtools.mobileharness.platform.android.shared.ApplicationIds;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.AndroidNetworkActivityLoggingDecoratorSpec;

/** Job validator for the {@code AndroidNetworkActivityLoggingDecorator}. */
final class AndroidNetworkActivityLoggingJobValidator
    implements JobValidator, SpecConfigable<AndroidNetworkActivityLoggingDecoratorSpec> {

  @Override
  public ImmutableList<String> validate(JobInfo job) throws InterruptedException {
    ImmutableList.Builder<String> errorsBuilder = ImmutableList.builder();
    AndroidNetworkActivityLoggingDecoratorSpec spec;
    try {
      spec = job.combinedSpec(this);
    } catch (MobileHarnessException ex) {
      if (ex.getMessage() != null) {
        errorsBuilder.add(ex.getMessage());
      } else {
        errorsBuilder.add("Error getting combined spec: " + ex.getErrorId());
      }
      return errorsBuilder.build();
    }
    for (String packageId : spec.getPackageIdsList()) {
      if (!ApplicationIds.isValid(packageId)) {
        errorsBuilder.add("Invalid package id: " + packageId);
      }
    }
    return errorsBuilder.build();
  }
}
