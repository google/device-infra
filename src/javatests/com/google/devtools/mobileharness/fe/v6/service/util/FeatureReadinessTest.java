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

package com.google.devtools.mobileharness.fe.v6.service.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FeatureReadinessTest {

  private FeatureReadiness featureReadiness;

  @Before
  public void setUp() {
    featureReadiness = new FeatureReadiness();
  }

  @Test
  public void isDeviceFlashingReady_returnsTrue() {
    assertThat(featureReadiness.isDeviceFlashingReady()).isTrue();
  }

  @Test
  public void isDeviceLogcatReady_returnsTrue() {
    assertThat(featureReadiness.isDeviceLogcatReady()).isTrue();
  }

  @Test
  public void isDeviceQuarantineReady_returnsTrue() {
    assertThat(featureReadiness.isDeviceQuarantineReady()).isTrue();
  }

  @Test
  public void isDeviceScreenshotReady_returnsTrue() {
    assertThat(featureReadiness.isDeviceScreenshotReady()).isTrue();
  }

  @Test
  public void isDeviceRemoteControlReady_returnsTrue() {
    assertThat(featureReadiness.isDeviceRemoteControlReady()).isTrue();
  }

  @Test
  public void isHostDebugReady_returnsFalse() {
    assertThat(featureReadiness.isHostDebugReady()).isTrue();
  }

  @Test
  public void isHostDecommissionReady_returnsFalse() {
    assertThat(featureReadiness.isHostDecommissionReady()).isTrue();
  }

  @Test
  public void isLabServerStartReady_returnsTrue() {
    assertThat(featureReadiness.isLabServerStartReady()).isTrue();
  }

  @Test
  public void isLabServerRestartReady_returnsTrue() {
    assertThat(featureReadiness.isLabServerRestartReady()).isTrue();
  }

  @Test
  public void isLabServerStopReady_returnsTrue() {
    assertThat(featureReadiness.isLabServerStopReady()).isTrue();
  }

  @Test
  public void isLabServerUpdatePassThroughFlagsReady_returnsTrue() {
    assertThat(featureReadiness.isLabServerUpdatePassThroughFlagsReady()).isTrue();
  }

  @Test
  public void isHostConfigurationReady_returnsTrue() {
    assertThat(featureReadiness.isHostConfigurationReady()).isTrue();
  }

  @Test
  public void isDeviceConfigurationReady_returnsTrue() {
    assertThat(featureReadiness.isDeviceConfigurationReady()).isTrue();
  }

  @Test
  public void isLabServerReleaseReady_returnsTrue() {
    assertThat(featureReadiness.isLabServerReleaseReady()).isTrue();
  }

  @Test
  public void isDeviceDecommissionReady_returnsTrue() {
    assertThat(featureReadiness.isDeviceDecommissionReady()).isTrue();
  }

  @Test
  public void isAdvancedOperationsReady_returnsFalse() {
    assertThat(featureReadiness.isAdvancedOperationsReady()).isFalse();
  }
}
