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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.XtsType;
import com.google.devtools.mobileharness.infra.client.api.controller.device.DeviceQuerier;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionDetailHolder;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionDetail;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginLabel;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceInfo;
import com.google.wireless.qa.mobileharness.shared.proto.query.DeviceQuery.DeviceQueryResult;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RunCommandHandlerTest {

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";
  private static final String XTS_ROOT_DIR_NAME = "xts_root_dir";
  private static final String TIMESTAMP_DIR_NAME = "2023.06.13_06.27.28";

  private static final LocalFileUtil realLocalFileUtil = new LocalFileUtil();

  private static final Path JOB_1_GEN_DIR =
      Paths.get(
          RunfilesUtil.getRunfilesLocation(
              "javatests/com/google/devtools/atsconsole/controller/sessionplugin/testdata/runcommand/resultprocessing/job-1_gen/"));

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Bind @Mock private DeviceQuerier deviceQuerier;

  @Inject private RunCommandHandler runCommandHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    runCommandHandler = spy(runCommandHandler);
    doReturn(TIMESTAMP_DIR_NAME).when(runCommandHandler).getTimestampDirName();
  }

  @Test
  public void createXtsTradefedTestJobConfig_pickOneDevice() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        runCommandHandler.createXtsTradefedTestJobConfig(
            RunCommand.newBuilder().setTestPlan("cts").setXtsRootDir(XTS_ROOT_DIR_PATH).build(),
            /* xtsType= */ "CTS");

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build());

    // Asserts the driver
    assertThat(jobConfigOpt.get().getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParams = jobConfigOpt.get().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap)
        .containsExactly(
            "xts_type", "CTS", "xts_root_dir", XTS_ROOT_DIR_PATH, "xts_test_plan", "cts");
  }

  @Test
  public void createXtsTradefedTestJobConfig_shardCount2_pick2Devices() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        runCommandHandler.createXtsTradefedTestJobConfig(
            RunCommand.newBuilder()
                .setTestPlan("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardCount(2)
                .build(),
            /* xtsType= */ "CTS");

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build(),
            SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build());
  }

  @Test
  public void createXtsTradefedTestJobConfig_shardCount3_only2OnlineDevices_pick2Devices()
      throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        runCommandHandler.createXtsTradefedTestJobConfig(
            RunCommand.newBuilder()
                .setTestPlan("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .setShardCount(3)
                .build(),
            /* xtsType= */ "CTS");

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build(),
            SubDeviceSpec.newBuilder().setType("AndroidRealDevice").build());
  }

  @Test
  public void createXtsTradefedTestJobConfig_noOnlineDevices_noJobConfig() throws Exception {
    when(deviceQuerier.queryDevice(any())).thenReturn(DeviceQueryResult.getDefaultInstance());

    Optional<JobConfig> jobConfigOpt =
        runCommandHandler.createXtsTradefedTestJobConfig(
            RunCommand.newBuilder().setTestPlan("cts").setXtsRootDir(XTS_ROOT_DIR_PATH).build(),
            /* xtsType= */ "CTS");

    assertThat(jobConfigOpt).isEmpty();
  }

  @Test
  public void createXtsTradefedTestJobConfig_withGivenSerial() throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        runCommandHandler.createXtsTradefedTestJobConfig(
            RunCommand.newBuilder()
                .setTestPlan("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .addDeviceSerial("device_id_1")
                .addModuleName("module1")
                .setShardCount(2)
                .addExtraArg("--logcat-on-failure")
                .build(),
            /* xtsType= */ "CTS");

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(StringMap.newBuilder().putContent("serial", "device_id_1"))
                .build());

    // Asserts the driver
    assertThat(jobConfigOpt.get().getDriver().getName()).isEqualTo("XtsTradefedTest");
    String driverParams = jobConfigOpt.get().getDriver().getParam();
    Map<String, String> driverParamsMap =
        new Gson().fromJson(driverParams, new TypeToken<Map<String, String>>() {});
    assertThat(driverParamsMap)
        .containsExactly(
            "xts_type",
            "CTS",
            "xts_root_dir",
            XTS_ROOT_DIR_PATH,
            "xts_test_plan",
            "cts",
            "run_command_args",
            "-m module1 --shard-count 2 --logcat-on-failure");
  }

  @Test
  public void createXtsTradefedTestJobConfig_someGivenSerialsNotExist_pickExistingDevicesOnly()
      throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_3").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        runCommandHandler.createXtsTradefedTestJobConfig(
            RunCommand.newBuilder()
                .setTestPlan("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .addDeviceSerial("device_id_1")
                .addDeviceSerial("not_exist_device")
                .addDeviceSerial("device_id_3")
                .build(),
            /* xtsType= */ "CTS");

    assertThat(jobConfigOpt).isPresent();
    assertThat(jobConfigOpt.get().getDevice().getSubDeviceSpecList())
        .containsExactly(
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(StringMap.newBuilder().putContent("serial", "device_id_1"))
                .build(),
            SubDeviceSpec.newBuilder()
                .setType("AndroidRealDevice")
                .setDimensions(StringMap.newBuilder().putContent("serial", "device_id_3"))
                .build());
  }

  @Test
  public void createXtsTradefedTestJobConfig_allGivenSerialsNotExist_noJobConfig()
      throws Exception {
    when(deviceQuerier.queryDevice(any()))
        .thenReturn(
            DeviceQueryResult.newBuilder()
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_1").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_2").addType("AndroidOnlineDevice"))
                .addDeviceInfo(
                    DeviceInfo.newBuilder().setId("device_id_3").addType("AndroidOnlineDevice"))
                .build());

    Optional<JobConfig> jobConfigOpt =
        runCommandHandler.createXtsTradefedTestJobConfig(
            RunCommand.newBuilder()
                .setTestPlan("cts")
                .setXtsRootDir(XTS_ROOT_DIR_PATH)
                .addDeviceSerial("device_id_4")
                .addDeviceSerial("device_id_5")
                .build(),
            /* xtsType= */ "CTS");

    assertThat(jobConfigOpt).isEmpty();
  }

  @Test
  public void handleResultProcessing_copyResultsAndLogsIntoXtsRootDir() throws Exception {
    File xtsRootDir = folder.newFolder(XTS_ROOT_DIR_NAME);
    RunCommand command =
        RunCommand.newBuilder()
            .setXtsType(XtsType.CTS)
            .setXtsRootDir(xtsRootDir.getAbsolutePath())
            .build();

    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder()
                    .setDevice("AndroidRealDevice")
                    .setDriver("XtsTradefedTest")
                    .build())
            .setSetting(
                JobSetting.newBuilder()
                    .setGenFileDir(JOB_1_GEN_DIR.toAbsolutePath().toString())
                    .build())
            .setTiming(new Timing())
            .build();
    jobInfo.properties().add(RunCommandHandler.XTS_TF_JOB_PROP, "true");
    jobInfo.tests().add("1", "test_name");

    SessionInfo sessionInfo =
        new SessionInfo(
            new SessionDetailHolder(SessionDetail.getDefaultInstance()),
            SessionPluginLabel.getDefaultInstance(),
            SessionPluginExecutionConfig.getDefaultInstance());
    sessionInfo.addJob(jobInfo);

    runCommandHandler.handleResultProcessing(command, sessionInfo);

    assertThat(
            xtsRootDir
                .toPath()
                .resolve(String.format("android-cts/results/%s", TIMESTAMP_DIR_NAME))
                .toFile()
                .isDirectory())
        .isTrue();
    assertThat(
            xtsRootDir
                .toPath()
                .resolve(String.format("android-cts/results/%s.zip", TIMESTAMP_DIR_NAME))
                .toFile()
                .isFile())
        .isTrue();
    List<Path> newFilesInResultsDir =
        realLocalFileUtil.listFilePaths(
            xtsRootDir.toPath().resolve("android-cts/results"), /* recursively= */ true);
    assertThat(newFilesInResultsDir.stream().map(f -> f.getFileName().toString()))
        .containsExactly("test_result.xml", String.format("%s.zip", TIMESTAMP_DIR_NAME));

    List<Path> newFilesInLogsDir =
        realLocalFileUtil.listFilePaths(
            xtsRootDir.toPath().resolve("android-cts/logs"), /* recursively= */ true);
    assertThat(newFilesInLogsDir.stream().map(f -> f.getFileName().toString()))
        .containsExactly("host_adb_log.txt", "xts_tf_output.log", "command_history.txt");
  }
}
