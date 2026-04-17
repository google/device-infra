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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.host.util.HostActionButtonCreator;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManager;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureManagerFactory;
import com.google.devtools.mobileharness.fe.v6.service.util.FeatureReadiness;
import com.google.devtools.mobileharness.fe.v6.service.util.UniverseScope;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class LabServerDeployButtonBuilderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();

  @Mock private HostActionButtonCreator mockHostActionButtonCreator;
  @Mock private FeatureManagerFactory mockFeatureManagerFactory;
  @Mock private FeatureManager mockFeatureManager;
  @Mock private FeatureReadiness mockFeatureReadiness;

  private LabServerDeployButtonBuilder labServerDeployButtonBuilder;
  private static final UniverseScope UNIVERSE = new UniverseScope.SelfUniverse();

  @Before
  public void setUp() {
    labServerDeployButtonBuilder =
        new LabServerDeployButtonBuilder(
            mockHostActionButtonCreator, mockFeatureManagerFactory, mockFeatureReadiness);
    when(mockFeatureManagerFactory.create(UNIVERSE)).thenReturn(mockFeatureManager);
  }

  @Test
  public void build_verifiesSupplierOnLine40_returnsFalseForFusionLab() {
    when(mockFeatureManager.isLabServerDeployFeatureEnabled()).thenReturn(true);
    when(mockFeatureReadiness.isLabServerDeployReady()).thenReturn(true);

    var unused =
        labServerDeployButtonBuilder.build(UNIVERSE, Optional.empty(), Optional.of("FUSION_LAB"));

    ArgumentCaptor<BooleanSupplier> enabledSupplierCaptor =
        ArgumentCaptor.forClass(BooleanSupplier.class);

    verify(mockHostActionButtonCreator)
        .buildButton(
            any(LabInfo.class),
            eq("FUSION_LAB"),
            any(BooleanSupplier.class),
            any(BooleanSupplier.class),
            enabledSupplierCaptor.capture(),
            eq("Deploy the lab server"));

    assertThat(enabledSupplierCaptor.getValue().getAsBoolean()).isFalse();
  }
}
