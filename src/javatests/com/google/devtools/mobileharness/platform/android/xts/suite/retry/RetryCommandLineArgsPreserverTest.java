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

package com.google.devtools.mobileharness.platform.android.xts.suite.retry;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.infra.ats.common.proto.SessionRequestInfo;
import com.google.devtools.mobileharness.infra.ats.common.proto.XtsCommonProto.RetryType;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RetryCommandLineArgsPreserverTest {

  private static final SessionRequestInfo RETRY =
      SessionRequestInfo.newBuilder()
          .setTestPlan("retry")
          .setCommandLineArgs("retry --retry 0")
          .setXtsRootDir("/xts_root")
          .setXtsType("cts")
          .setRetrySessionIndex(0)
          .setRetryType(RetryType.FAILED)
          .build();

  private static SessionRequestInfo preserve(SessionRequestInfo retry, String previousCommand) {
    return RetryCommandLineArgsPreserver.preservePreviousSessionArgs(
        retry, Optional.of(previousCommand));
  }

  @Test
  public void preservePreviousSessionArgs_preservesNonDenylistedArgs() {
    SessionRequestInfo result =
        preserve(
            RETRY,
            "cts --module-arg ModuleA:arg:value --module-arg \"ModuleA[instant]:arg:a b\""
                + " --test-arg foo:bar:baz --business-logic-url https://bl.example.com"
                + " --ignore-business-logic-failure --report-system-checkers"
                + " --enable-token-sharding --skip-device-info false");

    assertThat(result.getModuleArgsList())
        .containsExactly("ModuleA:arg:value", "ModuleA[instant]:arg:a b")
        .inOrder();
    assertThat(result.getExtraArgsList()).containsExactly("--test-arg", "foo:bar:baz").inOrder();
    assertThat(result.getBusinessLogicUrl()).isEqualTo("https://bl.example.com");
    assertThat(result.getIgnoreBusinessLogicFailure()).isTrue();
    assertThat(result.getReportSystemCheckers()).isTrue();
    assertThat(result.getEnableTokenSharding()).isTrue();
    assertThat(result.hasSkipDeviceInfo()).isTrue();
    assertThat(result.getSkipDeviceInfo()).isFalse();
  }

  @Test
  public void preservePreviousSessionArgs_doesNotPreserveDenylistedArgs() {
    SessionRequestInfo result =
        preserve(
            RETRY,
            "cts -m ModuleA -t Class#test --include-filter \"ModuleB Class#test\""
                + " --exclude-filter ModuleC --strict-include-filter ModuleD"
                + " --module-metadata-include-filter component foo"
                + " --module-metadata-exclude-filter component bar --exclude-runner MoblyTest"
                + " --shard-count 4 --sharding-mode MODULE --retry-type NOT_EXECUTED");

    assertThat(result).isEqualTo(RETRY);
  }

  @Test
  public void preservePreviousSessionArgs_retryModuleArgsOverridePreviousOnesWithSameKey() {
    SessionRequestInfo retry =
        RETRY.toBuilder()
            .addModuleArgs("ModuleA:arg1:new")
            .addModuleArgs("ModuleA:arg3:key1:=new")
            .build();

    SessionRequestInfo result =
        preserve(
            retry,
            "cts --module-arg ModuleA:arg1:old --module-arg ModuleA:arg2:value"
                + " --module-arg ModuleA:arg3:key1:=old --module-arg ModuleA:arg3:key2:=value");

    assertThat(result.getModuleArgsList())
        .containsExactly(
            "ModuleA:arg2:value",
            "ModuleA:arg3:key2:=value",
            "ModuleA:arg1:new",
            "ModuleA:arg3:key1:=new")
        .inOrder();
  }

  @Test
  public void preservePreviousSessionArgs_retryExtraArgsAndValuesTakePrecedence() {
    SessionRequestInfo retry =
        RETRY.toBuilder()
            .addExtraArgs("--max-testcase-run-count")
            .addExtraArgs("3")
            .setBusinessLogicUrl("https://retry.example.com")
            .setSkipDeviceInfo(false)
            .build();

    SessionRequestInfo result =
        preserve(
            retry,
            "cts --max-testcase-run-count 2 --business-logic-url https://bl.example.com"
                + " --skip-device-info true");

    assertThat(result.getExtraArgsList())
        .containsExactly("--max-testcase-run-count", "2", "--max-testcase-run-count", "3")
        .inOrder();
    assertThat(result.getBusinessLogicUrl()).isEqualTo("https://retry.example.com");
    assertThat(result.getSkipDeviceInfo()).isFalse();
  }

  @Test
  public void preservePreviousSessionArgs_identicalExtraArgs_notDuplicated() {
    SessionRequestInfo retry =
        RETRY.toBuilder().addExtraArgs("--test-arg").addExtraArgs("foo:bar:baz").build();

    SessionRequestInfo result = preserve(retry, "cts --test-arg foo:bar:baz");

    assertThat(result.getExtraArgsList()).containsExactly("--test-arg", "foo:bar:baz").inOrder();
  }

  @Test
  public void preservePreviousSessionArgs_nothingToPreserve_returnsRetryUnchanged() {
    SessionRequestInfo retry = RETRY.toBuilder().addModuleArgs("ModuleA:arg:value").build();

    assertThat(RetryCommandLineArgsPreserver.preservePreviousSessionArgs(retry, Optional.empty()))
        .isSameInstanceAs(retry);
    assertThat(preserve(retry, "  ")).isSameInstanceAs(retry);
    // Same command line, e.g. the ATS server re-runs the original command as a retry.
    assertThat(preserve(retry, "retry --retry 0")).isSameInstanceAs(retry);
    // Unparsable command lines.
    assertThat(preserve(retry, "cts --shard-count \"unterminated")).isSameInstanceAs(retry);
    assertThat(preserve(retry, "cts --module-arg invalid")).isSameInstanceAs(retry);
  }
}
