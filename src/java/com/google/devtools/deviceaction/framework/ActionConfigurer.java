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

package com.google.devtools.deviceaction.framework;

import com.google.devtools.deviceaction.common.error.DeviceActionException;
import com.google.devtools.deviceaction.common.schemas.ActionConfig;
import com.google.devtools.deviceaction.common.schemas.ActionOptions;
import com.google.devtools.deviceaction.common.schemas.Command;
import com.google.devtools.deviceaction.framework.proto.ActionSpec;

/** An interface to create {@link ActionConfig}. */
public interface ActionConfigurer {

  /**
   * Creates {@link ActionConfig} from {@link ActionOptions}.
   *
   * @return final configure for the action.
   * @throws DeviceActionException if fails to create configure.
   * @throws InterruptedException if interrupted.
   */
  ActionConfig createActionConfigure(ActionOptions options)
      throws DeviceActionException, InterruptedException;

  /**
   * Creates {@link ActionConfig} from {@link Command} and initial {@link ActionSpec}.
   *
   * @return final configure for the action.
   * @throws DeviceActionException if fails to create configure.
   * @throws InterruptedException if interrupted.
   */
  ActionConfig createActionConfigure(Command cmd, ActionSpec spec)
      throws DeviceActionException, InterruptedException;
}
