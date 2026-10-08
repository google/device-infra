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

package com.google.devtools.mobileharness.infra.ats.tradefed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.TradefedTestDriverSpec;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Strategy interface for Tradefed based test suite runners, to differentiate between xTS and
 * Non-XTS runs.
 *
 * <p>The "work dir" is the working directory for Tradefed, used to store logs, results, and other
 * temporary files. It is passed to Tradefed via the TF_WORK_DIR environment variable.
 */
public interface TradefedRunStrategy {

  /**
   * Sets up the work directory and prepares for the test run, including linking JDK, test cases,
   * tools and libs for xTS and non-xTS runs.
   *
   * @param spec the driver spec
   * @param workDir the working directory for the test run
   * @param testInfo the test info
   */
  void setUpWorkDir(TradefedTestDriverSpec spec, Path workDir, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  /**
   * Returns the concatenated classpath for the Tradefed command.
   *
   * @param workDir the working directory for the test run
   * @param spec the driver spec
   */
  String getConcatenatedJarPath(Path workDir, TradefedTestDriverSpec spec)
      throws MobileHarnessException;

  /**
   * Returns the environment variables for the Tradefed command.
   *
   * @param workDir the working directory for the test run
   * @param spec the driver spec
   * @param device the device
   * @param envPath the environment path
   */
  ImmutableMap<String, String> getEnvironment(
      Path workDir, TradefedTestDriverSpec spec, Device device, String envPath)
      throws MobileHarnessException, InterruptedException;

  /**
   * Returns the path to the Java executable.
   *
   * @param workDir the working directory for the test run
   */
  String getJavaPath(Path workDir);

  /** Returns the main class to run. */
  String getMainClass();

  /**
   * Returns a list of JVM defines to use.
   *
   * @param workDir the working directory for the test run
   */
  ImmutableList<String> getJvmDefines(Path workDir);

  /**
   * Returns a predicate for filtering result directories/files to only include ones from the
   * current session.
   */
  Predicate<Path> getCurrentSessionResultFilter();

  /**
   * Returns the result directory in the given work directory.
   *
   * @param workDir the working directory for the test run
   */
  Path getResultsDirInWorkDir(Path workDir);

  /**
   * Returns the log directory in the given work directory.
   *
   * @param workDir the working directory for the test run
   */
  Path getLogsDirInWorkDir(Path workDir);

  /**
   * Returns the root directory for saving results and logs in test's gen-file directory.
   *
   * @param testInfo the test info
   */
  Path getGenFileDir(TestInfo testInfo) throws MobileHarnessException;

  /**
   * Returns a list of extra JVM flags to use.
   *
   * @param workDir the working directory for the test run
   */
  default ImmutableList<String> getExtraJvmFlags(Path workDir) {
    return ImmutableList.of();
  }

  /**
   * Returns a list of extra command-line arguments for the Tradefed run command.
   *
   * @param testInfo the test info
   */
  default ImmutableList<String> getExtraRunCommandArgs(TestInfo testInfo) {
    return ImmutableList.of();
  }

  /**
   * Sets the MH test result after the Tradefed process has exited.
   *
   * <p>The default implementation only looks at the process exit code: {@code ERROR} if the process
   * didn't start or exited with a non-zero code, {@code PASS} otherwise.
   *
   * @param testInfo the test info
   * @param tfExitCode the exit code of the Tradefed process, or empty if the process didn't start
   */
  default void setTestResult(TestInfo testInfo, Optional<Integer> tfExitCode)
      throws MobileHarnessException {
    if (tfExitCode.isEmpty()) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              MobileHarnessExceptionFactory.createUserFacingException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR,
                  "Tradefed command didn't start",
                  /* cause= */ null));
      return;
    }
    if (tfExitCode.get() != 0) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              MobileHarnessExceptionFactory.createUserFacingException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR,
                  "Non-zero Tradefed command exit code: " + tfExitCode.get(),
                  /* cause= */ null));
      return;
    }
    testInfo.resultWithCause().setPass();
  }
}
