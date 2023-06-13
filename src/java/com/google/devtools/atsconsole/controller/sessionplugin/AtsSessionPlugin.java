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
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginConfig.CommandCase;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Failure;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.AtsSessionPluginOutput.Success;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.DumpCommand;
import com.google.devtools.atsconsole.controller.proto.SessionPluginProto.ListCommand;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionEndedEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionInfo;
import com.google.devtools.mobileharness.infra.client.longrunningservice.model.SessionStartingEvent;
import com.google.devtools.mobileharness.infra.client.longrunningservice.proto.SessionProto.SessionPluginOutput;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Optional;
import javax.inject.Inject;

/** OmniLab long-running client session plugin for ATS 2.0. */
public class AtsSessionPlugin {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SessionInfo sessionInfo;
  private final DumpEnvVarCommandHandler dumpEnvVarCommandHandler;
  private final DumpStackTraceCommandHandler dumpStackCommandHandler;
  private final DumpUptimeCommandHandler dumpUptimeCommandHandler;
  private final ListDevicesCommandHandler listDevicesCommandHandler;
  private final ListModulesCommandHandler listModulesCommandHandler;
  private final RunCommandHandler runCommandHandler;

  /** Set in {@link #onSessionStarting}. */
  private volatile AtsSessionPluginConfig config;

  /** Set in {@link #onSessionStarting}. */
  private volatile Optional<AtsSessionPluginOutput> runCommandSessionStartingOutput;

  @Inject
  AtsSessionPlugin(
      SessionInfo sessionInfo,
      DumpEnvVarCommandHandler dumpEnvVarCommandHandler,
      DumpStackTraceCommandHandler dumpStackCommandHandler,
      DumpUptimeCommandHandler dumpUptimeCommandHandler,
      ListDevicesCommandHandler listDevicesCommandHandler,
      ListModulesCommandHandler listModulesCommandHandler,
      RunCommandHandler runCommandHandler) {
    this.sessionInfo = sessionInfo;
    this.dumpEnvVarCommandHandler = dumpEnvVarCommandHandler;
    this.dumpStackCommandHandler = dumpStackCommandHandler;
    this.dumpUptimeCommandHandler = dumpUptimeCommandHandler;
    this.listDevicesCommandHandler = listDevicesCommandHandler;
    this.listModulesCommandHandler = listModulesCommandHandler;
    this.runCommandHandler = runCommandHandler;
  }

  @Subscribe
  public void onSessionStarting(SessionStartingEvent event)
      throws MobileHarnessException, InvalidProtocolBufferException, InterruptedException {
    config =
        sessionInfo
            .getSessionPluginExecutionConfig()
            .getConfig()
            .unpack(AtsSessionPluginConfig.class);
    logger.atInfo().log("Config: %s", shortDebugString(config));

    onSessionStarting();
  }

  private void onSessionStarting()
      throws MobileHarnessException, InvalidProtocolBufferException, InterruptedException {
    if (config.getCommandCase().equals(CommandCase.RUN_COMMAND)) {
      runCommandSessionStartingOutput =
          runCommandHandler.handle(config.getRunCommand(), sessionInfo);
      return;
    } else if (config.getCommandCase().equals(CommandCase.LIST_COMMAND)) {
      ListCommand listCommand = config.getListCommand();
      if (listCommand.getCommandCase().equals(ListCommand.CommandCase.LIST_DEVICES_COMMAND)) {
        AtsSessionPluginOutput output =
            listDevicesCommandHandler.handle(listCommand.getListDevicesCommand());
        setOutput(output);
        return;
      } else if (listCommand
          .getCommandCase()
          .equals(ListCommand.CommandCase.LIST_MODULES_COMMAND)) {
        AtsSessionPluginOutput output =
            listModulesCommandHandler.handle(listCommand.getListModulesCommand());
        setOutput(output);
        return;
      }
    } else if (config.getCommandCase().equals(CommandCase.DUMP_COMMAND)) {
      DumpCommand dumpCommand = config.getDumpCommand();
      if (dumpCommand.getCommandCase().equals(DumpCommand.CommandCase.DUMP_STACK_TRACE_COMMAND)) {
        AtsSessionPluginOutput output =
            dumpStackCommandHandler.handle(dumpCommand.getDumpStackTraceCommand());
        setOutput(output);
        return;
      } else if (dumpCommand
          .getCommandCase()
          .equals(DumpCommand.CommandCase.DUMP_ENV_VAR_COMMAND)) {
        AtsSessionPluginOutput output =
            dumpEnvVarCommandHandler.handle(dumpCommand.getDumpEnvVarCommand());
        setOutput(output);
        return;
      } else if (dumpCommand.getCommandCase().equals(DumpCommand.CommandCase.DUMP_UPTIME_COMMAND)) {
        AtsSessionPluginOutput output =
            dumpUptimeCommandHandler.handle(dumpCommand.getDumpUptimeCommand());
        setOutput(output);
        return;
      }
    }
    setOutput(
        AtsSessionPluginOutput.newBuilder()
            .setFailure(
                Failure.newBuilder()
                    .setErrorMessage(String.format("Unimplemented [%s]", shortDebugString(config))))
            .build());
  }

  @Subscribe
  public void onSessionEnded(SessionEndedEvent event)
      throws MobileHarnessException, InterruptedException {
    if (config.getCommandCase().equals(CommandCase.RUN_COMMAND)) {
      if (runCommandSessionStartingOutput.isPresent()) {
        setOutput(runCommandSessionStartingOutput.get());
        return;
      }

      runCommandHandler.handleResultProcessing(config.getRunCommand(), sessionInfo);
      setOutput(
          AtsSessionPluginOutput.newBuilder()
              .setSuccess(
                  Success.newBuilder()
                      .setOutputMessage(
                          String.format(
                              "run_command session [%s] ended", sessionInfo.getSessionId())))
              .build());
    }
  }

  private void setOutput(AtsSessionPluginOutput output) {
    sessionInfo.setSessionPluginOutput(
        oldOutput -> SessionPluginOutput.newBuilder().setOutput(Any.pack(output)).build());
    logger.atInfo().log("Output: %s", shortDebugString(output));
  }
}
