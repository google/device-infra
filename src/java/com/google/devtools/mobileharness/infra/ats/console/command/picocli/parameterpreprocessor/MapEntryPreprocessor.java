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

package com.google.devtools.mobileharness.infra.ats.console.command.picocli.parameterpreprocessor;

import java.util.Map;
import java.util.Stack;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.IParameterPreprocessor;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/** An abstract preprocessor to preprocess map entries with key value pairs. */
abstract class MapEntryPreprocessor implements IParameterPreprocessor {

  @Override
  public boolean preprocess(
      Stack<String> args, CommandSpec commandSpec, ArgSpec argSpec, Map<String, Object> info) {
    if (args.isEmpty() || args.peek().startsWith("-")) {
      throw generatePreprocessParameterException(commandSpec);
    }
    String key = args.pop();
    if (args.isEmpty() || args.peek().startsWith("-")) {
      throw generatePreprocessParameterException(commandSpec);
    }
    String value = args.pop();

    putEntry(argSpec, key, value);
    return true;
  }

  ParameterException generatePreprocessParameterException(CommandSpec commandSpec) {
    return new ParameterException(
        commandSpec.commandLine(),
        Ansi.AUTO.string("Must provide key value pair in format '--option <key> <value>'"));
  }

  abstract void putEntry(ArgSpec argSpec, String key, String value);
}
