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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.shared.emulator.AndroidJitEmulatorUtil;
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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private final LocalFileUtil localFileUtil;
  private final SystemUtil systemUtil;

  public NonXtsRunStrategy(LocalFileUtil localFileUtil) {
    this(localFileUtil, new SystemUtil());
  }

  public NonXtsRunStrategy(LocalFileUtil localFileUtil, SystemUtil systemUtil) {
    this.localFileUtil = localFileUtil;
    this.systemUtil = systemUtil;
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

    return extraArgs.build();
  }

  private static void addInvocationData(
      ImmutableList.Builder<String> command, String key, String value) {
    command.add("--invocation-data").add(String.format("%s=%s", key, value));
  }
}
