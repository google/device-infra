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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableListMultimap;
import com.google.devtools.mobileharness.api.model.job.out.Result;
import com.google.devtools.mobileharness.api.model.job.out.Result.ResultTypeWithCause;
import com.google.devtools.mobileharness.api.model.proto.Test.TestResult;
import com.google.devtools.mobileharness.api.testrunner.device.cache.XtsDeviceCache;
import com.google.devtools.mobileharness.infra.ats.common.XtsPropertyName.Job;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.RunCommand;
import com.google.devtools.mobileharness.infra.client.longrunningservice.constant.SessionProperties;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoFileUtil;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import com.google.wireless.qa.mobileharness.client.api.event.JobEndEvent;
import com.google.wireless.qa.mobileharness.shared.comm.message.TestMessageUtil;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.JobLocator;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfo;
import com.google.wireless.qa.mobileharness.shared.model.job.TestInfos;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Properties;
import com.google.wireless.qa.mobileharness.shared.model.job.out.Timing;
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
public final class AtsSessionPluginTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Bind @Mock private SessionInfo sessionInfo;
  @Bind @Mock private DumpEnvVarCommandHandler dumpEnvVarCommandHandler;
  @Bind @Mock private DumpStackTraceCommandHandler dumpStackCommandHandler;
  @Bind @Mock private DumpUptimeCommandHandler dumpUptimeCommandHandler;
  @Bind @Mock private ListDevicesCommandHandler listDevicesCommandHandler;
  @Bind @Mock private ListModulesCommandHandler listModulesCommandHandler;
  @Bind @Mock private RunCommandHandler runCommandHandler;
  @Bind @Mock private TestMessageUtil testMessageUtil;
  @Bind @Mock private XtsTradefedRuntimeInfoFileUtil xtsTradefedRuntimeInfoFileUtil;
  @Bind @Mock private LocalFileUtil localFileUtil;
  @Bind @Mock private XtsDeviceCache xtsDeviceCache;

  @Inject private AtsSessionPlugin atsSessionPlugin;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void onSessionStarting() throws Exception {
    RunCommand runCommand = RunCommand.getDefaultInstance();
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(AtsSessionPluginConfig.newBuilder().setRunCommand(runCommand).build()))
                .build());
    atsSessionPlugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    verify(sessionInfo).putSessionProperty(SessionProperties.PROPERTY_KEY_COMMAND_ID, "1");
  }

  @Test
  public void onJobEnd_nonTradefedJob_atsModuleRunResultFileWritten() throws Exception {
    JobInfo jobInfo = mock(JobInfo.class);
    TestInfos testInfos = mock(TestInfos.class);
    TestInfo testInfo = mock(TestInfo.class);
    Result result = mock(Result.class);

    when(jobInfo.locator()).thenReturn(new JobLocator("job_id", "job_name"));
    Properties jobProperties = new Properties(new Timing());
    jobProperties.add(Job.IS_XTS_NON_TF_JOB, "true");
    when(jobInfo.properties()).thenReturn(jobProperties);
    when(jobInfo.tests()).thenReturn(testInfos);
    when(testInfos.getAll()).thenReturn(ImmutableListMultimap.of("test_id", testInfo));
    when(testInfo.resultWithCause()).thenReturn(result);
    when(result.get()).thenReturn(ResultTypeWithCause.create(TestResult.PASS, /* cause= */ null));
    when(testInfo.getGenFileDir()).thenReturn("/tmp/test_gen_file_dir");

    JobEndEvent event = new JobEndEvent(jobInfo, /* jobError= */ null);

    atsSessionPlugin.onJobEnd(event);

    verify(localFileUtil)
        .writeToFile("/tmp/test_gen_file_dir/ats_module_run_result.textproto", "result: PASS\n");
  }
}
