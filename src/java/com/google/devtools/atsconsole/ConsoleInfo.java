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

package com.google.devtools.atsconsole;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Class to store console info. */
public final class ConsoleInfo {

  private final AtomicBoolean shouldExitConsole = new AtomicBoolean(false);
  private final AtomicReference<String> moblyTestCasesDir = new AtomicReference<>();
  private final AtomicReference<String> resultsDirectory = new AtomicReference<>();
  private final AtomicReference<String> moblyTestZipSuiteMainFile = new AtomicReference<>();

  private static class SingletonHolder {
    private static final ConsoleInfo INSTANCE;

    static {
      INSTANCE = new ConsoleInfo();
    }
  }

  @VisibleForTesting
  ConsoleInfo() {}

  public static ConsoleInfo getInstance() {
    return SingletonHolder.INSTANCE;
  }

  /** Sets whether exit the console. */
  public void setShouldExitConsole(boolean shouldExit) {
    shouldExitConsole.set(shouldExit);
  }

  /** Gets whether exit the console. */
  public boolean getShouldExitConsole() {
    return shouldExitConsole.get();
  }

  /** Sets the directory which contains the Mobly test cases in zip file format. */
  public void setMoblyTestCasesDir(String moblyTestCasesDir) {
    this.moblyTestCasesDir.set(moblyTestCasesDir);
  }

  /** Gets the directory which contains the Mobly test cases in zip file format. */
  public Optional<String> getMoblyTestCasesDir() {
    return Optional.ofNullable(moblyTestCasesDir.get());
  }

  /** Sets the directory in which the test results are saved. */
  public void setResultsDirectory(String resultsDirectory) {
    this.resultsDirectory.set(resultsDirectory);
  }

  /** Gets the directory in which the test results are saved. */
  public Optional<String> getResultsDirectory() {
    return Optional.ofNullable(resultsDirectory.get());
  }

  /** Sets the suite main file used along with the Mobly Test Zip. */
  public void setMoblyTestZipSuiteMainFile(String moblyTestZipSuiteMainFile) {
    this.moblyTestZipSuiteMainFile.set(moblyTestZipSuiteMainFile);
  }

  /** Gets the suite main file used along with the Mobly Test Zip. */
  public Optional<String> getMoblyTestZipSuiteMainFile() {
    return Optional.ofNullable(moblyTestZipSuiteMainFile.get());
  }
}
