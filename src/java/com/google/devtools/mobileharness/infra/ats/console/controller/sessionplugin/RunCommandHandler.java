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

package com.google.devtools.mobileharness.infra.ats.console.controller.sessionplugin;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.common.CreateJobConfigUtil;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.XtsType;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gson.Gson;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.DeviceList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.Driver;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

/** Handler for "run" commands. */
class RunCommandHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ANDROID_REAL_DEVICE_TYPE = "AndroidRealDevice";
  @VisibleForTesting static final String XTS_TF_JOB_PROP = "xts-tradefed-job";
  @VisibleForTesting static final String XTS_NON_TF_JOB_PROP = "xts-non-tradefed-job";

  private final CreateJobConfigUtil createJobConfigUtil;
  private final LocalFileUtil localFileUtil;
  private final ModuleConfigurationHelper moduleConfigurationHelper;
  private final ConfigurationUtil configurationUtil;

  @Inject
  RunCommandHandler(
      DeviceQuerier deviceQuerier,
      LocalFileUtil localFileUtil,
      ModuleConfigurationHelper moduleConfigurationHelper,
      ConfigurationUtil configurationUtil) {
    this.createJobConfigUtil = new CreateJobConfigUtil(deviceQuerier);
    this.localFileUtil = localFileUtil;
    this.moduleConfigurationHelper = moduleConfigurationHelper;
    this.configurationUtil = configurationUtil;
  }

  /**
   * Creates tradefed jobs based on the {@code command} and adds the jobs to the {@code
   * sessionInfo}.
   *
   * <p>Jobs added to the session by the plugin will be started by the session job runner later.
   *
   * @return a list of added tradefed job IDs
   */
  @CanIgnoreReturnValue
  ImmutableList<String> addTradefedJobs(RunCommand command, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    Optional<JobInfo> jobInfo = createXtsTradefedTestJob(command);
    if (jobInfo.isEmpty()) {
      logger.atInfo().log(
          "No tradefed jobs created, double check device availability. The run command -> %s",
          shortDebugString(command));
      return ImmutableList.of();
    }
    jobInfo.get().properties().add(XTS_TF_JOB_PROP, "true");
    sessionInfo.addJob(jobInfo.get());
    String jobId = jobInfo.get().locator().getId();
    logger.atInfo().log(
        "Added tradefed job[%s] to the session %s", jobId, sessionInfo.getSessionId());
    return ImmutableList.of(jobId);
  }

  private Optional<JobInfo> createXtsTradefedTestJob(RunCommand runCommand)
      throws MobileHarnessException, InterruptedException {
    String xtsRootDir = runCommand.getXtsRootDir();
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log(
          "xTS root dir [%s] doesn't exist, skip creating tradefed jobs.", xtsRootDir);
      return Optional.empty();
    }

    XtsType xtsType = runCommand.getXtsType();
    ImmutableMap<String, Configuration> configsMap =
        configurationUtil.getConfigsFromDirs(
            ImmutableList.of(getXtsTestCasesDir(Path.of(xtsRootDir), xtsType).toFile()));

    List<String> modules = runCommand.getModuleNameList();
    ImmutableSet<String> allTfModules =
        configsMap.values().stream()
            .map(config -> config.getMetadata().getXtsModule())
            .collect(toImmutableSet());
    ImmutableList<String> givenMatchedTfModules =
        modules.stream().filter(allTfModules::contains).collect(toImmutableList());
    boolean noGivenModuleForTf = !modules.isEmpty() && givenMatchedTfModules.isEmpty();
    if (noGivenModuleForTf) {
      logger.atInfo().log(
          "Skip creating tradefed jobs as none of given modules is for tradefed module: %s",
          modules);
      return Optional.empty();
    }

    Optional<JobConfig> jobConfig =
        createXtsTradefedTestJobConfig(runCommand, givenMatchedTfModules);
    if (jobConfig.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(JobInfoCreator.createJobInfo(jobConfig.get(), ImmutableList.of(), null));
  }

  @VisibleForTesting
  Optional<JobConfig> createXtsTradefedTestJobConfig(
      RunCommand runCommand, ImmutableList<String> tfModules)
      throws MobileHarnessException, InterruptedException {
    String testPlan = runCommand.getTestPlan();
    String xtsRootDir = runCommand.getXtsRootDir();
    String xtsType = runCommand.getXtsType().name();
    List<String> deviceSerials = runCommand.getDeviceSerialList();
    int shardCount = runCommand.getShardCount();
    List<String> extraArgs = runCommand.getExtraArgList();

    ImmutableList<SubDeviceSpec> subDeviceSpecList =
        createJobConfigUtil.getSubDeviceSpecList(deviceSerials, shardCount);
    if (subDeviceSpecList.isEmpty()) {
      logger.atInfo().log("Found no devices to create the job config.");
      return Optional.empty();
    }

    JobConfig.Builder jobConfigBuilder =
        JobConfig.newBuilder()
            .setName("xts-tradefed-test-job")
            .setExecMode("local")
            .setJobTimeoutSec(3 * 24 * 60 * 60)
            .setTestTimeoutSec(3 * 24 * 60 * 60)
            .setStartTimeoutSec(5 * 60)
            .setPriority(Priority.HIGH)
            .setTestAttempts(1)
            .setTests(
                StringList.newBuilder()
                    .addContent(String.format("xts-tradefed-test-%s", testPlan)));
    jobConfigBuilder.setDevice(DeviceList.newBuilder().addAllSubDeviceSpec(subDeviceSpecList));

    Map<String, String> driverParams = new HashMap<>();
    driverParams.put("xts_type", xtsType);
    driverParams.put("xts_root_dir", xtsRootDir);
    driverParams.put("xts_test_plan", testPlan);

    ImmutableList<String> shardCountArg =
        shardCount > 0
            ? ImmutableList.of(String.format("--shard-count %s", shardCount))
            : ImmutableList.of();
    String runCommandArgs =
        Joiner.on(' ')
            .join(
                Streams.concat(
                        tfModules.stream().map(module -> String.format("-m %s", module)),
                        shardCountArg.stream(),
                        extraArgs.stream())
                    .collect(toImmutableList()));
    if (!runCommandArgs.isEmpty()) {
      driverParams.put("run_command_args", runCommandArgs);
    }

    jobConfigBuilder.setDriver(
        Driver.newBuilder().setName("XtsTradefedTest").setParam(new Gson().toJson(driverParams)));

    JobConfig jobConfig = jobConfigBuilder.build();
    logger.atInfo().log("XtsTradefedTest job config: %s", shortDebugString(jobConfig));

    return Optional.of(jobConfig);
  }

  /**
   * Creates non-tradefed jobs based on the {@code runCommand} and adds the jobs to the {@code
   * sessionInfo}.
   *
   * @return a list of added non-tradefed job IDs
   */
  @CanIgnoreReturnValue
  ImmutableList<String> addNonTradefedJobs(RunCommand runCommand, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    ImmutableList<JobInfo> jobInfos = createXtsNonTradefedJobs(runCommand);
    if (jobInfos.isEmpty()) {
      logger.atInfo().log(
          "No valid module(s) matched, no non-tradefed jobs will run. The run command -> %s",
          shortDebugString(runCommand));

      return ImmutableList.of();
    }
    ImmutableList.Builder<String> nonTradefedJobIds = ImmutableList.builder();
    jobInfos.forEach(
        jobInfo -> {
          sessionInfo.addJob(jobInfo);
          nonTradefedJobIds.add(jobInfo.locator().getId());
          logger.atInfo().log(
              "Added non-tradefed job[%s] to the session %s",
              jobInfo.locator().getId(), sessionInfo.getSessionId());
        });
    return nonTradefedJobIds.build();
  }

  @VisibleForTesting
  ImmutableList<JobInfo> createXtsNonTradefedJobs(RunCommand runCommand)
      throws MobileHarnessException, InterruptedException {
    String testPlan = runCommand.getTestPlan();
    // Currently only support CTS
    if (!testPlan.equals("cts")) {
      return ImmutableList.of();
    }

    String xtsRootDir = runCommand.getXtsRootDir();
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log(
          "xTS root dir [%s] doesn't exist, skip creating non-tradefed jobs.", xtsRootDir);
      return ImmutableList.of();
    }

    XtsType xtsType = runCommand.getXtsType();
    ImmutableMap<String, Configuration> configsMap =
        configurationUtil.getConfigsV2FromDirs(
            ImmutableList.of(getXtsTestCasesDir(Path.of(xtsRootDir), xtsType).toFile()));

    List<String> modules = runCommand.getModuleNameList();
    ImmutableSet<String> allNonTfModules =
        configsMap.values().stream()
            .map(config -> config.getMetadata().getXtsModule())
            .collect(toImmutableSet());
    ImmutableSet<String> givenMatchedNonTfModules =
        modules.stream().filter(allNonTfModules::contains).collect(toImmutableSet());
    boolean noGivenModuleForNonTf = !modules.isEmpty() && givenMatchedNonTfModules.isEmpty();
    if (noGivenModuleForNonTf) {
      logger.atInfo().log(
          "Skip creating non-tradefed jobs as none of given modules is for non-tradefed module: %s",
          modules);
      return ImmutableList.of();
    }

    ImmutableList.Builder<JobInfo> jobInfos = ImmutableList.builder();

    List<String> androidDeviceSerials = runCommand.getDeviceSerialList();

    for (Map.Entry<String, Configuration> entry : configsMap.entrySet()) {
      String configModuleName = entry.getValue().getMetadata().getXtsModule();
      if (givenMatchedNonTfModules.isEmpty()
          || givenMatchedNonTfModules.contains(configModuleName)) {
        Optional<JobInfo> jobInfoOpt =
            createXtsNonTradefedJob(
                Path.of(xtsRootDir), xtsType, Path.of(entry.getKey()), entry.getValue());
        if (jobInfoOpt.isPresent()) {
          JobInfo jobInfo = jobInfoOpt.get();
          if (!androidDeviceSerials.isEmpty()) {
            jobInfo
                .subDeviceSpecs()
                .getAllSubDevices()
                .forEach(
                    subDeviceSpec -> {
                      if (!subDeviceSpec.type().equals(ANDROID_REAL_DEVICE_TYPE)) {
                        return;
                      }
                      subDeviceSpec
                          .deviceRequirement()
                          .dimensions()
                          .add(
                              "serial",
                              String.format(
                                  "regex:(%s)", Joiner.on('|').join(androidDeviceSerials)));
                    });
          }
          jobInfos.add(jobInfo);
        }
      }
    }

    return jobInfos.build();
  }

  private Optional<JobInfo> createXtsNonTradefedJob(
      Path xtsRootDir, XtsType xtsType, Path moduleConfigPath, Configuration moduleConfig)
      throws MobileHarnessException, InterruptedException {
    Optional<JobInfo> jobInfoOpt = createBaseXtsNonTradefedJob(moduleConfig);
    if (jobInfoOpt.isEmpty()) {
      return Optional.empty();
    }

    ImmutableList<File> fileDepDirs =
        ImmutableList.of(
            moduleConfigPath.getParent().toFile(),
            getXtsTestCasesDir(xtsRootDir, xtsType).toFile());

    JobInfo jobInfo = jobInfoOpt.get();
    moduleConfigurationHelper.updateJobInfo(jobInfo, moduleConfig, fileDepDirs);
    jobInfo.properties().add(XTS_NON_TF_JOB_PROP, "true");
    return Optional.of(jobInfo);
  }

  private Optional<JobInfo> createBaseXtsNonTradefedJob(Configuration moduleConfig)
      throws MobileHarnessException, InterruptedException {
    String xtsModule = moduleConfig.getMetadata().getXtsModule();
    List<Device> moduleDevices = moduleConfig.getDevicesList();
    if (moduleDevices.isEmpty()) {
      logger.atInfo().log(
          "Found no devices to create the job config for xts non-tradefed job with module %s.",
          xtsModule);
      return Optional.empty();
    }

    List<SubDeviceSpec> subDeviceSpecList = new ArrayList<>();
    for (Device device : moduleDevices) {
      if (device.getName().isEmpty()) {
        logger.atWarning().log("Device name is missing in a <device> in module %s", xtsModule);
        return Optional.empty();
      } else {
        subDeviceSpecList.add(SubDeviceSpec.newBuilder().setType(device.getName()).build());
      }
    }

    JobConfig.Builder jobConfigBuilder =
        JobConfig.newBuilder()
            .setName(String.format("xts-mobly-aosp-package-job-%s", xtsModule))
            .setExecMode("local")
            .setJobTimeoutSec(5 * 24 * 60 * 60)
            .setTestTimeoutSec(5 * 24 * 60 * 60)
            .setStartTimeoutSec(1 * 60 * 60)
            .setPriority(Priority.HIGH)
            .setTestAttempts(1)
            .setTests(
                StringList.newBuilder()
                    .addContent(String.format("xts-mobly-aosp-package-test-%s", xtsModule)));
    jobConfigBuilder.setDevice(DeviceList.newBuilder().addAllSubDeviceSpec(subDeviceSpecList));
    jobConfigBuilder.setDriver(Driver.newBuilder().setName("MoblyAospPackageTest"));
    JobConfig jobConfig = jobConfigBuilder.build();
    logger.atInfo().log(
        "Non-tradefed job base config for module %s: %s", xtsModule, shortDebugString(jobConfig));

    return Optional.of(JobInfoCreator.createJobInfo(jobConfig, ImmutableList.of(), null));
  }

  /**
   * Copies xTS tradefed generated logs/results into proper locations within the given xts root dir.
   */
  void handleResultProcessing(RunCommand command, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    try {
      if (!localFileUtil.isDirExist(command.getXtsRootDir())) {
        logger.atInfo().log(
            "xTS root dir [%s] doesn't exist, skip processing result.", command.getXtsRootDir());
        return;
      }

      Path xtsRootDir = Path.of(command.getXtsRootDir());
      Optional<JobInfo> job =
          sessionInfo.getAllJobs().stream()
              .filter(jobInfo -> jobInfo.properties().has(XTS_TF_JOB_PROP))
              .findFirst();
      if (job.isEmpty()) {
        logger.atInfo().log("Found no job, skip processing result.");
        return;
      }
      Optional<TestInfo> test = job.get().tests().getAll().values().stream().findFirst();
      if (test.isEmpty()) {
        logger.atInfo().log("Found no test, skip processing result.");
        return;
      }

      String testGenFileDir = test.get().getGenFileDir();
      List<Path> genFiles = localFileUtil.listFilesOrDirs(Paths.get(testGenFileDir), path -> true);
      if (genFiles.isEmpty()) {
        logger.atInfo().log("Found no gen files, skip processing result.");
        return;
      }

      XtsType xtsType = command.getXtsType();
      String timestampDirName = getTimestampDirName();
      Path resultDir = getResultDir(xtsRootDir, xtsType, timestampDirName);
      Path logDir = getLogDir(xtsRootDir, xtsType, timestampDirName);
      Path tfLogDir = logDir.resolve("tradefed_log");
      Path atsLogDir = logDir.resolve("ats_log");

      for (Path genFile : genFiles) {
        if (genFile.getFileName().toString().endsWith("gen-files")) {
          Path logsDir = genFile.resolve("logs");
          if (logsDir.toFile().exists()) {
            localFileUtil.prepareDir(tfLogDir);
            List<Path> logsSubDirs = localFileUtil.listDirs(logsDir);
            for (Path logsSubDir : logsSubDirs) {
              logger.atInfo().log("Copying dir [%s] into dir [%s]", logsSubDir, tfLogDir);
              localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                  logsSubDir, tfLogDir, ImmutableList.of("-rf"));
            }
          }
          Path genFileResultsDir = genFile.resolve("results");
          if (genFileResultsDir.toFile().exists()) {
            localFileUtil.prepareDir(resultDir);
            List<Path> resultsSubFilesOrDirs =
                localFileUtil.listFilesOrDirs(
                    genFileResultsDir,
                    filePath -> !filePath.getFileName().toString().equals("latest"));
            for (Path resultsSubFileOrDir : resultsSubFilesOrDirs) {
              if (resultsSubFileOrDir.toFile().isDirectory()) {
                // If it's a dir, copy its content into the new result dir.
                List<Path> resultFilesOrDirs =
                    localFileUtil.listFilesOrDirs(resultsSubFileOrDir, path -> true);
                for (Path resultFileOrDir : resultFilesOrDirs) {
                  logger.atInfo().log(
                      "Copying file/dir [%s] into dir [%s]", resultFileOrDir, resultDir);
                  localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                      resultFileOrDir, resultDir, ImmutableList.of("-rf"));
                }
              } else if (resultsSubFileOrDir.getFileName().toString().endsWith(".zip")) {
                // If it's a zip file, copy it as a sibling file as the new result dir and rename it
                // as "<new_result_dir_name>.zip"
                logger.atInfo().log(
                    "Copying file/dir [%s] into dir [%s]", resultsSubFileOrDir, resultDir);
                localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                    resultsSubFileOrDir,
                    resultDir.resolveSibling(String.format("%s.zip", resultDir.getFileName())),
                    ImmutableList.of("-rf"));
              } else {
                logger.atInfo().log(
                    "Copying file/dir [%s] into dir [%s]", resultsSubFileOrDir, resultDir);
                localFileUtil.copyFileOrDirWithOverridingCopyOptions(
                    resultsSubFileOrDir, resultDir, ImmutableList.of("-rf"));
              }
            }
          }
        } else {
          if (!atsLogDir.toFile().exists()) {
            localFileUtil.prepareDir(atsLogDir);
          }
          logger.atInfo().log("Copying file/dir [%s] into dir [%s]", genFile, atsLogDir);
          localFileUtil.copyFileOrDirWithOverridingCopyOptions(
              genFile, atsLogDir, ImmutableList.of("-rf"));
        }
      }
    } finally {
      sessionInfo.setSessionPluginOutput(
          oldOutput ->
              (oldOutput == null ? AtsSessionPluginOutput.newBuilder() : oldOutput.toBuilder())
                  .setSuccess(
                      Success.newBuilder()
                          .setOutputMessage(
                              String.format(
                                  "run_command session [%s] ended", sessionInfo.getSessionId())))
                  .build(),
          AtsSessionPluginOutput.class);
    }
  }

  private Path getResultDir(Path xtsRootDir, XtsType xtsType, String timestampDirName) {
    return getXtsResultsDir(xtsRootDir, xtsType).resolve(timestampDirName);
  }

  private Path getLogDir(Path xtsRootDir, XtsType xtsType, String timestampDirName) {
    return getXtsLogsDir(xtsRootDir, xtsType).resolve(timestampDirName);
  }

  @VisibleForTesting
  String getTimestampDirName() {
    return new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss", Locale.getDefault())
        .format(new Timestamp(Clock.systemUTC().millis()));
  }

  private Path getXtsResultsDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(
        String.format("android-%s/results", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsLogsDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(String.format("android-%s/logs", Ascii.toLowerCase(xtsType.name())));
  }

  private Path getXtsTestCasesDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(
        String.format("android-%s/testcases", Ascii.toLowerCase(xtsType.name())));
  }
}
