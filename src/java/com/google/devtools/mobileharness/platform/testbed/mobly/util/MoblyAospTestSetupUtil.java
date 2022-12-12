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

package com.google.devtools.mobileharness.platform.testbed.mobly.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandException;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Utility for executing Mobly tests packaged as zip source distributions (e.g. from AOSP). */
public class MoblyAospTestSetupUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String TEST_SELECTOR_ALL = "all";
  public static final String DEFAULT_TEST_PATH = "suite_main.py";

  private final LocalFileUtil localFileUtil;
  private final CommandExecutor executor;

  public MoblyAospTestSetupUtil() {
    this(new LocalFileUtil(), new CommandExecutor());
  }

  @VisibleForTesting
  MoblyAospTestSetupUtil(LocalFileUtil localFileUtil, CommandExecutor executor) {
    this.localFileUtil = localFileUtil;
    this.executor = executor;
  }

  /**
   * Sets up the host for Mobly and generates an executable test command.
   *
   * @param moblyZip The {@link Path} to the zipped Mobly test package. See go/mobly-mh-aosp-codelab
   *     for details regarding its format.
   * @param moblyUnzipDir The destination {@link Path} to unzip the Mobly package to.
   * @param venvPath The {@link Path} at which a venv should be generated.
   * @param configFile The {@link Path} to the Mobly test config.
   * @param testPath Relative path to the specific test/suite to run.
   * @param testCaseSelector Specifies a subset of test cases in a test file to run.
   * @param pythonVersion Desired Python version.
   */
  public String[] setupEnvAndGenerateTestCommand(
      Path moblyZip,
      Path moblyUnzipDir,
      Path venvPath,
      Path configFile,
      @Nullable String testPath,
      @Nullable String testCaseSelector,
      @Nullable String pythonVersion)
      throws MobileHarnessException, InterruptedException {
    Path sysPythonBin = getPythonPath(pythonVersion);
    Path venvPythonBin = createVenv(sysPythonBin, venvPath);
    Path moblyTestBin = resolveMoblyTestBin(moblyZip, moblyUnzipDir, testPath);
    installMoblyTestPackage(venvPythonBin, moblyUnzipDir);

    return getTestCommand(venvPythonBin, moblyTestBin, configFile, testCaseSelector);
  }

  /** Creates the Python virtual environment for installing dependencies and executing the test. */
  @VisibleForTesting
  Path createVenv(Path sysPythonBin, Path venvPath)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Creating Python venv at %s", venvPath);
    List<String> venvCmd = new ArrayList<>();
    venvCmd.add(sysPythonBin.toString());
    venvCmd.add("-m");
    venvCmd.add("venv");
    venvCmd.add(venvPath.toString());
    try {
      executor.run(Command.of(venvCmd));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_CREATE_VENV_ERROR,
          "Failed to create Python venv on host. Please check error logs.",
          e);
    }
    return venvPath.resolve("bin").resolve("python3");
  }

  /** Locate the path to the system Python binary. */
  @VisibleForTesting
  Path getPythonPath(@Nullable String pythonVersion)
      throws MobileHarnessException, InterruptedException {
    if (pythonVersion == null) {
      pythonVersion = "python3";
    }

    if (!pythonVersion.startsWith("python")) {
      pythonVersion = String.format("python%s", pythonVersion);
    }
    CommandResult result = executor.exec(Command.of("which", pythonVersion).successExitCodes(0, 1));

    if (result.exitCode() != 0) {
      String possiblePythons = executor.run(Command.of("whereis", "python"));
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_PYTHON_VERSION_NOT_FOUND_ERROR,
          String.format(
              "Unable to find a suitable python version. Attempted to find \"%s\"."
                  + " Executables found: %s.",
              pythonVersion, possiblePythons));
    }
    return Paths.get(result.stdout().trim());
  }

  /** Unzips the test package and resolves the test binary path. */
  @VisibleForTesting
  Path resolveMoblyTestBin(Path moblyZip, Path moblyUnzipDir, @Nullable String testPath)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Unzipping Mobly test package to %s", moblyUnzipDir);
    try {
      localFileUtil.unzipFile(moblyZip, moblyUnzipDir);
      localFileUtil.grantFileOrDirFullAccessRecursively(moblyUnzipDir);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_UNZIP_TEST_PACKAGE_ERROR,
          "Failed to unzip the Mobly test package. Please ensure that it is in the correct format.",
          e);
    }
    if (testPath == null) {
      logger.atInfo().log(
          "No test path specified by user. Using %s as default.", DEFAULT_TEST_PATH);
      testPath = DEFAULT_TEST_PATH;
    }
    Path moblyTestBin = moblyUnzipDir.resolve(testPath);
    if (!localFileUtil.isFileExist(moblyTestBin)) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_RESOLVE_TEST_PATH_ERROR,
          String.format(
              "The specified test file %s does not exist in the given test package.", testPath));
    }
    return moblyTestBin;
  }

  /** Installs the Mobly test package. */
  @VisibleForTesting
  void installMoblyTestPackage(Path venvPythonBin, Path moblyUnzipDir)
      throws MobileHarnessException, InterruptedException {
    List<String> pipCmd = new ArrayList<>();
    pipCmd.add(venvPythonBin.toString());
    pipCmd.add("-m");
    pipCmd.add("pip");
    pipCmd.add("install");
    pipCmd.add(moblyUnzipDir.toString());
    logger.atInfo().log(
        "Installing Mobly test package with command: %s.",
        new StringBuilder(Joiner.on(" ").join(pipCmd)));
    try {
      executor.run(Command.of(pipCmd));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_PIP_INSTALL_ERROR,
          "Failed to install the test package via pip. Please check the error logs. Make sure "
              + "that the test package contains a valid 'setup.cfg' or 'pyproject.toml' file.",
          e);
    }
  }

  /** Generates the test execution command. */
  @VisibleForTesting
  String[] getTestCommand(
      Path venvPythonBin, Path moblyTestBin, Path configFile, @Nullable String testCaseSelector) {
    List<String> cmdElements =
        Lists.newArrayList(
            venvPythonBin.toString(), moblyTestBin.toString(), "--config=" + configFile);
    if (testCaseSelector != null && !testCaseSelector.equals(TEST_SELECTOR_ALL)) {
      logger.atInfo().log("Selected test cases: %s", testCaseSelector);
      cmdElements.add("--test_case");
      for (String testCase : Splitter.on(" ").split(testCaseSelector)) {
        cmdElements.add(testCase);
      }
    }
    return cmdElements.toArray(new String[0]);
  }
}
