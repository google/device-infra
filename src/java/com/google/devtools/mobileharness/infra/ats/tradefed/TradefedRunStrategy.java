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
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.XtsTradefedTestDriverSpec;
import java.nio.file.Path;
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
  void setUpWorkDir(XtsTradefedTestDriverSpec spec, Path workDir, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  /**
   * Returns the concatenated classpath for the Tradefed command.
   *
   * @param workDir the working directory for the test run
   * @param spec the driver spec
   */
  String getConcatenatedJarPath(Path workDir, XtsTradefedTestDriverSpec spec)
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
      Path workDir, XtsTradefedTestDriverSpec spec, Device device, String envPath)
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
}
