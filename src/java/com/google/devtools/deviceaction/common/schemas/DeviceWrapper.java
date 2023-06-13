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

import com.google.auto.value.AutoValue;
import com.google.devtools.deviceaction.framework.proto.Operand;
import java.util.Optional;

/** A wrapper class for operand and possible device config file. */
@AutoValue
public abstract class DeviceWrapper {

  /** The operand data needed by action spec. */
  public abstract Operand operand();

  /** The possible path to device config file. */
  public abstract Optional<String> deviceConfigFile();

  /** Creates a {@link DeviceWrapper}. */
  public static DeviceWrapper create(Operand operand, Optional<String> deviceConfigFile) {
    return new AutoValue_DeviceWrapper(operand, deviceConfigFile);
  }
}
