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

package com.google.devtools.mobileharness.shared.util.flags;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FlagsTest {

  @After
  public void tearDown() {
    Flags.resetToDefault();
  }

  @Test
  public void parse() throws Exception {
    Flags.parse(new String[] {"--whatever_flag=hoo"});

    assertThat(Flags.instance().supplementalResDir.getNonNull()).isEmpty();
    assertThat(Flags.instance().extraAdbCommandTimeout.getNonNull()).isEqualTo(Duration.ZERO);

    Flags.parse(new String[] {"--mh_adb_command_extra_timeout=123s"});

    assertThat(Flags.instance().supplementalResDir.getNonNull()).isEmpty();
    assertThat(Flags.instance().extraAdbCommandTimeout.getNonNull())
        .isEqualTo(Duration.ofSeconds(123L));

    Flags.parse(new String[] {"--supplemental_res_dir=foo"});

    assertThat(Flags.instance().supplementalResDir.getNonNull()).isEqualTo("foo");
    assertThat(Flags.instance().extraAdbCommandTimeout.getNonNull())
        .isEqualTo(Duration.ofSeconds(123L));

    Flags.parse(new String[] {"--cache_eviction_trim_to_ratio=0.5"});
    assertThat(Flags.instance().cacheEvictionTrimToRatio.getNonNull()).isEqualTo(0.5);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            Flags.parse(
                new String[] {
                  "--reset_device_in_android_real_device_setup=true",
                  "--keep_test_harness_false=true",
                }));

    Flags.resetToDefault();

    assertThat(Flags.instance().supplementalResDir.getNonNull()).isEmpty();
    assertThat(Flags.instance().extraAdbCommandTimeout.getNonNull()).isEqualTo(Duration.ZERO);
  }
}
