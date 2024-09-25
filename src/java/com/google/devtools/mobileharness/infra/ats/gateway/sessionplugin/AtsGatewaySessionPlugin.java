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

import static com.google.devtools.mobileharness.shared.util.base.ProtoTextFormat.shortDebugString;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.gateway.proto.Setting.JobConfig;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.gateway.proto.SessionPluginProto.AtsGatewaySessionPluginConfig;
import com.google.devtools.mobileharness.infra.ats.gateway.proto.SessionPluginProto.AtsGatewaySessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.gateway.proto.SessionPluginProto.JobOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.shared.util.jobconfig.JobInfoCreator;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.wireless.qa.mobileharness.shared.model.job.JobInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;

/** Session plugin for sessions from gateway. */
public class AtsGatewaySessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SessionInfo sessionInfo;

  /** Set in {@link #onSessionStarting(SessionStartingEvent)}. */
  private volatile AtsGatewaySessionPluginConfig config;

  @Inject
  AtsGatewaySessionPlugin(SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }

  // TODO: b/333348984 - Implement the method.
  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws MobileHarnessException, InvalidProtocolBufferException, InterruptedException {
    config =
        sessionInfo
            .getSessionPluginExecutionConfig()
            .getConfig()
            .unpack(AtsGatewaySessionPluginConfig.class);
    logger.atInfo().log("Config: %s", shortDebugString(config));

    onSessionStarting();
  }

  private void onSessionStarting() throws MobileHarnessException, InterruptedException {
    List<JobInfo> jobInfos = new ArrayList<>();
    AtsGatewaySessionPluginOutput.Builder pluginOutput = AtsGatewaySessionPluginOutput.newBuilder();
    for (JobConfig jobConfig : config.getJobConfigList()) {
      // TODO: b/333348984 - Implement the details.
      // Creates JobInfo.
      JobInfo jobInfo =
          JobInfoCreator.createJobInfo(
              UUID.randomUUID().toString(),
              /* actualUser= */ "",
              /* jobAccessAccount= */ "",
              jobConfig,
              /* sessionTmpDir= */ "",
              /* sessionGenDir= */ "");
      jobInfos.add(jobInfo);

      // Creates JobOutput.
      pluginOutput.putJobOutput(jobInfo.locator().getId(), JobOutput.getDefaultInstance());
    }
    jobInfos.forEach(sessionInfo::addJob);
    sessionInfo.setSessionPluginOutput(
        oldOutput -> pluginOutput.build(), AtsGatewaySessionPluginOutput.class);
  }
}
