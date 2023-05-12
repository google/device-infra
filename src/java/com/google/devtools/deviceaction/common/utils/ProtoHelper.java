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

package com.google.devtools.deviceaction.common.utils;

import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.framework.proto.ActionSpec;
import com.google.devtools.deviceaction.framework.proto.DeviceConfig;
import com.google.devtools.deviceaction.framework.proto.Operand;
import com.google.devtools.deviceaction.framework.proto.Unary;
import com.google.devtools.deviceaction.framework.proto.action.InstallMainlineSpec;
import java.util.Optional;

/** A helper class to handle protos. */
public class ProtoHelper {

  /** Merges the specs from device config and the action. */
  public static ActionSpec mergeActionSpec(
      Command cmd, ActionSpec actionSpec, DeviceConfig deviceConfig) throws DeviceActionException {
    ActionSpec.Builder builder = ActionSpec.newBuilder();
    builder.mergeFrom(getActionSpec(cmd, deviceConfig));
    builder.mergeFrom(actionSpec);
    return builder.build();
  }

  /** Gets the first device if possible. */
  public static Optional<Operand> getFirst(ActionSpec actionSpec) {
    if (actionSpec.hasUnary()) {
      return Optional.of(actionSpec.getUnary().getFirst());
    } else if (actionSpec.hasBinary()) {
      return Optional.of(actionSpec.getBinary().getFirst());
    }
    return Optional.empty();
  }

  /** Gets the second device if possible. */
  public static Optional<Operand> getSecond(ActionSpec actionSpec) {
    if (actionSpec.hasBinary()) {
      return Optional.of(actionSpec.getBinary().getSecond());
    }
    return Optional.empty();
  }

  private static ActionSpec getActionSpec(Command cmd, DeviceConfig deviceConfig)
      throws DeviceActionException {
    switch (cmd) {
      case INSTALL_MAINLINE:
        InstallMainlineSpec installMainlineSpec =
            deviceConfig.getExtension(InstallMainlineSpec.installMainlineSpec);
        return ActionSpec.newBuilder()
            .setUnary(
                Unary.newBuilder()
                    .setExtension(InstallMainlineSpec.ext, installMainlineSpec)
                    .build())
            .build();
      default:
        throw new DeviceActionException("INVALID_CMD", ErrorType.CUSTOMER_ISSUE, "Not supported");
    }
  }

  private ProtoHelper() {}
}
