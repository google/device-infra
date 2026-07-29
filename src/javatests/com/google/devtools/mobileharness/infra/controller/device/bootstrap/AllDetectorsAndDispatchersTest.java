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

import com.google.devtools.mobileharness.api.devicemanager.detector.AndroidJitEmulatorDetector;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AllDetectorsAndDispatchersTest {
  @Rule public final SetFlags flags = new SetFlags();

  @Test
  public void detectorCandidatesForLabServerOss_isNotEmpty() {
    assertThat(AllDetectorsAndDispatchers.detectorCandidatesForLabServerOss()).isNotEmpty();
  }

  @Test
  public void detectorCandidatesForLabServerOss_withLocalJitEmulator_containsJitDetector() {
    flags.set("enable_emulator_detection", "true");
    flags.set("android_jit_emulator_num", "1");

    var detectors = AllDetectorsAndDispatchers.detectorCandidatesForLabServerOss();
    boolean hasJitDetector =
        detectors.stream().anyMatch(d -> d instanceof AndroidJitEmulatorDetector);
    assertThat(hasJitDetector).isTrue();
  }

  @Test
  public void detectorCandidatesForLabServerOss_withRemoteJitEmulator_containsJitDetector() {
    flags.set("enable_emulator_detection", "true");
    flags.set("remote_android_jit_emulator_num", "1");

    var detectors = AllDetectorsAndDispatchers.detectorCandidatesForLabServerOss();
    boolean hasJitDetector =
        detectors.stream().anyMatch(d -> d instanceof AndroidJitEmulatorDetector);
    assertThat(hasJitDetector).isTrue();
  }

  @Test
  public void detectorCandidatesForLabServerOss_withoutJitEmulator_doesNotContainJitDetector() {
    flags.set("enable_emulator_detection", "true");
    flags.set("android_jit_emulator_num", "0");
    flags.set("remote_android_jit_emulator_num", "0");

    var detectors = AllDetectorsAndDispatchers.detectorCandidatesForLabServerOss();
    boolean hasJitDetector =
        detectors.stream().anyMatch(d -> d instanceof AndroidJitEmulatorDetector);
    assertThat(hasJitDetector).isFalse();
  }
}
