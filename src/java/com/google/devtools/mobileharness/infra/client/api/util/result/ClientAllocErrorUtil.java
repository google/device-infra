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

package com.google.devtools.mobileharness.infra.client.api.util.result;

import com.google.devtools.mobileharness.api.model.error.ErrorId;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.proto.Test;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;

/** Checks whether a test/job has alloc error/failure based on the error code. */
public final class ClientAllocErrorUtil {

  public static boolean isJobAllocError(JobInfo jobInfo) {
    if (jobInfo.resultWithCause().get().type() == Test.TestResult.ERROR
        && jobInfo.resultWithCause().get().causeException().isPresent()) {
      ErrorId errorId = jobInfo.resultWithCause().get().causeException().get().getErrorId();
      return errorId.equals(InfraErrorId.CLIENT_JR_JOB_HAS_ALLOC_ERROR_TEST);
    }
    return false;
  }

  public static boolean isJobAllocFail(JobInfo jobInfo) {
    if (jobInfo.resultWithCause().get().type() == Test.TestResult.ERROR
        && jobInfo.resultWithCause().get().causeException().isPresent()) {
      ErrorId errorId = jobInfo.resultWithCause().get().causeException().get().getErrorId();
      return errorId.equals(InfraErrorId.CLIENT_JR_JOB_HAS_ALLOC_FAIL_TEST);
    }
    return false;
  }

  public static boolean isTestAllocError(TestInfo testInfo) {
    if (testInfo.resultWithCause().get().type() == Test.TestResult.ERROR
        && testInfo.resultWithCause().get().causeException().isPresent()) {
      ErrorId errorId = testInfo.resultWithCause().get().causeException().get().getErrorId();
      return errorId.equals(InfraErrorId.CLIENT_JR_ALLOC_INFRA_ERROR);
    }
    return false;
  }

  public static boolean isTestAllocFail(TestInfo testInfo) {
    if (testInfo.resultWithCause().get().type() == Test.TestResult.ERROR
        && testInfo.resultWithCause().get().causeException().isPresent()) {
      ErrorId errorId = testInfo.resultWithCause().get().causeException().get().getErrorId();
      return errorId.equals(InfraErrorId.CLIENT_JR_ALLOC_USER_CONFIG_ERROR);
    }
    return false;
  }

  private ClientAllocErrorUtil() {}
}
