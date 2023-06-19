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

package com.google.devtools.atsconsole.controller.sessionplugin;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.lang.Math.min;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.XtsType;
import com.google.devtools.deviceinfra.shared.util.flags.Flags;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.gson.Gson;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.DeviceList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.Driver;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
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
import java.util.Set;
import javax.inject.Inject;

/** Handler for "run" commands. */
class RunCommandHandler {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String DEVICE_DEFAULT_OWNER = "mobileharness-device-default-owner";
  private static final String ANDROID_REAL_DEVICE_TYPE = "AndroidRealDevice";
  @VisibleForTesting static final String XTS_TF_JOB_PROP = "xts-tradefed-job";

  private final AndroidAdbInternalUtil adbInternalUtil;
  private final LocalFileUtil localFileUtil;

  @Inject
  RunCommandHandler(AndroidAdbInternalUtil adbInternalUtil, LocalFileUtil localFileUtil) {
    this.adbInternalUtil = adbInternalUtil;
    this.localFileUtil = localFileUtil;
  }

  /**
   * Creates jobs based on the {@code command} and adds the jobs to the {@code sessionInfo}.
   *
   * <p>Jobs added to the session by the plugin will be started by the session job runner later.
   */
  Optional<AtsSessionPluginOutput> handle(RunCommand command, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    Optional<JobInfo> jobInfo = createXtsTradefedTestJob(command, command.getXtsType().name());
    if (jobInfo.isEmpty()) {
      return Optional.of(
          AtsSessionPluginOutput.newBuilder()
              .setFailure(
                  Failure.newBuilder()
                      .setErrorMessage(
                          String.format(
                              "Not able to create a job info per the run command, double check"
                                  + " device availability: %s",
                              shortDebugString(command))))
              .build());
    }
    jobInfo.get().properties().add(XTS_TF_JOB_PROP, "true");
    sessionInfo.addJob(jobInfo.get());
    logger.atInfo().log(
        "Added job[%s] to the session %s",
        jobInfo.get().locator().getId(), sessionInfo.getSessionId());
    return Optional.empty();
  }

  private Optional<JobInfo> createXtsTradefedTestJob(RunCommand runCommand, String xtsType)
      throws MobileHarnessException, InterruptedException {
    Optional<JobConfig> jobConfig = createXtsTradefedTestJobConfig(runCommand, xtsType);
    if (jobConfig.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(JobInfoCreator.createJobInfo(jobConfig.get(), ImmutableList.of(), null));
  }

  @VisibleForTesting
  Optional<JobConfig> createXtsTradefedTestJobConfig(RunCommand runCommand, String xtsType)
      throws MobileHarnessException, InterruptedException {
    String testPlan = runCommand.getTestPlan();
    String xtsRootDir = runCommand.getXtsRootDir();
    List<String> deviceSerials = runCommand.getDeviceSerialList();
    List<String> modules = runCommand.getModuleNameList();
    int shardCount = runCommand.getShardCount();
    List<String> extraArgs = runCommand.getExtraArgList();

    ImmutableList<SubDeviceSpec> subDeviceSpecList =
        getSubDeviceSpecList(deviceSerials, shardCount);
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
            .setRunAs(DEVICE_DEFAULT_OWNER)
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
                        modules.stream().map(module -> String.format("-m %s", module)),
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
   * Gets a list of SubDeviceSpec for the job. One SubDeviceSpec maps to one subdevice used for
   * running the job as the job may need multiple devices to run the test.
   */
  private ImmutableList<SubDeviceSpec> getSubDeviceSpecList(
      List<String> passedInDeviceSerials, int shardCount)
      throws MobileHarnessException, InterruptedException {
    Set<String> allAndroidOnlineDevices;
    if (Flags.instance().detectAdbDevice.getNonNull()) {
      allAndroidOnlineDevices = adbInternalUtil.getRealDeviceSerials(/* online= */ true);
    } else {
      allAndroidOnlineDevices = ImmutableSet.of();
    }
    logger.atInfo().log("All online devices: %s", allAndroidOnlineDevices);
    if (passedInDeviceSerials.isEmpty()) {
      return pickAndroidOnlineDevices(allAndroidOnlineDevices, shardCount);
    }

    ArrayList<String> existingPassedInDeviceSerials = new ArrayList<>();
    passedInDeviceSerials.forEach(
        serial -> {
          if (allAndroidOnlineDevices.contains(serial)) {
            existingPassedInDeviceSerials.add(serial);
          } else {
            logger.atInfo().log("Passed in device serial [%s] is not detected, skipped.", serial);
          }
        });
    if (existingPassedInDeviceSerials.isEmpty()) {
      logger.atInfo().log("None of passed in devices exist [%s], skipped.", passedInDeviceSerials);
      return ImmutableList.of();
    }
    return existingPassedInDeviceSerials.stream()
        .map(
            serial ->
                SubDeviceSpec.newBuilder()
                    .setType(ANDROID_REAL_DEVICE_TYPE)
                    .setDimensions(StringMap.newBuilder().putContent("serial", serial))
                    .build())
        .collect(toImmutableList());
  }

  private ImmutableList<SubDeviceSpec> pickAndroidOnlineDevices(
      Set<String> allAndroidOnlineDevices, int shardCount) {
    if (shardCount <= 1 && !allAndroidOnlineDevices.isEmpty()) {
      return ImmutableList.of(SubDeviceSpec.newBuilder().setType(ANDROID_REAL_DEVICE_TYPE).build());
    }
    int numOfNeededDevices = min(allAndroidOnlineDevices.size(), shardCount);
    ImmutableList.Builder<SubDeviceSpec> deviceSpecList = ImmutableList.builder();
    for (int i = 0; i < numOfNeededDevices; i++) {
      deviceSpecList.add(SubDeviceSpec.newBuilder().setType(ANDROID_REAL_DEVICE_TYPE).build());
    }
    return deviceSpecList.build();
  }

  /**
   * Copies xTS tradefed generated logs/results into proper locations within the given xts root dir.
   */
  void handleResultProcessing(RunCommand command, SessionInfo sessionInfo)
      throws MobileHarnessException, InterruptedException {
    Path xtsRootDir = Path.of(command.getXtsRootDir());
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log("xTS root dir [%s] doesn't exist, skip processing result.", xtsRootDir);
      return;
    }

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
}
