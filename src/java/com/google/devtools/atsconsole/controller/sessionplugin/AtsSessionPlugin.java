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

import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginConfig;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginOutput;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import javax.inject.Inject;

/** OmniLab long-running client session plugin for ATS 2.0. */
public class AtsSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SessionInfo sessionInfo;

  /** Set in {@link #onSessionStarting}. */
  private volatile AtsSessionPluginConfig config;

  @Inject
  AtsSessionPlugin(SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event) throws InvalidProtocolBufferException {
    config =
        sessionInfo
            .getSessionPluginExecutionConfig()
            .getConfig()
            .unpack(AtsSessionPluginConfig.class);
    logger.atInfo().log("Config: %s", shortDebugString(config));
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event) {
    AtsSessionPluginOutput output = AtsSessionPluginOutput.getDefaultInstance();
    sessionInfo.setSessionPluginOutput(
        oldOutput -> SessionPluginOutput.newBuilder().setOutput(Any.pack(output)).build());
    logger.atInfo().log("Output: %s", shortDebugString(output));
  }
}
