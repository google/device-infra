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

package com.google.devtools.mobileharness.fe.v6.service.device.handlers;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.DeviceInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.common.ActionButtonState;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class ConfigurationButtonBuilderTest {
  private static final String UNIVERSE = "google_1p";

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private FeatureManagerFactory featureManagerFactory;
  @Mock private FeatureManager featureManager;

  private ConfigurationButtonBuilder configurationButtonBuilder;

  @Before
  public void setUp() {
    configurationButtonBuilder = new ConfigurationButtonBuilder(featureManagerFactory);
    when(featureManagerFactory.create(UNIVERSE)).thenReturn(featureManager);
  }

  @Test
  public void build_configurationDisabled_invisible() {
    when(featureManager.isConfigurationFeatureEnabled()).thenReturn(false);

    assertThat(
            configurationButtonBuilder
                .build(DeviceInfo.getDefaultInstance(), UNIVERSE)
                .getVisible())
        .isFalse();
  }

  @Test
  public void build_configurationEnabled_visibleEnabledWithTooltip() {
    when(featureManager.isConfigurationFeatureEnabled()).thenReturn(true);

    ActionButtonState state =
        configurationButtonBuilder.build(DeviceInfo.getDefaultInstance(), UNIVERSE);

    assertThat(state.getVisible()).isTrue();
    assertThat(state.getEnabled()).isTrue();
    assertThat(state.getTooltip()).isEqualTo("Configure the device");
  }
}
