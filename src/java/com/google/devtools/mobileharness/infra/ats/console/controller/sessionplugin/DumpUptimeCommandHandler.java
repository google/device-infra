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

import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DumpUptimeCommand;
import com.google.devtools.mobileharness.infra.client.longrunningservice.Annotations.ServerStartTime;
import com.google.devtools.mobileharness.shared.util.time.TimeUtils;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;

/** Handler for "dump uptime" commands. */
class DumpUptimeCommandHandler {

  private final Instant serverStartTime;

  @Inject
  DumpUptimeCommandHandler(@ServerStartTime Instant serverStartTime) {
    this.serverStartTime = serverStartTime;
  }

  AtsSessionPluginOutput handle(@SuppressWarnings("unused") DumpUptimeCommand dumpUptimeCommand) {
    Duration elapsedTime = Duration.between(serverStartTime, Instant.now());
    String result =
        String.format(
            "OLC server has been running for %s", TimeUtils.toReadableDurationString(elapsedTime));
    return AtsSessionPluginOutput.newBuilder()
        .setSuccess(Success.newBuilder().setOutputMessage(result))
        .build();
  }
}
