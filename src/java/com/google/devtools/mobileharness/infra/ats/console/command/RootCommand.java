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

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/** Empty root command. Business logic are delegated to the subcommands. */
@Command(
    name = "", // Set it as empty, so it won't show on the "--help" usage
    subcommands = {
      DumpCommand.class,
      ExitCommand.class,
      HelpCommand.class,
      ListCommand.class,
      LogCommand.class,
      RunCommand.class,
      SetCommand.class,
    })
public final class RootCommand {}
