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
import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;
import static com.google.devtools.mobileharness.shared.util.concurrent.Callables.threadRenaming;
import static com.google.devtools.mobileharness.shared.util.concurrent.MoreFutures.logFailure;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
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
import com.google.devtools.mobileharness.infra.client.longrunningservice.controller.LogRecorder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.LogProto.LogRecord.SourceType;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidJitEmulatorUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsCommandUtil;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.common.util.XtsDirUtil;
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
import com.google.devtools.mobileharness.shared.util.path.PathUtil;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils;
import com.google.devtools.mobileharness.shared.util.shell.ShellUtils.TokenizationException;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.devtools.mobileharness.shared.util.time.Sleeper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import org.apache.commons.text.StringSubstitutor;

/** Driver for running Tradefed based xTS test suites. */
@DriverAnnotation(help = "Running Tradefed based xTS test suites.")
public class XtsTradefedTest extends BaseDriver
    implements XtsTradefedTestSpec, SpecConfigable<XtsTradefedTestDriverSpec> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final ImmutableSet<String> EXCLUDED_JAR_FILES =
      ImmutableSet.of(
          "ats_console_deploy.jar",
          "ats_olc_server_deploy.jar",
          "ats_olc_server_local_mode_deploy.jar");
  private static final ImmutableList<String> EXCLUDED_JAR_FILE_PATTERNS =
      ImmutableList.of("art-run-test.*", "art-gtest-jars.*");

  private static final ImmutableSet<String> FILTER_KEYS =
      ImmutableSet.of(
          "--include-filter",
          "--exclude-filter",
          "--compatibility:include-filter",
          "--compatibility:exclude-filter");

  private static final ImmutableSet<String> DYNAMIC_JOB_TEST_DEPENDENCIES =
      ImmutableSet.of("CtsPreconditions", "CtsDeviceInfo");

  // Will remove these cases from MCTS.
  private static final ImmutableSet<String> STATIC_JOB_TEST_DEPENDENCIES =
      ImmutableSet.of(
          "cts-dalvik-host-test-runner",
          "net-tests-utils-host-common",
          "CtsBackupHostTestCases",
          "compatibility-host-provider-preconditions");

  private static final String TF_PATH_KEY = "TF_PATH";

  private static final Duration KILL_TF_AFTER_FINISH_TIME = Duration.ofMinutes(4L);

  // The max zip file is around 20GB, disk write speed is 100MB/s, and normally no more than 10
  // tests are doing unzip operation at the same time, therefore each test can unzip at 10MB/s speed
  // on average, and each test takes on average 34 minutes to finish unzipping. Adding some buffer
  // so that most tests can finish within timeout limit.
  private static final Duration ANDROID_XTS_ZIP_UNCOMPRESS_TIMEOUT = Duration.ofHours(2L);

  private static final String TF_AGENT_RESOURCE_PATH =
      "/com/google/devtools/mobileharness/platform/android/xts/agent/tradefed_invocation_agent_deploy.jar";

  private static final String STATIC_MCTS_LIST_FILE_PATH =
      "/devtools/mobileharness/infra/controller/test/util/xtsdownloader/configs/mcts_list.txt";

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

  private final Object tfProcessLock = new Object();

  @Nullable
  @GuardedBy("tfProcessLock")
  private XtsTradefedRunCancellation xtsTradefedRunCancellation;

  @Nullable
  @GuardedBy("tfProcessLock")
  private CommandProcess tfProcess;

  private volatile ImmutableSet<String> previousResultDirNames = ImmutableSet.of();

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
    // TODO: Remove this logging after debugging.
    logger.atInfo().log("spec: %s", spec);
    logger.atInfo().log("params: %s", testInfo.jobInfo().params());
    logger.atInfo().log("proto spec: %s", testInfo.jobInfo().protoSpec());

    String xtsType = spec.getXtsType();

    CompositeDeviceUtil.cacheTestbed(testInfo, getDevice());
    Path tmpXtsRootDir = null;
    try {
      tmpXtsRootDir = prepareXtsWorkDir(xtsType);
      setUpXtsWorkDir(spec, getXtsRootDir(spec, testInfo), tmpXtsRootDir, xtsType, testInfo);
      logger.atInfo().log("xTS Tradefed temp working root directory is %s", tmpXtsRootDir);

      Optional<Integer> tfExitCode = runXtsCommand(testInfo, spec, tmpXtsRootDir, xtsType);

      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log("Finished running %s test. xTS run command exit code: %s", xtsType, tfExitCode);
      setTestResult(testInfo, tfExitCode.orElse(null));
    } finally {
      scheduledThreadPool.shutdown();
      CompositeDeviceUtil.uncacheTestbed(getDevice());
      postTest(tmpXtsRootDir, testInfo, xtsType);
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

  private void postTest(Path tmpXtsRootDir, TestInfo testInfo, String xtsType) {
    if (tmpXtsRootDir == null) {
      logger.atInfo().log(
          "xTS Tradefed temp working dir is not initialized, skip post test processing.");
      return;
    }
    // Copies xTS TF generated logs and results for this invocation in the test's gen file dir, so
    // they will be transferred to the client side.
    // The result file structure will look like:
    // test_gen_dir/android-<xts>-gen-files/
    // |---- results/YYYY.MM.DD_HH.MM.SS/
    // |---- logs/YYYY.MM.DD_HH.MM.SS/
    try {
      Path xtsGenFileDir =
          Path.of(testInfo.getGenFileDir(), String.format("android-%s-gen-files", xtsType));

      localFileUtil.prepareDir(xtsGenFileDir);
      localFileUtil.grantFileOrDirFullAccess(xtsGenFileDir);

      Path tmpXtsResultsDir = XtsDirUtil.getXtsResultsDir(tmpXtsRootDir, xtsType);
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
      Path tmpXtsLogsDir = XtsDirUtil.getXtsLogsDir(tmpXtsRootDir, xtsType);
      if (localFileUtil.isDirExist(tmpXtsLogsDir)) {
        localFileUtil.copyFileOrDir(tmpXtsLogsDir, xtsGenFileDir);
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Error when copying xTS TF gen files: %s", MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when copying xTS TF gen files: %s", MoreThrowables.shortDebugString(e));
      Thread.currentThread().interrupt();
    }

    try {
      localFileUtil.removeFileOrDir(tmpXtsRootDir);
    } catch (MobileHarnessException e) {
      logger.atWarning().log(
          "Failed to clean up xTS Tradefed temp directory [%s]: %s",
          tmpXtsRootDir, MoreThrowables.shortDebugString(e));
    } catch (InterruptedException e) {
      logger.atWarning().log(
          "Interrupted when clean up xTS Tradefed temp directory [%s]: %s",
          tmpXtsRootDir, MoreThrowables.shortDebugString(e));
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
  private Optional<Integer> runXtsCommand(
      TestInfo testInfo, XtsTradefedTestDriverSpec spec, Path tmpXtsRootDir, String xtsType)
      throws MobileHarnessException, InterruptedException {
    ImmutableMap<String, String> env =
        getEnvironmentToTradefedConsole(tmpXtsRootDir, xtsType, spec);

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

    ImmutableList<String> cmd =
        XtsCommandUtil.getXtsJavaCommand(
            xtsType,
            tmpXtsRootDir,
            jvmFlagsBuilder.build(),
            requireNonNull(
                env.getOrDefault(
                    TF_PATH_KEY, getConcatenatedJarPath(tmpXtsRootDir, spec, xtsType))),
            getXtsRunCommandArgs(spec, env, testInfo));
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

  private boolean isJarFileIncluded(
      String fileName, ImmutableList<Pattern> excludedJarFilePatterns) {
    return excludedJarFilePatterns.stream()
        .map(pattern -> pattern.matcher(fileName))
        .noneMatch(Matcher::matches);
  }

  private String getConcatenatedJarPath(
      Path tmpXtsRootDir, XtsTradefedTestDriverSpec spec, String xtsType)
      throws MobileHarnessException {
    ImmutableList<Pattern> excludedJarFilePatterns =
        EXCLUDED_JAR_FILE_PATTERNS.stream().map(Pattern::compile).collect(toImmutableList());
    Set<String> leadingJarsSet = getLeadingJarsInClasspath(spec);

    ListMultimap<String, Path> foundLeadingJars = ArrayListMultimap.create();
    ImmutableList.Builder<Path> restOfJars = ImmutableList.builder();
    try {
      Path linkXtsToolsDir = XtsDirUtil.getXtsToolsDir(tmpXtsRootDir, xtsType);
      Path linkXtsToolsDirRealPath = linkXtsToolsDir.toRealPath();
      Path linkXtsTestcasesDir = XtsDirUtil.getXtsTestCasesDir(tmpXtsRootDir, xtsType);
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
      // In setUpXtsWorkDir, it created symlinks for files and directories directly under the
      // "testcases" folder.
      // Currently we cannot list files within a symlinked sub-directory, so we handle sub-files and
      // sub-directories separately.
      localFileUtil
          .listFilesOrDirs(
              linkXtsTestcasesDirRealPath,
              fileOrDir ->
                  Files.isRegularFile(fileOrDir)
                      && fileOrDir.getFileName().toString().endsWith(".jar")
                      && isJarFileIncluded(
                          fileOrDir.getFileName().toString(), excludedJarFilePatterns))
          .forEach(
              jar -> {
                if (leadingJarsSet.contains(jar.getFileName().toString())) {
                  foundLeadingJars.put(jar.getFileName().toString(), jar);
                } else {
                  restOfJars.add(jar);
                }
              });

      List<Path> linkXtsTestcasesSubDirPaths = localFileUtil.listDirs(linkXtsTestcasesDirRealPath);
      for (Path linkXtsTestcasesSubDirPath : linkXtsTestcasesSubDirPaths) {
        Path linkXtsTestcasesSubDirRealPath = linkXtsTestcasesSubDirPath.toRealPath();
        localFileUtil
            .listFilePaths(
                linkXtsTestcasesSubDirRealPath,
                /* recursively= */ true,
                path ->
                    path.getFileName().toString().endsWith(".jar")
                        && isJarFileIncluded(
                            path.getFileName().toString(), excludedJarFilePatterns))
            .forEach(
                jar -> {
                  Path newJarPath =
                      replacePathPrefix(
                          jar, linkXtsTestcasesSubDirRealPath.getParent(), linkXtsTestcasesDir);
                  if (leadingJarsSet.contains(jar.getFileName().toString())) {
                    foundLeadingJars.put(jar.getFileName().toString(), newJarPath);
                  } else {
                    restOfJars.add(newJarPath);
                  }
                });
      }

      ImmutableList.Builder<Path> result = ImmutableList.builder();
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

  private static Set<String> getLeadingJarsInClasspath(XtsTradefedTestDriverSpec spec) {
    LinkedHashSet<String> leadingJarsSet = new LinkedHashSet<>();
    // Always put tradefed.jar at the beginning
    leadingJarsSet.add("tradefed.jar");
    if (!spec.getLeadingJarsInClasspathList().isEmpty()) {
      leadingJarsSet.addAll(spec.getLeadingJarsInClasspathList());
    }
    return leadingJarsSet;
  }

  private static Path replacePathPrefix(Path path, Path currentPrefix, Path newPrefix) {
    return Path.of(path.toString().replaceFirst(currentPrefix.toString(), newPrefix.toString()));
  }

  private Path getXtsRootDir(XtsTradefedTestDriverSpec spec, TestInfo testInfo)
      throws MobileHarnessException {
    if (spec.hasXtsRootDir()) {
      return Path.of(spec.getXtsRootDir());
    } else if (spec.hasAndroidXtsZip()) {
      // Unzip android-xts zip file and return the xts root dir
      Path androidXtsZip = Path.of(spec.getAndroidXtsZip());
      long startTime = clock.instant().toEpochMilli();
      try {
        String unzippedPath =
            PathUtil.join(
                testInfo.getTmpFileDir(), androidXtsZip.toString().replace('.', '_') + "_unzipped");
        localFileUtil.prepareDir(unzippedPath);
        // TODO: cache the unzip result to reduce lab disk usage.
        localFileUtil.unzipFile(
            androidXtsZip.toString(), unzippedPath, ANDROID_XTS_ZIP_UNCOMPRESS_TIMEOUT);
        return Path.of(unzippedPath);
      } catch (MobileHarnessException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new MobileHarnessException(
            AndroidErrorId.XTS_TRADEFED_GET_XTS_ROOT_DIR_ERROR,
            "Failed to unzip " + androidXtsZip,
            e);
      } finally {
        logger.atInfo().log(
            "Unzipping %s took %d seconds",
            androidXtsZip,
            Duration.between(Instant.ofEpochMilli(startTime), clock.instant()).toSeconds());
      }
    }
    throw new MobileHarnessException(
        AndroidErrorId.XTS_TRADEFED_GET_XTS_ROOT_DIR_ERROR,
        "Failed to get the xts root dir. Full spec: " + shortDebugString(spec));
  }

  private ImmutableMap<String, String> getEnvironmentToTradefedConsole(
      Path tmpXtsRootDir, String xtsType, XtsTradefedTestDriverSpec spec)
      throws MobileHarnessException, InterruptedException {
    Map<String, String> environmentToTradefedConsole = new HashMap<>();
    environmentToTradefedConsole.put(
        "LD_LIBRARY_PATH", getConcatenatedLdLibraryPath(tmpXtsRootDir, xtsType));
    environmentToTradefedConsole.put("PATH", getEnvPath());
    environmentToTradefedConsole.put("TF_WORK_DIR", tmpXtsRootDir.toString());
    if (getDevice().hasDimension(Dimension.Name.DEVICE_CLASS_NAME, "AndroidJitEmulator")) {
      environmentToTradefedConsole.put(
          "TF_GLOBAL_CONFIG", AndroidJitEmulatorUtil.getHostConfigPath());
    }
    if (!spec.getEnvVars().isEmpty()) {
      String envVarJson = spec.getEnvVars();
      Map<String, String> envVar =
          new Gson().fromJson(envVarJson, new TypeToken<Map<String, String>>() {}.getType());
      for (Map.Entry<String, String> entry : envVar.entrySet()) {
        if (entry.getKey().isEmpty() || entry.getValue().isEmpty()) {
          continue;
        }
        if (entry.getKey().equals(TF_PATH_KEY)) {
          // Override TF_PATH since the original TF_PATH may be unavailable due to symlink
          environmentToTradefedConsole.put(
              TF_PATH_KEY, getConcatenatedJarPath(tmpXtsRootDir, spec, xtsType));
        } else {
          String value = entry.getValue().replace("${TF_WORK_DIR}", tmpXtsRootDir.toString());
          // This will override the existing entry if it exists.
          environmentToTradefedConsole.put(entry.getKey(), value);
        }
      }
    }

    return ImmutableMap.copyOf(environmentToTradefedConsole);
  }

  private String getConcatenatedLdLibraryPath(Path tmpXtsRootDir, String xtsType) {
    List<String> libPathSegments = new ArrayList<>();
    libPathSegments.add(XtsDirUtil.getXtsLibDir(tmpXtsRootDir, xtsType).toString());
    libPathSegments.add(XtsDirUtil.getXtsLib64Dir(tmpXtsRootDir, xtsType).toString());
    String existingLdLibraryPath = systemUtil.getEnv("LD_LIBRARY_PATH");
    if (existingLdLibraryPath != null) {
      libPathSegments.add(existingLdLibraryPath);
    }
    return Joiner.on(':').join(libPathSegments);
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

    if (spec.getXtsTestPlanFile().isEmpty()) {
      xtsRunCommand.addAll(xtsCommand);
    } else {
      // Build final config based on local env vars
      String configTemplate = localFileUtil.readFile(spec.getXtsTestPlanFile());
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

  @VisibleForTesting
  Path prepareXtsWorkDir(String xtsType) throws MobileHarnessException {
    try {
      Path dir =
          Path.of(
              Flags.instance().atsXtsWorkDir.getNonNull().isEmpty()
                  ? requireNonNull(JAVA_IO_TMPDIR.value())
                  : Flags.instance().atsXtsWorkDir.getNonNull(),
              String.format("xts-root-dir-%s", this.testId),
              String.format("android-%s", xtsType));
      // We need to preparer /xts-root-dir/android-xts/testcases since we create symlink to all the
      // sub test case directories.
      localFileUtil.prepareDir(Path.of(dir.toString(), "testcases"));
      localFileUtil.grantFileOrDirFullAccess(dir.getParent());
      return dir.getParent();
    } catch (MobileHarnessException | RuntimeException e) {
      throw new MobileHarnessException(
          AndroidErrorId.XTS_TRADEFED_CREATE_TEMP_DIR_ERROR, "Failed to create temp directory.", e);
    }
  }

  private void setUpXtsWorkDir(
      XtsTradefedTestDriverSpec spec,
      Path sourceXtsRootDir,
      Path tmpXtsWorkDir,
      String xtsType,
      TestInfo testInfo)
      throws MobileHarnessException, InterruptedException {
    Path sourceXtsBundledJdkDir = XtsDirUtil.getXtsJdkDir(sourceXtsRootDir, xtsType);
    Path sourceXtsBundledTestcasesDir = XtsDirUtil.getXtsTestCasesDir(sourceXtsRootDir, xtsType);
    Path sourceXtsBundledToolsDir = XtsDirUtil.getXtsToolsDir(sourceXtsRootDir, xtsType);
    Path sourceXtsBundledLibDir = XtsDirUtil.getXtsLibDir(sourceXtsRootDir, xtsType);
    Path sourceXtsBundledLib64Dir = XtsDirUtil.getXtsLib64Dir(sourceXtsRootDir, xtsType);

    Path linkJdkDir = XtsDirUtil.getXtsJdkDir(tmpXtsWorkDir, xtsType);
    Path linkTestcasesDir = XtsDirUtil.getXtsTestCasesDir(tmpXtsWorkDir, xtsType);
    Path linkToolsDir = XtsDirUtil.getXtsToolsDir(tmpXtsWorkDir, xtsType);
    Path linkLibDir = XtsDirUtil.getXtsLibDir(tmpXtsWorkDir, xtsType);
    Path linkLib64Dir = XtsDirUtil.getXtsLib64Dir(tmpXtsWorkDir, xtsType);

    if (Flags.instance().xtsJdkDir.getNonNull().isEmpty()) {
      // Create symlinks for the downloaded JDK only for the dynamic download jobs.
      if (testInfo.properties().has(XtsConstants.XTS_DYNAMIC_DOWNLOAD_PATH_JDK_PROPERTY_KEY)) {
        Path downloadedJdkPath =
            Path.of(
                testInfo.getTmpFileDir()
                    + testInfo
                        .properties()
                        .get(XtsConstants.XTS_DYNAMIC_DOWNLOAD_PATH_JDK_PROPERTY_KEY));
        createSymlink(linkJdkDir, downloadedJdkPath);
        logger.atInfo().log("Use the downloaded JDK files from %s", downloadedJdkPath);
      } else {
        createSymlink(linkJdkDir, sourceXtsBundledJdkDir);
      }
    } else {
      // Create symlinks for the JDK passed in via the flag by the user.
      logger.atInfo().log(
          "Use the JDK files from %s passed in via the flag --xts_jdk_dir",
          Flags.instance().xtsJdkDir.getNonNull());
      Path jdkDir = Path.of(Flags.instance().xtsJdkDir.getNonNull());
      localFileUtil.grantFileOrDirFullAccess(jdkDir);
      createSymlink(linkJdkDir, jdkDir);
    }

    // Create symlinks for the test cases.
    // For dynamic download jobs, create symlinks to the dynamic downloaded test cases.
    // For non dynamic download jobs, create symlinks to the original static test cases.
    if (isXtsDynamicDownloaderEnabled(testInfo)) {
      Set<String> xtsDynamicDownloadTestList = new HashSet<>();
      Set<String> missingTestList = new HashSet<>(DYNAMIC_JOB_TEST_DEPENDENCIES);
      String testListProperty =
          testInfo.properties().get(XtsConstants.XTS_DYNAMIC_DOWNLOAD_PATH_TEST_LIST_PROPERTY_KEY);
      String preloadMainlineVersion =
          testInfo.properties().get(XtsConstants.PRELOAD_MAINLINE_VERSION_TEST_PROPERTY_KEY);
      if (testListProperty != null && preloadMainlineVersion != null) {
        if (localFileUtil.isFileOrDirExist(testListProperty)) {
          xtsDynamicDownloadTestList.addAll(getStringSetFromResourceFile(testListProperty));
          // Save the test list file to the test gen file dir and further in xts/logs.
          localFileUtil.copyFileOrDir(
              testListProperty,
              testInfo.getGenFileDir()
                  + "/"
                  + preloadMainlineVersion
                  + "_"
                  + PathUtil.basename(testListProperty));
        }
      } else {
        xtsDynamicDownloadTestList.addAll(
            getStringSetFromResourceFile(getStaticMctsListFilePath()));
      }

      if (testInfo
          .jobInfo()
          .properties()
          .getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME)
          .orElse("")
          .equals(XtsConstants.DYNAMIC_MCTS_JOB_NAME)) {
        if (testInfo.properties().has(XtsConstants.XTS_DYNAMIC_DOWNLOAD_PATH_TEST_PROPERTY_KEY)) {
          // Integrates the dynamic downloaded test cases with the temp XTS workspace.
          missingTestList.addAll(
              createSymlinksForDynamicDownloadTestCases(
                  linkTestcasesDir,
                  Path.of(
                      testInfo.getTmpFileDir()
                          + testInfo
                              .properties()
                              .get(XtsConstants.XTS_DYNAMIC_DOWNLOAD_PATH_TEST_PROPERTY_KEY)),
                  /* isDynamicDownload= */ true,
                  xtsDynamicDownloadTestList));
          // Also include the test dependencies for dynamic download test cases.
          logger.atInfo().log("Missing dynamic download test list: %s", missingTestList);
          createSymlinksForDynamicDownloadTestCases(
              linkTestcasesDir,
              sourceXtsBundledTestcasesDir,
              /* isDynamicDownload= */ true,
              missingTestList);
        } else {
          createSymlinksForTestCases(linkTestcasesDir, sourceXtsBundledTestcasesDir);
        }
      }

      if (testInfo
          .jobInfo()
          .properties()
          .getOptional(XtsConstants.XTS_DYNAMIC_DOWNLOAD_JOB_NAME)
          .orElse("")
          .equals(XtsConstants.STATIC_XTS_JOB_NAME)) {
        // Integrates the static test cases with the temp XTS workspace.
        createSymlinksForDynamicDownloadTestCases(
            linkTestcasesDir,
            sourceXtsBundledTestcasesDir,
            /* isDynamicDownload= */ false,
            xtsDynamicDownloadTestList);
      }
    } else {
      createSymlinksForTestCases(linkTestcasesDir, sourceXtsBundledTestcasesDir);
    }

    // Create symlinks for the tools and libs.
    createSymlink(linkToolsDir, sourceXtsBundledToolsDir);
    createSymlink(linkLibDir, sourceXtsBundledLibDir);
    createSymlink(linkLib64Dir, sourceXtsBundledLib64Dir);

    if (useTfRunRetry(spec, testInfo)) {
      // When using TF "run retry", TF looks for the corresponding previous result dir per given
      // session index. So it needs to link the previous session's result dir to the work dir so TF
      // can locate the prev result to complete the retry.
      Path resultsDirInTmpXtsWorkDir = XtsDirUtil.getXtsResultsDir(tmpXtsWorkDir, xtsType);

      String prevSessionResultDirName = "0";
      Path linkPrevSessionResultDir = resultsDirInTmpXtsWorkDir.resolve(prevSessionResultDirName);
      previousResultDirNames = ImmutableSet.of(prevSessionResultDirName);

      localFileUtil.prepareDir(resultsDirInTmpXtsWorkDir);
      // Link the result dir so TF can load prev results and merge files
      createSymlink(
          linkPrevSessionResultDir, Path.of(spec.getPrevSessionTestResultXml()).getParent());

      // Link subplan dir so TF can use the subplan for retry
      Path sourceXtsSubPlansDir = XtsDirUtil.getXtsSubPlansDir(sourceXtsRootDir, xtsType);
      if (localFileUtil.isDirExist(sourceXtsSubPlansDir)) {
        Path linkSubPlansDir = XtsDirUtil.getXtsSubPlansDir(tmpXtsWorkDir, xtsType);
        createSymlink(linkSubPlansDir, sourceXtsSubPlansDir);
      }
    }

    if (isRunWithSubPlan(spec)) {
      Path subplansDirInTmpXtsWorkDir = XtsDirUtil.getXtsSubPlansDir(tmpXtsWorkDir, xtsType);
      localFileUtil.prepareDir(subplansDirInTmpXtsWorkDir);
      localFileUtil.grantFileOrDirFullAccess(subplansDirInTmpXtsWorkDir);
      localFileUtil.copyFileOrDir(
          spec.getSubplanXml(), subplansDirInTmpXtsWorkDir.toAbsolutePath().toString());
      // Also copy the subplan xml file the test GenFileDir for debugging if needed
      localFileUtil.copyFileOrDir(spec.getSubplanXml(), testInfo.getGenFileDir());
    }
  }

  @CanIgnoreReturnValue
  private static Path createSymlink(Path link, Path target) throws MobileHarnessException {
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

  private void createSymlinksForTestCases(Path link, Path target) throws MobileHarnessException {
    try {
      localFileUtil.checkFileOrDir(target);
    } catch (MobileHarnessException e) {
      // File does not exist, no need to integrate.
      logger.atWarning().log("%s does not exist.", target);
      return;
    }

    // Create symlink to the immediate subfiles and subdirectories of the xts test cases
    List<String> subTestCases = localFileUtil.listFileOrDirPaths(target.toString());
    for (String subTestCase : subTestCases) {
      Path subTestCasePath = Path.of(subTestCase);
      Path tmpXtsTestcasePath = link.resolve(subTestCasePath.getFileName().toString());
      createSymlink(tmpXtsTestcasePath, subTestCasePath);
    }
    logger.atInfo().log(
        "Finished integrating the test cases [%s] with the temp XTS workspace [%s].", target, link);
  }

  @CanIgnoreReturnValue
  private Set<String> createSymlinksForDynamicDownloadTestCases(
      Path link, Path target, boolean isDynamicDownload, Set<String> dynamicDownloadTestList)
      throws MobileHarnessException {
    try {
      localFileUtil.checkFileOrDir(target);
    } catch (MobileHarnessException e) {
      // File does not exist, no need to integrate.
      logger.atWarning().log("%s does not exist.", target);
      return dynamicDownloadTestList;
    }

    // Create symlink to the immediate subfiles and subdirectories of the xts test cases.
    // For dynamic download jobs, only create symlinks for the test cases in the test list.
    // For non dynamic download jobs, create symlinks for the test cases not in the test list.
    List<String> subTestCases = localFileUtil.listFileOrDirPaths(target.toString());
    Set<String> foundTests = new HashSet<>();
    for (String subTestCase : subTestCases) {
      Path subTestCasePath = Path.of(subTestCase);
      String subTestCaseName = subTestCasePath.getFileName().toString();
      foundTests.add(subTestCaseName);
      boolean shouldCreateSymlink =
          isDynamicDownload
              ? dynamicDownloadTestList.contains(subTestCaseName)
              : !dynamicDownloadTestList.contains(subTestCaseName)
                  || STATIC_JOB_TEST_DEPENDENCIES.contains(subTestCaseName);

      if (shouldCreateSymlink) {
        Path tmpXtsTestcasePath = link.resolve(subTestCasePath.getFileName().toString());
        createSymlink(tmpXtsTestcasePath, subTestCasePath);
      }
    }

    logger.atInfo().log(
        "Finished integrating the test cases [%s] with the temp XTS workspace [%s].", target, link);

    // Return the test cases that are present in the test list but are missing in the dynamic
    // download folder.
    dynamicDownloadTestList.removeAll(foundTests);
    return dynamicDownloadTestList;
  }

  /** Returns {@code true} if xts dynamic downloader is enabled. */
  private static boolean isXtsDynamicDownloaderEnabled(TestInfo testInfo) {
    return testInfo
        .jobInfo()
        .properties()
        .getBoolean(XtsConstants.IS_XTS_DYNAMIC_DOWNLOAD_ENABLED)
        .orElse(false);
  }

  private ImmutableSet<String> getStringSetFromResourceFile(String filePath)
      throws MobileHarnessException {
    return localFileUtil.readLineListFromFile(filePath).stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .collect(toImmutableSet());
  }

  private static boolean isRunRetryWithSubPlan(XtsTradefedTestDriverSpec spec) {
    return spec.getXtsTestPlan().equals("retry") && !spec.getSubplanXml().isEmpty();
  }

  private static boolean useTfRunRetry(XtsTradefedTestDriverSpec spec, TestInfo testInfo) {
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
  private static boolean isRunWithSubPlan(XtsTradefedTestDriverSpec spec) {
    return !spec.getSubplanXml().isEmpty();
  }

  private String getTradefedAgentFilePath() throws MobileHarnessException {
    return resUtil.getResourceFile(getClass(), TF_AGENT_RESOURCE_PATH);
  }

  private String getStaticMctsListFilePath() throws MobileHarnessException {
    return resUtil.getResourceFile(getClass(), STATIC_MCTS_LIST_FILE_PATH);
  }
}
