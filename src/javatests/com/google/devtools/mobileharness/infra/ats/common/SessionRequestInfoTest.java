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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SessionRequestInfoTest {

  @Test
  public void withRetrySessionResultDirName_retrySessionIndexCleared() {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("test_plan")
            .setCommandLineArgs("command_line_args")
            .setXtsRootDir("xts_root_dir")
            .setXtsType("xts_type")
            .setRetrySessionIndex(0)
            .build();

    assertThat(sessionRequestInfo.retrySessionIndex()).hasValue(0);

    SessionRequestInfo updatedSessionRequestInfo =
        sessionRequestInfo.withRetrySessionResultDirName("retry_session_result_dir_name");

    assertThat(updatedSessionRequestInfo.retrySessionIndex()).isEmpty();
    assertThat(updatedSessionRequestInfo)
        .isEqualTo(
            SessionRequestInfo.builder()
                .setTestPlan("test_plan")
                .setCommandLineArgs("command_line_args")
                .setXtsRootDir("xts_root_dir")
                .setXtsType("xts_type")
                .setRetrySessionResultDirName("retry_session_result_dir_name")
                .build());
  }

  @Test
  public void withRetrySessionResultDirName_noRetrySessionIndexSetBefore_retrySessionIndexNotSet() {
    SessionRequestInfo sessionRequestInfo =
        SessionRequestInfo.builder()
            .setTestPlan("test_plan")
            .setCommandLineArgs("command_line_args")
            .setXtsRootDir("xts_root_dir")
            .setXtsType("xts_type")
            .build();

    assertThat(sessionRequestInfo.retrySessionIndex()).isEmpty();

    SessionRequestInfo updatedSessionRequestInfo =
        sessionRequestInfo.withRetrySessionResultDirName("retry_session_result_dir_name");

    assertThat(updatedSessionRequestInfo.retrySessionIndex()).isEmpty();
    assertThat(updatedSessionRequestInfo)
        .isEqualTo(
            SessionRequestInfo.builder()
                .setTestPlan("test_plan")
                .setCommandLineArgs("command_line_args")
                .setXtsRootDir("xts_root_dir")
                .setXtsType("xts_type")
                .setRetrySessionResultDirName("retry_session_result_dir_name")
                .build());
  }
}
