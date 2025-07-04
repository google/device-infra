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

import com.google.devtools.mobileharness.infra.ats.console.command.alias.AliasManager;
import com.google.devtools.mobileharness.infra.ats.console.util.console.ConsoleUtil;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  private static final Pattern ALIAS_DEFINITION_PATTERN =
      Pattern.compile("(.+?)=['\"]?(.*?)['\"]?$");

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

    Matcher matcher = ALIAS_DEFINITION_PATTERN.matcher(aliasDefinition);

    if (!matcher.matches()) {
      throw new ParameterException(
          spec.commandLine(), "Invalid alias format. Expected: <alias_name>='<command>'");
    }

    String aliasName = matcher.group(1).trim();
    String aliasValue = matcher.group(2).trim();

    aliasManager.addAlias(aliasName, aliasValue);
    consoleUtil.printlnStdout("Alias '%s' created, value: [%s].", aliasName, aliasValue);

    return ExitCode.OK;
  }
}
