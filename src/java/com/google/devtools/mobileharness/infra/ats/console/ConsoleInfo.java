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

package com.google.devtools.mobileharness.infra.ats.console;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.infra.ats.console.Annotations.SystemProperties;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Class to store console info. */
@Singleton
public class ConsoleInfo {

  private static final String MOBLY_TESTCASES_DIR_PROPERTY_KEY = "MOBLY_TESTCASES_DIR";
  private static final String TEST_RESULTS_DIR_PROPERTY_KEY = "TEST_RESULTS_DIR";
  private static final String XTS_ROOT_DIR_PROPERTY_KEY = "XTS_ROOT";
  private static final String PYTHON_PACKAGE_INDEX_URL_PROPERTY_KEY = "PYTHON_PACKAGE_INDEX_URL";
  private static final String MOBLY_TEST_ZIP_SUITE_MAIN_FILE_PROPERTY_KEY =
      "MOBLY_TEST_ZIP_SUITE_MAIN_FILE";

  private final AtomicBoolean shouldExitConsole = new AtomicBoolean(false);
  private final AtomicReference<String> pythonPackageIndexUrl = new AtomicReference<>();
  private final AtomicReference<String> moblyTestCasesDir = new AtomicReference<>();
  private final AtomicReference<String> resultsDirectory = new AtomicReference<>();

  private final ImmutableMap<String, String> systemProperties;

  @Inject
  @VisibleForTesting
  public ConsoleInfo(@SystemProperties ImmutableMap<String, String> systemProperties) {
    this.systemProperties = systemProperties;
    setMoblyTestCasesDir(systemProperties.get(MOBLY_TESTCASES_DIR_PROPERTY_KEY));
    setResultsDirectory(systemProperties.get(TEST_RESULTS_DIR_PROPERTY_KEY));
    setPythonPackageIndexUrl(systemProperties.get(PYTHON_PACKAGE_INDEX_URL_PROPERTY_KEY));
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

  /** Gets the suite main file used along with the Mobly Test Zip. */
  public Optional<String> getMoblyTestZipSuiteMainFile() {
    return Optional.ofNullable(systemProperties.get(MOBLY_TEST_ZIP_SUITE_MAIN_FILE_PROPERTY_KEY));
  }

  /** Gets the xTS root directory. */
  public Optional<String> getXtsRootDirectory() {
    return Optional.ofNullable(systemProperties.get(XTS_ROOT_DIR_PROPERTY_KEY));
  }

  /** Sets the base URL of Python Package Index. */
  public void setPythonPackageIndexUrl(String pythonPackageIndexUrl) {
    this.pythonPackageIndexUrl.set(pythonPackageIndexUrl);
  }

  /** Gets the base URL of Python Package Index. */
  public Optional<String> getPythonPackageIndexUrl() {
    return Optional.ofNullable(pythonPackageIndexUrl.get());
  }
}
