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

import static com.google.common.base.StandardSystemProperty.JAVA_IO_TMPDIR;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.deviceinfra.platform.android.sdk.fastboot.Fastboot;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord.SourceType;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.command.LineCallbackException;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.wireless.qa.mobileharness.shared.api.CompositeDeviceUtil;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.inject.Inject;

/** Driver for running Tradefed based xTS test suites. */
@DriverAnnotation(help = "Running Tradefed based xTS test suites.")
public class XtsTradefedTest extends BaseDriver
    implements SpecConfigable<XtsTradefedTestDriverSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting
  static final String COMPATIBILITY_CONSOLE_CLASS =
      "com.android.compatibility.common.tradefed.command.CompatibilityConsole";

  private static final String XTS_TF_LOG = "xts_tf_output.log";

  private static final ImmutableSet<String> EXCLUDED_JAR_FILES =
      ImmutableSet.of("ats_olc_server_deploy.jar", "ats_console_deploy.jar");

  private volatile ImmutableSet<String> previousResultDirNames = ImmutableSet.of();

  private final CommandExecutor cmdExecutor;
  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;
  private final Adb adb;
  private final Fastboot fastboot;
  private final LogRecorder logRecorder;

  @Inject
  XtsTradefedTest(
      Device device,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil,
      Adb adb,
      Fastboot fastboot) {
    this(
        device,
        testInfo,
        cmdExecutor,
        localFileUtil,
        systemUtil,
        adb,
        fastboot,
        LogRecorder.getInstance());
  }

  @VisibleForTesting
  XtsTradefedTest(
      Device device,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil,
      Adb adb,
      Fastboot fastboot,
      LogRecorder logRecorder) {
    super(device, testInfo);
    this.cmdExecutor = cmdExecutor;
    this.localFileUtil = localFileUtil;
    this.systemUtil = systemUtil;
    this.adb = adb;
    this.fastboot = fastboot;
    this.logRecorder = logRecorder;
  }

  @Override
  public void run(TestInfo testInfo)
      throws com.google.wireless.qa.mobileharness.shared.MobileHarnessException,
          InterruptedException {
    XtsTradefedTestDriverSpec spec = testInfo.jobInfo().combinedSpec(this);
    XtsType xtsType = XtsType.valueOf(Ascii.toUpperCase(spec.getXtsType()));
    boolean isRunRetry = Ascii.equalsIgnoreCase("retry", getXtsTestPlan(spec));

    CompositeDeviceUtil.cacheTestbed(testInfo, getDevice());
    Path tmpXtsRootDir = null;
    try {
      tmpXtsRootDir = prepareXtsWorkDir(xtsType);
      setUpXtsWorkDir(getXtsRootDir(spec), tmpXtsRootDir, xtsType, isRunRetry);
      logger.atInfo().log("xTS Tradefed temp working root directory is %s", tmpXtsRootDir);

      boolean xtsRunCommandSuccess = runXtsCommand(testInfo, spec, tmpXtsRootDir, xtsType);
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Finished running %s test. xTS run command exit status: %s",
              xtsType, xtsRunCommandSuccess);

      if (xtsRunCommandSuccess) {
        testInfo.resultWithCause().setPass();
      }
    } finally {
      CompositeDeviceUtil.uncacheTestbed(getDevice());
      postTest(tmpXtsRootDir, testInfo, xtsType);
    }
  }

  private void postTest(Path tmpXtsRootDir, TestInfo testInfo, XtsType xtsType) {
    if (tmpXtsRootDir == null) {
      logger.atInfo().log(
          "xTS Tradefed temp working dir is not initialized, skip post test processing.");
      return;
    }
    // Copies xTS TF generated logs and results for this invocation in the test's gen file dir, so
    // they will be transferred to the client side.
    try {
      Path xtsGenFileDir =
          Paths.get(
              testInfo.getGenFileDir(),
              String.format("android-%s-gen-files", Ascii.toLowerCase(xtsType.name())));

      localFileUtil.prepareDir(xtsGenFileDir);
      localFileUtil.grantFileOrDirFullAccess(xtsGenFileDir);

      Path tmpXtsResultsDir = getXtsResultsDir(tmpXtsRootDir, xtsType);
      if (localFileUtil.isDirExist(tmpXtsResultsDir)) {
        // For "run retry", needs to skip those previous generated results and only copy the result
        // files belonging to this run.
        List<Path> newGenResultFilesOrDirs =
            localFileUtil.listFilesOrDirs(
                tmpXtsResultsDir,
                path ->
                    !previousResultDirNames.contains(path.getFileName().toString())
                        && !Objects.equals(path.getFileName().toString(), "latest"));
        Path xtsGenResultsDir = xtsGenFileDir.resolve("results");
        localFileUtil.prepareDir(xtsGenResultsDir);
        localFileUtil.grantFileOrDirFullAccess(xtsGenResultsDir);
        for (Path newGenResultFileOrDir : newGenResultFilesOrDirs) {
          localFileUtil.copyFileOrDir(newGenResultFileOrDir, xtsGenResultsDir);
        }
      }
      Path tmpXtsLogsDir = getXtsLogsDir(tmpXtsRootDir, xtsType);
      if (localFileUtil.isDirExist(tmpXtsLogsDir)) {
        localFileUtil.copyFileOrDir(tmpXtsLogsDir, xtsGenFileDir);
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Error when copying xTS TF gen files: %s", MoreThrowables.shortDebugString(e, 0));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when copying xTS TF gen files: %s", MoreThrowables.shortDebugString(e, 0));
      Thread.currentThread().interrupt();
    }

    try {
      localFileUtil.removeFileOrDir(tmpXtsRootDir);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to clean up xTS Tradefed temp directory [%s]: %s",
          tmpXtsRootDir, MoreThrowables.shortDebugString(e, 0));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when clean up xTS Tradefed temp directory [%s]: %s",
          tmpXtsRootDir, MoreThrowables.shortDebugString(e, 0));
      Thread.currentThread().interrupt();
    }
  }

  private boolean runXtsCommand(
      TestInfo testInfo, XtsTradefedTestDriverSpec spec, Path tmpXtsRootDir, XtsType xtsType)
      throws MobileHarnessException, InterruptedException {
    CommandProcess xtsProcess = null;
    try {
      xtsProcess = runCommand(testInfo, spec, tmpXtsRootDir, xtsType);
      return xtsProcess.await().exitCode() == 0;
    } catch (CommandFailureException e) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              new MobileHarnessException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR,
                  "Failed to run the xTS command: " + e.getMessage(),
                  e));
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
      TestInfo testInfo, XtsTradefedTestDriverSpec spec, Path tmpXtsRootDir, XtsType xtsType)
      throws MobileHarnessException, InterruptedException {
    String[] cmd = getXtsCommand(spec, tmpXtsRootDir, xtsType);
    ImmutableMap<String, String> env = getEnvironmentToTradefedConsole(tmpXtsRootDir, xtsType);
    // Logs command string for debug purpose
    StringBuilder cmdString =
        new StringBuilder(Joiner.on(' ').withKeyValueSeparator("=").join(env));
    if (cmdString.length() > 0) {
      cmdString.append(' ');
    }
    Joiner.on(' ').appendTo(cmdString, cmd);
    logger.atInfo().log("Running %s command:%n%s", xtsType.name(), cmdString);
    try {
      // The writer will be closed after the command exits.
      @SuppressWarnings("resource")
      BufferedWriter writer =
          Files.newBufferedWriter(Path.of(testInfo.getGenFileDir()).resolve(XTS_TF_LOG));

      return cmdExecutor.start(
          Command.of(cmd)
              .extraEnv(env)
              .onStdout(
                  LineCallback.does(
                      line -> {
                        logRecorder.addLogRecord(
                            LogRecord.newBuilder()
                                .setFormattedLogRecord(line + "\n")
                                .setSourceType(SourceType.TF)
                                .build());
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

  private String[] getXtsCommand(
      XtsTradefedTestDriverSpec spec, Path tmpXtsRootDir, XtsType xtsType)
      throws MobileHarnessException {
    ImmutableList.Builder<String> xtsCommand =
        ImmutableList.<String>builder()
            .add(getJavaBinary(), "-Xmx6g", "-XX:+HeapDumpOnOutOfMemoryError");
    xtsCommand.add("-cp", getConcatenatedJarPath(tmpXtsRootDir, spec, xtsType));

    for (Map.Entry<String, String> systemProp :
        getSystemPropsToTradefedConsole(tmpXtsRootDir, xtsType).entrySet()) {
      xtsCommand.add(String.format("-D%s=%s", systemProp.getKey(), systemProp.getValue()));
    }

    xtsCommand.add(COMPATIBILITY_CONSOLE_CLASS);
    xtsCommand.addAll(getXtsRunCommandArgs(spec));

    return xtsCommand.build().toArray(new String[0]);
  }

  private String getJavaBinary() {
    return systemUtil.getJavaBin();
  }

  private String getConcatenatedJarPath(
      Path tmpXtsRootDir, XtsTradefedTestDriverSpec spec, XtsType xtsType)
      throws MobileHarnessException {
    LinkedHashSet<String> leadingJarsSet = getLeadingJarsInClasspath(spec);

    ListMultimap<String, Path> foundLeadingJars = ArrayListMultimap.create();
    ImmutableList.Builder<Path> restOfJars = ImmutableList.<Path>builder();
    try {
      Path linkXtsToolsDir = getXtsToolsDir(tmpXtsRootDir, xtsType);
      Path linkXtsToolsDirRealPath = linkXtsToolsDir.toRealPath();
      Path linkXtsTestcasesDir = getXtsTestcasesDir(tmpXtsRootDir, xtsType);
      Path linkXtsTestcasesDirRealPath = linkXtsTestcasesDir.toRealPath();

      localFileUtil
          .listFilePaths(
              linkXtsToolsDirRealPath,
              /* recursively= */ false,
              path ->
                  path.getFileName().toString().endsWith(".jar")
                      && !EXCLUDED_JAR_FILES.contains(path.getFileName().toString()))
          .forEach(
              jar -> {
                Path newJarPath = replacePathPrefix(jar, linkXtsToolsDirRealPath, linkXtsToolsDir);
                if (leadingJarsSet.contains(jar.getFileName().toString())) {
                  foundLeadingJars.put(jar.getFileName().toString(), newJarPath);
                } else {
                  restOfJars.add(newJarPath);
                }
              });
      localFileUtil
          .listFilePaths(
              linkXtsTestcasesDirRealPath,
              /* recursively= */ true,
              path -> path.getFileName().toString().endsWith(".jar"))
          .forEach(
              jar -> {
                Path newJarPath =
                    replacePathPrefix(jar, linkXtsTestcasesDirRealPath, linkXtsTestcasesDir);
                if (leadingJarsSet.contains(jar.getFileName().toString())) {
                  foundLeadingJars.put(jar.getFileName().toString(), newJarPath);
                } else {
                  restOfJars.add(newJarPath);
                }
              });

      ImmutableList.Builder<Path> result = ImmutableList.<Path>builder();
      for (String leadingJar : leadingJarsSet) {
        result.addAll(foundLeadingJars.get(leadingJar));
      }
      result.addAll(restOfJars.build());

      return Joiner.on(':').join(result.build());
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_LIST_JARS_ERROR,
          "Failed to list jars in tools and testcases directories.",
          e);
    }
  }

  private LinkedHashSet<String> getLeadingJarsInClasspath(XtsTradefedTestDriverSpec spec) {
    LinkedHashSet<String> leadingJarsSet = new LinkedHashSet<>();
    if (!spec.getLeadingJarsInClasspathList().isEmpty()) {
      leadingJarsSet = new LinkedHashSet<>(spec.getLeadingJarsInClasspathList());
    }
    return leadingJarsSet;
  }

  private Path replacePathPrefix(Path path, Path currentPrefix, Path newPrefix) {
    return Path.of(path.toString().replaceFirst(currentPrefix.toString(), newPrefix.toString()));
  }

  private Path getXtsRootDir(XtsTradefedTestDriverSpec spec) {
    if (spec.hasXtsRootDir()) {
      return Paths.get(spec.getXtsRootDir());
    }
    // TODO: Unzip android-xts zip file and return the xts root dir
    return null;
  }

  private Path getXtsJdkDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/jdk", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsToolsDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/tools", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsLibDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/lib", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsTestcasesDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(
        String.format("android-%s/testcases", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsResultsDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(
        String.format("android-%s/results", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsLogsDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/logs", Ascii.toLowerCase(xtsType.name())));
  }

  private ImmutableMap<String, String> getSystemPropsToTradefedConsole(
      Path tmpXtsRootDir, XtsType xtsType) {
    switch (xtsType) {
      case CTS:
        return ImmutableMap.of("CTS_ROOT", tmpXtsRootDir.toString());
    }
    return ImmutableMap.of();
  }

  private ImmutableMap<String, String> getEnvironmentToTradefedConsole(
      Path tmpXtsRootDir, XtsType xtsType) throws MobileHarnessException, InterruptedException {
    ImmutableMap.Builder<String, String> environmentToTradefedConsole = ImmutableMap.builder();
    environmentToTradefedConsole.put(
        "LD_LIBRARY_PATH", getConcatenatedLdLibraryPath(tmpXtsRootDir, xtsType));
    environmentToTradefedConsole.put("PATH", getEnvPath());
    return environmentToTradefedConsole.buildOrThrow();
  }

  private String getConcatenatedLdLibraryPath(Path tmpXtsRootDir, XtsType xtsType) {
    Path xtsLibDir = getXtsLibDir(tmpXtsRootDir, xtsType);
    return String.format("%s:%s64", xtsLibDir, xtsLibDir);
  }

  private String getEnvPath() throws MobileHarnessException, InterruptedException {
    List<String> envPathSegments = new ArrayList<>();
    String adbPath = adb.getAdbPath();
    envPathSegments.add(
        Ascii.equalsIgnoreCase(adbPath, "adb")
            ? getSdkToolPath("adb").getParent().toString()
            : new File(adbPath).getParent());

    String fastbootPath = fastboot.getFastbootPath();
    envPathSegments.add(
        Ascii.equalsIgnoreCase(fastbootPath, "fastboot")
            ? getSdkToolPath("fastboot").getParent().toString()
            : new File(fastbootPath).getParent());

    if (systemUtil.getEnv("PATH") != null) {
      envPathSegments.add(systemUtil.getEnv("PATH"));
    }

    return Joiner.on(':').join(envPathSegments);
  }

  private Path getSdkToolPath(String sdkToolName)
      throws MobileHarnessException, InterruptedException {
    CommandResult result =
        cmdExecutor.exec(Command.of("which", sdkToolName).successExitCodes(0, 1));

    if (result.exitCode() != 0) {
      String possibleSdkTool = cmdExecutor.run(Command.of("whereis", sdkToolName));
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_SDK_TOOL_NOT_FOUND_ERROR,
          String.format(
              "Unable to find the sdk tool \"%s\". Executables found: %s",
              sdkToolName, possibleSdkTool));
    }
    return Path.of(result.stdout().trim());
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

  @VisibleForTesting
  Path prepareXtsWorkDir(XtsType xtsType) throws MobileHarnessException {
    try {
      Path dir =
          Paths.get(
              JAVA_IO_TMPDIR.value(),
              String.format("xts-root-dir-%s", UUID.randomUUID()),
              String.format("android-%s", Ascii.toLowerCase(xtsType.name())));
      localFileUtil.prepareDir(dir);
      localFileUtil.grantFileOrDirFullAccess(dir.getParent());
      return dir.getParent();
    } catch (MobileHarnessException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_CREATE_TEMP_DIR_ERROR, "Failed to create temp directory.", e);
    }
  }

  private void setUpXtsWorkDir(
      Path sourceXtsRootDir, Path tmpXtsWorkDir, XtsType xtsType, boolean isRunRetry)
      throws MobileHarnessException {
    Path sourceXtsBundledJdkDir = getXtsJdkDir(sourceXtsRootDir, xtsType);
    Path sourceXtsBundledTestcasesDir = getXtsTestcasesDir(sourceXtsRootDir, xtsType);
    Path sourceXtsBundledToolsDir = getXtsToolsDir(sourceXtsRootDir, xtsType);
    Path sourceXtsBundledLibDir = getXtsLibDir(sourceXtsRootDir, xtsType);
    Path sourceXtsBundledResultsDir = getXtsResultsDir(sourceXtsRootDir, xtsType);

    Path linkJdkDir = getXtsJdkDir(tmpXtsWorkDir, xtsType);
    Path linkTestcasesDir = getXtsTestcasesDir(tmpXtsWorkDir, xtsType);
    Path linkToolsDir = getXtsToolsDir(tmpXtsWorkDir, xtsType);
    Path linkLibDir = getXtsLibDir(tmpXtsWorkDir, xtsType);

    createSymlink(linkJdkDir, sourceXtsBundledJdkDir);
    createSymlink(linkTestcasesDir, sourceXtsBundledTestcasesDir);
    createSymlink(linkToolsDir, sourceXtsBundledToolsDir);
    createSymlink(linkLibDir, sourceXtsBundledLibDir);

    if (isRunRetry && localFileUtil.isDirExist(sourceXtsBundledResultsDir)) {
      // For "run retry", TF looks for the corresponding previous result dir per given session id.
      // So it needs to "copy" previous result dirs and their content so TF can locate the needed
      // files to start the retry.
      Path resultsDirInTmpXtsWorkDir = getXtsResultsDir(tmpXtsWorkDir, xtsType);
      localFileUtil.prepareDir(resultsDirInTmpXtsWorkDir);
      localFileUtil.grantFileOrDirFullAccess(resultsDirInTmpXtsWorkDir);

      List<Path> existingResultDirs = localFileUtil.listDirs(sourceXtsBundledResultsDir);
      previousResultDirNames =
          existingResultDirs.stream()
              .map(existingResultDir -> existingResultDir.getFileName().toString())
              .collect(toImmutableSet());
      for (Path existingResultDir : existingResultDirs) {
        Path resultDirInTmpXtsWorkDir =
            resultsDirInTmpXtsWorkDir.resolve(existingResultDir.getFileName().toString());
        localFileUtil.prepareDir(resultDirInTmpXtsWorkDir);
        List<Path> filesOrDirsInOneResultDir =
            localFileUtil.listFilesOrDirs(existingResultDir, p -> true);
        for (Path fileOrDir : filesOrDirsInOneResultDir) {
          createSymlink(
              resultDirInTmpXtsWorkDir.resolve(fileOrDir.getFileName().toString()), fileOrDir);
        }
      }
    }
  }

  @CanIgnoreReturnValue
  private Path createSymlink(Path link, Path target) throws MobileHarnessException {
    try {
      // if the link already existed, we have to remove it before creating
      Files.deleteIfExists(link);
      Files.createSymbolicLink(link, target);
    } catch (IOException ioe) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_CREATE_SYMLINK_ERROR,
          String.format("Failed to create symbolic link [%s] to [%s]", link, target),
          ioe);
    } catch (UnsupportedOperationException uoe) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_CREATE_SYMLINK_UNSUPPORTED_ERROR,
          String.format(
              "Failed to create symbolic link [%s] to [%s] - unsupported operation", link, target),
          uoe);
    }
    return link;
  }

  /** Type for xTS. */
  public enum XtsType {
    CTS
  }
}
