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

package com.google.devtools.mobileharness.shared.util.system;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SystemUtilTest {

  private final SystemUtil systemUtil = new SystemUtil();

  @Test
  public void getProcessStartTime() {
    Instant processStartTime = systemUtil.getProcessStartTime().orElseThrow();

    assertThat(processStartTime).isGreaterThan(Instant.EPOCH);
    assertThat(processStartTime).isAtMost(Instant.now());
  }

  @Test
  public void getProcessUptime() {
    Duration processUptime = systemUtil.getProcessUptime().orElseThrow();

    assertThat(processUptime).isAtLeast(Duration.ZERO);
    assertThat(systemUtil.getProcessUptime().orElseThrow()).isAtLeast(processUptime);
  }
}
