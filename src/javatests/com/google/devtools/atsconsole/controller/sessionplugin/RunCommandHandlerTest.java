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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.platform.android.sdktool.adb.AndroidAdbInternalUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.StringMap;
import com.google.wireless.qa.mobileharness.shared.proto.JobConfig.SubDeviceSpec;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class RunCommandHandlerTest {

  private static final String XTS_ROOT_DIR_PATH = "/path/to/xts_root_dir";

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Bind @Mock private AndroidAdbInternalUtil adbInternalUtil;

  @Inject private RunCommandHandler runCommandHandler;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    runCommandHandler = spy(runCommandHandler);
  }

  @Test
  public void createXtsTradefedTestJobConfig_pickOneDevice() throws Exception {
    when(adbInternalUtil.getRealDeviceSerials(/* online= */ true))
        .thenReturn(ImmutableSet.of("device_id_1", "device_id_2"));

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
    when(adbInternalUtil.getRealDeviceSerials(/* online= */ true))
        .thenReturn(ImmutableSet.of("device_id_1", "device_id_2"));

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
    when(adbInternalUtil.getRealDeviceSerials(/* online= */ true))
        .thenReturn(ImmutableSet.of("device_id_1", "device_id_2"));

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
    when(adbInternalUtil.getRealDeviceSerials(/* online= */ true)).thenReturn(ImmutableSet.of());

    Optional<JobConfig> jobConfigOpt =
        runCommandHandler.createXtsTradefedTestJobConfig(
            RunCommand.newBuilder().setTestPlan("cts").setXtsRootDir(XTS_ROOT_DIR_PATH).build(),
            /* xtsType= */ "CTS");

    assertThat(jobConfigOpt).isEmpty();
  }

  @Test
  public void createXtsTradefedTestJobConfig_withGivenSerial() throws Exception {
    when(adbInternalUtil.getRealDeviceSerials(/* online= */ true))
        .thenReturn(ImmutableSet.of("device_id_1", "device_id_2"));

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
    when(adbInternalUtil.getRealDeviceSerials(/* online= */ true))
        .thenReturn(ImmutableSet.of("device_id_1", "device_id_2", "device_id_3"));

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
    when(adbInternalUtil.getRealDeviceSerials(/* online= */ true))
        .thenReturn(ImmutableSet.of("device_id_1", "device_id_2", "device_id_3"));

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
}
