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
import com.google.devtools.mobileharness.infra.ats.console.controller.olcserver.ServerLogPrinter;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

/** Command for "log" commands. */
@Command(
    name = "log",
    sortOptions = false,
    description = "Enable/disable showing server streaming log in console")
class LogCommand implements Callable<Integer> {

  private final ServerLogPrinter serverLogPrinter;

  @Inject
  LogCommand(ServerLogPrinter serverLogPrinter) {
    this.serverLogPrinter = serverLogPrinter;
  }

  @Override
  public Integer call() throws MobileHarnessException, InterruptedException {
    serverLogPrinter.enable();
    return ExitCode.OK;
  }
}
