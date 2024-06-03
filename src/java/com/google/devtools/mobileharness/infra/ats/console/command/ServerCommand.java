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
import com.google.devtools.mobileharness.infra.ats.common.olcserver.ServerPreparer;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** Command for the OLC server instance. */
@Command(
    name = "server",
    sortOptions = false,
    description = "Restart the OLC server, etc",
    synopsisSubcommandLabel = "")
final class ServerCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  private final ConsoleUtil consoleUtil;
  private final ServerPreparer serverPreparer;

  @Inject
  ServerCommand(ConsoleUtil consoleUtil, ServerPreparer serverPreparer) {
    this.consoleUtil = consoleUtil;
    this.serverPreparer = serverPreparer;
  }

  @Override
  public Integer call() {
    throw new ParameterException(spec.commandLine(), "Missing required subcommand");
  }

  @Command(
      name = "restart",
      aliases = {"r"},
      description =
          "Restart the OLC server. Use --forcibly or -f to forcibly kill the existing server.")
  public void restart(@Option(names = {"--forcibly", "-f"}) boolean forcibly)
      throws MobileHarnessException, InterruptedException {
    if (serverPreparer.tryConnectToOlcServer().isPresent()) {
      serverPreparer.killExistingServer(forcibly);
    } else {
      consoleUtil.printlnStdout("No running OLC server found.");
    }

    serverPreparer.prepareOlcServer();
  }
}
