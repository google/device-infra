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

package com.google.devtools.mobileharness.infra.controller.device.bootstrap;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.shared.util.flags.Flags;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AllDetectorsAndDispatchersTest {
  @Test
  public void detectorCandidatesForLabServerOss_isNotEmpty() {
    Flags.parseOss(new String[] {});
    assertThat(AllDetectorsAndDispatchers.detectorCandidatesForLabServerOss()).isNotEmpty();
  }

  @Test
  public void detectorCandidatesForLabServerOss_allFlagsEnabled_isNotEmpty() {
    Flags.parse(
        new String[] {
          "--detect_adb_device=true",
          "--enable_fastboot_detector=true",
          "--enable_evb_board_detection=true",
          "--enable_emulator_detection=true",
          "--cloud_tf_avd_device_num=1",
          "--oxygen_android_virtual_device_num=1",
          "--enable_physical_tf_avd_detection=true",
          "--android_jit_emulator_num=1",
          "--gem5_device_num=1",
          "--enable_maneki_detection=true",
        });
    assertThat(AllDetectorsAndDispatchers.detectorCandidatesForLabServerOss()).isNotEmpty();
  }
}
