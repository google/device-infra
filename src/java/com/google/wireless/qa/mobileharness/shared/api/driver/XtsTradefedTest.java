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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.deviceinfra.shared.util.shell.ShellUtils;
import com.google.devtools.deviceinfra.shared.util.shell.ShellUtils.TokenizationException;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.command.LineCallbackException;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.XtsTradefedTestDriverSpec;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Driver for running Tradefed based xTS test suites. */
@DriverAnnotation(help = "Running Tradefed based xTS test suites.")
public class XtsTradefedTest extends BaseDriver
    implements SpecConfigable<XtsTradefedTestDriverSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting
  static final String COMPATIBILITY_CONSOLE_CLASS =
      "com.android.compatibility.common.tradefed.command.CompatibilityConsole";

  private static final String XTS_TF_LOG = "xts_tf_output.log";

  private final CommandExecutor cmdExecutor;
  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;
  private final Adb adb;
  private final Fastboot fastboot;

  public XtsTradefedTest(Device device, TestInfo testInfo) {
    this(
        device,
        testInfo,
        new CommandExecutor(),
        new LocalFileUtil(),
        new SystemUtil(),
        new Adb(),
        new Fastboot());
  }

  @VisibleForTesting
  XtsTradefedTest(
      Device device,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil,
      Adb adb,
      Fastboot fastboot) {
    super(device, testInfo);
    this.cmdExecutor = cmdExecutor;
    this.localFileUtil = localFileUtil;
    this.systemUtil = systemUtil;
    this.adb = adb;
    this.fastboot = fastboot;
  }

  @Override
  public void run(TestInfo testInfo)
      throws com.google.wireless.qa.mobileharness.shared.MobileHarnessException,
          InterruptedException {
    XtsTradefedTestDriverSpec spec = testInfo.jobInfo().combinedSpec(this);
    XtsType xtsType = XtsType.valueOf(Ascii.toUpperCase(spec.getXtsType()));

    boolean xtsRunCommandSuccess = runXtsCommand(testInfo, spec, xtsType);
    testInfo
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Finished running %s test. xTS run command exit status: %s",
            xtsType, xtsRunCommandSuccess);

    if (!xtsRunCommandSuccess) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              new MobileHarnessException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR, "Failed to run the xTS command."));
    } else {
      testInfo.resultWithCause().setPass();
    }
  }

  private boolean runXtsCommand(TestInfo testInfo, XtsTradefedTestDriverSpec spec, XtsType xtsType)
      throws MobileHarnessException, InterruptedException {
    CommandProcess xtsProcess = null;
    try {
      xtsProcess = runCommand(testInfo, spec, xtsType);
      return xtsProcess.await().exitCode() == 0;
    } catch (CommandFailureException e) {
      logger.atWarning().log("xTS run command failed: %s", e.getMessage());
      return false;
    } catch (CommandTimeoutException e) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.TIMEOUT,
              new MobileHarnessException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_TIMEOUT,
                  "xTS run command timed out.",
                  e));
      return false;
    } catch (InterruptedException e) {
      testInfo.log().atWarning().alsoTo(logger).withCause(e).log("xTS Tradefed was interrupted.");
      throw e;
    } finally {
      if (xtsProcess != null && xtsProcess.isAlive()) {
        xtsProcess.killWithSignal(/* signal= */ 2);
      }
    }
  }

  private CommandProcess runCommand(
      TestInfo testInfo, XtsTradefedTestDriverSpec spec, XtsType xtsType)
      throws MobileHarnessException {
    String[] cmd = getXtsCommand(spec, xtsType);
    ImmutableMap<String, String> env = getEnvironmentToTradefedConsole(spec, xtsType);
    // Logs command string for debug purpose
    StringBuilder cmdString =
        new StringBuilder(Joiner.on(' ').withKeyValueSeparator("=").join(env));
    if (cmdString.length() > 0) {
      cmdString.append(' ');
    }
    Joiner.on(' ').appendTo(cmdString, cmd);
    logger.atInfo().log("Running %s command:%n%s", xtsType.name(), cmdString);
    try {
      BufferedWriter writer =
          Files.newBufferedWriter(Path.of(testInfo.getGenFileDir()).resolve(XTS_TF_LOG));

      return cmdExecutor.start(
          Command.of(cmd)
              .extraEnv(env)
              .onStdout(
                  LineCallback.does(
                      line -> {
                        try {
                          writer.write(line + "\n");
                        } catch (IOException e) {
                          throw new LineCallbackException(
                              "Failed to write",
                              e,
                              /* killCommand= */ false,
                              /* stopReadingOutput= */ true);
                        }
                      }))
              .onExit(
                  unused -> {
                    try {
                      writer.close();
                    } catch (IOException e) {
                      testInfo
                          .log()
                          .atWarning()
                          .alsoTo(logger)
                          .log("Unable to close writer for %s", XTS_TF_LOG);
                    }
                  })
              .redirectStderr(true)
              .timeout(getXtsTimeout(testInfo)));
    } catch (IOException | CommandStartException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_START_COMMAND_ERROR,
          "Failed to start the xTS command: " + cmdString,
          e);
    }
  }

  private String[] getXtsCommand(XtsTradefedTestDriverSpec spec, XtsType xtsType)
      throws MobileHarnessException {
    Path xtsRootDir = getXtsRootDir(spec);
    ImmutableList.Builder<String> xtsCommand =
        ImmutableList.<String>builder()
            .add(getJavaBinary(), "-Xmx6g", "-XX:+HeapDumpOnOutOfMemoryError");
    xtsCommand.add("-cp", getConcatenatedJarPath(xtsRootDir, xtsType));

    for (Map.Entry<String, String> systemProp :
        getSystemPropsToTradefedConsole(spec, xtsType).entrySet()) {
      xtsCommand.add(String.format("-D%s=%s", systemProp.getKey(), systemProp.getValue()));
    }

    xtsCommand.add(COMPATIBILITY_CONSOLE_CLASS);
    xtsCommand.addAll(getXtsRunCommandArgs(spec));

    return xtsCommand.build().toArray(new String[0]);
  }

  private String getJavaBinary() {
    return systemUtil.getJavaBin();
  }

  private String getConcatenatedJarPath(Path xtsRootDir, XtsType xtsType)
      throws MobileHarnessException {
    ImmutableList.Builder<Path> jars = ImmutableList.<Path>builder();
    localFileUtil
        .listFilePaths(
            getXtsBundledToolsDir(xtsRootDir, xtsType),
            /* recursively= */ false,
            path -> path.getFileName().toString().endsWith(".jar"))
        .stream()
        .forEach(jars::add);
    localFileUtil
        .listFilePaths(
            getXtsBundledTestcasesDir(xtsRootDir, xtsType),
            /* recursively= */ true,
            path -> path.getFileName().toString().endsWith(".jar"))
        .stream()
        .forEach(jars::add);
    return Joiner.on(':').join(jars.build());
  }

  private Path getXtsRootDir(XtsTradefedTestDriverSpec spec) {
    if (spec.hasXtsRootDir()) {
      return Paths.get(spec.getXtsRootDir());
    }
    // TODO: Unzip android-xts zip file and return the xts root dir
    return null;
  }

  private Path getXtsBundledToolsDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/tools", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsBundledLibDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/lib", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsBundledTestcasesDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(
        String.format("android-%s/testcases", Ascii.toLowerCase(xtsType.name())));
  }

  private ImmutableMap<String, String> getSystemPropsToTradefedConsole(
      XtsTradefedTestDriverSpec spec, XtsType xtsType) {
    switch (xtsType) {
      case CTS:
        return ImmutableMap.of("CTS_ROOT", getXtsRootDir(spec).toString());
    }
    return ImmutableMap.of();
  }

  private ImmutableMap<String, String> getEnvironmentToTradefedConsole(
      XtsTradefedTestDriverSpec spec, XtsType xtsType) {
    ImmutableMap.Builder<String, String> environmentToTradefedConsole = ImmutableMap.builder();
    environmentToTradefedConsole.put(
        "LD_LIBRARY_PATH", getConcatenatedLdLibraryPath(spec, xtsType));
    environmentToTradefedConsole.put("PATH", getEnvPath());
    return environmentToTradefedConsole.buildOrThrow();
  }

  private String getConcatenatedLdLibraryPath(XtsTradefedTestDriverSpec spec, XtsType xtsType) {
    Path xtsBundledLibDir = getXtsBundledLibDir(getXtsRootDir(spec), xtsType);
    return String.format("%s:%s64", xtsBundledLibDir, xtsBundledLibDir);
  }

  private String getEnvPath() {
    return Joiner.on(':')
        .join(
            new File(adb.getAdbPath()).getParent(),
            new File(fastboot.getFastbootPath()).getParent(),
            systemUtil.getEnv("PATH"));
  }

  private ImmutableList<String> getXtsRunCommandArgs(XtsTradefedTestDriverSpec spec) {
    ImmutableList.Builder<String> xtsRunCommand =
        ImmutableList.<String>builder().add("run", "commandAndExit", getXtsTestPlan(spec));

    xtsRunCommand.addAll(getExtraRunCommandArgs(spec));

    // Appends allocated device(s) serial
    getDeviceIds().forEach(serial -> xtsRunCommand.add("-s", serial));

    return xtsRunCommand.build();
  }

  private String getXtsTestPlan(XtsTradefedTestDriverSpec spec) {
    return spec.getXtsTestPlan();
  }

  private ImmutableList<String> getExtraRunCommandArgs(XtsTradefedTestDriverSpec spec) {
    List<String> extraRunCommandArgs = new ArrayList<>();

    if (spec.hasRunCommandArgs()) {
      try {
        ShellUtils.tokenize(extraRunCommandArgs, spec.getRunCommandArgs());
      } catch (TokenizationException te) {
        logger.atWarning().withCause(te).log(
            "Failed to parse the run command args [%s]", spec.getRunCommandArgs());
        return ImmutableList.of();
      }
    }
    return ImmutableList.copyOf(extraRunCommandArgs);
  }

  private ImmutableList<String> getDeviceIds() {
    Device device = getDevice();
    if (!(device instanceof CompositeDevice)) {
      return ImmutableList.of(device.getDeviceId());
    }
    CompositeDevice compositeDevice = (CompositeDevice) device;
    return compositeDevice.getManagedDevices().stream()
        .map(Device::getDeviceId)
        .collect(toImmutableList());
  }

  private static Duration getXtsTimeout(TestInfo testInfo) throws MobileHarnessException {
    // Use remaining time to run the xts command but leave 2 minutes for post processing.
    return testInfo.timer().remainingTimeJava().minusMinutes(2);
  }

  /** Type for xTS. */
  public enum XtsType {
    CTS;
  }
}
