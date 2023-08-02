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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Utility for running Mobly tests packaged in AOSP and distributed via the Android Build. */
public class MoblyAospTestSetupUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String TEST_SELECTOR_ALL = "all";

  // Add one of the two following files to your test package to declare pip dependencies.
  // requirements.txt file (https://pip.pypa.io/en/stable/reference/requirements-file-format/)
  public static final String REQUIREMENTS_TXT = "requirements.txt";
  // pyproject.toml file (https://setuptools.pypa.io/en/latest/userguide/pyproject_config.html)
  public static final String PYPROJECT_TOML = "pyproject.toml";

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
   * @param moblyPkg The {@link Path} to the Mobly test package. See go/mobly-mh-aosp-codelab for
   *     details regarding its format.
   * @param moblyUnzipDir The destination {@link Path} to unzip the Mobly package to.
   * @param venvPath The {@link Path} at which a venv should be generated.
   * @param configFile The {@link Path} to the Mobly test config.
   * @param testPath Relative path to the specific test/suite to run.
   * @param testCaseSelector Specifies a subset of test cases in a test file to run.
   * @param pythonVersion Desired Python version.
   * @param installMoblyTestDepsArgs args used when installing Mobly test deps.
   */
  public String[] setupEnvAndGenerateTestCommand(
      Path moblyPkg,
      Path moblyUnzipDir,
      Path venvPath,
      Path configFile,
      @Nullable String testPath,
      @Nullable String testCaseSelector,
      @Nullable String pythonVersion,
      @Nullable InstallMoblyTestDepsArgs installMoblyTestDepsArgs)
      throws MobileHarnessException, InterruptedException {
    Path moblyTestBin = resolveMoblyTestBin(moblyPkg, moblyUnzipDir, testPath);
    Path venvPythonBin = null;
    // Use a virtualenv if testPath is specified, or the test package has pip dependencies.
    // Otherwise, run the test package directly as a binary.
    if (testPath != null || hasDeps(moblyUnzipDir)) {
      Path sysPythonBin = getPythonPath(pythonVersion);
      venvPythonBin = createVenv(sysPythonBin, venvPath);
      installMoblyTestDeps(venvPythonBin, moblyUnzipDir, installMoblyTestDepsArgs);
    }

    return getTestCommand(venvPythonBin, moblyTestBin, configFile, testCaseSelector);
  }

  /**
   * Unzips the test package and resolves the test binary path. If testPath is not specified, assume
   * that the test package itself is executable, and return its path instead.
   */
  @VisibleForTesting
  Path resolveMoblyTestBin(Path moblyPkg, Path moblyUnzipDir, @Nullable String testPath)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Unzipping Mobly test package to %s", moblyUnzipDir);
    try {
      localFileUtil.unzipFile(moblyPkg, moblyUnzipDir);
      localFileUtil.grantFileOrDirFullAccessRecursively(moblyUnzipDir);
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_UNZIP_TEST_PACKAGE_ERROR,
          "Failed to unzip the Mobly test package. Please ensure that it is in the correct format.",
          e);
    }
    if (testPath == null) {
      logger.atInfo().log("No test path specified by user. Run the test package directly.");
      return moblyPkg;
    } else {
      Path moblyTestBin = moblyUnzipDir.resolve(testPath);
      if (!localFileUtil.isFileExist(moblyTestBin)) {
        throw new MobileHarnessException(
            ExtErrorId.MOBLY_AOSP_RESOLVE_TEST_PATH_ERROR,
            String.format(
                "The specified test file %s does not exist in the given test package.", testPath));
      }
      return moblyTestBin;
    }
  }

  /** Checks if the unzipped Mobly test package has any files declaring pip dependencies. */
  boolean hasDeps(Path moblyUnzipDir) {
    return localFileUtil.isFileExist(moblyUnzipDir.resolve(REQUIREMENTS_TXT))
        || localFileUtil.isFileExist(moblyUnzipDir.resolve(PYPROJECT_TOML));
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
    return Path.of(result.stdout().trim());
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

  /** Installs the Mobly test dependencies. */
  @VisibleForTesting
  void installMoblyTestDeps(
      Path venvPythonBin,
      Path moblyUnzipDir,
      @Nullable InstallMoblyTestDepsArgs installMoblyTestDepsArgs)
      throws MobileHarnessException, InterruptedException {
    Duration cmdTimeout = Duration.ofMinutes(10);
    List<String> pipCmd = new ArrayList<>();
    pipCmd.add(venvPythonBin.toString());
    pipCmd.add("-m");
    pipCmd.add("pip");
    if (installMoblyTestDepsArgs != null && installMoblyTestDepsArgs.defaultTimeout().isPresent()) {
      pipCmd.add(
          String.format(
              "--default-timeout=%d", installMoblyTestDepsArgs.defaultTimeout().get().toSeconds()));
      cmdTimeout = installMoblyTestDepsArgs.defaultTimeout().get();
    }
    pipCmd.add("install");
    if (installMoblyTestDepsArgs != null && installMoblyTestDepsArgs.indexUrl().isPresent()) {
      pipCmd.add("-i");
      pipCmd.add(installMoblyTestDepsArgs.indexUrl().get());
    }
    String requirementsFile = moblyUnzipDir.resolve(REQUIREMENTS_TXT).toString();
    String pyprojectFile = moblyUnzipDir.resolve(PYPROJECT_TOML).toString();
    if (localFileUtil.isFileExist(requirementsFile)) {
      pipCmd.add("-r");
      pipCmd.add(requirementsFile);
    } else if (localFileUtil.isFileExist(pyprojectFile)) {
      pipCmd.add(moblyUnzipDir.toString());
    } else {
      logger.atInfo().log(
          "No requirements.txt or pyproject.toml file found. Skipping deps install.");
      return;
    }
    logger.atInfo().log(
        "Installing Mobly test dependencies with command: %s.", Joiner.on(" ").join(pipCmd));
    try {
      executor.run(Command.of(pipCmd).timeout(cmdTimeout.plusMinutes(1)));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_PIP_INSTALL_ERROR,
          "Failed to install the test dependencies via pip. Please check the error logs. Make sure "
              + "that the test package contains a valid "
              + "'requirements.txt'/'setup.cfg'/'pyproject.toml' file.",
          e);
    }
  }

  /** Generates the test execution command. */
  @VisibleForTesting
  String[] getTestCommand(
      @Nullable Path venvPythonBin,
      Path moblyTestBin,
      Path configFile,
      @Nullable String testCaseSelector) {
    List<String> cmdElements = Lists.newArrayList();
    if (venvPythonBin != null) {
      cmdElements.add(venvPythonBin.toString());
    }
    cmdElements.add(moblyTestBin.toString());
    cmdElements.add("--config=" + configFile);
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
