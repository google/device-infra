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

import static com.google.protobuf.TextFormat.shortDebugString;

import com.google.common.eventbus.Subscribe;
import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest;
import com.google.devtools.mobileharness.infra.ats.server.proto.ServiceProto.SessionRequest.RequestCase;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import javax.inject.Inject;

/** */
final class AtsServerSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Set in {@link #onSessionStarting}. */
  private volatile SessionRequest request;

  private final SessionInfo sessionInfo;
  private final NewMultiCommandRequestHandler newMultiCommandRequestHandler;

  @Inject
  AtsServerSessionPlugin(
      SessionInfo sessionInfo, NewMultiCommandRequestHandler newMultiCommandRequestHandler) {
    this.sessionInfo = sessionInfo;
    this.newMultiCommandRequestHandler = newMultiCommandRequestHandler;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws InvalidProtocolBufferException, InterruptedException, MobileHarnessException {
    request =
        sessionInfo.getSessionPluginExecutionConfig().getConfig().unpack(SessionRequest.class);
    logger.atInfo().log("Config: %s", shortDebugString(request));

    if (request.getRequestCase().equals(RequestCase.NEW_MULTI_COMMAND_REQUEST)) {
      newMultiCommandRequestHandler.handle(request.getNewMultiCommandRequest(), sessionInfo);
    }
  }
}
