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

package com.google.devtools.mobileharness.shared.subprocess.agent;

import static java.util.Arrays.stream;
import static net.bytebuddy.agent.builder.AgentBuilder.Listener.StreamWriting.toSystemError;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.api.model.error.MobileHarnessException;
import com.google.devtools.mobileharness.shared.commandhistory.controller.LocalCommandHistoryManager;
import com.google.devtools.mobileharness.shared.commandhistory.controller.LocalCommandHistoryRecorder;
import com.google.devtools.mobileharness.shared.commandhistory.proto.CommandRecordProto.LocalCommandRecords;
import com.google.devtools.mobileharness.shared.subprocess.listener.ProcessBuilderListener;
import com.google.devtools.mobileharness.shared.subprocess.proto.AgentConfigProto.MobileHarnessAgentConfig;
import com.google.devtools.mobileharness.shared.util.file.local.LocalFileUtil;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

/** Java Agent for Mobile Harness subprocesses. */
public class MobileHarnessAgent {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Entry point of the agent.
   *
   * @param agentArgs {@link MobileHarnessAgentConfig} text proto file path
   */
  public static void premain(String agentArgs, Instrumentation inst) {
    try {
      new MobileHarnessAgent().run(agentArgs, inst);
    } catch (MobileHarnessException | ParseException | RuntimeException e) {
      logger.atWarning().withCause(e).log("Error occurred while running Mobile Harness agent");
    }
  }

  private final LocalFileUtil localFileUtil = new LocalFileUtil();

  private MobileHarnessAgent() {}

  private void run(String agentConfigFilePath, Instrumentation inst)
      throws MobileHarnessException, ParseException {
    MobileHarnessAgentConfig config =
        TextFormat.parse(
            localFileUtil.readFile(agentConfigFilePath),
            ExtensionRegistry.newInstance(),
            MobileHarnessAgentConfig.class);

    if (config.getEnableCommandHistory()) {
      enableCommandHistoryRecording(config, inst);
    }
  }

  private void enableCommandHistoryRecording(
      MobileHarnessAgentConfig config, Instrumentation inst) {
    if (stream(inst.getAllLoadedClasses())
        .anyMatch(clazz -> clazz.getName().equals("java.lang.ProcessBuilder"))) {
      logger.atWarning().log(
          "Can not instrument ProcessBuilder class to record command history"
              + " because the class has been loaded");
      return;
    }

    interceptProcessBuilder(inst);

    LocalCommandHistoryRecorder.getInstance().start();
    ProcessBuilderListener.getInstance().setHandler(LocalCommandHistoryRecorder.getInstance());

    if (!config.getCommandHistoryFilePath().isEmpty()) {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> saveCommandHistoryFile(config.getCommandHistoryFilePath()),
                  "command-history-saver"));
    }
  }

  private void saveCommandHistoryFile(String commandHistoryFilePath) {
    LocalCommandRecords commandRecords = LocalCommandHistoryManager.getInstance().getAll();
    try {
      localFileUtil.writeToFile(commandHistoryFilePath, commandRecords.toByteArray());
    } catch (MobileHarnessException e) {
      logger.atWarning().withCause(e).log(
          "Failed to write command history file: %s", commandHistoryFilePath);
    }
  }

  /** Intercepts {@code java.lang.ProcessBuilder.start()} invocations. */
  private void interceptProcessBuilder(Instrumentation inst) {
    new AgentBuilder.Default()
        .with(toSystemError().withErrorsOnly())
        .disableClassFormatChanges()
        .ignore(none(), not(isBootstrapClassLoader()))
        .type(named("java.lang.ProcessBuilder"))
        .transform(
            (builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(
                    Advice.to(ProcessBuilderInterceptor.class)
                        .on(named("start").and(takesNoArguments()))))
        .installOn(inst);
  }

  private static class ProcessBuilderInterceptor {

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This Object object,
        @Advice.Return Object result,
        @Advice.Thrown Throwable exception) {
      ProcessBuilderListener.getInstance()
          .onProcessStarted(((ProcessBuilder) object).command(), (Process) result, exception);
    }
  }
}
