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

package com.google.devtools.mobileharness.infra.ats.console.command;

import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.AtsSessionStub;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.HelpCommand;

/** Command for "remove" commands. */
@Command(
    name = "remove",
    sortOptions = false,
    description = "Remove commands.",
    synopsisSubcommandLabel = "",
    subcommands = {
      HelpCommand.class,
    })
class RemoveCommand {

  private final AtsSessionStub atsSessionStub;

  @Inject
  RemoveCommand(AtsSessionStub atsSessionStub) {
    this.atsSessionStub = atsSessionStub;
  }

  @Command(
      name = "allCommands",
      description = "Remove all commands currently waiting to be executed")
  int allCommands() throws MobileHarnessException {
    atsSessionStub.abortUnstartedSessions();
    return ExitCode.OK;
  }
}
