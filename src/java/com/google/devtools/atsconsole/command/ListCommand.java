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

import com.google.devtools.atsconsole.ConsoleUtil;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

/** Command for "list" commands. */
@Command(
    name = "list",
    aliases = {"l"},
    sortOptions = false,
    description = "List invocations, devices, modules, etc.")
public class ListCommand implements Callable<Integer> {

  private final ConsoleUtil consoleUtil;

  @Inject
  ListCommand(ConsoleUtil consoleUtil) {
    this.consoleUtil = consoleUtil;
  }

  @Command(
      name = "commands",
      aliases = {"c"},
      description = "List all commands currently waiting to be executed")
  public int commands() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(name = "configs", description = "List all known configurations")
  public int configs() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "devices",
      aliases = {"d"},
      description = "List all detected or known devices")
  public int devices() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "invocations",
      aliases = {"i"},
      description = "List all invocation threads")
  public int invocations() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "modules",
      aliases = {"m"},
      description = "List all modules available")
  public int modules() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "plans",
      aliases = {"p"},
      description = "List all plans available")
  public int plans() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Command(
      name = "results",
      aliases = {"r"},
      description = "List all results")
  public int results() {
    consoleUtil.printErrorLine("Unimplemented");
    return ExitCode.SOFTWARE;
  }

  @Override
  public Integer call() {
    consoleUtil.printErrorLine("Unable to handle command 'list'.  Enter 'help' for help.");
    return ExitCode.USAGE;
  }
}
