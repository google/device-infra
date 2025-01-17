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
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.AndroidRoboTestSpec;
import java.util.List;

/** {@link JobValidator} for AndroidRoboTest. */
public class AndroidRoboTestJobValidator
    implements JobValidator, SpecConfigable<AndroidRoboTestSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public List<String> validate(JobInfo jobInfo) throws InterruptedException {
    ImmutableList.Builder<String> errorsBuilder = ImmutableList.builder();
    jobInfo.log().atInfo().alsoTo(logger).log("\n\nRunning AndroidRoboTestJobValidator\n\n");
    AndroidRoboTestSpec spec;
    try {
      spec = jobInfo.combinedSpec(this);
      jobInfo.log().atInfo().alsoTo(logger).log("Android Robo Test Spec: %s", spec);
    } catch (MobileHarnessException ex) {
      if (ex.getMessage() != null) {
        errorsBuilder.add(ex.getMessage());
      } else {
        errorsBuilder.add("Error getting combined spec: " + ex.getErrorId());
      }
      return errorsBuilder.build();
    }
    if (spec.getAppApk().isEmpty()) {
      errorsBuilder.add("App apk cannot be empty.");
    }
    if (spec.getCrawlerApk().isEmpty()) {
      errorsBuilder.add("Crawler apk cannot be empty.");
    }
    if (spec.getCrawlerStubApk().isEmpty()) {
      errorsBuilder.add("Crawler stub apk cannot be empty.");
    }
    return errorsBuilder.build();
  }
}
