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

package com.google.wireless.qa.mobileharness.shared.api.decorator;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DecoratorAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.driver.Driver;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.decorator.PythonVersionCheckDecoratorSpec;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/** Decorator to check if the host's Python version meets the requirement. */
@DecoratorAnnotation(
    help = "Decorator to fail the test if the host's Python version does not meet the requirement.")
public class PythonVersionCheckDecorator extends BaseDecorator
    implements SpecConfigable<PythonVersionCheckDecoratorSpec> {

  private static final Pattern PYTHON_VERSION_PATTERN =
      Pattern.compile("Python (\\d+\\.\\d+(\\.\\d+)?)");

  private final CommandExecutor commandExecutor;

  @Inject
  PythonVersionCheckDecorator(
      Driver decorated, TestInfo testInfo, CommandExecutor commandExecutor) {
    super(decorated, testInfo);
    this.commandExecutor = commandExecutor;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    PythonVersionCheckDecoratorSpec spec =
        testInfo.jobInfo().combinedSpec(this, getDevice().getDeviceId());

    if (spec.hasMinPythonVersion() || spec.hasPythonVersion()) {
      try {
        checkVersion(spec);
      } catch (MobileHarnessException e) {
        testInfo.resultWithCause().setNonPassing(TestResult.ERROR, e);
        testInfo.getRootTest().resultWithCause().setNonPassing(TestResult.ERROR, e);
        // Don't throw the exception out to avoid the cause being overridden.
        return;
      }
    }

    getDecorated().run(testInfo);
  }

  private void checkVersion(PythonVersionCheckDecoratorSpec spec)
      throws MobileHarnessException, InterruptedException {
    PythonVersion hostVersion = getHostPythonVersion();

    if (spec.hasMinPythonVersion()) {
      if (hostVersion.compareTo(PythonVersion.parse(spec.getMinPythonVersion())) < 0) {
        throw new MobileHarnessException(
            ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_VERSION_NOT_MATCH,
            String.format(
                "Host Python3 version %s is lower than the required minimum version %s",
                hostVersion, spec.getMinPythonVersion()));
      }
    }

    if (spec.hasPythonVersion()) {
      if (hostVersion.compareTo(PythonVersion.parse(spec.getPythonVersion())) != 0) {
        throw new MobileHarnessException(
            ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_VERSION_NOT_MATCH,
            String.format(
                "Host Python3 version %s is not the required version %s",
                hostVersion, spec.getPythonVersion()));
      }
    }
  }

  private PythonVersion getHostPythonVersion() throws MobileHarnessException, InterruptedException {
    String output;
    try {
      output = commandExecutor.run(Command.of("python3", "--version"));
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_RUN_COMMAND_ERROR,
          "Failed to run 'python3 --version'. Ensure python3 is installed and in the PATH.",
          e);
    }
    Matcher matcher = PYTHON_VERSION_PATTERN.matcher(output.trim());
    if (matcher.find()) {
      return PythonVersion.parse(matcher.group(1));
    }
    throw new MobileHarnessException(
        ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_PARSE_VERSION_ERROR,
        "Failed to parse Python version from output: " + output);
  }

  @AutoValue
  abstract static class PythonVersion implements Comparable<PythonVersion> {
    static PythonVersion create(int major, int minor, int patch) {
      return new AutoValue_PythonVersionCheckDecorator_PythonVersion(major, minor, patch);
    }

    abstract int major();

    abstract int minor();

    abstract int patch();

    static PythonVersion parse(String versionString) throws MobileHarnessException {
      List<String> parts = Splitter.on('.').splitToList(versionString);
      try {
        int major = Integer.parseInt(parts.get(0));
        int minor = parts.size() > 1 ? Integer.parseInt(parts.get(1)) : 0;
        int patch = parts.size() > 2 ? Integer.parseInt(parts.get(2)) : 0;
        return create(major, minor, patch);
      } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
        throw new MobileHarnessException(
            ExtErrorId.PYTHON_VERSION_CHECK_DECORATOR_PARSE_VERSION_ERROR,
            "Failed to parse python version string: " + versionString,
            e);
      }
    }

    @Override
    public int compareTo(PythonVersion other) {
      if (this.major() != other.major()) {
        return this.major() - other.major();
      }
      if (this.minor() != other.minor()) {
        return this.minor() - other.minor();
      }
      return this.patch() - other.patch();
    }

    @Override
    public final String toString() {
      return String.format("%d.%d.%d", major(), minor(), patch());
    }
  }
}
