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
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.devtools.deviceinfra.platform.android.lightning.internal.sdk.adb.Adb;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.TradefedConfigGenerator;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord.SourceType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidJitEmulatorUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper;
import com.google.devtools.mobileharness.platform.android.xts.suite.TestSuiteHelper.DeviceInfo;
import com.google.devtools.mobileharness.shared.constant.LogRecordImportance.Importance;
import com.google.devtools.mobileharness.shared.util.command.Command;
import com.google.devtools.mobileharness.shared.util.command.CommandExecutor;
import com.google.devtools.mobileharness.shared.util.command.CommandFailureException;
import com.google.devtools.mobileharness.shared.util.command.CommandProcess;
import com.google.devtools.mobileharness.shared.util.command.CommandResult;
import com.google.devtools.mobileharness.shared.util.command.CommandStartException;
import com.google.devtools.mobileharness.shared.util.command.CommandTimeoutException;
import com.google.devtools.mobileharness.shared.util.command.LineCallback;
import com.google.devtools.mobileharness.shared.util.command.LineCallbackException;
import com.google.devtools.mobileharness.shared.util.command.linecallback.CommandOutputLogger;
import com.google.devtools.mobileharness.shared.util.concurrent.ThreadPools;
import com.google.devtools.mobileharness.shared.util.error.MoreThrowables;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.ResUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat.ParseException;
import com.google.wireless.qa.mobileharness.shared.android.Aapt;
import com.google.wireless.qa.mobileharness.shared.api.CompositeDeviceUtil;
import com.google.wireless.qa.mobileharness.shared.api.annotation.DriverAnnotation;
import com.google.wireless.qa.mobileharness.shared.api.device.CompositeDevice;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.api.spec.XtsTradefedTestSpec;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.XtsTradefedTestDriverSpec;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import org.apache.commons.text.StringSubstitutor;

