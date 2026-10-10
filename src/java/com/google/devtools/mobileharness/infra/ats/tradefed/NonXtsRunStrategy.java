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

package com.google.devtools.mobileharness.infra.ats.tradefed;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.AndroidErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessExceptionFactory;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidJitEmulatorUtil;
import com.google.devtools.mobileharness.platform.android.xts.constant.XtsConstants;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfo.TradefedInvocation;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.flags.Flags;
import com.google.devtools.mobileharness.shared.util.system.SystemUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.wireless.qa.mobileharness.shared.api.device.Device;
import com.google.wireless.qa.mobileharness.shared.constant.Dimension;
import com.google.wireless.qa.mobileharness.shared.constant.PropertyName;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.spec.driver.TradefedTestDriverSpec;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** An implementation of {@link TradefedRunStrategy} for non-XTS runs. */
public final class NonXtsRunStrategy implements TradefedRunStrategy {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String TF_PATH_KEY = "TF_PATH";
  private static final String CONSOLE_CLASS = "com.android.tradefed.command.Console";
  private static final String TF_TMP_DIR = "tf_tmp";
  private static final String INVOCATION_ID_PROPERTY = "ab_invocation_id";
  private static final String WORKUNIT_ID_PROPERTY = "ab_workunit_id";
  private static final String APPEND_ANTS_INVOCATION_DATA_KEY = "APPEND_ANTS_INVOCATION_DATA";
  private static final String APPEND_RDB_INVOCATION_DATA_KEY = "APPEND_RDB_INVOCATION_DATA";
  private static final String ATE_SHARD_COUNT_PARAM = "ate_shard_count";
  private static final String ATE_SHARD_INDEX_PARAM = "ate_shard_index";

  /** Max length of the Tradefed invocation error message put in the MH result / property. */
  private static final int MAX_INVOCATION_ERROR_MESSAGE_LENGTH = 1000;

  /** Max number of failed invocations whose error messages are joined in the MH result. */
  private static final int MAX_INVOCATION_ERRORS_IN_MESSAGE = 5;

  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;
  private final XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil;

  public NonXtsRunStrategy(LocalFileUtil localFileUtil) {
    this(localFileUtil, new SystemUtil());
  }

  public NonXtsRunStrategy(LocalFileUtil localFileUtil, SystemUtil systemUtil) {
    this(localFileUtil, systemUtil, new XtsTradefedRuntimeInfoFileUtil());
  }

  public NonXtsRunStrategy(
      LocalFileUtil localFileUtil,
      SystemUtil systemUtil,
      XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil) {
    this.localFileUtil = localFileUtil;
    this.systemUtil = systemUtil;
    this.xtsTradefedRuntimeInfoFileUtil = xtsTradefedRuntimeInfoFileUtil;
  }

  @Override
  public void setUpWorkDir(TradefedTestDriverSpec spec, Path workDir, TestInfo testInfo)
      throws MobileHarnessException {
    localFileUtil.prepareDir(workDir);
    localFileUtil.grantFileOrDirFullAccess(workDir);
    Path tfTmpDir = workDir.resolve(TF_TMP_DIR);
    localFileUtil.prepareDir(tfTmpDir);
    localFileUtil.grantFileOrDirFullAccess(tfTmpDir);
  }

  @Override
  public String getConcatenatedJarPath(Path workDir, TradefedTestDriverSpec spec)
      throws MobileHarnessException {
    Path tradefedDir = Path.of(Flags.tradefedBinaryDir.get());
    ImmutableList.Builder<String> jarPaths = ImmutableList.builder();
    if (localFileUtil.isDirExist(tradefedDir)) {
      localFileUtil
          .listFilePaths(
              tradefedDir,
              /* recursively= */ false,
              path -> path.getFileName().toString().endsWith(".jar"))
          .forEach(path -> jarPaths.add(path.toString()));
    } else {
      logger.atWarning().log(
          "Generic Tradefed directory %s not found for generic TF run.", tradefedDir);
    }
    return Joiner.on(':').join(jarPaths.build());
  }

