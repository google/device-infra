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

package com.google.devtools.mobileharness.platform.android.xts.config;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.ExtErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Device;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Option;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.TargetPreparer;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.in.SubDeviceSpec;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ModuleConfigurationHelperTest {
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private Path tempDirPath;
  private ModuleConfigurationHelper moduleConfigurationHelper;
  private LocalFileUtil localFileUtil;

  @Before
  public void setUp() {
    moduleConfigurationHelper = new ModuleConfigurationHelper();
    localFileUtil = new LocalFileUtil();
  }

  @Before
  public void prepareTempDir() throws Exception {
    tempDirPath = tempFolder.getRoot().toPath();
  }

  @Test
  public void updateJobInfo_addParamsAndFiles() throws Exception {
    JobInfo jobInfo = createBaseJobInfo();
    Path file = tempDirPath.resolve("testcase");
    localFileUtil.writeToFile(file.toString(), "content");
    Configuration config =
        Configuration.newBuilder()
            .setTest(
                ConfigurationProto.Test.newBuilder()
                    .setClazz("com.google.wireless.qa.mobileharness.shared.api.driver.NoOpDriver"))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .addOptions(Option.newBuilder().setName("param1").setValue("value1"))
            .addOptions(Option.newBuilder().setName("param2").setValue("value2"))
            .addOptions(Option.newBuilder().setName("filename").setKey("file").setValue("testcase"))
            .build();

    moduleConfigurationHelper.updateJobInfo(
        jobInfo, config, ImmutableList.of(tempDirPath.toFile()));

    assertThat(jobInfo.params().getAll()).containsExactly("param1", "value1", "param2", "value2");
    assertThat(jobInfo.files().getAll())
        .containsExactly("filename", file.toAbsolutePath().toString());
  }

  @Test
  public void updateJobInfo_fileNotFound() {
    JobInfo jobInfo = createBaseJobInfo();
    Configuration config =
        Configuration.newBuilder()
            .setTest(ConfigurationProto.Test.newBuilder().setClazz("NoOpDriver"))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .addOptions(Option.newBuilder().setName("filename").setKey("file").setValue("testcase"))
            .build();

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        moduleConfigurationHelper.updateJobInfo(
                            jobInfo, config, ImmutableList.of(tempDirPath.toFile())))
                .getErrorId())
        .isEqualTo(BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND);
  }

  @Test
  public void updateJobInfo_updateDeviceSpecs() throws Exception {
    JobInfo jobInfo = createBaseJobInfo();
    // 2 AndroidRealDevice, 1 NoOpDevice
    jobInfo.subDeviceSpecs().addSubDevice("NoOpDevice");
    jobInfo.subDeviceSpecs().addSubDevice("AndroidRealDevice");
    Path file1 = tempDirPath.resolve("file1");
    Path file2 = tempDirPath.resolve("dir").resolve("file2");
    Path file3 = tempDirPath.resolve("dir").resolve("file3");
    localFileUtil.writeToFile(file1.toString(), "content");
    localFileUtil.writeToFile(file2.toString(), "content");
    localFileUtil.writeToFile(file3.toString(), "content");
    Configuration config =
        Configuration.newBuilder()
            .setTest(ConfigurationProto.Test.newBuilder().setClazz("NoOpDriver"))
            .addDevices(
                Device.newBuilder()
                    .setName("AndroidRealDevice")
                    .addTargetPreparers(
                        TargetPreparer.newBuilder()
                            .setClazz("NoOpDecorator")
                            // Repeated options
                            .addOptions(
                                Option.newBuilder().setName("dummy_string_group").setValue("str1"))
                            .addOptions(
                                Option.newBuilder()
                                    .setName("dummy_string_group")
                                    .setValue("str2"))))
            .addDevices(
                Device.newBuilder()
                    .setName("AndroidRealDevice")
                    .addTargetPreparers(
                        TargetPreparer.newBuilder()
                            .setClazz("NoOpDecorator")
                            // File option
                            .addOptions(
                                Option.newBuilder().setName("dummy_file").setValue("file1"))))
            .addDevices(
                Device.newBuilder()
                    .setName("NoOpDevice")
                    .addTargetPreparers(
                        TargetPreparer.newBuilder()
                            .setClazz("NoOpDecorator")
                            // Repeated file options
                            .addOptions(
                                Option.newBuilder().setName("dummy_file_group").setValue("file2"))
                            .addOptions(
                                Option.newBuilder().setName("dummy_file_group").setValue("file3"))))
            .build();

    moduleConfigurationHelper.updateJobInfo(
        jobInfo, config, ImmutableList.of(tempDirPath.toFile()));

    checkSubDeviceSpec(
        jobInfo.subDeviceSpecs().getSubDevice(0),
        "AndroidRealDevice",
        ImmutableList.of("NoOpDecorator"),
        "{\n"
            + "  \"NoOpDecoratorSpec\": {\n"
            + "    \"dummy_string_group\": [\n"
            + "      \"str1\",\n"
            + "      \"str2\"\n"
            + "    ]\n"
            + "  }\n"
            + "}");
    checkSubDeviceSpec(
        jobInfo.subDeviceSpecs().getSubDevice(1),
        "NoOpDevice",
        ImmutableList.of("NoOpDecorator"),
        "{\n"
            + "  \"NoOpDecoratorSpec\": {\n"
            + "    \"dummy_file_group\": [\n"
            + String.format("      \"%s\",\n", file2.toAbsolutePath())
            + String.format("      \"%s\"\n", file3.toAbsolutePath())
            + "    ]\n"
            + "  }\n"
            + "}");
    checkSubDeviceSpec(
        jobInfo.subDeviceSpecs().getSubDevice(2),
        "AndroidRealDevice",
        ImmutableList.of("NoOpDecorator"),
        "{\n"
            + "  \"NoOpDecoratorSpec\": {\n"
            + String.format("    \"dummy_file\": \"%s\"\n", file1.toAbsolutePath())
            + "  }\n"
            + "}");
  }

  @Test
  public void updateJobInfo_deviceNumberNotMatch() {
    // 1 AndroidRealDevice
    JobInfo jobInfo = createBaseJobInfo();
    Configuration config =
        Configuration.newBuilder()
            .setTest(ConfigurationProto.Test.newBuilder().setClazz("NoOpDriver"))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .addDevices(Device.newBuilder().setName("NoOpDevice"))
            .build();

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        moduleConfigurationHelper.updateJobInfo(
                            jobInfo, config, ImmutableList.of()))
                .getErrorId())
        .isEqualTo(ExtErrorId.MODULE_CONFIG_DEVICE_NUMBER_NOT_MATCH);
  }

  @Test
  public void updateJobInfo_updateDriverSpec() throws Exception {
    JobInfo jobInfo = createBaseJobInfo();
    Configuration config =
        Configuration.newBuilder()
            .setTest(
                ConfigurationProto.Test.newBuilder()
                    .setClazz("NoOpDriver")
                    .addOptions(Option.newBuilder().setName("sleep_time_sec").setValue("100")))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .build();

    moduleConfigurationHelper.updateJobInfo(jobInfo, config, ImmutableList.of());

    assertThat(jobInfo.scopedSpecs().toJsonString())
        .isEqualTo("{\n  \"NoOpDriverSpec\": {\n    \"sleep_time_sec\": 100\n  }\n}");
  }

  @Test
  public void updateJobInfo_driverNotMatch() {
    JobInfo jobInfo = createBaseJobInfo();
    Configuration config =
        Configuration.newBuilder()
            .setTest(
                ConfigurationProto.Test.newBuilder()
                    .setClazz(
                        "com.google.wireless.qa.mobileharness.shared.api.driver.AnotherDriver"))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .build();

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        moduleConfigurationHelper.updateJobInfo(
                            jobInfo, config, ImmutableList.of()))
                .getErrorId())
        .isEqualTo(ExtErrorId.MODULE_CONFIG_DRIVER_NOT_MATCH);
  }

  @Test
  public void updateJobInfo_unrecognizedOption() {
    JobInfo jobInfo = createBaseJobInfo();
    Configuration config =
        Configuration.newBuilder()
            .setTest(
                ConfigurationProto.Test.newBuilder()
                    .setClazz("NoOpDriver")
                    .addOptions(Option.newBuilder().setName("unknown").setValue("value")))
            .addDevices(Device.newBuilder().setName("AndroidRealDevice"))
            .build();

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () ->
                        moduleConfigurationHelper.updateJobInfo(
                            jobInfo, config, ImmutableList.of()))
                .getErrorId())
        .isEqualTo(ExtErrorId.MODULE_CONFIG_UNRECOGNIZED_OPTION_ERROR);
  }

  private JobInfo createBaseJobInfo() {
    return JobInfo.newBuilder()
        .setLocator(new JobLocator("job_id", "job_name"))
        .setType(
            JobType.newBuilder().setDriver("NoOpDriver").setDevice("AndroidRealDevice").build())
        .build();
  }

  private void checkSubDeviceSpec(
      SubDeviceSpec subDeviceSpec, String type, List<String> decorators, String scopedSpecJson) {
    assertThat(subDeviceSpec.type()).isEqualTo(type);
    assertThat(subDeviceSpec.decorators().getAll()).containsExactlyElementsIn(decorators);
    assertThat(subDeviceSpec.scopedSpecs().toString()).isEqualTo(scopedSpecJson);
  }
}
