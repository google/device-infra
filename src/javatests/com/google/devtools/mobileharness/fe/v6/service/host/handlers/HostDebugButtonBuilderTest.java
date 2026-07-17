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

package com.google.devtools.mobileharness.fe.v6.service.host.handlers;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.model.proto.Lab.LabStatus;
import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class HostDebugButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FeatureManagerFactory mockFeatureManagerFactory;
  @Mock private FeatureManager mockFeatureManager;
  @Mock private FeatureReadiness mockFeatureReadiness;

  private HostDebugButtonBuilder hostDebugButtonBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();
  private static final LabInfo LAB_RUNNING =
      LabInfo.newBuilder().setLabStatus(LabStatus.LAB_RUNNING).build();
  private static final LabInfo LAB_MISSING =
      LabInfo.newBuilder().setLabStatus(LabStatus.LAB_MISSING).build();

  @Before
  public void setUp() {
    hostDebugButtonBuilder =
        new HostDebugButtonBuilder(mockFeatureManagerFactory, mockFeatureReadiness);
    when(mockFeatureManagerFactory.create(UNIVERSE)).thenReturn(mockFeatureManager);
  }

  @Test
  public void build_debugFeatureDisabled_returnsInvisible() {
    when(mockFeatureManager.isHostDebugFeatureEnabled()).thenReturn(false);

    var result = hostDebugButtonBuilder.build(UNIVERSE, Optional.empty(), Optional.empty());

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_debugFeatureEnabledNotReady_returnsComingSoon() {
    when(mockFeatureManager.isHostDebugFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isHostDebugReady()).thenReturn(false);

    var result = hostDebugButtonBuilder.build(UNIVERSE, Optional.of(LAB_RUNNING), Optional.empty());

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getIsReady()).isFalse();
    assertThat(result.getTooltip()).isEqualTo("Debug the host");
  }

  @Test
  public void build_debugFeatureEnabledReady_returnsVisibleAndEnabled() {
    when(mockFeatureManager.isHostDebugFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isHostDebugReady()).thenReturn(true);

    var result = hostDebugButtonBuilder.build(UNIVERSE, Optional.of(LAB_RUNNING), Optional.empty());

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getTooltip()).isEqualTo("Debug the host");
  }

  @Test
  public void build_debugFeatureEnabledHostMissing_returnsVisibleAndDisabled() {
    when(mockFeatureManager.isHostDebugFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isHostDebugReady()).thenReturn(true);

    var result = hostDebugButtonBuilder.build(UNIVERSE, Optional.of(LAB_MISSING), Optional.empty());

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isFalse();
  }
}