  @Override
  public ImmutableMap<String, String> getEnvironment(
      Path workDir, TradefedTestDriverSpec spec, Device device, String envPath)
      throws MobileHarnessException {
    Map<String, String> environmentToTradefedConsole = new HashMap<>();
    environmentToTradefedConsole.put("PATH", envPath);
    environmentToTradefedConsole.put("TF_WORK_DIR", workDir.toString());
    if (!Flags.tradefedHostConfig.getNonNull().isEmpty()) {
      environmentToTradefedConsole.put("TF_GLOBAL_CONFIG", Flags.tradefedHostConfig.getNonNull());
    } else if (device.hasDimension(Dimension.Name.DEVICE_CLASS_NAME, "AndroidJitEmulator")) {
      environmentToTradefedConsole.put(
          "TF_GLOBAL_CONFIG", AndroidJitEmulatorUtil.getHostConfigPath());
    }
    if (!Flags.tradefedServiceAccountKeyFile.getNonNull().isEmpty()) {
      environmentToTradefedConsole.put(
          "GOOGLE_APPLICATION_CREDENTIALS", Flags.tradefedServiceAccountKeyFile.getNonNull());
    }
    if (!spec.getEnvVars().isEmpty()) {
      String envVarJson = spec.getEnvVars();
      Map<String, String> envVar =
          new Gson().fromJson(envVarJson, new TypeToken<Map<String, String>>() {}.getType());
      for (Map.Entry<String, String> entry : envVar.entrySet()) {
        if (entry.getKey().isEmpty() || entry.getValue().isEmpty()) {
          continue;
        }
        String value = entry.getValue().replace("${TF_WORK_DIR}", workDir.toString());
        if (entry.getKey().equals(TF_PATH_KEY)) {
          // For NON_XTS, merge provided TF_PATH with scanned jars.
          environmentToTradefedConsole.put(
              TF_PATH_KEY, value + ":" + getConcatenatedJarPath(workDir, spec));
        } else {
          // This will override the existing entry if it exists.
          environmentToTradefedConsole.put(entry.getKey(), value);
        }
      }
    }

    return ImmutableMap.copyOf(environmentToTradefedConsole);
  }

  @Override
  public String getJavaPath(Path workDir) {
    return systemUtil.getJavaBin();
  }

  @Override
  public String getMainClass() {
    return CONSOLE_CLASS;
  }

  @Override
  public ImmutableList<String> getJvmDefines(Path workDir) {
    return ImmutableList.of();
  }

  @Override
  public Predicate<Path> getCurrentSessionResultFilter() {
    return unused -> true;
  }

  @Override
  public Path getResultsDirInWorkDir(Path workDir) {
    return workDir.resolve("results");
  }

  @Override
  public Path getLogsDirInWorkDir(Path workDir) {
    // Resolve the output directory in the TF tmp dir.
    try {
      List<Path> hostLogs =
          localFileUtil.listFilePaths(
              workDir,
              /* recursively= */ true,
              path ->
                  path.getFileName().toString().startsWith("host_log_")
                      && path.getFileName().toString().endsWith(".txt"));
      if (!hostLogs.isEmpty()) {
        return hostLogs.get(0).getParent();
      }
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log("Failed to find host log file.");
    }
    return workDir.resolve("logs");
  }

  @Override
  public Path getGenFileDir(TestInfo testInfo) throws MobileHarnessException {
    return Path.of(testInfo.getGenFileDir(), "non-xts-gen-files");
  }

  @Override
  public ImmutableList<String> getExtraJvmFlags(Path workDir) {
    return ImmutableList.of(String.format("-Djava.io.tmpdir=%s", workDir.resolve(TF_TMP_DIR)));
  }

