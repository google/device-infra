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

package com.google.devtools.mobileharness.infra.ats.common;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.protobuf.TextFormat.shortDebugString;
import static java.lang.Math.min;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.InfraErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.XtsType;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.gson.Gson;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.Job.Priority;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.DeviceList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.Driver;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryFilter;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/** Helper class for ATS applications to create job config. */
public class SessionRequestHandlerUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String ANDROID_REAL_DEVICE_TYPE = "AndroidRealDevice";

  private final DeviceQuerier deviceQuerier;
  private final LocalFileUtil localFileUtil;
  private final ConfigurationUtil configurationUtil;

  @Inject
  SessionRequestHandlerUtil(
      DeviceQuerier deviceQuerier,
      LocalFileUtil localFileUtil,
      ConfigurationUtil configurationUtil) {
    this.deviceQuerier = deviceQuerier;
    this.localFileUtil = localFileUtil;
    this.configurationUtil = configurationUtil;
  }

  /**
   * Data holder used to create jobInfo. Data comes from session request handlers, like
   * RunCommandHandler.
   */
  @AutoValue
  public abstract static class SessionRequestInfo {
    public abstract String testPlan();

    public abstract String xtsRootDir();

    public abstract Optional<String> androidXtsZip();

    public abstract ImmutableList<String> deviceSerials();

    public abstract ImmutableList<String> moduleNames();

    public abstract Optional<Integer> shardCount();

    public abstract ImmutableList<String> extraArgs();

    public abstract XtsType xtsType();

    public abstract Optional<String> pythonPkgIndexUrl();

    public static Builder builder() {
      return new AutoValue_SessionRequestHandlerUtil_SessionRequestInfo.Builder()
          .setModuleNames(ImmutableList.of())
          .setDeviceSerials(ImmutableList.of())
          .setExtraArgs(ImmutableList.of());
    }

    /** Builder to create SessionRequestInfo. */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setTestPlan(String testPlan);

      public abstract Builder setXtsRootDir(String xtsRootDir);

      public abstract Builder setDeviceSerials(List<String> deviceSerials);

      public abstract Builder setModuleNames(List<String> moduleNames);

      public abstract Builder setShardCount(int shardCount);

      public abstract Builder setExtraArgs(List<String> extraArgs);

      public abstract Builder setXtsType(XtsType xtsType);

      public abstract Builder setPythonPkgIndexUrl(String pythonPkgIndexUrl);

      public abstract Builder setAndroidXtsZip(String androidXtsZip);

      public abstract SessionRequestInfo build();
    }
  }

  /**
   * Gets a list of SubDeviceSpec for the job. One SubDeviceSpec maps to one subdevice used for
   * running the job as the job may need multiple devices to run the test.
   */
  public ImmutableList<SubDeviceSpec> getSubDeviceSpecList(
      List<String> passedInDeviceSerials, int shardCount)
      throws MobileHarnessException, InterruptedException {
    ImmutableSet<String> allAndroidDevices = getAllAndroidDevices();
    logger.atInfo().log("All android devices: %s", allAndroidDevices);
    if (passedInDeviceSerials.isEmpty()) {
      return pickAndroidOnlineDevices(allAndroidDevices, shardCount);
    }

    ArrayList<String> existingPassedInDeviceSerials = new ArrayList<>();
    passedInDeviceSerials.forEach(
        serial -> {
          if (allAndroidDevices.contains(serial)) {
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

  private ImmutableSet<String> getAllAndroidDevices()
      throws MobileHarnessException, InterruptedException {
    DeviceQueryResult queryResult;
    try {
      queryResult = deviceQuerier.queryDevice(DeviceQueryFilter.getDefaultInstance());
    } catch (com.google.wireless.qa.mobileharness.shared.MobileHarnessException e) {
      throw new MobileHarnessException(
          InfraErrorId.ATSC_RUN_COMMAND_QUERY_DEVICE_ERROR, "Failed to query device", e);
    }
    return queryResult.getDeviceInfoList().stream()
        .filter(
            deviceInfo ->
                deviceInfo.getTypeList().stream()
                    .anyMatch(deviceType -> deviceType.startsWith("Android")))
        .map(DeviceInfo::getId)
        .collect(toImmutableSet());
  }

  public Optional<JobInfo> createXtsTradefedTestJob(SessionRequestInfo sessionRequestInfo)
      throws MobileHarnessException, InterruptedException {
    String xtsRootDir = sessionRequestInfo.xtsRootDir();
    if (!localFileUtil.isDirExist(xtsRootDir)) {
      logger.atInfo().log(
          "xTS root dir [%s] doesn't exist, skip creating tradefed jobs.", xtsRootDir);
      return Optional.empty();
    }

    XtsType xtsType = sessionRequestInfo.xtsType();
    ImmutableMap<String, Configuration> configsMap =
        configurationUtil.getConfigsFromDirs(
            ImmutableList.of(getXtsTestCasesDir(Path.of(xtsRootDir), xtsType).toFile()));

    List<String> modules = sessionRequestInfo.moduleNames();
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
        createXtsTradefedTestJobConfig(sessionRequestInfo, givenMatchedTfModules);
    if (jobConfig.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(JobInfoCreator.createJobInfo(jobConfig.get(), ImmutableList.of(), null));
  }

  @VisibleForTesting
  Optional<JobConfig> createXtsTradefedTestJobConfig(
      SessionRequestInfo sessionRequestInfo, ImmutableList<String> tfModules)
      throws MobileHarnessException, InterruptedException {
    String testPlan = sessionRequestInfo.testPlan();
    String xtsRootDir = sessionRequestInfo.xtsRootDir();
    String xtsType = sessionRequestInfo.xtsType().name();
    List<String> deviceSerials = sessionRequestInfo.deviceSerials();
    Integer shardCount = sessionRequestInfo.shardCount().orElse(0);
    List<String> extraArgs = sessionRequestInfo.extraArgs();

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
            .setPriority(Priority.HIGH)
            .setTestAttempts(1)
            .setTests(
                StringList.newBuilder()
                    .addContent(String.format("xts-tradefed-test-%s", testPlan)));
    jobConfigBuilder.setDevice(DeviceList.newBuilder().addAllSubDeviceSpec(subDeviceSpecList));

    Map<String, String> driverParams = new HashMap<>();
    driverParams.put("xts_type", xtsType);

    // Use android xts zip file path if specified in request. Otherwise use root directory path.
    if (sessionRequestInfo.androidXtsZip().isPresent()) {
      driverParams.put("android_xts_zip", sessionRequestInfo.androidXtsZip().get());
    } else {
      driverParams.put("xts_root_dir", xtsRootDir);
    }
    driverParams.put("xts_test_plan", testPlan);

    ImmutableList<String> shardCountArg =
        shardCount > 0
            ? ImmutableList.of(String.format("--shard-count %s", shardCount))
            : ImmutableList.of();
    String sessionRequestInfoArgs =
        Joiner.on(' ')
            .join(
                Streams.concat(
                        tfModules.stream().map(module -> String.format("-m %s", module)),
                        shardCountArg.stream(),
                        extraArgs.stream())
                    .collect(toImmutableList()));
    if (!sessionRequestInfoArgs.isEmpty()) {
      driverParams.put("run_command_args", sessionRequestInfoArgs);
    }

    jobConfigBuilder.setDriver(
        Driver.newBuilder().setName("XtsTradefedTest").setParam(new Gson().toJson(driverParams)));

    JobConfig jobConfig = jobConfigBuilder.build();
    logger.atInfo().log("XtsTradefedTest job config: %s", shortDebugString(jobConfig));

    return Optional.of(jobConfig);
  }

  private Path getXtsTestCasesDir(Path xtsRootDir, XtsType xtsType) {
    return xtsRootDir.resolve(
        String.format("android-%s/testcases", Ascii.toLowerCase(xtsType.name())));
  }
}