/** Driver for running Tradefed based xTS test suites. */
@DriverAnnotation(help = "Running Tradefed based xTS test suites.")
public class XtsTradefedTest extends BaseDriver
    implements XtsTradefedTestSpec, SpecConfigable<XtsTradefedTestDriverSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> FILTER_KEYS =
      ImmutableSet.of(
          "--include-filter",
          "--exclude-filter",
          "--compatibility:include-filter",
          "--compatibility:exclude-filter");

  private static final String TF_PATH_KEY = "TF_PATH";

  private static final Duration KILL_TF_AFTER_FINISH_TIME = Duration.ofMinutes(4L);

  private static final String TF_AGENT_RESOURCE_PATH =
      "/com/google/devtools/mobileharness/platform/android/xts/agent/tradefed_invocation_agent_deploy.jar";

  private final CommandExecutor cmdExecutor;
  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;
  private final Adb adb;
  private final Aapt aapt;
  private final LogRecorder logRecorder;
  private final ListeningExecutorService threadPool;
  private final ListeningScheduledExecutorService scheduledThreadPool;
  private final Sleeper sleeper;
  private final ResUtil resUtil;
  private final Clock clock;
  private final String testId;
  private TradefedRunStrategy tradefedRunStrategy;

  private final Object tfProcessLock = new Object();

  @Nullable
  @GuardedBy("tfProcessLock")
  private XtsTradefedRunCancellation xtsTradefedRunCancellation;

  @Nullable
  @GuardedBy("tfProcessLock")
  private CommandProcess tfProcess;

  @Inject
  XtsTradefedTest(
      Device device,
      TestInfo testInfo,
      CommandExecutor cmdExecutor,
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil,
      Adb adb,
      Aapt aapt,
      ListeningExecutorService threadPool,
      Sleeper sleeper,
      ResUtil resUtil,
      Clock clock) {
    super(device, testInfo);
    this.cmdExecutor = cmdExecutor;
    this.localFileUtil = localFileUtil;
    this.systemUtil = systemUtil;
    this.adb = adb;
    this.aapt = aapt;
    this.threadPool = threadPool;
    this.sleeper = sleeper;
    this.logRecorder = LogRecorder.getInstance();
    this.testId = testInfo.locator().getId();
    this.scheduledThreadPool =
        ThreadPools.createStandardScheduledThreadPool(
            "xts-tradefed-test-" + testInfo.locator().getId(), /* corePoolSize= */ 2);
    this.resUtil = resUtil;
    this.clock = clock;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    XtsTradefedTestDriverSpec spec = testInfo.jobInfo().combinedSpec(this);

    String xtsType = spec.getXtsType();
    boolean isXtsRun = !xtsType.isEmpty();
    tradefedRunStrategy =
        isXtsRun
            ? new XtsRunStrategy(localFileUtil, resUtil, systemUtil, clock)
            : new NonXtsRunStrategy(localFileUtil);

    CompositeDeviceUtil.cacheTestbed(testInfo, getDevice());
    Path workDir = null; // This will be TF_WORK_DIR
    try {
      workDir = createWorkDir();
      tradefedRunStrategy.setUpWorkDir(spec, workDir, xtsType, testInfo);
      logger.atInfo().log("Tradefed temp working root directory is %s", workDir);
      saveFilteredExpandedModuleNames(testInfo, workDir, xtsType);

      Optional<Integer> tfExitCode = runTradefedCommand(testInfo, spec, workDir, xtsType);

      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Finished running %s test. TF run command exit code: %s", xtsType, tfExitCode);
      setTestResult(testInfo, tfExitCode.orElse(null));
      addTestResultPropertiesToJob(workDir, xtsType, testInfo);
    } finally {
      scheduledThreadPool.shutdown();
      CompositeDeviceUtil.uncacheTestbed(getDevice());
      postTest(workDir, testInfo, xtsType);
    }
  }

  /**
   * Saves the filtered expanded module names (e.g. `arm64-v8a CtsBatteryHealthTestCases`) to the
   * test properties.
   *
   * <p>This is done so that the result processing logic later may use this list to genearte the
   * result files with correct module info when it's not available from tradefed generated result
   * files (due to errors encountered by the tf process etc.).
   */
  private void saveFilteredExpandedModuleNames(TestInfo testInfo, Path workDir, String xtsType)
      throws MobileHarnessException, InterruptedException {
    if (xtsType.isEmpty()) {
      return;
    }
    TestSuiteHelper suiteHelper = new TestSuiteHelper(workDir.toString(), xtsType);
    // It's a map of expanded module names (e.g. `arm64-v8a CtsBatteryHealthTestCases`) to their
    // configurations.
    Map<String, Configuration> configs =
        suiteHelper.loadTests(
            DeviceInfo.builder()
                .setDeviceId(getDevice().getDeviceId())
                .setSupportedAbiList(
                    testInfo
                        .jobInfo()
                        .properties()
                        .getOptional(Job.DEVICE_SUPPORTED_ABI_LIST)
                        .orElse(""))
                .build());

    // Filtered by include/exclude filters, the given module names, and subplan.
    // Non-expanded, e.g. `CtsBatteryHealthTestCases`.
    List<String> filteredTradefedModules =
        Splitter.on(",")
            .omitEmptyStrings()
            .splitToList(
                testInfo
                    .jobInfo()
                    .properties()
                    .getOptional(Job.FILTERED_TRADEFED_MODULES)
                    .orElse(""));

    // List of expanded module names (e.g. `arm64-v8a CtsBatteryHealthTestCases`), separated by
    // comma.
    String filteredExpandedModules =
        configs.entrySet().stream()
            .filter(
                entry ->
                    filteredTradefedModules.contains(entry.getValue().getMetadata().getXtsModule()))
            .map(Entry::getKey)
            .collect(joining(","));

    testInfo
        .properties()
        .add(
            XtsConstants.TRADEFED_FILTERED_EXPANDED_MODULES_FOR_TEST_PROPERTY_KEY,
            filteredExpandedModules);
  }

  private void setTestResult(TestInfo testInfo, @Nullable Integer tfExitCode) {
    if (tfExitCode == null) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              MobileHarnessExceptionFactory.createUserFacingException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR,
                  "xTS command didn't start",
                  /* cause= */ null));
      return;
    }
    if (tfExitCode != 0) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              MobileHarnessExceptionFactory.createUserFacingException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR,
                  "Non-zero xTS command exit code: " + tfExitCode,
                  /* cause= */ null));
      return;
    }
    testInfo.resultWithCause().setPass();
  }

  private void postTest(Path workDir, TestInfo testInfo, String xtsType) {
    if (workDir == null) {
      logger.atInfo().log(
          "Tradefed temp working dir is not initialized, skip post test processing.");
      return;
    }
    // Copies TF generated logs and results for this invocation in the test's gen file dir, so
    // they will be transferred to the client side.
    // The result file structure will look like:
    // test_gen_dir/android-<xts>-gen-files/
    // |---- results/YYYY.MM.DD_HH.MM.SS/
    // |---- logs/YYYY.MM.DD_HH.MM.SS/
    try {
      Path genFileDir;
      Path resultsInWorkDir;
      Path logsInWorkDir;

      if (!xtsType.isEmpty()) {
        genFileDir =
            Path.of(testInfo.getGenFileDir(), String.format("android-%s-gen-files", xtsType));
        resultsInWorkDir = XtsDirUtil.getXtsResultsDir(workDir, xtsType);
        logsInWorkDir = XtsDirUtil.getXtsLogsDir(workDir, xtsType);
      } else {
        genFileDir = Path.of(testInfo.getGenFileDir(), "tradefed-gen-files");
        resultsInWorkDir = workDir.resolve("results");
        logsInWorkDir = workDir.resolve("logs");
      }

      localFileUtil.prepareDir(genFileDir);
      localFileUtil.grantFileOrDirFullAccess(genFileDir);

      if (localFileUtil.isDirExist(resultsInWorkDir)) {
        List<Path> resultsToCopy;
        if (!xtsType.isEmpty()) {
          resultsToCopy =
              localFileUtil.listFilesOrDirs(
                  resultsInWorkDir,
                  path ->
                      !tradefedRunStrategy
                              .getPreviousResultDirNames()
                              .contains(path.getFileName().toString())
                          && !Objects.equals(path.getFileName().toString(), "latest"));
        } else {
          resultsToCopy = localFileUtil.listFilesOrDirs(resultsInWorkDir, path -> true);
        }
        Path resultsGenDir = genFileDir.resolve("results");
        localFileUtil.prepareDir(resultsGenDir);
        localFileUtil.grantFileOrDirFullAccess(resultsGenDir);
        for (Path resultFileOrDir : resultsToCopy) {
          localFileUtil.copyFileOrDir(resultFileOrDir, resultsGenDir);
        }
      }
      if (localFileUtil.isDirExist(logsInWorkDir)) {
        localFileUtil.copyFileOrDir(logsInWorkDir, genFileDir.resolve("logs"));
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Error when copying Tradefed gen files: %s", MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when copying Tradefed gen files: %s", MoreThrowables.shortDebugString(e));
      Thread.currentThread().interrupt();
    }

    try {
      localFileUtil.removeFileOrDir(workDir);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to clean up Tradefed temp directory [%s]: %s",
          workDir, MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when clean up Tradefed temp directory [%s]: %s",
          workDir, MoreThrowables.shortDebugString(e));
      Thread.currentThread().interrupt();
    }
  }

  @Subscribe
  private void onTestMessage(TestMessageEvent event) throws ParseException, InterruptedException {
    XtsTradefedRunCancellation.Builder xtsTradefedRunCancellation =
        XtsTradefedRunCancellation.newBuilder();
    if (event.decodeProtoTestMessage(
        xtsTradefedRunCancellation, ExtensionRegistry.getEmptyRegistry())) {
      handleXtsTradefedRunCancellation(xtsTradefedRunCancellation.build());
    }
  }

  private void handleXtsTradefedRunCancellation(
      XtsTradefedRunCancellation xtsTradefedRunCancellation) throws InterruptedException {
    getTest()
        .log()
        .atInfo()
        .alsoTo(logger)
        .log(
            "Receive XtsTradefedRunCancellation: %s", shortDebugString(xtsTradefedRunCancellation));
    synchronized (tfProcessLock) {
      this.xtsTradefedRunCancellation = xtsTradefedRunCancellation;
      if (tfProcess != null) {
        int signal = xtsTradefedRunCancellation.getKillTradefedSignal();
        getTest().log().atInfo().alsoTo(logger).log("Kill TF with signal %s", signal);
        tfProcess.killWithSignal(signal);
      }
    }
  }

  /** Returns the exit code of the TF process or empty if the process doesn't start. */
  private Optional<Integer> runTradefedCommand(
      TestInfo testInfo, XtsTradefedTestDriverSpec spec, Path workDir, String xtsType)
      throws MobileHarnessException, InterruptedException {
    ImmutableMap<String, String> env =
        tradefedRunStrategy.getEnvironment(workDir, xtsType, spec, getDevice(), getEnvPath());

    boolean disableTfResultLog = spec.hasDisableTfResultLog() && spec.getDisableTfResultLog();

    // Creates runtime info file path.
    Path runtimeInfoFilePath =
        Path.of(testInfo.getGenFileDir()).resolve(XtsConstants.TRADEFED_RUNTIME_INFO_FILE_NAME);
    testInfo
        .properties()
        .add(XtsConstants.TRADEFED_RUNTIME_INFO_FILE_PATH, runtimeInfoFilePath.toString());

    // Creates JVM flags.
    ImmutableList.Builder<String> jvmFlagsBuilder =
        ImmutableList.<String>builder()
            .add(
                "-Xmx" + Flags.instance().xtsTfXmx.getNonNull(), "-XX:+HeapDumpOnOutOfMemoryError");
    if (Flags.instance().enableXtsTradefedInvocationAgent.getNonNull()) {
      jvmFlagsBuilder.add(
          String.format("-javaagent:%s=%s", getTradefedAgentFilePath(), runtimeInfoFilePath));
    }

    ImmutableList<String> runCommandArgs = getXtsRunCommandArgs(spec, env, testInfo);
    String classpath =
        requireNonNull(
            env.getOrDefault(
                TF_PATH_KEY, tradefedRunStrategy.getConcatenatedJarPath(workDir, spec, xtsType)));
    ImmutableList<String> cmd =
        ImmutableList.<String>builder()
            .add(tradefedRunStrategy.getJavaPath(workDir, xtsType))
            .addAll(jvmFlagsBuilder.build())
            .add("-cp")
            .add(classpath)
            .addAll(tradefedRunStrategy.getJvmDefines(workDir, xtsType))
            .add(tradefedRunStrategy.getMainClass())
            .addAll(runCommandArgs)
            .build();

    // Logs command string for debug purpose
    StringBuilder commandString =
        new StringBuilder(Joiner.on(' ').withKeyValueSeparator("=").join(env));
    if (commandString.length() > 0) {
      commandString.append(' ');
    }
    Joiner.on(' ').appendTo(commandString, cmd);
    logger.atInfo().log("Running %s command:%n%s", xtsType, commandString);

    // Creates CommandOutputLogger.
    String testId = testInfo.locator().getId();
    String outputLoggerPrefix =
        String.format("TF-%s ", testId.substring(0, min(4, testId.length())));
    CommandOutputLogger commandOutputLogger =
        new CommandOutputLogger(outputLoggerPrefix, outputLoggerPrefix);

    // Gets session client ID if any.
    @Nullable
    String olcSessionClientId = spec.hasOlcSessionClientId() ? spec.getOlcSessionClientId() : null;

    // Prepares TF output file.
    Path tfOutputPath;
    if (spec.hasXtsLogRootPath()) {
      Path logRootPath = Path.of(spec.getXtsLogRootPath());
      localFileUtil.prepareDir(logRootPath);
      String xtsJobType =
          testInfo
              .jobInfo()
              .properties()
              .getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME)
              .map(s -> Ascii.toLowerCase(s) + "_")
              .orElse("");
      Path invocationPath =
          localFileUtil.createTempDir(
              logRootPath, XtsConstants.TRADEFED_INVOCATION_DIR_NAME_PREFIX + xtsJobType);
      localFileUtil.setFilePermission(invocationPath, "rwxr-xr-x");
      tfOutputPath =
          invocationPath
              .resolve(
                  String.format(
                      "%s_test_%s",
                      testInfo.jobInfo().type().getDriver(), testInfo.locator().getId()))
              .resolve(XtsConstants.TRADEFED_OUTPUT_FILE_NAME);
      localFileUtil.prepareParentDir(tfOutputPath);
      testInfo
          .properties()
          .add(XtsConstants.TRADEFED_INVOCATION_DIR_NAME, invocationPath.getFileName().toString());
    } else {
      tfOutputPath =
          Path.of(testInfo.getGenFileDir()).resolve(XtsConstants.TRADEFED_OUTPUT_FILE_NAME);
    }

    CommandProcess process = null;
    try (BufferedWriter outputFileWriter = Files.newBufferedWriter(tfOutputPath)) {
      synchronized (tfProcessLock) {
        if (xtsTradefedRunCancellation != null) {
          testInfo
              .log()
              .atInfo()
              .alsoTo(logger)
              .log(
                  "Skip starting xTS TF since it was cancelled: %s",
                  shortDebugString(xtsTradefedRunCancellation));
          return Optional.empty();
        }
        tfProcess =
            cmdExecutor.start(
                Command.of(cmd)
                    .extraEnv(env)
                    .onStdout(
                        new TradefedStdoutLineCallback(
                            olcSessionClientId,
                            commandOutputLogger,
                            outputFileWriter,
                            disableTfResultLog))
                    .onExit(
                        commandResult -> {
                          synchronized (tfProcessLock) {
                            tfProcess = null;
                          }
                        })
                    .redirectStderr(true)
                    .needStdoutInResult(false)
                    .needStderrInResult(false)
                    .timeout(getXtsTimeout(testInfo)));
        process = tfProcess;
        long pid = process.getPid();
        testInfo.log().atInfo().alsoTo(logger).log("xTS TF started, pid=%s", pid);
      }

      return Optional.of(process.await().exitCode());
    } catch (CommandStartException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_START_COMMAND_ERROR,
          "Failed to start the xTS command: " + commandString,
          e);
    } catch (IOException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_COMMAND_OUTPUT_FILE_ERROR,
          "Failed to operate TF output file " + tfOutputPath,
          e);
    } catch (CommandFailureException e) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              MobileHarnessExceptionFactory.createUserFacingException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR,
                  "Failed to run the xTS command: " + e.getMessage(),
                  e));
      return Optional.of(e.result().exitCode());
    } catch (CommandTimeoutException e) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.TIMEOUT,
              MobileHarnessExceptionFactory.createUserFacingException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_TIMEOUT,
                  "xTS run command timed out.",
                  e));
      return Optional.of(e.result().exitCode());
    } catch (InterruptedException e) {
      testInfo.log().atWarning().alsoTo(logger).log("xTS Tradefed was interrupted.");
      throw e;
    } finally {
      if (process != null && process.isAlive()) {
        process.killAndThenKillForcibly(/* timeout= */ Duration.ofMinutes(1L));
      }
    }
  }

  private class TradefedStdoutLineCallback implements LineCallback {

    @Nullable private final String olcSessionClientId;
    private final CommandOutputLogger commandOutputLogger;
    private final Writer outputFileWriter;
    private final AtomicBoolean tfHasFinished = new AtomicBoolean();
    private final boolean disableTfResultLog;

    private boolean printingTfResult;

    private TradefedStdoutLineCallback(
        @Nullable String olcSessionClientId,
        CommandOutputLogger commandOutputLogger,
        Writer outputFileWriter,
        boolean disableTfResultLog) {
      this.olcSessionClientId = olcSessionClientId;
      this.commandOutputLogger = commandOutputLogger;
      this.outputFileWriter = outputFileWriter;
      this.disableTfResultLog = disableTfResultLog;
    }

    @Override
    public Response onLine(String line) {
      // Writes to LogManager (for log stream).
      if (tfResultStarting(line)) {
        printingTfResult = true;
        if (disableTfResultLog) {
          printToLogRecorder("Generating result of one TF execution");
        }
      }
      if (!printingTfResult || !disableTfResultLog) {
        printToLogRecorder(line);
      }
      if (tfResultEnded(line)) {
        printingTfResult = false;
        if (disableTfResultLog) {
          printToLogRecorder("Generated result of one TF execution");
        }
      }

      // Writes to CommandOutputLogger (for logs in logger files).
      commandOutputLogger.logStdoutLine(line);

      // Writes to TF log file.
      try {
        outputFileWriter.write(line + "\n");
      } catch (IOException e) {
        throw new LineCallbackException(
            "Failed to write", e, /* killCommand= */ false, /* stopReadingOutput= */ true);
      }

      // Checks if TF has finished.
      if (tfFinished(line) && tfHasFinished.compareAndSet(false, true)) {
        getTest()
            .log()
            .atInfo()
            .alsoTo(logger)
            .log(
                "One TF finished (the command is still running, and it will"
                    + " finish after all TF and the post-processing"
                    + " finish)");
        logFailure(
            threadPool.submit(
                threadRenaming(
                    (Callable<Void>)
                        () -> {
                          sleeper.sleep(KILL_TF_AFTER_FINISH_TIME);

                          // Kills the TF process if it finished but the
                          // process is still alive.
                          CommandProcess tfProcess;
                          synchronized (tfProcessLock) {
                            tfProcess = XtsTradefedTest.this.tfProcess;
                          }
                          if (tfProcess != null && tfProcess.isAlive()) {
                            getTest()
                                .log()
                                .atInfo()
                                .alsoTo(logger)
                                .log(
                                    "Kill TF process since it has finished" + " for %s",
                                    KILL_TF_AFTER_FINISH_TIME);
                            tfProcess.killAndThenKillForcibly(
                                /* timeout= */ Duration.ofSeconds(50L));
                          }
                          return null;
                        },
                    () -> "tf-process-monitor-" + getTest().locator().getId())),
            Level.WARNING,
            "Error occurred when waiting TF process exits");
      }

      return Response.empty();
    }

    private void printToLogRecorder(String line) {
      LogRecord.Builder logRecord =
          LogRecord.newBuilder()
              .setFormattedLogRecord(line + "\n")
              .setSourceType(SourceType.TF)
              .setImportance(Importance.TF.value());
      if (olcSessionClientId != null) {
        logRecord.setClientId(olcSessionClientId);
      }
      logRecorder.addLogRecord(logRecord.build());
    }

    private boolean tfFinished(String line) {
      return line.contains("CommandScheduler: All done");
    }

    private boolean tfResultStarting(String line) {
      return line.equals("================= Results ==================");
    }

    private boolean tfResultEnded(String line) {
      return line.equals("============== End of Results ==============");
    }
  }

  @VisibleForTesting
  Path createWorkDir() {
    return Path.of(
        Flags.instance().atsXtsWorkDir.getNonNull().isEmpty()
            ? requireNonNull(JAVA_IO_TMPDIR.value())
            : Flags.instance().atsXtsWorkDir.getNonNull(),
        String.format("xts-root-dir-%s", this.testId));
  }

  private String getEnvPath() throws MobileHarnessException, InterruptedException {
    List<String> envPathSegments = new ArrayList<>();

    // Adds adb path.
    envPathSegments.add(getSdkToolDirPath(adb.getAdbPath(), "adb"));

    // Adds aapt path.
    envPathSegments.add(getSdkToolDirPath(aapt.getAaptPath(), "aapt"));

    // Adds existing paths.
    String existingPaths = systemUtil.getEnv("PATH");
    if (existingPaths != null) {
      envPathSegments.add(existingPaths);
    }

    return Joiner.on(':').join(envPathSegments);
  }

  private String getSdkToolDirPath(String sdkToolPath, String sdkToolName)
      throws MobileHarnessException, InterruptedException {
    return Ascii.equalsIgnoreCase(sdkToolPath, sdkToolName)
        ? getSdkToolAbsolutePath(sdkToolName).getParent().toString()
        : new File(sdkToolPath).getParent();
  }

  private Path getSdkToolAbsolutePath(String sdkToolPath)
      throws MobileHarnessException, InterruptedException {
    CommandResult result =
        cmdExecutor.exec(Command.of("which", sdkToolPath).successExitCodes(0, 1));

    if (result.exitCode() != 0) {
      String possibleSdkTool = cmdExecutor.run(Command.of("whereis", sdkToolPath));
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_SDK_TOOL_NOT_FOUND_ERROR,
          String.format(
              "Unable to find the sdk tool \"%s\". Executables found: %s",
              sdkToolPath, possibleSdkTool));
    }
    return Path.of(result.stdout().trim());
  }

  private ImmutableList<String> getXtsRunCommandArgs(
      XtsTradefedTestDriverSpec spec, Map<String, String> envVars, TestInfo testInfo)
      throws MobileHarnessException {
    ImmutableList.Builder<String> xtsRunCommand =
        ImmutableList.<String>builder().add("run", "commandAndExit");

    String testPlan =
        isRunRetryWithSubPlan(spec) ? spec.getPrevSessionXtsTestPlan() : spec.getXtsTestPlan();
    ImmutableList.Builder<String> xtsCommandBuilder = ImmutableList.<String>builder().add(testPlan);
    if (isRunWithSubPlan(spec)) {
      xtsCommandBuilder.add(
          "--subplan", com.google.common.io.Files.getNameWithoutExtension(spec.getSubplanXml()));
    }
    if (useTfRunRetry(spec, testInfo)) {
      // In setUpXtsWorkDir, it copies the previous session's test-record proto files and
      // test_result.xml file under a result dir, which always has session index 0
      xtsCommandBuilder.add("--retry", "0");
      if (!spec.getRetryType().isEmpty()) {
        xtsCommandBuilder.add("--retry-type", spec.getRetryType());
      }
    }
    ImmutableList<String> xtsCommand =
        xtsCommandBuilder.addAll(getExtraRunCommandArgs(spec)).build();
    logger.atInfo().log("spec: %s", spec);
    if (spec.getXtsTestPlanFile().isEmpty()) {
      xtsRunCommand.addAll(xtsCommand);
    } else {
      // Build final config based on local env vars
      String configTemplate = localFileUtil.readFile(spec.getXtsTestPlanFile());
      if (testInfo
              .jobInfo()
              .properties()
              .getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME)
              .orElse("")
              .equals(XtsConstants.DYNAMIC_MCTS_JOB_NAME)
          && !testInfo
              .jobInfo()
              .properties()
              .getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_INDEX)
              .orElse("0")
              .equals("0")
          && !spec.getNoDeviceActionXtsTestPlanFile().isEmpty()) {
        configTemplate = localFileUtil.readFile(spec.getNoDeviceActionXtsTestPlanFile());
      }
      StringSubstitutor sub = new StringSubstitutor(envVars);
      String config = sub.replace(configTemplate);

      ImmutableList.Builder<String> formattedCommandBuilder = ImmutableList.builder();
      for (int i = 0; i < xtsCommand.size(); i++) {
        if (i > 0 && FILTER_KEYS.contains(xtsCommand.get(i - 1))) {
          formattedCommandBuilder.add(String.format("&quot;%s&quot;", xtsCommand.get(i)));
        } else {
          formattedCommandBuilder.add(
              xtsCommand.get(i).replace("\"", "&quot;").replace("\\", "\\\\"));
        }
      }
      // Replace ${COMMAND} with the xTS command
      config =
          config.replace(
              TradefedConfigGenerator.COMMAND_LINE_TEMPLATE,
              String.join(" ", formattedCommandBuilder.build()));
      // Replace ${FILE_tag} with the real file path
      for (Entry<String, String> entry : testInfo.jobInfo().files().getAll().entries()) {
        config =
            config.replace(
                String.format(TradefedConfigGenerator.FILE_TEMPLATE, entry.getKey()),
                "file://" + entry.getValue());
      }
      // Redirect output to the gen file dir.
      config =
          config.replace(
              TradefedConfigGenerator.OUTPUT_DIR_TEMPLATE, "file://" + testInfo.getGenFileDir());
      logger.atInfo().log("Run xTS cluster command with config:\n%s", config);
      String testPlanFile =
          Path.of(testInfo.getGenFileDir())
              .resolve(Path.of(spec.getXtsTestPlanFile()).getFileName())
              .toString();
      localFileUtil.writeToFile(testPlanFile, config);
      xtsRunCommand.add(testPlanFile);
    }

    // Appends allocated device(s) serial
    if (getDevice().hasDimension(Dimension.Name.DEVICE_CLASS_NAME, "AndroidJitEmulator")) {
      logger.atInfo().log("Adding TF-based virtual device name to xts run command");
      getDeviceIds()
          .forEach(
              serial ->
                  xtsRunCommand.add(
                      "-s", AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed(serial)));
    } else {
      getDeviceIds().forEach(serial -> xtsRunCommand.add("-s", serial));
    }

    return xtsRunCommand.build();
  }

  private static ImmutableList<String> getExtraRunCommandArgs(XtsTradefedTestDriverSpec spec) {
    if (spec.hasRunCommandArgs()) {
      try {
        return ShellUtils.tokenize(spec.getRunCommandArgs());
      } catch (TokenizationException te) {
        logger.atWarning().withCause(te).log(
            "Failed to parse the run command args [%s]", spec.getRunCommandArgs());
        return ImmutableList.of();
      }
    }
    return ImmutableList.of();
  }

  private ImmutableList<String> getDeviceIds() {
    Device device = getDevice();
    if (device instanceof CompositeDevice compositeDevice) {
      return compositeDevice.getManagedDevices().stream()
          .map(this::getDeviceId)
          .collect(toImmutableList());
    }

    return ImmutableList.of(getDeviceId(device));
  }

  private String getDeviceId(Device device) {
    String id = device.getDeviceId();
    if (id.startsWith(AndroidAdbInternalUtil.OUTPUT_USB_ID_TOKEN)) {
      List<String> serials = device.getDimension("serial");
      if (!serials.isEmpty()) {
        id = serials.get(0);
      }
    }
    return id;
  }

  private static Duration getXtsTimeout(TestInfo testInfo) throws MobileHarnessException {
    // Use remaining time to run the xts command but leave 2 minutes for post processing.
    return testInfo.timer().remainingTimeJava().minusMinutes(2);
  }

  private void addTestResultPropertiesToJob(Path workDir, String xtsType, TestInfo testInfo)
      throws MobileHarnessException {
    // TODO: Remove this check once the non-xTS run can also generate result files.
    if (xtsType.isEmpty()) {
      return;
    }
    Path resultsDir = XtsDirUtil.getXtsResultsDir(workDir, xtsType);
    if (localFileUtil.isDirExist(resultsDir)) {
      List<Path> resultDirs =
          localFileUtil.listFilesOrDirs(
              resultsDir,
              path ->
                  localFileUtil.isDirExist(path)
                      && !tradefedRunStrategy
                          .getPreviousResultDirNames()
                          .contains(path.getFileName().toString())
                      && !Objects.equals(path.getFileName().toString(), "latest"));
      if (!resultDirs.isEmpty()) {
        Path testResultXmlPath = resultDirs.get(0).resolve("test_result.xml");
        if (localFileUtil.isFileExist(testResultXmlPath)) {
          testInfo.properties().add(XtsConstants.TRADEFED_JOBS_HAS_RESULT_FILE, "true");
        }
      }
    }
  }

  private static boolean isRunRetryWithSubPlan(XtsTradefedTestDriverSpec spec) {
    return spec.getXtsTestPlan().equals("retry") && !spec.getSubplanXml().isEmpty();
  }

  private static boolean useTfRunRetry(XtsTradefedTestDriverSpec spec, TestInfo testInfo) {
    return !spec.getXtsType().isEmpty()
        && spec.getXtsTestPlan().equals("retry")
        && !spec.getPrevSessionTestResultXml().isEmpty()
        && testInfo.jobInfo().files().isTagNotEmpty(TAG_PREV_SESSION_TEST_RECORD_PB_FILES);
  }

  /**
   * Checks if it's passed with a subplan xml file to run the tradefed.
   *
   * <p>There are two cases that a subplan xml file passed to this driver. #1 is "run retry" which
   * creates a subplan xml file and passes it to the driver and its test plan is "retry". #2 is
   * running with a given subplan name, which may create a modified subplan xml file and pass it to
   * the driver.
   */
  private static boolean isRunWithSubPlan(XtsTradefedTestDriverSpec spec) {
    return !spec.getSubplanXml().isEmpty();
  }

  private String getTradefedAgentFilePath() throws MobileHarnessException {
    return resUtil.getResourceFile(getClass(), TF_AGENT_RESOURCE_PATH);
  }
}
