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

import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.joining;

import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DumpEnvVarCommand;

/** Handler for "dump env" commands. */
class DumpEnvVarCommandHandler {

  AtsSessionPluginOutput handle(@SuppressWarnings("unused") DumpEnvVarCommand dumpEnvVarCommand) {
    String result =
        System.getenv().entrySet().stream()
            .sorted(comparingByKey())
            .map(entry -> String.format("\t%s=%s", entry.getKey(), entry.getValue()))
            .collect(joining("\n"));
    return AtsSessionPluginOutput.newBuilder()
        .setSuccess(Success.newBuilder().setOutputMessage(result))
        .build();
  }
}
