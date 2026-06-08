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
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class FeatureReadinessTest {

  private FeatureReadiness featureReadiness;

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Environment environment;

  @Before
  public void setUp() {
    featureReadiness = new FeatureReadiness(environment);
  }

  @Test
  public void isDeviceFlashingReady_returnsFalse() {
    assertThat(featureReadiness.isDeviceFlashingReady()).isFalse();
  }

  @Test
  public void isDeviceLogcatReady_returnsFalse() {
    assertThat(featureReadiness.isDeviceLogcatReady()).isFalse();
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
  public void isDeviceRemoteControlReady_returnsFalse() {
    assertThat(featureReadiness.isDeviceRemoteControlReady()).isFalse();
  }

  @Test
  public void isHostDebugReady_returnsFalse() {
    assertThat(featureReadiness.isHostDebugReady()).isFalse();
  }

  @Test
  public void isHostDecommissionReady_returnsFalse() {
    assertThat(featureReadiness.isHostDecommissionReady()).isFalse();
  }

  @Test
  public void isLabServerStartReady_returnsFalse() {
    assertThat(featureReadiness.isLabServerStartReady()).isFalse();
  }

  @Test
  public void isLabServerRestartReady_returnsFalse() {
    assertThat(featureReadiness.isLabServerRestartReady()).isFalse();
  }

  @Test
  public void isLabServerStopReady_returnsFalse() {
    assertThat(featureReadiness.isLabServerStopReady()).isFalse();
  }

  @Test
  public void isLabServerUpdatePassThroughFlagsReady_returnsFalse() {
    assertThat(featureReadiness.isLabServerUpdatePassThroughFlagsReady()).isFalse();
  }

  @Test
  public void isHostConfigurationReady_internal_returnsFalse() {
    when(environment.isGoogleInternal()).thenReturn(true);
    assertThat(featureReadiness.isHostConfigurationReady()).isFalse();
  }

  @Test
  public void isHostConfigurationReady_oss_returnsTrue() {
    when(environment.isGoogleInternal()).thenReturn(false);
    assertThat(featureReadiness.isHostConfigurationReady()).isTrue();
  }

  @Test
  public void isDeviceConfigurationReady_internal_returnsFalse() {
    when(environment.isGoogleInternal()).thenReturn(true);
    assertThat(featureReadiness.isDeviceConfigurationReady()).isFalse();
  }

  @Test
  public void isDeviceConfigurationReady_oss_returnsTrue() {
    when(environment.isGoogleInternal()).thenReturn(false);
    assertThat(featureReadiness.isDeviceConfigurationReady()).isTrue();
  }

  @Test
  public void isLabServerReleaseReady_returnsFalse() {
    assertThat(featureReadiness.isLabServerReleaseReady()).isFalse();
  }

  @Test
  public void isDeviceDecommissionReady_returnsFalse() {
    assertThat(featureReadiness.isDeviceDecommissionReady()).isFalse();
  }
}
