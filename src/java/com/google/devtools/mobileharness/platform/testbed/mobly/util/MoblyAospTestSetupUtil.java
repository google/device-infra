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

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
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
  private final MoblyPythonVenvUtil moblyPythonVenvUtil;

  public MoblyAospTestSetupUtil() {
    this(new LocalFileUtil(), new CommandExecutor(), new MoblyPythonVenvUtil());
  }

  @VisibleForTesting
  MoblyAospTestSetupUtil(LocalFileUtil localFileUtil, CommandExecutor executor) {
    this(localFileUtil, executor, new MoblyPythonVenvUtil(localFileUtil, executor));
  }

  @VisibleForTesting
  MoblyAospTestSetupUtil(
      LocalFileUtil localFileUtil,
      CommandExecutor executor,
      MoblyPythonVenvUtil moblyPythonVenvUtil) {
    this.localFileUtil = localFileUtil;
    this.executor = executor;
    this.moblyPythonVenvUtil = moblyPythonVenvUtil;
  }

  /**
   * Sets up the host for Mobly and generates an executable test command.
   *
   * @param moblyPkg The {@link Path} to the Mobly test package.
   * @param moblyUnzipDir The destination {@link Path} to unzip the Mobly package to.
   * @param venvPath The {@link Path} at which a venv should be generated.
   * @param configFile The {@link Path} to the Mobly test config.
   * @param testbedName The name of the testbed to run the test on.
   * @param testPath Relative path to the specific test/suite to run.
   * @param testExecutionCommand The command to execute the mobly test with extra runner and params,
   *     for example, use the mobly android partner runner with "mobly_runner mobly_test_suite -i".
   * @param testCaseSelector Specifies a subset of test cases in a test file to run.
   * @param pythonVersion Desired Python version.
   * @param installPythonPkgDepsArgs args used when installing Python package deps.
   */
  public String[] setupEnvAndGenerateTestCommand(
      Path moblyPkg,
      Path moblyUnzipDir,
      Path venvPath,
      Path configFile,
      String testbedName,
      @Nullable String testPath,
      @Nullable String testExecutionCommand,
      @Nullable String testCaseSelector,
      @Nullable String pythonVersion,
      @Nullable InstallPythonPkgDepsArgs installPythonPkgDepsArgs)
      throws MobileHarnessException, InterruptedException {
    prepareMoblyPkg(moblyPkg, moblyUnzipDir);
    Path venvPythonBin = null;
    // Use a virtualenv if testPath is specified, or the test package is a whl that needs to be
    // installed or has pip dependencies.
    // Otherwise, run the test package directly as a binary.
    if (testPath != null || isWhl(moblyPkg) || hasDeps(moblyUnzipDir)) {
      Path sysPythonBin = moblyPythonVenvUtil.getPythonPath(pythonVersion);
      venvPythonBin = moblyPythonVenvUtil.createVenv(sysPythonBin, venvPath);
      moblyPythonVenvUtil.installPythonPkgDeps(
          venvPythonBin, isWhl(moblyPkg) ? moblyPkg : moblyUnzipDir, installPythonPkgDepsArgs);
    }
    Path moblyTestBin = resolveMoblyTestBin(moblyPkg, moblyUnzipDir, venvPath, testPath);
    ImmutableList<String> executionArgs =
        resolveTestExecutionCommand(testExecutionCommand, moblyPkg, moblyUnzipDir, venvPath);

    return getTestCommand(
        executionArgs, venvPythonBin, moblyTestBin, configFile, testbedName, testCaseSelector);
  }

  /** Prepares the Mobly test package and unzips it for use. */
  void prepareMoblyPkg(Path moblyPkg, Path moblyUnzipDir)
      throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Unzipping Mobly test package to %s", moblyUnzipDir);
    try {
      localFileUtil.unzipFile(moblyPkg, moblyUnzipDir);
    } catch (MobileHarnessException e) {
      // Certain test packages contain files that cannot be extracted properly, causing the unzip
      // command to fail even if the remainder of the files are successfully extracted. We do not
      // rely on the problematic files for the test, so do not block on the error.
      logger.atWarning().log(
          "Encountered an error during test package unzip. See error message for details: %s",
          e.getMessage());
    }
    localFileUtil.grantFileOrDirFullAccess(moblyPkg);
    localFileUtil.grantFileOrDirFullAccessRecursively(moblyUnzipDir);
  }

  /** Checks if the file is a whl file that needs to be installed. */
  boolean isWhl(Path file) {
    return file.toString().endsWith(".whl");
  }

  /**
   * Checks if the moblyPkg itself is a whl dependency or the unzipped Mobly test package has any
   * files declaring pip dependencies.
   */
  boolean hasDeps(Path moblyUnzipDir) throws MobileHarnessException {
    return localFileUtil.isFileExist(moblyUnzipDir.resolve(REQUIREMENTS_TXT))
        || localFileUtil.isFileExist(moblyUnzipDir.resolve(PYPROJECT_TOML));
  }

  /**
   * Resolves the Mobly test binary to run. If testPath is not specified, assume that the test
   * package itself is executable, and return its path instead.
   */
  @VisibleForTesting
  Path resolveMoblyTestBin(
      Path moblyPkg, Path moblyUnzipDir, Path venvPath, @Nullable String testPath)
      throws MobileHarnessException, InterruptedException {
    if (testPath == null) {
      logger.atInfo().log("No test path specified by user. Run the test package directly.");
      return moblyPkg;
    } else {
      return resolveBin(moblyUnzipDir, venvPath, testPath)
          .orElseThrow(
              () ->
                  new MobileHarnessException(
                      ExtErrorId.MOBLY_AOSP_RESOLVE_TEST_PATH_ERROR,
                      String.format(
                          "The specified test file %s does not exist in the given test package.",
                          testPath)));
    }
  }

  /**
   * Resolves the executable in the test execution command to make it executable by {@link
   * CommandExecutor}.
   */
  @VisibleForTesting
  ImmutableList<String> resolveTestExecutionCommand(
      String testExecutionCommand, Path moblyPkg, Path moblyUnzipDir, Path venvPath)
      throws MobileHarnessException, InterruptedException {
    if (isNullOrEmpty(testExecutionCommand)) {
      return ImmutableList.of();
    }
    try {
      ImmutableList<String> args = ShellUtils.tokenize(testExecutionCommand);
      String executableName = args.get(0);
      if (executableName.equals(moblyPkg.getFileName().toString())) {
        // Execute the moblyPkg directly as a binary.
        return ImmutableList.<String>builder()
            .add(moblyPkg.toString())
            .addAll(args.subList(1, args.size()))
            .build();
      } else {
        return resolveBin(moblyUnzipDir, venvPath, executableName)
            .map(
                // Execute the executable contained within the moblyPkg.
                executablePath ->
                    ImmutableList.<String>builder()
                        .add(executablePath.toString())
                        .addAll(args.subList(1, args.size()))
                        .build())
            .orElseGet(
                // The executor may be a system bin, return the original test execution command.
                () -> {
                  logger.atWarning().log(
                      "Failed to resolve the binary %s in the test execution command. Assuming"
                          + " the test execution command is already executable.",
                      executableName);
                  return args;
                });
      }
    } catch (TokenizationException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_RESOLVE_TEST_EXECUTION_COMMAND_ERROR,
          String.format("Invalid test execution command: %s.", testExecutionCommand),
          e);
    }
  }

  /** Generates the test execution command. */
  @VisibleForTesting
  String[] getTestCommand(
      List<String> testExecutionCommand,
      @Nullable Path venvPythonBin,
      Path moblyTestBin,
      Path configFile,
      String testbedName,
      @Nullable String testCaseSelector)
      throws MobileHarnessException, InterruptedException {
    List<String> cmdElements = Lists.newArrayList();
    if (testExecutionCommand.isEmpty()) {
      if (venvPythonBin != null) {
        cmdElements.add(venvPythonBin.toString());
      }
      cmdElements.add(moblyTestBin.toString());
    } else {
      cmdElements.addAll(testExecutionCommand);
    }
    cmdElements.add("--config=" + configFile);
    cmdElements.add("--test_bed=" + testbedName);

    if (testCaseSelector != null && !testCaseSelector.equals(TEST_SELECTOR_ALL)) {
      // If the test package is not a Mobly suite, drop the test class names from the test case
      // selector.
      final boolean isMoblySuite = isMoblySuite(venvPythonBin, moblyTestBin);
      ImmutableList<String> testCases =
          Splitter.on(" ")
              .splitToStream(testCaseSelector)
              .map(
                  testName ->
                      !isMoblySuite && testName.contains(".")
                          ? Splitter.on(".").splitToList(testName).get(1)
                          : testName)
              .collect(toImmutableList());

      logger.atInfo().log("Selected test cases: %s", testCases);
      cmdElements.add("--tests");
      cmdElements.addAll(testCases);
    }
    return cmdElements.toArray(new String[0]);
  }

  /** Resolves the binary path. Try to find the binary in the unzipped directory or venv. */
  Optional<Path> resolveBin(Path moblyUnzipDir, Path venvPath, String relativePath)
      throws MobileHarnessException, InterruptedException {
    Path bin = moblyUnzipDir.resolve(relativePath);
    if (localFileUtil.isFileExist(bin)) {
      return Optional.of(bin);
    }
    // If the binary is not found in the unzipped directory, try to find it in the venv.
    Path venvTestBin = venvPath.resolve("bin").resolve(relativePath);
    if (localFileUtil.isFileExist(venvTestBin)) {
      return Optional.of(venvTestBin);
    }
    return Optional.empty();
  }

  // Check if the test package is a Mobly suite via the test case list.
  boolean isMoblySuite(@Nullable Path venvPythonBin, Path moblyTestBin)
      throws MobileHarnessException, InterruptedException {
    List<String> cmdElements = Lists.newArrayList();
    if (venvPythonBin != null) {
      cmdElements.add(venvPythonBin.toString());
    }
    cmdElements.add(moblyTestBin.toString());
    cmdElements.add("-l");

    Command listTestsCommand = Command.of(cmdElements).redirectStderr(false);
    String result = executor.run(listTestsCommand);
    if (result != null) {
      List<String> lines = Splitter.on('\n').trimResults().omitEmptyStrings().splitToList(result);
      for (String line : lines) {
        Matcher matcher = XtsConstants.MOBLY_TEST_CLASS_PATTERN.matcher(line);
        if (!matcher.matches()) {
          // Test name is in the format of "TestClass.test_case" if it is a Mobly suite, and just
          // "test_case" otherwise.
          return line.contains(".");
        }
      }
    }
    return false;
  }
}
