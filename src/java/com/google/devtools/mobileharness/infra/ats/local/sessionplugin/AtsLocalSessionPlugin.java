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

package com.google.devtools.mobileharness.infra.ats.local.sessionplugin;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Job.AllocationExitStrategy;
import com.google.devtools.mobileharness.infra.ats.local.proto.AtsLocalSessionPluginProto.AtsLocalSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.local.proto.AtsLocalSessionPluginProto.AtsLocalSessionPluginOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationUtil;
import com.google.devtools.mobileharness.platform.android.xts.config.ConfigurationXmlParser;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.DeviceList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.Driver;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringList;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import java.io.File;
import java.util.List;
import javax.inject.Inject;

/** Session plugin to run ATS test locally. */
public class AtsLocalSessionPlugin {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String ANDROID_REAL_DEVICE_TYPE = "AndroidRealDevice";

  private final SessionInfo sessionInfo;
  private final LocalFileUtil localFileUtil;
  private final ModuleConfigurationHelper moduleConfigurationHelper;

  /** Set in {@link #onSessionStarting}. */
  private volatile AtsLocalSessionPluginConfig config;

  @Inject
  AtsLocalSessionPlugin(
      SessionInfo sessionInfo,
      LocalFileUtil localFileUtil,
      ModuleConfigurationHelper moduleConfigurationHelper) {
    this.sessionInfo = sessionInfo;
    this.localFileUtil = localFileUtil;
    this.moduleConfigurationHelper = moduleConfigurationHelper;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws InterruptedException, InvalidProtocolBufferException, MobileHarnessException {
    config =
        sessionInfo
            .getSessionPluginExecutionConfig()
            .getConfig()
            .unpack(AtsLocalSessionPluginConfig.class);
    addJob();
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event) {
    sessionInfo.setSessionPluginOutput(
        (oldOutput) -> getPluginOutput(sessionInfo.getAllJobs()),
        AtsLocalSessionPluginOutput.class);
  }

  public void addJob() throws MobileHarnessException, InterruptedException {
    logger.atInfo().log("Handle configuration: %s", config);
    verifySessionConfig();

    File moduleConfigFile = new File(config.getTestConfig());
    Configuration moduleConfig = ConfigurationXmlParser.parse(moduleConfigFile);
    String className = ConfigurationUtil.getSimpleClassName(moduleConfig.getTest().getClazz());
    JobConfig.Builder jobConfig =
        JobConfig.newBuilder()
            .setName("ats-local-job")
            .setExecMode("local")
            .setJobTimeoutSec(3 * 24 * 60 * 60)
            .setTestTimeoutSec(3 * 24 * 60 * 60)
            .setStartTimeoutSec(5 * 60)
            .setTestAttempts(1)
            .setDriver(Driver.newBuilder().setName(className))
            .setTests(StringList.newBuilder().addContent("ats-local-test"));
    jobConfig.setDevice(
        DeviceList.newBuilder()
            .addAllSubDeviceSpec(getSubDeviceSpecList(moduleConfig.getDevicesList())));
    // Fail fast if devices are not available
    jobConfig.setAllocationExitStrategy(AllocationExitStrategy.FAIL_FAST_NO_MATCH);
    jobConfig.setParams(
        StringMap.newBuilder()
            // Use the config's parent directory as the xts test dir for searching implicit
            // dependencies.
            .putContent("xts_test_dir", moduleConfigFile.getParent())
            .putContent("xts_suite_info", getSuiteInfoMap(moduleConfig)));

    JobInfo jobInfo = JobInfoCreator.createJobInfo(jobConfig.build(), ImmutableList.of(), "");
    ImmutableList<File> dependencies =
        config.getArtifactList().stream().map(File::new).collect(toImmutableList());
    moduleConfigurationHelper.updateJobInfo(
        jobInfo, moduleConfig, /* moduleDeviceConfig= */ null, dependencies);
    sessionInfo.addJob(jobInfo);
  }

  private void verifySessionConfig() throws MobileHarnessException {
    logger.atInfo().log("Verifying ATest session plugin config");
    localFileUtil.checkFile(config.getTestConfig());
    for (String artifact : config.getArtifactList()) {
      localFileUtil.checkFileOrDir(artifact);
    }
  }

  private ImmutableList<SubDeviceSpec> getSubDeviceSpecList(List<Device> deviceConfigs) {
    ImmutableList.Builder<SubDeviceSpec> subDeviceSpecList = ImmutableList.builder();
    StringMap.Builder dimensions = StringMap.newBuilder();
    if (!config.getDeviceSerialList().isEmpty()) {
      dimensions.putContent(
          "serial", String.format("regex:(%s)", Joiner.on("|").join(config.getDeviceSerialList())));
    }
    for (Device deviceConfig : deviceConfigs) {
      String type =
          deviceConfig.getName().isEmpty() ? ANDROID_REAL_DEVICE_TYPE : deviceConfig.getName();
      subDeviceSpecList.add(
          SubDeviceSpec.newBuilder().setType(type).setDimensions(dimensions).build());
    }
    return subDeviceSpecList.build();
  }

  private String getSuiteInfoMap(Configuration moduleConfig) {
    // Gets the suite name from test-suite-tag.
    String suiteName =
        moduleConfig.getOptionsList().stream()
            .filter(option -> option.getName().equals("test-suite-tag"))
            .findFirst()
            .map(option -> option.getValue())
            .orElse("");
    return Joiner.on(",").withKeyValueSeparator("=").join(ImmutableMap.of("suite_name", suiteName));
  }

  private AtsLocalSessionPluginOutput getPluginOutput(List<JobInfo> jobInfos) {
    AtsLocalSessionPluginOutput.Builder outputBuilder = AtsLocalSessionPluginOutput.newBuilder();
    if (!jobInfos.isEmpty()) {
      outputBuilder.setResult(jobInfos.get(0).resultWithCause().get().type());
      outputBuilder.setResultDetail(jobInfos.get(0).resultWithCause().get().toStringWithDetail());
    }
    return outputBuilder.build();
  }
}
