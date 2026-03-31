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

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.shared.util.junit.rule.SetFlagsOss;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class FeatureManagerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final SetFlagsOss flags = new SetFlagsOss();

  @Mock private Environment mockEnvironment;

  // --- Scenario 1: Internal (1P), google_1p universe ---

  @Test
  public void isDeviceFlashingFeatureEnabled_internal_google1p_returnsTrue() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "google_1p");
    assertThat(featureManager.isDeviceFlashingFeatureEnabled()).isTrue();
  }

  @Test
  public void isConfigurationFeatureEnabled_scenario1_flagTrue_returnsTrue() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    flags.setAllFlags(ImmutableMap.of("fe_enable_configuration", "true"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "google_1p");
    assertThat(featureManager.isConfigurationFeatureEnabled()).isTrue();
  }

  @Test
  public void isConfigurationFeatureEnabled_scenario1_flagFalse_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    flags.setAllFlags(ImmutableMap.of("fe_enable_configuration", "false"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "google_1p");
    assertThat(featureManager.isConfigurationFeatureEnabled()).isFalse();
  }

  // --- Scenario 2: Internal (1P), non-google_1p universe ---

  @Test
  public void isDeviceFlashingFeatureEnabled_internal_nonGoogle1p_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "xiaomi");
    assertThat(featureManager.isDeviceFlashingFeatureEnabled()).isFalse();
  }

  @Test
  public void isConfigurationFeatureEnabled_scenario2_flagTrue_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    flags.setAllFlags(ImmutableMap.of("fe_enable_configuration", "true"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "xiaomi");
    assertThat(featureManager.isConfigurationFeatureEnabled()).isFalse();
  }

  // --- Scenario 3: OSS / ATS, Any universe ---

  @Test
  public void isDeviceFlashingFeatureEnabled_oss_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(false);
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "xiaomi");
    assertThat(featureManager.isDeviceFlashingFeatureEnabled()).isFalse();
  }

  @Test
  public void isConfigurationFeatureEnabled_scenario3_flagTrue_returnsTrue() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(false);
    flags.setAllFlags(ImmutableMap.of("fe_enable_configuration", "true"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "xiaomi");
    assertThat(featureManager.isConfigurationFeatureEnabled()).isTrue();
  }

  @Test
  public void isConfigurationFeatureEnabled_scenario3_flagFalse_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(false);
    flags.setAllFlags(ImmutableMap.of("fe_enable_configuration", "false"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "xiaomi");
    assertThat(featureManager.isConfigurationFeatureEnabled()).isFalse();
  }

  // --- Other features (Logcat, Quarantine, Screenshot) following similar logic ---

  @Test
  public void otherFeatures_internal_google1p_returnsTrue() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "google_1p");
    assertThat(featureManager.isDeviceLogcatFeatureEnabled()).isTrue();
    assertThat(featureManager.isDeviceQuarantineFeatureEnabled()).isTrue();
    assertThat(featureManager.isDeviceScreenshotFeatureEnabled()).isTrue();
  }

  @Test
  public void otherFeatures_internal_nonGoogle1p_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "xiaomi");
    assertThat(featureManager.isDeviceLogcatFeatureEnabled()).isFalse();
    assertThat(featureManager.isDeviceQuarantineFeatureEnabled()).isFalse();
    assertThat(featureManager.isDeviceScreenshotFeatureEnabled()).isFalse();
  }

  @Test
  public void otherFeatures_oss_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(false);
    FeatureManager featureManager = new FeatureManager(mockEnvironment, "google_1p");
    assertThat(featureManager.isDeviceLogcatFeatureEnabled()).isFalse();
    assertThat(featureManager.isDeviceQuarantineFeatureEnabled()).isFalse();
    assertThat(featureManager.isDeviceScreenshotFeatureEnabled()).isFalse();
  }
}
