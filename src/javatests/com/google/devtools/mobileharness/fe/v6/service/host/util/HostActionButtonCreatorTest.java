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

package com.google.devtools.mobileharness.fe.v6.service.host.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HostActionButtonCreatorTest {

  private HostActionButtonCreator hostActionButtonCreator;
  private static final LabInfo LAB_INFO = LabInfo.getDefaultInstance();

  @Before
  public void setUp() {
    hostActionButtonCreator = new HostActionButtonCreator();
  }

  @Test
  public void buildButton_notVisible_returnsInvisibleState() {
    BooleanSupplier visibleSupplier = () -> false;

    ActionButtonState state =
        hostActionButtonCreator.buildButton(
            LAB_INFO, "test_type", visibleSupplier, () -> true, () -> true, "test_tooltip");

    assertThat(state.getVisible()).isFalse();
  }

  @Test
  public void buildButton_visible_returnsVisibleState() {
    BooleanSupplier visibleSupplier = () -> true;

    ActionButtonState state =
        hostActionButtonCreator.buildButton(
            LAB_INFO, "test_type", visibleSupplier, () -> true, () -> true, "test_tooltip");

    assertThat(state.getVisible()).isTrue();
  }

  @Test
  public void buildButton_isCoreLab_returnsInvisible() {
    BooleanSupplier visibleSupplier = () -> true;

    ActionButtonState state =
        hostActionButtonCreator.buildButton(
            LAB_INFO, "SHARED_LAB", visibleSupplier, () -> true, () -> true, "test_tooltip");

    assertThat(state.getVisible()).isFalse();
  }
}
