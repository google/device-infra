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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
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
import com.google.devtools.mobileharness.infra.ats.server.sessionplugin.TradefedConfigGenerator;
import com.google.devtools.mobileharness.infra.ats.tradefed.NonXtsRunStrategy;
import com.google.devtools.mobileharness.infra.ats.tradefed.TradefedRunStrategy;
import com.google.devtools.mobileharness.infra.ats.tradefed.XtsRunStrategy;
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord.SourceType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidJitEmulatorUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsCommandUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.message.proto.TestMessageProto.XtsTradefedRunCancellation;
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
import com.google.wireless.qa.mobileharness.shared.api.spec.TradefedTestSpec;
import com.google.wireless.qa.mobileharness.shared.comm.message.event.TestMessageEvent;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.spec.SpecConfigable;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.TradefedTestDriverSpec;
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
public class TradefedTest extends BaseDriver
    implements TradefedTestSpec, SpecConfigable<TradefedTestDriverSpec> {
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
  private final XtsCommandUtil xtsCommandUtil;
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
  TradefedTest(
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
      Clock clock,
      XtsCommandUtil xtsCommandUtil) {
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
            "tradefed-test-" + testInfo.locator().getId(), /* corePoolSize= */ 2);
    this.resUtil = resUtil;
    this.clock = clock;
    this.xtsCommandUtil = xtsCommandUtil;
  }

  @Override
  public void run(TestInfo testInfo) throws MobileHarnessException, InterruptedException {
    TradefedTestDriverSpec spec = testInfo.jobInfo().combinedSpec(this);
    String xtsType = Strings.emptyToNull(spec.getXtsType());
    tradefedRunStrategy =
        spec.getXtsType().isEmpty()
            ? new NonXtsRunStrategy(localFileUtil)
            : new XtsRunStrategy(
                localFileUtil, resUtil, systemUtil, clock, xtsType, xtsCommandUtil);

    CompositeDeviceUtil.cacheTestbed(testInfo, getDevice());
    Path workDir = null; // This will be TF_WORK_DIR
    try {
      workDir = createWorkDir();
      tradefedRunStrategy.setUpWorkDir(spec, workDir, testInfo);
      logger.atInfo().log("Tradefed temp working root directory is %s", workDir);
      if (tradefedRunStrategy instanceof XtsRunStrategy) {
        try {
          ((XtsRunStrategy) tradefedRunStrategy)
              .saveFilteredExpandedModuleNames(testInfo, workDir, getDevice());
        } catch (MobileHarnessException e) {
          logger.atWarning().withCause(e).log(
              "Failed when saving filtered expanded module names. Non-fatal; continuing test.");
        } catch (InterruptedException e) {
          logger.atWarning().withCause(e).log(
              "Interrupted when saving filtered expanded module names. Non-fatal; continuing"
                  + " test.");
          Thread.currentThread().interrupt();
        }
      }

      Optional<Integer> tfExitCode = runTradefedCommand(testInfo, spec, workDir);

      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Finished running %s test. TF run command exit code: %s",
              spec.getXtsType(), tfExitCode);
      setTestResult(testInfo, tfExitCode.orElse(null));
      addTestResultPropertiesToJob(workDir, xtsType, testInfo);
    } finally {
      scheduledThreadPool.shutdown();
      CompositeDeviceUtil.uncacheTestbed(getDevice());
      postTest(workDir, testInfo);
    }
  }

  private void addTestResultPropertiesToJob(
      Path workDir, @Nullable String xtsType, TestInfo testInfo) throws MobileHarnessException {
    // TODO: Remove this check once the non-xTS run can also generate result files.
    if (xtsType == null) {
      return;
    }
    Path resultsDir = XtsDirUtil.getXtsResultsDir(workDir, xtsType);
    if (localFileUtil.isDirExist(resultsDir)) {
      List<Path> resultDirs =
          localFileUtil.listFilesOrDirs(
              resultsDir,
              path ->
                  localFileUtil.isDirExist(path)
                      && tradefedRunStrategy.getCurrentSessionResultFilter().test(path));
      if (!resultDirs.isEmpty()) {
        Path testResultXmlPath = resultDirs.get(0).resolve("test_result.xml");
        if (localFileUtil.isFileExist(testResultXmlPath)) {
          testInfo.properties().add(XtsConstants.TRADEFED_JOBS_HAS_RESULT_FILE, "true");
        }
      }
    }
  }

  private void setTestResult(TestInfo testInfo, @Nullable Integer tfExitCode) {
    if (tfExitCode == null) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              MobileHarnessExceptionFactory.createUserFacingException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_ERROR,
                  "Tradefed command didn't start",
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
                  "Non-zero Tradefed command exit code: " + tfExitCode,
                  /* cause= */ null));
      return;
    }
    testInfo.resultWithCause().setPass();
  }

  private void postTest(Path workDir, TestInfo testInfo) {
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
      Path genFileDir = tradefedRunStrategy.getGenFileDir(testInfo);
      Path resultsInWorkDir = tradefedRunStrategy.getResultsDirInWorkDir(workDir);
      Path logsInWorkDir = tradefedRunStrategy.getLogsDirInWorkDir(workDir);

      localFileUtil.prepareDir(genFileDir);
      localFileUtil.grantFileOrDirFullAccess(genFileDir);

      if (localFileUtil.isDirExist(resultsInWorkDir)) {
        // For "run retry", needs to skip those previous generated results and only copy the result
        // files belonging to this run.
        List<Path> resultsToCopy =
            localFileUtil.listFilesOrDirs(
                resultsInWorkDir, tradefedRunStrategy.getCurrentSessionResultFilter()::test);
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
      TestInfo testInfo, TradefedTestDriverSpec spec, Path workDir)
      throws MobileHarnessException, InterruptedException {
    ImmutableMap<String, String> env =
        tradefedRunStrategy.getEnvironment(workDir, spec, getDevice(), getEnvPath());

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

    ImmutableList<String> runCommandArgs = getTradefedRunCommandArgs(spec, env, testInfo);
    String classpath =
        requireNonNull(
            env.getOrDefault(
                TF_PATH_KEY, tradefedRunStrategy.getConcatenatedJarPath(workDir, spec)));
    ImmutableList<String> cmd =
        xtsCommandUtil.getTradefedJavaCommand(
            tradefedRunStrategy.getJavaPath(workDir),
            jvmFlagsBuilder.build(),
            classpath,
            tradefedRunStrategy.getJvmDefines(workDir),
            tradefedRunStrategy.getMainClass(),
            runCommandArgs);

    // Logs command string for debug purpose
    StringBuilder commandString =
        new StringBuilder(Joiner.on(' ').withKeyValueSeparator("=").join(env));
    if (commandString.length() > 0) {
      commandString.append(' ');
    }
    Joiner.on(' ').appendTo(commandString, cmd);
    logger.atInfo().log("Running %s command:%n%s", spec.getXtsType(), commandString);

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
                  "Skip starting TF since it was cancelled: %s",
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
                    .timeout(getTradefedTimeout(testInfo)));
        process = tfProcess;
        long pid = process.getPid();
        testInfo.log().atInfo().alsoTo(logger).log("TF started, pid=%s", pid);
      }

      return Optional.of(process.await().exitCode());
    } catch (CommandStartException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_START_COMMAND_ERROR,
          "Failed to start Tradefed command: " + commandString,
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
                  "Failed to run Tradefed command: " + e.getMessage(),
                  e));
      return Optional.of(e.result().exitCode());
    } catch (CommandTimeoutException e) {
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.TIMEOUT,
              MobileHarnessExceptionFactory.createUserFacingException(
                  AndroidErrorId.XTS_TRADEFED_RUN_COMMAND_TIMEOUT,
                  "Tradefed run command timed out.",
                  e));
      return Optional.of(e.result().exitCode());
    } catch (InterruptedException e) {
      testInfo.log().atWarning().alsoTo(logger).log("Tradefed was interrupted.");
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
                            tfProcess = TradefedTest.this.tfProcess;
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

  private ImmutableList<String> getTradefedRunCommandArgs(
      TradefedTestDriverSpec spec, Map<String, String> envVars, TestInfo testInfo)
      throws MobileHarnessException {
    ImmutableList.Builder<String> tradefedRunCommand =
        ImmutableList.<String>builder().add("run", "commandAndExit");

    ImmutableList.Builder<String> tradefedCommandBuilder = ImmutableList.<String>builder();
    String testPlan =
        isRunRetryWithSubPlan(spec) ? spec.getPrevSessionXtsTestPlan() : spec.getXtsTestPlan();
    if (!testPlan.isEmpty()) {
      tradefedCommandBuilder.add(testPlan);
    }
    if (isRunWithSubPlan(spec)) {
      tradefedCommandBuilder.add(
          "--subplan", com.google.common.io.Files.getNameWithoutExtension(spec.getSubplanXml()));
    }
    if (useTfRunRetry(spec, testInfo)) {
      // In setUpXtsWorkDir, it copies the previous session's test-record proto files and
      // test_result.xml file under a result dir, which always has session index 0
      tradefedCommandBuilder.add("--retry", "0");
      if (!spec.getRetryType().isEmpty()) {
        tradefedCommandBuilder.add("--retry-type", spec.getRetryType());
      }
    }
    ImmutableList<String> tradefedCommand =
        tradefedCommandBuilder.addAll(getExtraRunCommandArgs(spec)).build();

    if (spec.getXtsTestPlanFile().isEmpty()) {
      tradefedRunCommand.addAll(tradefedCommand);
    } else {
      // Build final config based on local env vars
      String configTemplate = localFileUtil.readFile(spec.getXtsTestPlanFile());
      StringSubstitutor sub = new StringSubstitutor(envVars);
      String config = sub.replace(configTemplate);

      ImmutableList.Builder<String> formattedCommandBuilder = ImmutableList.builder();
      for (int i = 0; i < tradefedCommand.size(); i++) {
        if (i > 0 && FILTER_KEYS.contains(tradefedCommand.get(i - 1))) {
          formattedCommandBuilder.add(String.format("&quot;%s&quot;", tradefedCommand.get(i)));
        } else {
          formattedCommandBuilder.add(
              tradefedCommand.get(i).replace("\"", "&quot;").replace("\\", "\\\\"));
        }
      }
      // Replace ${COMMAND} with the Tradefed command
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
      logger.atInfo().log("Run Tradefed cluster command with config:\n%s", config);
      String testPlanFile =
          Path.of(testInfo.getGenFileDir())
              .resolve(Path.of(spec.getXtsTestPlanFile()).getFileName())
              .toString();
      localFileUtil.writeToFile(testPlanFile, config);
      tradefedRunCommand.add(testPlanFile);
    }

    // Appends allocated device(s) serial
    if (getDevice().hasDimension(Dimension.Name.DEVICE_CLASS_NAME, "AndroidJitEmulator")) {
      logger.atInfo().log("Adding TF-based virtual device name to Tradefed run command");
      getDeviceIds()
          .forEach(
              serial ->
                  tradefedRunCommand.add(
                      "-s", AndroidJitEmulatorUtil.getVirtualDeviceNameInTradefed(serial)));
    } else {
      getDeviceIds().forEach(serial -> tradefedRunCommand.add("-s", serial));
    }

    return tradefedRunCommand.build();
  }

  private static ImmutableList<String> getExtraRunCommandArgs(TradefedTestDriverSpec spec) {
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

  private static Duration getTradefedTimeout(TestInfo testInfo) throws MobileHarnessException {
    // Use remaining time to run the Tradefed command but leave 2 minutes for post processing.
    return testInfo.timer().remainingTimeJava().minusMinutes(2);
  }

  @VisibleForTesting
  Path createWorkDir() {
    return Path.of(
        Flags.instance().atsXtsWorkDir.getNonNull().isEmpty()
            ? requireNonNull(JAVA_IO_TMPDIR.value())
            : Flags.instance().atsXtsWorkDir.getNonNull(),
        String.format("tradefed-root-dir-%s", this.testId));
  }

  private static boolean isRunRetryWithSubPlan(TradefedTestDriverSpec spec) {
    return spec.getXtsTestPlan().equals("retry") && !spec.getSubplanXml().isEmpty();
  }

  private static boolean useTfRunRetry(TradefedTestDriverSpec spec, TestInfo testInfo) {
    return spec.getXtsTestPlan().equals("retry")
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
  private static boolean isRunWithSubPlan(TradefedTestDriverSpec spec) {
    return !spec.getSubplanXml().isEmpty();
  }

  private String getTradefedAgentFilePath() throws MobileHarnessException {
    return resUtil.getResourceFile(getClass(), TF_AGENT_RESOURCE_PATH);
  }
}
