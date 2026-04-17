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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LabServerUpdatePassThroughFlagsButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private FeatureManagerFactory mockFeatureManagerFactory;
  @Mock private FeatureManager mockFeatureManager;
  @Mock private FeatureReadiness mockFeatureReadiness;

  private LabServerUpdatePassThroughFlagsButtonBuilder labServerUpdatePassThroughFlagsButtonBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Before
  public void setUp() {
    labServerUpdatePassThroughFlagsButtonBuilder =
        new LabServerUpdatePassThroughFlagsButtonBuilder(
            mockFeatureManagerFactory, mockFeatureReadiness);
    when(mockFeatureManagerFactory.create(UNIVERSE)).thenReturn(mockFeatureManager);
  }

  @Test
  public void build_featureDisabled_returnsInvisibleAndDoesNotCheckReadiness() {
    when(mockFeatureManager.isLabServerUpdatePassThroughFlagsFeatureEnabled()).thenReturn(false);

    var result = labServerUpdatePassThroughFlagsButtonBuilder.build(UNIVERSE);

    verify(mockFeatureReadiness, never()).isLabServerUpdatePassThroughFlagsReady();
    assertThat(result.getVisible()).isFalse();
    assertThat(result.getEnabled()).isFalse();
  }

  @Test
  public void build_featureEnabled_returnsInvisibleAndChecksReadiness() {
    when(mockFeatureManager.isLabServerUpdatePassThroughFlagsFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerUpdatePassThroughFlagsReady()).thenReturn(true);

    var result = labServerUpdatePassThroughFlagsButtonBuilder.build(UNIVERSE);

    verify(mockFeatureReadiness).isLabServerUpdatePassThroughFlagsReady();
    assertThat(result.getVisible()).isFalse();
    assertThat(result.getEnabled()).isTrue();
  }
}
