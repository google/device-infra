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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth8;
import com.google.devtools.mobileharness.api.model.error.BasicErrorId;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.infra.ats.local.proto.AtsLocalSessionPluginProto.AtsLocalSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.local.proto.AtsLocalSessionPluginProto.AtsLocalSessionPluginOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.platform.android.xts.config.ModuleConfigurationHelper;
import com.google.devtools.mobileharness.shared.util.runfiles.RunfilesUtil;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class AtsLocalSessionPluginTest {
  private static final String TEST_CONFIG_PATH =
      RunfilesUtil.getRunfilesLocation(
          "javatests/com/google/devtools/mobileharness/infra/ats/local/testdata/test.xml");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private SessionInfo sessionInfo;
  @Bind @Mock private ModuleConfigurationHelper moduleConfigurationHelper;

  @Captor private ArgumentCaptor<JobInfo> jobInfoCaptor;
  @Captor private ArgumentCaptor<UnaryOperator<AtsLocalSessionPluginOutput>> pluginOutputCaptor;

  @Inject private AtsLocalSessionPlugin plugin;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void onSessionStarting_addJob_success() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        AtsLocalSessionPluginConfig.newBuilder()
                            .setTestConfig(TEST_CONFIG_PATH)
                            .build()))
                .build());

    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    verify(sessionInfo).addJob(jobInfoCaptor.capture());
    JobInfo jobInfo = jobInfoCaptor.getValue();
    assertThat(jobInfo.type().getDriver()).isEqualTo("NoOpDriver");
    assertThat(jobInfo.subDeviceSpecs().getSubDeviceCount()).isEqualTo(2);
    assertThat(jobInfo.subDeviceSpecs().getSubDevice(0).type()).isEqualTo("NoOpDevice");
    assertThat(jobInfo.subDeviceSpecs().getSubDevice(0).deviceRequirement().dimensions().isEmpty())
        .isTrue();
  }

  @Test
  public void onSessionStarting_addJob_withSerials() throws Exception {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        AtsLocalSessionPluginConfig.newBuilder()
                            .setTestConfig(TEST_CONFIG_PATH)
                            .addDeviceSerial("serial_1")
                            .addDeviceSerial("serial_2")
                            .build()))
                .build());

    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    verify(sessionInfo).addJob(jobInfoCaptor.capture());
    JobInfo jobInfo = jobInfoCaptor.getValue();
    assertThat(jobInfo.subDeviceSpecs().getSubDeviceCount()).isEqualTo(2);
    assertThat(jobInfo.subDeviceSpecs().getSubDevice(0).type()).isEqualTo("NoOpDevice");
    Optional<String> serialDimension =
        jobInfo.subDeviceSpecs().getSubDevice(0).deviceRequirement().dimensions().get("serial");
    Truth8.assertThat(serialDimension).hasValue("regex:(serial_1|serial_2)");
  }

  @Test
  public void onSessionStarting_addJob_testConfigNotExist() {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        AtsLocalSessionPluginConfig.newBuilder()
                            .setTestConfig("path/to/file")
                            .build()))
                .build());

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> plugin.onSessionStarting(new SessionStartingEvent(sessionInfo)))
                .getErrorId())
        .isEqualTo(BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND);
  }

  @Test
  public void onSessionStarting_addJob_artifactNotExist() {
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        AtsLocalSessionPluginConfig.newBuilder()
                            .setTestConfig(TEST_CONFIG_PATH)
                            .addArtifact("path/to/file")
                            .build()))
                .build());

    assertThat(
            assertThrows(
                    MobileHarnessException.class,
                    () -> plugin.onSessionStarting(new SessionStartingEvent(sessionInfo)))
                .getErrorId())
        .isEqualTo(BasicErrorId.LOCAL_FILE_OR_DIR_NOT_FOUND);
  }

  @Test
  public void onSessionEnded_success() {
    JobInfo jobInfo =
        JobInfo.newBuilder()
            .setLocator(new JobLocator("job_id", "job_name"))
            .setType(
                JobType.newBuilder().setDriver("driver").setDevice("AndroidRealDevice").build())
            .build();
    jobInfo.resultWithCause().setPass();
    when(sessionInfo.getAllJobs()).thenReturn(ImmutableList.of(jobInfo));

    plugin.onSessionEnded(new SessionEndedEvent(sessionInfo, null));

    verify(sessionInfo)
        .setSessionPluginOutput(
            pluginOutputCaptor.capture(), eq(AtsLocalSessionPluginOutput.class));
    AtsLocalSessionPluginOutput pluginOutput = pluginOutputCaptor.getValue().apply(null);
    assertThat(pluginOutput.getResult()).isEqualTo(TestResult.PASS);
    assertThat(pluginOutput.getResultDetail()).isEqualTo("PASS");
  }
}
