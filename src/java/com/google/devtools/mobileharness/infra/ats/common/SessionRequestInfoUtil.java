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

package com.google.devtools.mobileharness.infra.ats.common;

import com.google.common.base.Preconditions;
import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;

/** Utility for validating and helper logic of {@link SessionRequestInfo}. */
public final class SessionRequestInfoUtil {

  private SessionRequestInfoUtil() {}

  /**
   * Validates retry constraints for session requests.
   *
   * @throws IllegalStateException if the retry requirements are not met.
   */
  public static void validate(SessionRequestInfo sessionRequestInfo) {
    if (sessionRequestInfo.getTestPlan().equals("retry")) {
      Preconditions.checkState(
          sessionRequestInfo.hasRetrySessionIndex()
              || sessionRequestInfo.hasRetrySessionResultDirName()
              || sessionRequestInfo.hasRetrySessionId());
      if (sessionRequestInfo.hasRetrySessionId()) {
        Preconditions.checkState(sessionRequestInfo.hasRetryResultDir());
      }
    }
  }

  /**
   * Builds and validates a {@link SessionRequestInfo} instance.
   *
   * @throws IllegalStateException if the retry requirements are not met.
   */
  public static SessionRequestInfo buildAndValidate(SessionRequestInfo.Builder builder) {
    SessionRequestInfo requestInfo = builder.build();
    validate(requestInfo);
    return requestInfo;
  }
}
