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

package com.google.devtools.mobileharness.infra.ats.server.sessionplugin;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.DeviceActionConfigObject;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.DeviceActionConfigObject.DeviceActionConfigObjectType;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.DeviceActionConfigObject.Option;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestEnvironment.LogLevel;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResource;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.TestResourceParameters;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.protobuf.Duration;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TradefedConfigGeneratorTest {
  private static final String EMPTY_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin/testdata/empty.xml");
  private static final String COMMAND_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/ats/server/sessionplugin/testdata/command.xml");

  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  @Test
  public void generateXml_empty() throws Exception {
    try (OutputStream outputStream = new ByteArrayOutputStream()) {
      TradefedConfigGenerator.generateXml(
          outputStream, TestEnvironment.getDefaultInstance(), ImmutableList.of(), 1);
      // Replace line-breaks
      String config = outputStream.toString().replace("\r\n", "\n");
      assertThat(config).isEqualTo(localFileUtil.readFile(EMPTY_PATH).trim());
    }
  }

  @Test
  public void generateXml_success() throws Exception {
    TestEnvironment testEnvironment =
        TestEnvironment.newBuilder()
            .putEnvVars("env1", "value1")
            .putEnvVars("env2", "")
            .addSetupScripts("setup.sh")
            .setUseSubprocessReporting(true)
            .setOutputIdleTimeout(Duration.newBuilder().setSeconds(3600))
            .addJvmOptions("-Xmx16g")
            .putJavaProperties("CTS_ROOT", "${TF_WORK_DIR}")
            .setUseParallelSetup(true)
            .addDeviceActionConfigObjects(
                DeviceActionConfigObject.newBuilder()
                    .setType(DeviceActionConfigObjectType.TARGET_PREPARER)
                    .setClassName("class1")
                    .addOptionValues(Option.newBuilder().setName("option1").addValue("value"))
                    .addOptionValues(
                        Option.newBuilder()
                            .setName("option2")
                            .addValue("value1")
                            .addValue("value2")))
            .addDeviceActionConfigObjects(
                DeviceActionConfigObject.newBuilder()
                    .setType(DeviceActionConfigObjectType.RESULT_REPORTER)
                    .setClassName("class2"))
            // Unused fields
            .addOutputFilePatterns("pattern")
            .setOutputFileUploadUrl("url")
            .setInvocationTimeout(Duration.newBuilder().setSeconds(7200))
            .setContextFilePattern(".*.zip")
            .addExtraContextFiles("file")
            .setRetryCommandLine("retry")
            .setLogLevel(LogLevel.DEBUG)
            .build();
    TestResource testResource =
        TestResource.newBuilder()
            .setUrl("url")
            .setName("zip")
            .setPath("path")
            .setDecompress(false)
            .setDecompressDir("decompress_dir")
            .setMountZip(true)
            .setParams(TestResourceParameters.newBuilder().addDecompressFiles("file"))
            .build();

    try (OutputStream outputStream = new ByteArrayOutputStream()) {
      TradefedConfigGenerator.generateXml(
          outputStream, testEnvironment, ImmutableList.of(testResource), 2);
      // Replace line-breaks
      String config = outputStream.toString().replace("\r\n", "\n");
      assertThat(config).isEqualTo(localFileUtil.readFile(COMMAND_PATH).trim());
    }
  }
}
