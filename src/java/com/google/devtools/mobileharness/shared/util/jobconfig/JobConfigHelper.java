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

package com.google.devtools.mobileharness.shared.util.jobconfig;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.JobUser;
import com.google.devtools.mobileharness.shared.util.sharedpool.SharedPoolJobUtil;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Timeout;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import java.time.Duration;

/** JobConfigHelper is a utility class to help finalize a JobConfig */
final class JobConfigHelper {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** */
  public static Timeout.Builder finalizeTimeout(JobConfig jobConfig) {
    Duration finalizedJobTimeout = Duration.ofSeconds(jobConfig.getJobTimeoutSec());
    Duration finalizedTestTimeout = Duration.ofSeconds(jobConfig.getTestTimeoutSec());
    Duration finalizedStartTimeout = Duration.ofSeconds(jobConfig.getStartTimeoutSec());
    Timeout.Builder timeout = Timeout.newBuilder();

    timeout.setJobTimeoutMs(finalizedJobTimeout.toMillis());
    timeout.setTestTimeoutMs(finalizedTestTimeout.toMillis());
    timeout.setStartTimeoutMs(finalizedStartTimeout.toMillis());

    // Rewrites start timeout in M&M if necessary. see http://b/135557356 for more details.
    return SharedPoolJobUtil.maybeExtendStartTimeout(timeout.build(), jobConfig).toBuilder();
  }

  public static JobUser finalizeUser(JobConfig jobConfig) throws MobileHarnessException {
    return JobUser.newBuilder()
        .setRunAs(jobConfig.getRunAs())
        .setActualUser(jobConfig.getRunAs())
        .build();
  }

  private JobConfigHelper() {}
}
