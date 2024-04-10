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

package com.google.devtools.mobileharness.infra.ats.gateway.sessionplugin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.devtools.mobileharness.api.gateway.proto.Setting.JobConfig;
import com.google.devtools.mobileharness.infra.ats.gateway.proto.SessionPluginProto.AtsGatewaySessionPluginConfig;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginExecutionConfig;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import com.google.protobuf.Any;
import com.google.wireless.qa.mobileharness.shared.proto.Job.JobType;
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
public final class AtsGatewaySessionPluginTest {

  private static final String TEST_DRIVER_CLASS =
      "com.google.devtools.mobileharness.api.testrunner.utp.driver.NoOpDriver";
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Bind @Mock private SessionInfo sessionInfo;

  @Inject AtsGatewaySessionPlugin plugin;

  @Before
  public void setUp() {
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void onSessionStarting_addTwoJobs() throws Exception {
    JobConfig jobConfig1 = createJob("fake1", "serial1");
    JobConfig jobConfig2 = createJob("fake2", "serial2");
    when(sessionInfo.getSessionPluginExecutionConfig())
        .thenReturn(
            SessionPluginExecutionConfig.newBuilder()
                .setConfig(
                    Any.pack(
                        AtsGatewaySessionPluginConfig.newBuilder()
                            .addJobConfig(jobConfig1)
                            .addJobConfig(jobConfig2)
                            .build()))
                .build());

    plugin.onSessionStarting(new SessionStartingEvent(sessionInfo));

    verify(sessionInfo, times(2)).addJob(any());
  }

  private static JobConfig createJob(String jobName, String deviceId) {
    return JobConfig.newBuilder()
        .setName(jobName)
        .setType(JobType.newBuilder().setDevice(deviceId).setDriver(TEST_DRIVER_CLASS))
        .build();
  }
}
