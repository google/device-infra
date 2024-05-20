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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.plugin.SkipTestException;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.Configuration;
import com.google.devtools.mobileharness.platform.android.xts.config.proto.ConfigurationProto.ConfigurationMetadata;
import com.google.wireless.qa.mobileharness.shared.controller.event.TestStartingEvent;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobSetting;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Files;
import com.google.wireless.qa.mobileharness.shared.model.job.in.Params;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class ModuleConfigurationLabPluginTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private ConfigurationUtil configurationUtil;
  @Mock private ModuleConfigurationHelper moduleConfigurationHelper;
  @Mock private TestInfo testInfo;
  @Mock private JobInfo jobInfo;
  @Mock private Files files;
  @Mock private Params params;
  @Mock private JobSetting jobSetting;
  @Mock private TestStartingEvent testStartingEvent;

  private static final String TEST_PACKAGE_PATH = "test_package_path";

  ModuleConfigurationLabPlugin moduleConfigurationLabPlugin;

  @Before
  public void setUp() throws Exception {
    moduleConfigurationLabPlugin =
        new ModuleConfigurationLabPlugin(configurationUtil, moduleConfigurationHelper);
    when(testStartingEvent.getTest()).thenReturn(testInfo);
    when(testInfo.jobInfo()).thenReturn(jobInfo);
    when(jobInfo.files()).thenReturn(files);
    when(jobInfo.params()).thenReturn(params);
    when(files.getSingle(ModuleConfigurationLabPlugin.AB_TEST_PACKAGE))
        .thenReturn(TEST_PACKAGE_PATH);
    when(jobInfo.setting()).thenReturn(jobSetting);
    when(jobSetting.getTmpFileDir()).thenReturn("tmp");
  }

  @Test
  public void onTestStarting_withoutModuleName() throws Exception {
    when(params.getOptional(ModuleConfigurationLabPlugin.MODULE_NAME)).thenReturn(Optional.empty());
    Configuration targetConfig = Configuration.getDefaultInstance();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(
            ImmutableMap.of(
                "module1", targetConfig, "module2", Configuration.getDefaultInstance()));

    moduleConfigurationLabPlugin.onTestStarting(testStartingEvent);

    verify(moduleConfigurationHelper).updateJobInfo(any(), eq(targetConfig), any(), any());
  }

  @Test
  public void onTestStarting_withModuleName() throws Exception {
    String targetModuleName = "TargetModule";
    when(params.getOptional(ModuleConfigurationLabPlugin.MODULE_NAME))
        .thenReturn(Optional.of(targetModuleName));
    Configuration targetConfig =
        Configuration.newBuilder()
            .setMetadata(ConfigurationMetadata.newBuilder().setXtsModule(targetModuleName))
            .build();
    when(configurationUtil.getConfigsV2FromDirs(any()))
        .thenReturn(
            ImmutableMap.of(
                "module1", Configuration.getDefaultInstance(), "module2", targetConfig));

    moduleConfigurationLabPlugin.onTestStarting(testStartingEvent);

    verify(moduleConfigurationHelper).updateJobInfo(any(), eq(targetConfig), any(), any());
  }

  @Test
  public void onTestStarting_configNotFound() {
    when(params.getOptional(ModuleConfigurationLabPlugin.MODULE_NAME)).thenReturn(Optional.empty());
    when(configurationUtil.getConfigsV2FromDirs(any())).thenReturn(ImmutableMap.of());

    SkipTestException exception =
        assertThrows(
            SkipTestException.class,
            () -> moduleConfigurationLabPlugin.onTestStarting(testStartingEvent));

    assertThat(exception.testResult()).isEqualTo(TestResult.ERROR);
    assertThat(exception.errorId()).isEqualTo(BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND);
  }
}