  @Override
  public ImmutableList<String> getExtraRunCommandArgs(TestInfo testInfo) {
    ImmutableList.Builder<String> extraArgs = ImmutableList.builder();
    boolean appendAnts = Boolean.parseBoolean(systemUtil.getEnv(APPEND_ANTS_INVOCATION_DATA_KEY));
    boolean appendRdb = Boolean.parseBoolean(systemUtil.getEnv(APPEND_RDB_INVOCATION_DATA_KEY));

    String workUnitId = testInfo.properties().get(WORKUNIT_ID_PROPERTY);
    String invocationId = testInfo.jobInfo().properties().get(INVOCATION_ID_PROPERTY);
    if (appendAnts && workUnitId != null && invocationId != null) {
      addInvocationData(extraArgs, "invocation_id", invocationId);
      addInvocationData(extraArgs, "work_unit_id", workUnitId);
    }

    String resultDbInvocationId =
        testInfo.properties().getOptional(PropertyName.Test.RESULTDB_INVOCATION_ID).orElse("");
    String resultDbUpdateToken =
        testInfo.properties().getOptional(PropertyName.Test.RESULTDB_UPDATE_TOKEN).orElse("");
    if (appendRdb && !resultDbInvocationId.isEmpty() && !resultDbUpdateToken.isEmpty()) {
      addInvocationData(extraArgs, "resultdb_invocation_id", resultDbInvocationId);
      addInvocationData(extraArgs, "resultdb_invocation_update_token", resultDbUpdateToken);
    }

    String resultDbRootInvocationId =
        testInfo.properties().getOptional(PropertyName.Test.RESULTDB_ROOT_INVOCATION_ID).orElse("");
    String resultDbWorkUnitId =
        testInfo.properties().getOptional(PropertyName.Test.RESULTDB_WORK_UNIT_ID).orElse("");
    String resultDbWorkUnitUpdateToken =
        testInfo
            .properties()
            .getOptional(PropertyName.Test.RESULTDB_WORK_UNIT_UPDATE_TOKEN)
            .orElse("");
    if (appendRdb
        && !resultDbRootInvocationId.isEmpty()
        && !resultDbWorkUnitId.isEmpty()
        && !resultDbWorkUnitUpdateToken.isEmpty()) {
      addInvocationData(extraArgs, "resultdb_root_invocation_id", resultDbRootInvocationId);
      addInvocationData(extraArgs, "resultdb_work_unit_id", resultDbWorkUnitId);
      addInvocationData(extraArgs, "resultdb_work_unit_update_token", resultDbWorkUnitUpdateToken);
    }

    int ateShardCount = testInfo.jobInfo().params().getInt(ATE_SHARD_COUNT_PARAM, 0);
    if (ateShardCount > 1) {
      extraArgs.add(
          "--shard-count",
          Integer.toString(ateShardCount),
          "--shard-index",
          Integer.toString(testInfo.jobInfo().params().getInt(ATE_SHARD_INDEX_PARAM, 0)));
    }

    return extraArgs.build();
  }

  /**
   * {@inheritDoc}
   *
   * <p>For non-xTS runs, when {@code --enable_non_xts_tradefed_result_from_invocation} is set, the
   * result is derived from the data recorded by the Tradefed invocation agent instead of only the
   * process exit code (Tradefed {@code Console ... run commandAndExit} exits with 0 even if the
   * invocation failed):
   *
   * <ol>
   *   <li>A result that is already non-passing (e.g. timeout) is kept as is.
   *   <li>A missing / non-zero exit code is handled by the default implementation.
   *   <li>Any Tradefed invocation with an error message recorded in the runtime info file makes the
   *       test {@code ERROR}.
   *   <li>Otherwise the test is {@code PASS}.
   * </ol>
   */
  @Override
  public void setTestResult(TestInfo testInfo, Optional<Integer> tfExitCode)
      throws MobileHarnessException {
    if (!Flags.enableNonXtsTradefedResultFromInvocation.getNonNull()
        || tfExitCode.isEmpty()
        || tfExitCode.get() != 0) {
      TradefedRunStrategy.super.setTestResult(testInfo, tfExitCode);
      return;
    }
    TestResult currentResult = testInfo.resultWithCause().get().type();
    if (currentResult != TestResult.UNKNOWN && currentResult != TestResult.PASS) {
      testInfo
          .log()
          .atInfo()
          .alsoTo(logger)
          .log(
              "Test result is already %s, skip deriving it from Tradefed invocation.",
              currentResult);
      return;
    }

    ImmutableList<TradefedInvocation> failedInvocations =
        readFailedInvocations(
            Path.of(testInfo.getGenFileDir()).resolve(XtsConstants.TRADEFED_RUNTIME_INFO_FILE_NAME),
            testInfo);
    if (!failedInvocations.isEmpty()) {
      String errorSummary = summarizeInvocationErrors(failedInvocations);
      testInfo.properties().add(XtsConstants.TRADEFED_INVOCATION_ERROR, errorSummary);
      failedInvocations.forEach(
          invocation ->
              testInfo
                  .log()
                  .atWarning()
                  .alsoTo(logger)
                  .log(
                      "Tradefed invocation on %s failed:%n%s",
                      invocation.deviceIds(), invocation.errorMessage()));
      testInfo
          .resultWithCause()
          .setNonPassing(
              TestResult.ERROR,
              MobileHarnessExceptionFactory.createUserFacingException(
                  AndroidErrorId.XTS_TRADEFED_INVOCATION_ERROR,
                  String.format(
                      "Tradefed invocation failed: %s (see %s / %s in the test gen files for the"
                          + " full stack trace)",
                      errorSummary,
                      XtsConstants.TRADEFED_OUTPUT_FILE_NAME,
                      XtsConstants.TRADEFED_RUNTIME_INFO_FILE_NAME),
                  /* cause= */ null));
      return;
    }

    testInfo.resultWithCause().setPass();
  }

