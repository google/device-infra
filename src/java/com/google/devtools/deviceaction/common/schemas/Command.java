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

package com.google.devtools.deviceaction.common.schemas;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;
import static java.util.function.Function.identity;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.common.metrics.stability.model.proto.ErrorTypeProto.ErrorType;
import com.google.devtools.deviceaction.common.error.DeviceActionException;
import java.util.Optional;

/** Action commands. */
public enum Command {
  DEFAULT(""),
  INSTALL_MAINLINE("install_mainline"),
  RESET("reset");

  private static final ImmutableMap<String, Command> CMD_MAP;
  private final String name;

  static {
    CMD_MAP = stream(Command.values()).collect(toImmutableMap(cmd -> cmd.name, identity()));
  }

  Command(String name) {
    this.name = name;
  }

  /** Gets the command of {@code name}. */
  public static Command of(String name) throws DeviceActionException {
    return Optional.ofNullable(CMD_MAP.get(name))
        .orElseThrow(
            () ->
                new DeviceActionException(
                    "INVALID_CMD",
                    ErrorType.CUSTOMER_ISSUE,
                    String.format("The command %s is not supported.", name)));
  }
}
