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

package com.google.wireless.qa.mobileharness.shared.api.driver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.XtsTradefedTestDriverSpec;
import java.nio.file.Path;

/**
 * Strategy interface for Tradefed based test suite runners, to differentiate between xTS and
 * Non-XTS runs.
 *
 * <p>The "work dir" is the working directory for Tradefed, used to store logs, results, and other
 * temporary files. It is passed to Tradefed via the TF_WORK_DIR environment variable.
 */
interface TradefedRunStrategy {

  /**
   * Sets up the work directory and prepares for the test run, including linking JDK, test cases,
   * tools and libs for xTS runs.
   *
   * @param spec the driver spec
   * @param workDir the working directory for the test run
   * @param xtsType the type of xTS suite
   * @param testInfo the test info
   */
  void setUpWorkDir(XtsTradefedTestDriverSpec spec, Path workDir, String xtsType, TestInfo testInfo)
      throws MobileHarnessException, InterruptedException;

  /**
   * Returns the concatenated classpath for the Tradefed command.
   *
   * @param workDir the working directory for the test run
   * @param spec the driver spec
   * @param xtsType the type of xTS suite
   */
  String getConcatenatedJarPath(Path workDir, XtsTradefedTestDriverSpec spec, String xtsType)
      throws MobileHarnessException;

  /**
   * Returns the environment variables for the Tradefed command.
   *
   * @param workDir the working directory for the test run
   * @param xtsType the type of xTS suite
   * @param spec the driver spec
   * @param device the device
   * @param envPath the environment path
   */
  ImmutableMap<String, String> getEnvironment(
      Path workDir, String xtsType, XtsTradefedTestDriverSpec spec, Device device, String envPath)
      throws MobileHarnessException, InterruptedException;

  /**
   * Returns the path to the Java executable.
   *
   * @param workDir the working directory for the test run
   * @param xtsType the type of xTS suite
   */
  String getJavaPath(Path workDir, String xtsType);

  /** Returns the main class to run. */
  String getMainClass();

  /** Returns a list of JVM defines to use. */
  ImmutableList<String> getJvmDefines(Path workDir, String xtsType);

  /** Returns a set of result directory names from previous runs to be excluded. */
  ImmutableSet<String> getPreviousResultDirNames();
}
