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

package com.google.devtools.atsconsole.controller.sessionplugin;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.devtools.atsconsole.ConsoleUtil;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import picocli.CommandLine.ExitCode;

/** Printer for printing {@code AtsSessionPluginOutput} to console. */
public class PluginOutputPrinter {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Future callback which prints {@code AtsSessionPluginOutput} to console. */
  public static class PrintPluginOutputFutureCallback
      implements FutureCallback<AtsSessionPluginOutput> {

    private final ConsoleUtil consoleUtil;

    public PrintPluginOutputFutureCallback(ConsoleUtil consoleUtil) {
      this.consoleUtil = consoleUtil;
    }

    @Override
    public void onSuccess(AtsSessionPluginOutput output) {
      printOutput(output, consoleUtil);
    }

    @Override
    public void onFailure(Throwable error) {
      logger.atWarning().withCause(error).log("Failed to execute command");
    }
  }

  /**
   * Prints {@code AtsSessionPluginOutput} to ATS console.
   *
   * @return {@link ExitCode#OK} if the output contains a success, {@link ExitCode#SOFTWARE}
   *     otherwise
   */
  @CanIgnoreReturnValue
  public static int printOutput(AtsSessionPluginOutput output, ConsoleUtil consoleUtil) {
    switch (output.getResultCase()) {
      case SUCCESS:
        consoleUtil.printlnStdout(output.getSuccess().getOutputMessage());
        return ExitCode.OK;
      case FAILURE:
        consoleUtil.printlnStderr("Error: %s", output.getFailure().getErrorMessage());
        break;
      default:
    }
    return ExitCode.SOFTWARE;
  }

  private PluginOutputPrinter() {}
}
