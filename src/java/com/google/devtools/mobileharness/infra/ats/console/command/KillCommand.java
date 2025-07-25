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

import com.google.devtools.mobileharness.infra.ats.console.util.command.ExitUtil;
import com.google.devtools.mobileharness.infra.ats.console.util.console.InterruptibleLineReader;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

/** Command to exit the ATS console. */
@Command(name = "kill", description = "Kills the console more aggressively.")
final class KillCommand implements Callable<Integer> {

  @Option(
      names = {"--force", "-f"},
      required = false,
      paramLabel = "<force_kill>",
      description = "Whether to quit the console immediately.")
  private boolean force = false;

  private final InterruptibleLineReader interruptibleLineReader;
  private final ExitUtil exitUtil;

  @Inject
  KillCommand(InterruptibleLineReader interruptibleLineReader, ExitUtil exitUtil) {
    this.interruptibleLineReader = interruptibleLineReader;
    this.exitUtil = exitUtil;
  }

  @Override
  public Integer call() {
    exitUtil.cancelUnfinishedSessions("User triggered Kill Command.", /* aggressive= */ true);

    // Wait until no running sessions if not forcibly.
    if (force) {
      interruptibleLineReader.interrupt();
    } else {
      @SuppressWarnings("unused")
      var unused = exitUtil.waitUntilNoRunningSessionsAndInterruptLineReader();
    }

    return ExitCode.OK;
  }
}
