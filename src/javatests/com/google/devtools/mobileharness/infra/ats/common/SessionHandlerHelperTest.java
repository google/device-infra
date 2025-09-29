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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteVersion;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SessionHandlerHelperTest {

  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Test
  public void useTfRetry_flagIsTrue_returnTrue() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "true"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ false, "cts", TestSuiteVersion.create(14, 0, 0, 1)))
        .isTrue();
  }

  @Test
  public void useTfRetry_notAtsServerRequest_returnFalse() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ false, "cts", TestSuiteVersion.create(14, 0, 0, 1)))
        .isFalse();
  }

  @Test
  public void useTfRetry_atsServerRequest_xtsTypeNotInMap_returnTrue() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ true,
                "unknown-xts",
                TestSuiteVersion.create(14, 0, 0, 1)))
        .isTrue();
  }

  @Test
  public void useTfRetry_atsServerRequest_testSuiteVersionNull_returnFalse() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(SessionHandlerHelper.useTfRetry(/* isAtsServerRequest= */ true, "cts", null))
        .isFalse();
  }

  @Test
  public void useTfRetry_atsServerRequest_cts_versionLessThanMin_returnTrue() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ true, "cts", TestSuiteVersion.create(13, 0, 0, 1)))
        .isTrue();
  }

  @Test
  public void useTfRetry_atsServerRequest_cts_versionEqualToMin_returnFalse() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ true, "cts", TestSuiteVersion.create(14, 0, 0, 7)))
        .isFalse();
  }

  @Test
  public void useTfRetry_atsServerRequest_cts_versionGreaterThanMin_returnFalse() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ true, "cts", TestSuiteVersion.create(15, 0, 0, 1)))
        .isFalse();
  }

  @Test
  public void useTfRetry_atsServerRequest_ctsvhost_versionLessThanMin_returnTrue() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ true, "cts-v-host", TestSuiteVersion.create(15, 0, 0, 1)))
        .isTrue();
  }

  @Test
  public void useTfRetry_atsServerRequest_ctsvhost_versionEqualToMin_returnFalse() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ true, "cts-v-host", TestSuiteVersion.create(16, 0, 0, 1)))
        .isFalse();
  }

  @Test
  public void useTfRetry_atsServerRequest_gts_versionLessThanMin_returnTrue() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ true, "gts", TestSuiteVersion.create(11, 0, 0, 1)))
        .isTrue();
  }

  @Test
  public void useTfRetry_atsServerRequest_gts_versionEqualToMin_returnFalse() {
    flags.setAllFlags(ImmutableMap.of("use_tf_retry", "false"));

    assertThat(
            SessionHandlerHelper.useTfRetry(
                /* isAtsServerRequest= */ true, "gts", TestSuiteVersion.create(12, 0, 0, 1)))
        .isFalse();
  }
}
