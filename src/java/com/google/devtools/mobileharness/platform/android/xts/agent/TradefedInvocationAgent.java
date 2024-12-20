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

package com.google.devtools.mobileharness.platform.android.xts.agent;

import static net.bytebuddy.agent.builder.AgentBuilder.Listener.StreamWriting.toSystemError;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.common.flogger.FluentLogger;
import com.google.devtools.mobileharness.platform.android.xts.runtime.XtsTradefedRuntimeInfoMonitor;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Default;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.Transformer.NoOp;
import net.bytebuddy.implementation.MethodDelegation;

/** An agent to record Tradefed runtime info and disable result reporter. */
public class TradefedInvocationAgent {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * The entry point.
   *
   * @param agentArgs [String runtimeInfoFilePath]:[boolean disableResultReporter]
   */
  public static void premain(String agentArgs, Instrumentation inst) {
    new TradefedInvocationAgent().run(agentArgs, inst);
  }

  @SuppressWarnings("StringSplitter")
  private void run(String agentArgs, Instrumentation inst) {
    // Parses arguments.
    String[] arguments = agentArgs.split(":");
    String runtimeInfoFilePath = arguments.length >= 1 ? arguments[0] : "";
    boolean disableResultReporter = arguments.length >= 2 && Boolean.parseBoolean(arguments[1]);

    // Initializes logger.
    TradefedInvocationAgentLogger.init();

    // Disables result reporter.
    if (disableResultReporter) {
      logger.atInfo().log("Disable suite result reporter");
      intercept(
          inst,
          "com.android.tradefed.result.suite.SuiteResultReporter",
          (builder, typeDescription, classLoader, module, protectionDomain) ->
              builder
                  .method(named("invocationEnded").and(takesArguments(1)))
                  .intercept(MethodDelegation.to(NoOp.INSTANCE)),
          /* disableClassFormatChanges= */ false);
    }

    // Records runtime info.
    if (!runtimeInfoFilePath.isEmpty()) {
      logger.atInfo().log("Runtime info file path: %s", runtimeInfoFilePath);
      XtsTradefedRuntimeInfoMonitor.getInstance().start(Path.of(runtimeInfoFilePath));
      intercept(
          inst,
          "com.android.tradefed.invoker.TestInvocation",
          "invoke",
          4,
          TradefedTestInvocationInvokeMethodInterceptor.class);
      intercept(
          inst,
          "com.android.tradefed.cluster.ClusterCommandScheduler$InvocationEventHandler",
          "invocationComplete",
          2,
          TradefedInvocationEventHandlerInvocationCompleteMethodInterceptor.class);
    }
  }

  private static void intercept(
      Instrumentation inst,
      String className,
      String methodName,
      int argumentsLength,
      Class<?> interceptorClass) {
    intercept(
        inst,
        className,
        (builder, typeDescription, classLoader, module, protectionDomain) ->
            builder.visit(
                Advice.to(interceptorClass)
                    .on(named(methodName).and(takesArguments(argumentsLength)))),
        /* disableClassFormatChanges= */ true);
  }

  private static void intercept(
      Instrumentation inst,
      String className,
      Transformer transformer,
      boolean disableClassFormatChanges) {
    AgentBuilder agentBuilder = new Default().with(toSystemError().withErrorsOnly());
    if (disableClassFormatChanges) {
      agentBuilder = agentBuilder.disableClassFormatChanges();
    }
    agentBuilder
        .ignore(none(), not(isBootstrapClassLoader()))
        .type(named(className))
        .transform(transformer)
        .installOn(inst);
  }

  private static class TradefedTestInvocationInvokeMethodInterceptor {

    @Advice.OnMethodEnter()
    public static void onEnter(
        @Advice.This Object testInvocation, @Advice.Argument(value = 0) Object invocationContext) {
      XtsTradefedRuntimeInfoMonitor.getInstance()
          .onInvocationEnter(testInvocation, invocationContext);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This Object testInvocation,
        @Advice.Argument(value = 0) Object invocationContext,
        @Advice.Thrown Throwable exception) {
      XtsTradefedRuntimeInfoMonitor.getInstance()
          .onInvocationExit(testInvocation, invocationContext, exception);
    }
  }

  private static class TradefedInvocationEventHandlerInvocationCompleteMethodInterceptor {

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.This Object invocationEventHandler,
        @Advice.Argument(value = 0) Object invocationContext,
        @Advice.Thrown Throwable exception) {
      XtsTradefedRuntimeInfoMonitor.getInstance()
          .onInvocationComplete(invocationEventHandler, invocationContext, exception);
    }
  }

  private TradefedInvocationAgent() {}
}
