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

package com.google.devtools.mobileharness.infra.lab.controller;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.shared.util.flags.core.SetFlags;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Name;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension.Value;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LabDimensionManagerTest {

  @Rule public final SetFlags flags = new SetFlags();

  private final LabDimensionManager labDimensionManager = LabDimensionManager.getInstance();

  @Before
  public void setUp() {
    labDimensionManager
        .getSupportedLocalDimensions()
        .replace(Name.OMNI_MODE_USAGE, ImmutableList.of());
    labDimensionManager.getRequiredLocalDimensions().replace(Name.POOL, ImmutableList.of());
  }

  @Test
  public void initLocalDimensionsFromFlags_multiValueOmniModeUsage() {
    flags.set("add_supported_dimension_for_omni_mode_usage", "public_testing, dda");

    labDimensionManager.initLocalDimensionsFromFlags();

    assertThat(labDimensionManager.getSupportedLocalDimensions().get(Name.OMNI_MODE_USAGE))
        .containsExactly("public_testing", "dda")
        .inOrder();
  }

  @Test
  public void initLocalDimensionsFromFlags_singleValueOmniModeUsage() {
    flags.set("add_supported_dimension_for_omni_mode_usage", "public_testing");

    labDimensionManager.initLocalDimensionsFromFlags();

    assertThat(labDimensionManager.getSupportedLocalDimensions().get(Name.OMNI_MODE_USAGE))
        .containsExactly("public_testing");
  }

  @Test
  public void initLocalDimensionsFromFlags_nullOmniModeUsage() {
    flags.set("add_supported_dimension_for_omni_mode_usage", null);

    labDimensionManager.initLocalDimensionsFromFlags();

    assertThat(labDimensionManager.getSupportedLocalDimensions().get(Name.OMNI_MODE_USAGE))
        .isEmpty();
  }

  @Test
  public void initLocalDimensionsFromFlags_partnerSharedPool() {
    flags.set("add_required_dimension_for_partner_shared_pool", "true");

    labDimensionManager.initLocalDimensionsFromFlags();

    assertThat(labDimensionManager.getRequiredLocalDimensions().get(Name.POOL))
        .containsExactly(Value.POOL_PARTNER_SHARED);
  }
}
