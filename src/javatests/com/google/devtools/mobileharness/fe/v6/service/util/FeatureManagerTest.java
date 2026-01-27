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

  @Mock private Environment mockEnvironment;

  @Test
  public void isDeviceFlashingEnabled_googleInternal_returnsTrue() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(true);
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceFlashingEnabled()).isTrue();
  }

  @Test
  public void isDeviceFlashingEnabled_oss_returnsFalse() {
    when(mockEnvironment.isGoogleInternal()).thenReturn(false);
    FeatureManager featureManager = new FeatureManager(mockEnvironment);
    assertThat(featureManager.isDeviceFlashingEnabled()).isFalse();
  }
}
