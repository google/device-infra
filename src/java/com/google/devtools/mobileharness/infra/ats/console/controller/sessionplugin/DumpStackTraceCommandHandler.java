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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.mobileharness.infra.ats.console.controller.proto.SessionPluginProto.DumpStackTraceCommand;
import java.util.Map.Entry;

/** Handler for "dump stack" commands. */
class DumpStackTraceCommandHandler {

  AtsSessionPluginOutput handle(
      @SuppressWarnings("unused") DumpStackTraceCommand dumpStackTraceCommand) {
    String result = formatStackTraces();
    return AtsSessionPluginOutput.newBuilder()
        .setSuccess(Success.newBuilder().setOutputMessage(result))
        .build();
  }

  private static String formatStackTraces() {
    Thread currentThread = Thread.currentThread();
    return Thread.getAllStackTraces().entrySet().stream()
        .filter(threadEntry -> !threadEntry.getKey().equals(currentThread))
        .sorted(comparing(threadEntry -> threadEntry.getKey().getName()))
        .map(DumpStackTraceCommandHandler::formatThread)
        .collect(joining("\n\n"));
  }

  private static String formatThread(Entry<Thread, StackTraceElement[]> threadEntry) {
    StringBuilder result = new StringBuilder();
    result.append(threadEntry.getKey());
    for (StackTraceElement stackTraceElement : threadEntry.getValue()) {
      result.append("\n\t");
      result.append(stackTraceElement);
    }
    return result.toString();
  }
}
