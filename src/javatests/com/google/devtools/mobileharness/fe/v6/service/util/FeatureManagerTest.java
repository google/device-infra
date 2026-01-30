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
  @Rule public final SetFlagsOss flags = new SetFlagsOss(); // Use SetFlagsOss

  @Mock private Environment mockEnvironment;

  // No @After method needed, SetFlagsOss handles reset.

  @Test
  public void isDeviceFlashingEnabled_googleInternal_flagTrue_returnsTrue() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    flags.setAllFlags(ImmutableMap.of("fe_enable_device_flashing", "true"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceFlashingEnabled()).isTrue();
  }

  @Test
  public void isDeviceFlashingEnabled_googleInternal_flagFalse_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    flags.setAllFlags(ImmutableMap.of("fe_enable_device_flashing", "false"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceFlashingEnabled()).isFalse();
  }

  @Test
  public void isDeviceFlashingEnabled_oss_flagTrue_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(false);
    flags.setAllFlags(ImmutableMap.of("fe_enable_device_flashing", "true"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceFlashingEnabled()).isFalse();
  }

  @Test
  public void isDeviceFlashingEnabled_oss_flagFalse_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(false);
    flags.setAllFlags(ImmutableMap.of("fe_enable_device_flashing", "false"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceFlashingEnabled()).isFalse();
  }

  @Test
  public void isDeviceFlashingEnabled_flagNotSet_defaultsToFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    // Flag fe_enable_device_flashing is not set, defaults to false.
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceFlashingEnabled()).isFalse();
  }

  @Test
  public void isDeviceLogcatButtonEnabled_googleInternal_flagTrue_returnsTrue() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    flags.setAllFlags(ImmutableMap.of("fe_enable_device_logcat", "true"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceLogcatButtonEnabled()).isTrue();
  }

  @Test
  public void isDeviceLogcatButtonEnabled_googleInternal_flagFalse_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    flags.setAllFlags(ImmutableMap.of("fe_enable_device_logcat", "false"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceLogcatButtonEnabled()).isFalse();
  }

  @Test
  public void isDeviceLogcatButtonEnabled_oss_flagTrue_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(false);
    flags.setAllFlags(ImmutableMap.of("fe_enable_device_logcat", "true"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceLogcatButtonEnabled()).isFalse();
  }

  @Test
  public void isDeviceLogcatButtonEnabled_oss_flagFalse_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(false);
    flags.setAllFlags(ImmutableMap.of("fe_enable_device_logcat", "false"));
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceLogcatButtonEnabled()).isFalse();
  }

  @Test
  public void isDeviceLogcatButtonEnabled_flagNotSet_defaultsToFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    // Flag fe_enable_device_logcat is not set, defaults to false.
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceLogcatButtonEnabled()).isFalse();
  }
}
