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

import com.google.common.base.Splitter;
import com.google.devtools.mobileharness.infra.ats.console.command.alias.AliasManager;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Command to create an alias for a command. */
@Command(
    name = "alias",
    description = "Create an alias for a command. Usage: alias <alias_name>='<command>'.")
class AliasCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "<alias_definition>",
      description = "The alias definition, e.g., my_alias='some command'")
  private String aliasDefinition;

  private final AliasManager aliasManager;
  private final ConsoleUtil consoleUtil;

  @Inject
  AliasCommand(AliasManager aliasManager, ConsoleUtil consoleUtil) {
    this.aliasManager = aliasManager;
    this.consoleUtil = consoleUtil;
  }

  @Override
  public Integer call() {
    if (aliasDefinition == null) {
      aliasManager
          .getAll()
          .forEach(
              (aliasName, aliasValue) ->
                  consoleUtil.printlnStdout("alias %s='%s'", aliasName, aliasValue));
      return ExitCode.OK;
    }

    List<String> parts = Splitter.on('=').splitToList(aliasDefinition);

    if (parts.size() != 2) {
      throw new ParameterException(
          spec.commandLine(), "Invalid alias format. Expected: <alias_name>='<command>'");
    }

    String aliasName = parts.get(0).trim();
    String aliasValue = parts.get(1).trim();

    // Removes quotes that may or may not have been removed by the shell.
    if (aliasValue.length() >= 2
        && ((aliasValue.startsWith("'") && aliasValue.endsWith("'"))
            || (aliasValue.startsWith("\"") && aliasValue.endsWith("\"")))) {
      aliasValue = aliasValue.substring(1, aliasValue.length() - 1);
    }

    aliasManager.addAlias(aliasName, aliasValue);
    consoleUtil.printlnStdout("Alias '%s' created, value: [%s].", aliasName, aliasValue);

    return ExitCode.OK;
  }
}