  /** Reads the finished Tradefed invocations that recorded an error message, if any. */
  private ImmutableList<TradefedInvocation> readFailedInvocations(
      Path runtimeInfoFilePath, TestInfo testInfo) {
    if (!localFileUtil.isFileExist(runtimeInfoFilePath)) {
      return ImmutableList.of();
    }
    try {
      return xtsTradefedRuntimeInfoFileUtil
          .readInfo(runtimeInfoFilePath, /* lastModifiedTime= */ null)
          .map(
              fileDetail ->
                  fileDetail.runtimeInfo().invocations().stream()
                      .filter(invocation -> !invocation.isRunning())
                      .filter(invocation -> !isNullOrEmpty(invocation.errorMessage()))
                      .collect(toImmutableList()))
          .orElse(ImmutableList.of());
    } catch (IOException | RuntimeException | Error e) {
      testInfo
          .log()
          .atWarning()
          .alsoTo(logger)
          .withCause(e)
          .log("Failed to read Tradefed runtime info file %s", runtimeInfoFilePath);
      return ImmutableList.of();
    }
  }

  /**
   * Summarizes the error messages (which are usually full stack traces) of the given failed
   * invocations into one short, single-line-per-invocation message.
   */
  @VisibleForTesting
  static String summarizeInvocationErrors(List<TradefedInvocation> failedInvocations) {
    String summary =
        failedInvocations.stream()
            .limit(MAX_INVOCATION_ERRORS_IN_MESSAGE)
            .map(
                invocation ->
                    invocation.deviceIds().isEmpty()
                        ? firstLine(invocation.errorMessage())
                        : String.format(
                            "[%s] %s",
                            String.join(",", invocation.deviceIds()),
                            firstLine(invocation.errorMessage())))
            .collect(joining("; "));
    if (failedInvocations.size() > MAX_INVOCATION_ERRORS_IN_MESSAGE) {
      summary +=
          String.format(
              "; ... (%d more)", failedInvocations.size() - MAX_INVOCATION_ERRORS_IN_MESSAGE);
    }
    return summary.length() > MAX_INVOCATION_ERROR_MESSAGE_LENGTH
        ? summary.substring(0, MAX_INVOCATION_ERROR_MESSAGE_LENGTH) + "..."
        : summary;
  }

  private static String firstLine(String message) {
    int lineBreak = message.indexOf('\n');
    return (lineBreak < 0 ? message : message.substring(0, lineBreak)).strip();
  }

  private static void addInvocationData(
      ImmutableList.Builder<String> command, String key, String value) {
    command.add("--invocation-data").add(String.format("%s=%s", key, value));
  }
}
