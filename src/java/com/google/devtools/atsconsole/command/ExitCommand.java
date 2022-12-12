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

package com.google.devtools.atsconsole.command;

import com.google.devtools.atsconsole.ConsoleInfo;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;

/** Command to exit the ATS console. */
@Command(
    name = "exit",
    aliases = {"quit", "q"},
    mixinStandardHelpOptions = true,
    description = "Exit the console.")
final class ExitCommand implements Callable<Integer> {

  private final ConsoleInfo consoleInfo;

  @Override
  public Integer call() {
    consoleInfo.setShouldExitConsole(true);
    return 0;
  }

  @Inject
  ExitCommand(ConsoleInfo consoleInfo) {
    this.consoleInfo = consoleInfo;
  }
}
