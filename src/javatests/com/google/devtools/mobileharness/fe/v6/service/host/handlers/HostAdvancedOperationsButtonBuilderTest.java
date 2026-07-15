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

import com.google.devtools.mobileharness.fe.v6.service.proto.host.HostConnectivityStatus;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
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
public final class HostAdvancedOperationsButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FeatureReadiness mockFeatureReadiness;

  private HostAdvancedOperationsButtonBuilder builder;

  private static final Optional<String> FUSION_LAB_TYPE = Optional.of("FUSION_LAB");
  private static final Optional<String> SATELLITE_LAB_TYPE = Optional.of("MH_SATELLITE_LAB");

  private static final HostConnectivityStatus RUNNING_CONNECTIVITY =
      HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.RUNNING).build();
  private static final HostConnectivityStatus MISSING_CONNECTIVITY =
      HostConnectivityStatus.newBuilder().setState(HostConnectivityStatus.State.MISSING).build();

  @Before
  public void setUp() {
    builder = new HostAdvancedOperationsButtonBuilder(mockFeatureReadiness);
  }

  @Test
  public void build_nonFusionHost_returnsInvisible() {
    var result = builder.build(Optional.empty(), SATELLITE_LAB_TYPE, RUNNING_CONNECTIVITY);

    assertThat(result.getVisible()).isFalse();
  }

  @Test
  public void build_fusionHostRunningAndReady_returnsVisibleAndEnabled() {
    when(mockFeatureReadiness.isAdvancedOperationsReady()).thenReturn(true);

    var result = builder.build(Optional.empty(), FUSION_LAB_TYPE, RUNNING_CONNECTIVITY);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getIsReady()).isTrue();
    assertThat(result.getTooltip())
        .isEqualTo("Advanced operations and diagnostics for Fusion hosts");
  }

  @Test
  public void build_fusionHostNotRunning_returnsVisibleButDisabled() {
    when(mockFeatureReadiness.isAdvancedOperationsReady()).thenReturn(true);

    var result = builder.build(Optional.empty(), FUSION_LAB_TYPE, MISSING_CONNECTIVITY);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isFalse();
    assertThat(result.getTooltip())
        .isEqualTo("Advanced operations are only available when the lab server is Running.");
  }

  @Test
  public void build_fusionHostRunningNotReady_returnsComingSoon() {
    when(mockFeatureReadiness.isAdvancedOperationsReady()).thenReturn(false);

    var result = builder.build(Optional.empty(), FUSION_LAB_TYPE, RUNNING_CONNECTIVITY);

    assertThat(result.getVisible()).isTrue();
    assertThat(result.getEnabled()).isTrue();
    assertThat(result.getIsReady()).isFalse();
  }
}
