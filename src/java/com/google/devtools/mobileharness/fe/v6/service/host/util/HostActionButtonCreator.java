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

import com.google.devtools.mobileharness.api.query.proto.LabQueryProto.LabInfo;
import com.google.devtools.mobileharness.fe.v6.service.proto.device.ActionButtonState;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Utility class to build {@link ActionButtonState} for host action buttons. */
@Singleton
public class HostActionButtonCreator {

  public static final ActionButtonState INVISIBLE_STATE =
      ActionButtonState.newBuilder().setVisible(false).build();

  @Inject
  HostActionButtonCreator() {}

  /**
   * Builds the action button state based on feature enablement and readiness.
   *
   * @param labInfo lab info mapping to default if absent
   * @param labType lab type string mapping to empty if absent
   * @param buttonVisibleSupplier supplier to evaluate if the button is visible based on specific
   *     conditions
   * @param buttonReadySupplier supplier to evaluate if the button is ready based on specific
   *     conditions
   * @param buttonEnabledSupplier supplier to evaluate if the button is enabled based on specific
   *     conditions
   * @param tooltipText description for buttons tooltip
   */
  public ActionButtonState buildButton(
      LabInfo labInfo,
      String labType,
      BooleanSupplier buttonVisibleSupplier,
      BooleanSupplier buttonReadySupplier,
      BooleanSupplier buttonEnabledSupplier,
      String tooltipText) {

    if (!buttonVisibleSupplier.getAsBoolean()) {
      return INVISIBLE_STATE;
    }

    boolean isCoreLab =
        HostTypes.determineLabTypeDisplayNames(
                labInfo.equals(LabInfo.getDefaultInstance())
                    ? Optional.empty()
                    : Optional.of(labInfo),
                labType.isEmpty() ? Optional.empty() : Optional.of(labType))
            .contains(HostTypes.LAB_TYPE_CORE);
    if (isCoreLab) {
      return INVISIBLE_STATE;
    }

    return ActionButtonState.newBuilder()
        .setVisible(true)
        .setIsReady(buttonReadySupplier.getAsBoolean())
        .setEnabled(buttonEnabledSupplier.getAsBoolean())
        .setTooltip(tooltipText)
        .build();
  }
}
