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

/** Utility for setting up Python virtual environments. */
public class MoblyPythonVenvUtil {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Add one of the two following files to your Python package to declare pip dependencies.
  // requirements.txt file (https://pip.pypa.io/en/stable/reference/requirements-file-format/)
  public static final String REQUIREMENTS_TXT = "requirements.txt";
  // pyproject.toml file (https://setuptools.pypa.io/en/latest/userguide/pyproject_config.html)
  public static final String PYPROJECT_TOML = "pyproject.toml";

  private final LocalFileUtil localFileUtil;
  private final CommandExecutor executor;

  public MoblyPythonVenvUtil() {
    this(new LocalFileUtil(), new CommandExecutor());
  }

  @VisibleForTesting
  MoblyPythonVenvUtil(LocalFileUtil localFileUtil, CommandExecutor executor) {
    this.localFileUtil = localFileUtil;
    this.executor = executor;
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

  /** Creates the Python virtual environment for installing package dependencies. */
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

  /** Installs Python package dependencies. */
  @VisibleForTesting
  void installPythonPkgDeps(
      Path venvPythonBin, Path pkgDir, @Nullable InstallPythonPkgDepsArgs installPythonPkgDepsArgs)
      throws MobileHarnessException, InterruptedException {
    Duration cmdTimeout = Duration.ofMinutes(10);
    List<String> pipCmd = new ArrayList<>();
    pipCmd.add(venvPythonBin.toString());
    pipCmd.add("-m");
    pipCmd.add("pip");
    if (installPythonPkgDepsArgs != null && installPythonPkgDepsArgs.defaultTimeout().isPresent()) {
      pipCmd.add(
          String.format(
              "--default-timeout=%d", installPythonPkgDepsArgs.defaultTimeout().get().toSeconds()));
      cmdTimeout = installPythonPkgDepsArgs.defaultTimeout().get();
    }
    pipCmd.add("install");
    if (installPythonPkgDepsArgs != null && installPythonPkgDepsArgs.indexUrl().isPresent()) {
      pipCmd.add("-i");
      pipCmd.add(installPythonPkgDepsArgs.indexUrl().get());
    }
    String requirementsFile = pkgDir.resolve(REQUIREMENTS_TXT).toString();
    String pyprojectFile = pkgDir.resolve(PYPROJECT_TOML).toString();
    if (localFileUtil.isFileExist(requirementsFile)) {
      pipCmd.add("-r");
      pipCmd.add(requirementsFile);
    } else if (localFileUtil.isFileExist(pyprojectFile)) {
      pipCmd.add(pkgDir.toString());
    } else {
      logger.atInfo().log(
          "No requirements.txt or pyproject.toml file found. Skipping deps install.");
      return;
    }
    logger.atInfo().log(
        "Installing Python package dependencies with command: %s.", Joiner.on(" ").join(pipCmd));
    try {
      executor.run(Command.of(pipCmd).timeout(cmdTimeout.plusMinutes(1)));
    } catch (CommandException e) {
      throw new MobileHarnessException(
          ExtErrorId.MOBLY_AOSP_PIP_INSTALL_ERROR,
          "Failed to install Python package dependencies via pip. Please check the error logs."
              + " Make sure that the test package contains a valid "
              + "'requirements.txt'/'setup.cfg'/'pyproject.toml' file.",
          e);
    }
  }
}
