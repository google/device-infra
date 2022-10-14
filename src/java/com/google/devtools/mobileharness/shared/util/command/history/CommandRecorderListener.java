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

package com.google.devtools.mobileharness.shared.util.command.history;

import com.google.devtools.mobileharness.shared.util.command.CommandResult;

/** The listener that receives events from {@link CommandRecorder} */
public interface CommandRecorderListener {
  /**
   * Invokes when {@link CommandRecorder} adds the result to a command.
   *
   * @param commandRecord record of the command.
   * @param result command execution result.
   */
  void onAddCommandResult(CommandRecord commandRecord, CommandResult result);
}
