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

import com.google.devtools.atsconsole.ConsoleInfo;
import com.google.devtools.atsconsole.ConsoleUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Command to set console configurations. */
@Command(
    name = "set",
    sortOptions = false,
    mixinStandardHelpOptions = true,
    descriptionHeading = "%n",
    description = "Set console configurations.",
    synopsisHeading = "Usage:%n ")
final class SetCommand implements Callable<Integer> {

  @Option(
      names = "--mobly_testcases_dir",
      description = "Directory contains all being run Mobly testcases in zip file format.")
  private String moblyTestCasesDir;

  @Option(names = "--results_dir", description = "Directory in which the test results are saved.")
  private String resultsDir;

  private final ConsoleInfo consoleInfo;
  private final LocalFileUtil localFileUtil;
  private final ConsoleUtil consoleUtil;

  @Inject
  SetCommand(ConsoleInfo consoleInfo, LocalFileUtil localFileUtil, ConsoleUtil consoleUtil) {
    this.consoleInfo = consoleInfo;
    this.localFileUtil = localFileUtil;
    this.consoleUtil = consoleUtil;
  }

  @SuppressWarnings("ShortCircuitBoolean")
  @Override
  public Integer call() {
    boolean allSuccess = setMoblyTestCasesDir() & setResultsDir();
    return allSuccess ? 0 : 1;
  }

  private boolean setMoblyTestCasesDir() {
    if (moblyTestCasesDir != null) {
      moblyTestCasesDir = consoleUtil.completeHomeDirectory(moblyTestCasesDir);
      if (!moblyTestCasesDir.isEmpty() && localFileUtil.isDirExist(moblyTestCasesDir)) {
        consoleInfo.setMoblyTestCasesDir(moblyTestCasesDir);
      } else {
        consoleUtil.printLine(
            String.format(
                "Directory '%s' doesn't exist, please confirm and retry.", moblyTestCasesDir));
        return false;
      }
    }
    return true;
  }

  private boolean setResultsDir() {
    if (resultsDir != null) {
      resultsDir = consoleUtil.completeHomeDirectory(resultsDir);
      if (!resultsDir.isEmpty() && localFileUtil.isDirExist(resultsDir)) {
        consoleInfo.setResultsDirectory(resultsDir);
      } else {
        consoleUtil.printLine(
            String.format("Directory '%s' doesn't exist, please confirm and retry.", resultsDir));
        return false;
      }
    }
    return true;
  }
}
